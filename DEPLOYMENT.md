# Deploying ImpulseLock

A step-by-step runbook for the one deployment this project targets: **Aiven (MySQL) → Render
(backend) → Vercel (frontend)**.

For *why* this shape — why the frontend proxies instead of calling Render directly, why TLS is
mandatory, what Flyway does on first boot — see [`docs/v2/deployment-plan.md`](docs/v2/deployment-plan.md).
This file is only the "what do I click, in what order."

---

## Before you start

You need: a GitHub account with this repo pushed, plus free accounts on
[Aiven](https://aiven.io), [Render](https://render.com), and [Vercel](https://vercel.com).

**Do the steps in order.** Each platform needs a value the previous one produces:

```
Aiven          →  host, port, user, password
   ↓
Render         →  needs the DB values; produces https://<name>.onrender.com
   ↓
Vercel         →  needs the Render URL; produces https://<project>.vercel.app
   ↓
back to Render →  needs the Vercel URL for APP_CORS_ALLOWED_ORIGINS
```

That last arrow is a genuine chicken-and-egg: each side needs the other's URL. **The first deploy
of each service is expected to be broken end-to-end until Step 4 closes the loop.** That is
normal, not a mistake you made.

### Generate your JWT secret now

You will need it in Step 2. It must be **at least 32 characters** — `JwtService` feeds it straight
into `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))`, and jjwt rejects anything under 256 bits for
HS256 with a `WeakKeyException` at startup.

```bash
# macOS / Linux / Git Bash
openssl rand -base64 48
```

```powershell
# Windows PowerShell
$b = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
[Convert]::ToBase64String($b)
```

Save the output somewhere for the next few minutes. **Never use the default committed in
`application.properties`** — it is public in git history, and anyone who reads it can mint valid
access tokens for your deployment.

---

## Step 1 — Aiven: create the MySQL service

1. Aiven console → **Create service** → **MySQL**.
2. Pick the free plan, any cloud/region (choose one geographically near your Render region).
3. Wait for the service state to go from *Rebuilding* to **Running**. This takes a few minutes.
4. **Create the database.** Open the service → **Databases** tab → create one named
   `impulselock`.

   > This step is not optional and is the single most common first-deploy failure. Aiven
   > provisions the service with only `defaultdb`. Flyway creates *tables*, not *schemas* — if
   > the database in your JDBC URL does not exist, the app dies at connection time with
   > `Unknown database 'impulselock'` before Flyway ever runs.
   >
   > If you would rather not create one, use `defaultdb` in the URL below instead. Either works;
   > just make sure the name in the URL matches something that exists and is **empty** (Flyway
   > refuses a non-empty schema that has no `flyway_schema_history` table).

5. From the service **Overview** tab, copy these four values:

   | Aiven field | You'll use it as |
   |---|---|
   | Host | the `<host>` in the JDBC URL |
   | Port | the `<port>` in the JDBC URL |
   | User | `SPRING_DATASOURCE_USERNAME` (usually `avnadmin`) |
   | Password | `SPRING_DATASOURCE_PASSWORD` |

6. Assemble your JDBC URL — you will paste this into Render in the next step:

   ```
   jdbc:mysql://<host>:<port>/impulselock?sslMode=REQUIRED
   ```

   **The `?sslMode=REQUIRED` suffix is required.** Aiven refuses unencrypted connections, and the
   driver's default (`sslMode=PREFERRED`) silently downgrades to plaintext if the TLS handshake
   fails rather than erroring. `REQUIRED` makes that a hard failure. Do not use the older
   `useSSL=true&requireSSL=true` spelling — mysql-connector-j still accepts it but marks it
   deprecated, and ignores it entirely whenever `sslMode` is also present.

---

## Step 2 — Render: deploy the backend

1. Render dashboard → **New** → **Web Service** → connect this GitHub repo.
2. Configure:

   | Setting | Value |
   |---|---|
   | Language / Runtime | **Docker** |
   | Dockerfile Path | `./Dockerfile` (repo root — *not* `frontend/Dockerfile`) |
   | Branch | `main` |
   | Health Check Path | `/actuator/health` |
   | Instance Type | Free |

   The root `Dockerfile` is the same multi-stage build `docker-compose.yml` uses locally, so what
   Render builds is what you have already run.

3. Add the environment variables, in this order (Environment tab):

   | # | Variable | Value |
   |---|---|---|
   | 1 | `SPRING_DATASOURCE_URL` | the JDBC URL you assembled in Step 1.6 |
   | 2 | `SPRING_DATASOURCE_USERNAME` | Aiven user, usually `avnadmin` |
   | 3 | `SPRING_DATASOURCE_PASSWORD` | Aiven password — **mark as secret** |
   | 4 | `JWT_SECRET` | the value you generated above — **mark as secret** |
   | 5 | `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` for now; you'll fix this in Step 4 |
   | 6 | `SPRING_PROFILES_ACTIVE` | `prod` |

   Optional, all have working defaults — skip unless you want to change them:
   `JWT_ACCESS_TOKEN_TTL_MINUTES` (15), `JWT_REFRESH_TOKEN_TTL_DAYS` (7),
   `LOGIN_RATE_LIMIT_MAX_ATTEMPTS` (5), `LOGIN_RATE_LIMIT_WINDOW_MINUTES` (15).

   **Do not set `PORT`.** Render injects it, and `application.properties` binds
   `server.port=${PORT:8080}` to follow whatever Render assigns.

   **Do not skip `SPRING_PROFILES_ACTIVE=prod`.** Without it, Swagger UI and the raw OpenAPI spec
   stay publicly reachable on your Render URL.

4. Deploy. Watch the logs. A healthy first boot shows Flyway applying all five migrations:

   ```
   Successfully validated 5 migrations
   Creating Schema History table `impulselock`.`flyway_schema_history`
   Migrating schema `impulselock` to version "1 - init schema"
   ...
   Successfully applied 5 migrations
   ```

   Then Tomcat starting, then `Started ImpulseLockApplication`.

5. Confirm it's alive:

   ```bash
   curl https://<your-service>.onrender.com/actuator/health
   # {"status":"UP"}
   ```

6. **Copy your Render URL** (`https://<name>.onrender.com`). Step 3 needs it.

> On the free tier, Render spins the service down after inactivity, so the first request after an
> idle period takes ~50s to cold-start. Flyway re-runs on every boot, but after this first deploy
> it only reads `flyway_schema_history`, finds nothing pending, and moves on.

---

## Step 3 — Vercel: deploy the frontend

1. **Put the real Render URL into the rewrite first.** Edit `frontend/vercel.json` and replace the
   placeholder host in the `"destination"` field:

   ```json
   {
     "rewrites": [
       { "source": "/api/v2/:path*", "destination": "https://YOUR-SERVICE.onrender.com/api/v2/:path*" }
     ]
   }
   ```

   Replace **only** the host `REPLACE-WITH-RENDER-URL.onrender.com`. Keep the `https://` prefix
   and the `/api/v2/:path*` suffix exactly as they are. This is the only place in the repo where
   the Render URL appears.

   Commit and push:
   ```bash
   git add frontend/vercel.json
   git commit -m "chore: point vercel rewrite at the deployed Render backend"
   git push
   ```

2. Vercel dashboard → **Add New** → **Project** → import this repo.
3. Configure:

   | Setting | Value |
   |---|---|
   | Framework Preset | Create React App |
   | Root Directory | `frontend` |
   | Build Command | `npm run build` |
   | Output Directory | `build` |

4. **Do not set `REACT_APP_API_BASE_URL`.** Leave the Environment Variables section empty.

   > `getApiBaseUrl()` in `frontend/src/api.js` returns `''` when that variable is unset, so the
   > app emits relative `/api/v2/...` URLs — which is exactly what the rewrite intercepts. Setting
   > it switches the client to absolute cross-origin URLs and reintroduces the `SameSite=Strict`
   > cookie failure the rewrite exists to prevent.

5. Deploy, then **copy your Vercel URL** (`https://<project>.vercel.app`).

---

## Step 4 — Close the loop

Back in Render → your service → **Environment**:

- Set `APP_CORS_ALLOWED_ORIGINS` to your Vercel URL — scheme included, **no trailing slash**:

  ```
  https://your-project.vercel.app
  ```

Save. Render redeploys automatically.

> With the rewrite in place, the browser is same-origin and never sends a CORS preflight, so this
> variable is not what makes day-to-day traffic work. It still matters: `CorsConfig` sets
> `allowCredentials(true)`, and Spring rejects a wildcard origin in that mode. Leaving it at
> `localhost:3000` would block any direct browser call to the Render URL.

---

## Step 5 — Verify

Run these against your **Vercel** URL, not Render — the whole point is that the browser only ever
talks to Vercel.

```bash
# 1. The rewrite reaches the backend (400 = validation rejected it = the request got there)
curl -i -X POST https://your-project.vercel.app/api/v2/auth/login \
  -H 'Content-Type: application/json' -d '{}'

# 2. Register a real account
curl -i -X POST https://your-project.vercel.app/api/v2/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","email":"demo@example.com","password":"YourStrongPassw0rd!"}'

# 3. Log in and confirm the refresh cookie comes back
curl -i -X POST https://your-project.vercel.app/api/v2/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"YourStrongPassw0rd!"}'
# look for: Set-Cookie: refreshToken=...; Path=/api/v2/auth; Secure; HttpOnly; SameSite=Strict
```

Then in a browser, the real test that everything is wired correctly:

1. Open your Vercel URL and log in.
2. **Hard-refresh the page (Ctrl+Shift+R).**
3. You should still be logged in.

Step 3 is the one that matters. A hard refresh drops the in-memory access token, so the app must
silently call `/api/v2/auth/refresh` — which only works if the `SameSite=Strict` cookie was sent,
which only works if the rewrite is doing its job. **If you get bounced to the login screen, the
rewrite is wrong** — check Step 3.1.

---

## Step 6 — Promote yourself to admin

Registration always creates a plain `ROLE_USER` account by design, and this is a fresh database,
so any admin promotion you did locally does not carry over.

Connect to Aiven (TLS is mandatory here too):

```bash
mysql --host=<aiven-host> --port=<aiven-port> --user=avnadmin --password \
      --ssl-mode=REQUIRED impulselock
```

No `mysql` CLI? Any GUI client works — DBeaver, MySQL Workbench, TablePlus — just set SSL to
required in the connection settings.

```sql
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'demo' AND r.name = 'ROLE_ADMIN';
```

**Log out and log back in.** The role is baked into the JWT at login time, so an already-issued
access token will not pick it up. The **Admin** tab appears after the fresh login.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Unknown database 'impulselock'` | The database was never created in Aiven | Step 1.4 — create it, or point the URL at `defaultdb` |
| `Communications link failure` / TLS errors at boot | `?sslMode=REQUIRED` missing from the URL | Step 1.6 |
| `WeakKeyException` at startup | `JWT_SECRET` shorter than 32 characters | Regenerate with the command above |
| `Found non-empty schema ... without schema history table` | You pointed at a database that already has tables | Use a clean/empty database |
| Login works, but a hard refresh logs you out | The Vercel rewrite isn't matching — browser is going cross-site, cookie withheld | Step 3.1; confirm `REACT_APP_API_BASE_URL` is **not** set |
| API calls 404 from the browser | Rewrite `source`/`destination` path mismatch | Both must keep the `/api/v2/` prefix |
| CORS error calling Render directly | `APP_CORS_ALLOWED_ORIGINS` wrong or has a trailing slash | Step 4 |
| First request takes ~50s | Render free-tier cold start | Expected; not a bug |
| `/swagger-ui.html` returns 404 in prod | `SPRING_PROFILES_ACTIVE=prod` disables springdoc | Working as intended |

**Where to look:** Render → your service → **Logs** shows Spring Boot startup, Flyway output, and
every stack trace. Vercel → project → **Deployments** → a deployment → **Functions/Logs** shows
rewrite behavior. Most first-deploy problems are visible in Render's logs within the first 30
seconds of boot.

---

## Redeploying and rolling back

- **Both platforms auto-deploy on push to `main`.** Render rebuilds the Docker image; Vercel
  rebuilds the CRA bundle. No manual step, and `.github/workflows/cd.yml`'s `deploy` job stays
  disabled (`if: false`) precisely so it doesn't duplicate them.
- **Rollback**: Render → **Events** → *Rollback* to a previous deploy. Vercel → **Deployments** →
  *Promote to Production* on an older one.
- **Database rollback is not a thing here.** Flyway migrations are forward-only by convention —
  no down-migrations are authored. Reversing a schema change means writing a new `V6__...sql`,
  not rolling the database back. Rolling the *app* back to a version that predates a migration
  will fail startup on `ddl-auto=validate`, which is the intended loud failure rather than a
  silent schema mismatch.
