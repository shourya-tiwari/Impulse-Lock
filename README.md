# 🚀 ImpulseLock

### AI-Inspired Rule-Based Transaction Risk Evaluation System

---

## 📌 Overview

**ImpulseLock** is a full-stack application designed to prevent impulsive or risky financial transactions.
It evaluates transactions in real-time using a **rule-based decision engine** combined with **user-defined behavioral preferences**.

The system analyzes each transaction and returns a decision:

* ✅ **ALLOW** → Safe transaction
* ⚠️ **DELAY** → Risky, reconsider
* ❌ **BLOCK** → Dangerous, restrict

---

## 🎯 Key Features

### 🧠 Intelligent Decision Engine

* Modular rule-based system
* Extensible architecture (Strategy Pattern)
* Dynamic risk scoring

### 👤 User Preference Configuration

* Daily spending limit
* Night spending restriction
* Sensitivity level
* Restricted categories

### 💳 Transaction Evaluation

* Real-time API evaluation
* Risk scoring + explanation
* Persistent transaction storage

### 🌐 Full-Stack System

* Backend: Spring Boot (Java)
* Frontend: React.js
* Database: MySQL

---

## 🏗️ Project Architecture

```
ImpulseLock/
├── src/main/java/com/impulselock/impulselock/
│   ├── controller/     # REST APIs
│   ├── service/        # Business logic
│   ├── engine/         # Decision Engine
│   ├── rules/          # Rule implementations
│   ├── repository/     # JDBC database layer
│   ├── model/          # Core data models
│   ├── config/         # Bean & CORS configuration
│   ├── exception/      # Error handling
│   └── dto/            # Request/response objects
│
├── frontend/           # React UI
│
├── pom.xml             # Maven config
└── README.md
```

---

## ⚙️ How It Works

### 🔄 Flow

1. User sets preferences
2. User initiates transaction
3. Backend fetches user profile
4. DecisionEngine evaluates rules
5. Risk score is calculated
6. Decision is returned

---

## 🧠 Decision Logic

The system evaluates transactions using multiple rules:

* 💰 **HighAmountRule** → Checks daily limit
* 🌙 **NightSpendingRule** → Restricts night usage
* ⚡ **FrequentTransactionRule** → Detects rapid activity
* 🛑 **CategoryRestrictionRule** → Blocks restricted categories
* 🎚️ **SensitivityLevelRule** → Adjusts strictness

---

## 🛠️ Tech Stack

| Layer       | Technology        |
| ----------- | ----------------- |
| Backend     | Java, Spring Boot |
| Frontend    | React.js          |
| Database    | MySQL             |
| Build Tool  | Maven             |
| API Testing | Postman           |

---

## 🧪 API Endpoints

### 🔹 Evaluate Transaction

```
POST /transaction/evaluate
```

#### Request:

```json
{
  "userId": "U101",
  "amount": 1000,
  "category": "shopping",
  "merchant": "Amazon"
}
```

#### Response:

```json
{
  "decisionType": "BLOCK",
  "riskScore": 120.0,
  "explanation": "Transaction exceeds daily limit..."
}
```

---

### 🔹 Create User (Preferences)

```
POST /users
```

#### Request:

```json
{
  "userId": "U101",
  "dailyLimit": 3000,
  "nightSpendingAllowed": false,
  "sensitivityLevel": 5
}
```

---

## 💻 How to Run Locally

---

### 🔹 1. Clone Repository

```bash
git clone https://github.com/<your-username>/ImpulseLock.git
cd ImpulseLock
```

---

### 🔹 2. Setup MySQL Database

Create database:

```sql
CREATE DATABASE impulselock;
USE impulselock;
```

Create tables:

```sql
CREATE TABLE users (
    user_id VARCHAR(50) PRIMARY KEY,
    daily_limit DOUBLE,
    night_spending_allowed BOOLEAN,
    sensitivity_level INT
);

CREATE TABLE transactions (
    transaction_id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50),
    amount DOUBLE,
    category VARCHAR(50),
    merchant VARCHAR(100),
    timestamp DATETIME
);
```

---

### 🔹 3. Configure Backend

Edit:

```
src/main/resources/application.properties
```

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/impulselock
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

---

### 🔹 4. Run Backend

```bash
mvn spring-boot:run
```

Server runs at:

```
http://localhost:8080
```

---

### 🔹 5. Run Frontend

```bash
cd frontend
npm install
npm start
```

Frontend runs at:

```
http://localhost:3000
```

---

### 🔹 6. Test Application

1. Open frontend
2. Create user preferences
3. Evaluate transactions
4. View decision + risk

---

## 🎨 UI Features

* Dark modern fintech UI
* Real-time evaluation
* Color-coded decisions

  * 🟢 ALLOW
  * 🟡 DELAY
  * 🔴 BLOCK

---

## ⚠️ Common Issues & Fixes

### ❌ "Failed to fetch"

✔ Ensure backend is running
✔ Check correct API URL
✔ Add CORS configuration

---

### ❌ DB connection error

✔ Check MySQL running
✔ Verify username/password

---

### ❌ Port issues

✔ Backend → 8080
✔ Frontend → 3000

---

## 🔮 Future Enhancements

* 🤖 Machine Learning-based risk prediction
* 📊 Analytics dashboard
* 📱 Mobile app integration
* 🔐 Authentication & user accounts

---

## 👥 Team Contribution

| Member   | Contribution                       |
| -------- | ---------------------------------- |
| Shourya      | Decision Engine, Service, Frontend |
| Adhya | Controller & API                   |
| Aditi | Database & Repository              |
| Rishik | Config & Exception Handling        |

---

## 📈 Project Highlights

* Layered architecture
* Clean separation of concerns
* Extensible rule engine
* Full-stack integration

---

## 🧠 Key Learning Outcomes

* Spring Boot architecture
* REST API design
* JDBC integration
* React frontend development
* Git version control

---

## 📌 Conclusion

ImpulseLock demonstrates how **rule-based intelligence** can be used to simulate financial decision-making and prevent impulsive behavior in real-world systems.

---

## ⭐ If you like this project, give it a star!
