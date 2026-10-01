# SplitUp — Group Expense Splitter

SplitUp is a Java-based web application for managing shared expenses between friends or roommates and simplifying the debts between group members.

## Features

- Create a group with multiple members
- Share a group using a 6-character group code
- Add shared expenses
- Split expenses equally among selected members
- Split expenses using custom amounts
- View individual balances
- Generate a simplified settlement plan
- Delete expenses and automatically recalculate balances

## How It Works

When an expense is added:

1. The payer is credited with the amount they paid.
2. Each person included in the split is charged their share.
3. The application calculates everyone's net balance.
4. A debt simplification algorithm matches people who owe money with people who are owed money.

The settlement algorithm uses two `PriorityQueue` heaps:

- A max-heap for creditors
- A min-heap for debtors

The greedy algorithm runs in **O(n log n)** and produces at most **n - 1 transactions** for `n` people with non-zero balances.

The greedy approach is not guaranteed to find the mathematically minimum number of transactions. It is used as a practical approach for this project.

## Tech Stack

### Backend
- Java
- Object-Oriented Programming
- Java Collections Framework
- PriorityQueue
- JDK built-in HTTP Server
- Custom lightweight JSON handling

### Frontend
- HTML
- CSS
- JavaScript

### Storage
The application uses **in-memory storage** using Java Collections.

No MySQL or external database is required.

Because the data is stored in memory, groups and expenses are cleared when the server restarts.

## Project Structure

```text
SplitUp/
├── src/
│   └── splitter/
│       ├── Server.java
│       ├── Store.java
│       ├── Group.java
│       ├── Expense.java
│       ├── DebtSimplifier.java
│       └── JsonUtil.java
│
├── web/
│   ├── index.html
│   ├── app.js
│   └── style.css
│
├── .gitignore
└── README.md
```

## Running Locally

Make sure Java JDK is installed.

Compile the project:

```bash
javac -d out src/splitter/*.java
```

Run the server:

```bash
java -cp out splitter.Server
```

Then open:

```text
http://localhost:8080
```

## Deployment

SplitUp can be deployed as a Java web service.

### Build Command

```bash
javac -d out src/splitter/*.java
```

### Start Command

```bash
java -cp out splitter.Server
```

The server uses the `PORT` environment variable when deployed.


## Purpose

This project demonstrates:

- Object-Oriented Programming
- Java Collections
- Priority Queues
- Greedy algorithms
- HTTP server development
- Frontend-backend communication
- Input validation
- Concurrent request handling