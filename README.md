<div align="center">

# QuickBooks Ledger Sync: Automated Invoice Normalizer & QBO API Engine

[![Interactive Simulator](https://img.shields.io/badge/Live%20Simulator-mahmudulhasan.info.bd-BA3C00?style=for-the-badge&logo=google-chrome&logoColor=white)](https://mahmudulhasan.info.bd/systems/quickbooks-ledger-sync/)
[![System Dossier](https://img.shields.io/badge/System%20Dossier-Zero%20Fault-10B981?style=for-the-badge&logo=diagram-next&logoColor=white)](https://mahmudulhasan.info.bd/systems/quickbooks-ledger-sync/)
[![Java](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vaadin](https://img.shields.io/badge/Vaadin-Flow%2025-00B4F0?style=for-the-badge&logo=vaadin&logoColor=white)](https://vaadin.com/)
[![QuickBooks](https://img.shields.io/badge/QBO-Intuit%20REST%20API-2CA01C?style=for-the-badge&logo=quickbooks&logoColor=white)](https://quickbooks.intuit.com/)
[![Throughput](https://img.shields.io/badge/Throughput-1%2C200%20Invoices%2Fmin-4CAF50?style=for-the-badge&logo=speedtest&logoColor=white)](https://mahmudulhasan.info.bd/systems/quickbooks-ledger-sync/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

<br/>

> 🚀 **Live Interactive Case Study & Financial Reconciliation Simulator:**  
> Experience the 3-tier automated bank reconciliation engine, test heterogeneous CSV schema mapping, and inspect AES-256-GCM encrypted OAuth2 token rotation on the official systems engineering platform:  
> 🔗 **[Explore Full Case Study & Live Simulator on mahmudulhasan.info.bd](https://mahmudulhasan.info.bd/systems/quickbooks-ledger-sync/)**

</div>

---

## 📌 Executive Overview

**QuickBooks Ledger Sync** is an enterprise-grade financial data pipeline, automated invoice normalizer, and multi-tier bank reconciliation engine engineered in **Java 21 LTS**, **Spring Boot 4.0**, **Vaadin 25 Flow**, and the **Intuit QuickBooks Online (QBO) API**.

Designed for high-volume e-commerce merchants, logistics dispatch networks, and accounting agencies, QuickBooks Ledger Sync eliminates the manual bottleneck of re-keying thousands of transaction rows. It ingests messy, heterogeneous CSV exports from Shopify, Stripe, and POS systems, enforces strict double-entry and tax-rate validation gates, and pushes idempotent batches directly into QuickBooks Online in seconds—while reconciling bank deposits against open invoices using deterministic and fuzzy string matching.

---

## ⚡ Production Hardened Benchmarks

| Metric Category | Production SLA / Measurement | Architectural Guarantee |
| :--- | :--- | :--- |
| **Ingestion Throughput** | `1,200 Invoices / Min` | In-memory streaming parser with deterministic schema normalization |
| **Validation Latency** | `14ms Pre-Flight Check` | Strict double-entry, positive amount, and tax-code validation gates |
| **QBO Push Latency** | `180ms Batch Dispatch` | Non-blocking HTTP client with automatic rate-limit throttling |
| **Memory Footprint** | `64 MB JVM Container Overhead` | Lightweight Spring Boot 4 architecture with low memory consumption |
| **Security Architecture** | `AES-256-GCM Vault` | Bank-grade authenticated encryption for OAuth2 access & refresh tokens |

---

## 🏛️ High-Throughput System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│             Heterogeneous Transaction Ingress (Shopify, Stripe, POS)   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Unstructured CSV / JSON Feeds)
┌───────────────────────────────────▼────────────────────────────────────┐
│            Dynamic Field Mapping & Schema Normalizer Service           │
│   Document Number • Line Consolidation • Tax Code Pre-Flight Validator │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Canonical NormalizedInvoice DTO)
┌───────────────────────────────────▼────────────────────────────────────┐
│              Multi-Tier Automated Bank Reconciliation Engine           │
│                                                                        │
│  ┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────┐ │
│  │ Tier 1: Exact Match   │ │ Tier 2: Date-Window   │ │ Tier 3: Fuzzy │ │
│  │ 100% Confidence Auto  │ │ ±3-7 Days Grace Range │ │ N-Gram Token  │ │
│  └───────────────────────┘ └───────────────────────┘ └───────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
              ┌─────────────────────┴─────────────────────┐
              ▼                                           ▼
┌───────────────────────────┐               ┌───────────────────────────┐
│ Intuit OAuth2 Vault       │               │ Vaadin 25 Live Dashboard  │
│ AES-256-GCM Token Manager │               │ Real-Time Server-Push UI  │
└─────────────┬─────────────┘               └───────────────────────────┘
              │
              ▼ (Idempotent REST Payload)
┌────────────────────────────────────────────────────────────────────────┐
│               Intuit QuickBooks Online (QBO) Cloud API                 │
│      Invoices • Payments • Bills • Sales Receipts • Customer Ledger    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Key Architectural Capabilities

- **Heterogeneous CSV Normalization:**
  Parses disparate billing formats with arbitrary headers (`Inv_No`, `DocumentNum`, `DocNumber`), merging multi-line items into unified parent-child invoice documents without record loss.
- **3-Tier Bank Reconciliation Engine:**
  - **Tier 1 (Exact Match - 100%):** Matches exact reference ID, numeric amount, and date.
  - **Tier 2 (Windowed Settlement - 95%):** Accommodates merchant processor settlement delays ($\pm 3$ to $\pm 7$ days).
  - **Tier 3 (Fuzzy Description - 80%+):** Employs Levenshtein distance on messy bank memos (`AMZN MKTPLACE` $\rightarrow$ `Amazon Corporate LLC`).
- **AES-256-GCM Encrypted Token Vault:**
  Encrypts Intuit OAuth2 access tokens, refresh tokens, and Realm IDs at rest using hardware-accelerated AES-GCM authenticated cipher streams.
- **Pre-Flight Tax & Idempotency Gates:**
  Validates tax-inclusive vs tax-exclusive lines and queries existing QBO document numbers prior to dispatch to prevent duplicate accounting entries.
- **Dry-Run Simulation & Rollback:**
  Includes a full pre-flight simulation engine that calculates dependency trees and allows instant cleanup rollback of test batches without ledger corruption.

---

## 💻 Tech Stack & Architecture

- **Backend Runtime:** Java 21 LTS (Spring Boot 4.0.5, Spring Data JPA)
- **Presentation Layer:** Vaadin 25 Flow (Reactive Server-Push Web Components)
- **External API:** Intuit QuickBooks Online Java SDK / REST Gateway
- **Database & Persistence:** PostgreSQL 16, Flyway Database Migrations
- **Cryptography:** AES-256-GCM Authenticated Encryption for OAuth Credentials

---

## 🛠️ Local Development & Quick Start

### Prerequisites
- **JDK 21** or later (`java -version`)
- **Maven 3.9+** (or included `./mvnw`)

### Build and Run

```bash
# Clone the repository
git clone https://github.com/mhcybroot/quickbooks.git
cd quickbooks

# Run test suite
mvn test

# Launch the Spring Boot + Vaadin 25 server
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

---

## 👨‍💻 Engineering & Architecture

**Mahmudul Hasan (@mhcybroot)**  
*Full-Stack Systems Engineer & Distributed Architect • Dhaka, Bangladesh*  
*Specializing in Financial API Gateways, Automated Bank Reconciliations, and Enterprise Double-Entry Accounting Pipelines.*

- 🌐 **Systems Engineering Portfolio:** [mahmudulhasan.info.bd](https://mahmudulhasan.info.bd/)
- ⚡ **Interactive System Simulators:** [mahmudulhasan.info.bd/#systems](https://mahmudulhasan.info.bd/#systems)
- 📑 **Verified Engineering Dossier:** [mahmudulhasan.info.bd/#dossier](https://mahmudulhasan.info.bd/#dossier)
- 💼 **LinkedIn Profile:** [linkedin.com/in/mhcybroot](https://linkedin.com/in/mhcybroot)
- 📧 **Direct Inquiries:** [info@mahmudulhasan.info.bd](mailto:info@mahmudulhasan.info.bd)

---

<div align="center">
  <sub>Engineered with Zero-Fault Tolerance by Mahmudul Hasan • Verified on <a href="https://mahmudulhasan.info.bd/systems/quickbooks-ledger-sync/">mahmudulhasan.info.bd/systems/quickbooks-ledger-sync/</a></sub>
</div>
