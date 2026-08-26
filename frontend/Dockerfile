# Multi-stage build - see docs/v2/deployment-plan.md#dockerfiles.
# Stage 1: build the static React bundle.
FROM node:20-alpine AS build
WORKDIR /build
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: serve the static bundle with nginx, reverse-proxying /api/v2/* to the backend
# container (see nginx.conf) - this replaces CRA's dev-only "proxy" field in package.json,
# which only works with `npm start`, not a production build.
FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/build /usr/share/nginx/html
EXPOSE 80
