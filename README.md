# 🚦 ImpulseLock – Rule-Based Spending Control System (Phase 1)

ImpulseLock is a Java-based backend system designed to **detect and control impulsive spending behavior** in real time using a **rule-based decision engine**.

Instead of focusing on fraud, ImpulseLock focuses on **user-side financial discipline**, applying configurable rules to decide whether a transaction should be **allowed, delayed, or blocked**.

This repository currently contains **Phase 1** of the project, which focuses on **problem definition and object-oriented system design**.

---

## 🧠 Problem Statement

Many users lose money due to impulsive or poorly-timed spending (late-night purchases, exceeding daily limits, etc.).  
Most financial apps only provide **post-spending reports**, not **real-time prevention**.

ImpulseLock aims to:
- Analyze a transaction at execution time
- Apply behavioral spending rules
- Generate an explainable decision:
  - **ALLOW**
  - **DELAY**
  - **BLOCK**

---

## 📌 Phase 1 Scope (Current)

Phase 1 focuses strictly on **core OOP design**, as per academic rubrics.

### ✅ Included
- Domain models (`Transaction`, `UserProfile`, `Decision`)
- Rule Engine using **Strategy Pattern**
- Multiple spending rules
- Decision Engine with risk scoring
- Console-based simulation for demonstration

### ❌ Not Included (Planned for Phase 2)
- Database (JDBC / JPA)
- File handling
- REST APIs
- Authentication
- Frontend / UI

---

## 🧱 System Design Overview (Phase 1)

```text
Transaction + UserProfile
↓
SpendingRule (interface)
↓
Concrete Rule Implementations
↓
DecisionEngine
↓
Decision (ALLOW / DELAY / BLOCK)

```

This design demonstrates:

* Abstraction via interfaces
* Polymorphism via multiple rule implementations
* Clean separation of concerns
* Industry-standard **Strategy Pattern**

---

## 🛠️ Tech Stack (Phase 1)

* **Java 17**
* **Spring Boot 4**
* **Maven**
* Console-based execution (no DB, no APIs yet)

---

## 📁 Project Structure

```text
src/main/java/com/impulselock/impulselock
│
├── engine        → Decision logic
├── model         → Domain models
├── rules         → Spending rules (Strategy Pattern)
└── ImpulseLockApplication.java

```

---

## 🚀 How to Run the Project

### 1️⃣ Prerequisites

Make sure you have:

* Java **17 or higher**
* Maven installed
* Git installed

Check versions:

```bash
java -version
mvn -version
git --version

```

---

### 2️⃣ Clone the Repository

```bash
git clone https://github.com/shourya-tiwari/ImpulseLock.git
cd impulselock

```

---

### 3️⃣ Build the Project

```bash
mvn clean compile

```

You should see:

```text
BUILD SUCCESS

```

---

### 4️⃣ Run the Application

```bash
mvn spring-boot:run

```

On successful startup, you will see:

```text
Started ImpulseLockApplication

```

Followed by console output similar to:

```text
Decision: DELAY
Risk Score: 70.0
Reason: Transaction amount exceeds daily spending limit;

```

This output demonstrates the **rule engine and decision logic working correctly**.

---

## 🧪 Example Scenario (Phase 1 Demo)

* User daily limit: ₹2000
* Transaction amount: ₹3500
* Night spending: not allowed

**Result:**

* Rule triggered: `HighAmountRule`
* Risk Score: `70`
* Decision: **DELAY**

---

## 🎯 Academic Relevance

This phase satisfies:

* **Problem Definition**
* **Object-Oriented Design**
* **Use of Design Patterns**
* **Clean modular architecture**

Phase 2 will extend this system with persistence, APIs, and real-world integrations.

---

## 🔮 Future Enhancements (Phase 2+)

* Database integration (JDBC / JPA)
* File-based transaction ingestion
* REST APIs for transaction evaluation
* User-configurable rules
* Dashboard / frontend integration

---

## 📌 Status

✔ Phase 1 completed

⏳ Phase 2 planned

