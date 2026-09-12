# Patient Portal (Minimal Version)

A lightweight, minimal Patient Portal built with **Java 21**, **Spring Boot**, **Thymeleaf**, **Spring Data JPA**, and an in-memory **H2 Database**.

This application allows patients to request appointments online and view their scheduled visits, while healthcare providers manage, confirm, and reschedule appointment requests from their side.

---

## Prerequisites

Before running the application, ensure you have the following installed:

- **Java Development Kit (JDK) 21** or later
- **Git** (optional, for version control)
- **Maven** (included via `./mvnw` or `mvnw.cmd` wrapper; no manual Maven install required)

To verify your Java installation:
```bash
java -version
```

---

## How to Run the Application

### 1. Start the Spring Boot Server

From the project root directory, run:

**On Windows (PowerShell / Command Prompt):**
```powershell
.\mvnw.cmd spring-boot:run
```

**On Linux / macOS:**
```bash
./mvnw spring-boot:run
```

Once the application starts, open your browser and navigate to:
```
http://localhost:8080
```

### 2. Run Automated Tests

To execute the unit and integration test suite:

**On Windows:**
```powershell
.\mvnw.cmd test
```

**On Linux / macOS:**
```bash
./mvnw test
```

---

## Key Features & Architecture

### 1. Simple Role Switcher (No Auth Needed)
A role switcher in the header enables toggling between **Patient** and **Provider** views via query parameters (`?role=patient` and `?role=provider`).
- **Patient View**: `http://localhost:8080/appointments?role=patient`
- **Provider View**: `http://localhost:8080/appointments?role=provider`

### 2. Patient Capabilities
- **Request an Appointment**:
  - Fields: `Date & Time`, `Provider` (selected from available providers dropdown), `Appointment Type`, and optional `Reason for Visit`.
  - All new requests are automatically assigned **`PENDING`** status.
- **View Appointments**:
  - Displays all booked appointments with ID, Date/Time, Provider, Type, Reason, and Status badge.
- **Cancel Confirmed Appointment**:
  - Patients can only cancel appointments that are in **`CONFIRMED`** status.
  - **`PENDING`** appointments cannot be cancelled (Cancel button is disabled with descriptive tooltip).

### 3. Provider Capabilities
- **View Appointment Management**:
  - Displays all appointment requests submitted across the portal.
- **Confirm Appointment**:
  - Providers can confirm any **`PENDING`** appointment, changing its status to **`CONFIRMED`**.
  - Already **`CONFIRMED`** appointments cannot be confirmed again (Confirm button is disabled).
  - **`CANCELLED`** appointments cannot be confirmed (Confirm button is disabled).
- **Reschedule Appointment**:
  - Providers can update the date/time of an appointment.
  - Rescheduling a **`PENDING`** appointment keeps it in **`PENDING`** status.
  - Rescheduling a **`CONFIRMED`** appointment keeps it in **`CONFIRMED`** status.
  - **`CANCELLED`** appointments cannot be rescheduled (Reschedule button is disabled).

### 4. Concurrency Control & Conflict Prevention (Optimistic Locking)
Every state mutation (**Cancel**, **Confirm**, and **Reschedule**) is strictly protected by JPA Optimistic Locking (`@Version`):
- **Universal Protection across Roles**:
  - If a patient cancels while a provider is attempting to reschedule the same appointment, the provider's stale reschedule is rejected.
  - If a provider reschedules while a patient is attempting to cancel, the patient's stale cancellation is rejected.
  - If multiple staff members attempt to confirm or reschedule simultaneously, only the first action succeeds.
- **Strict Version Requirement**:
  - The `version` parameter is mandatory on all mutation endpoints (`/cancel`, `/confirm`, `/reschedule`). If omitted or null, the request is rejected immediately with an error to prevent accidental overrides by future API integrations.
- **Graceful Conflict Alert**:
  - If a version collision is detected, the transaction aborts cleanly with HTTP 409 Conflict.
  - The user is cleanly advised:
    > *"This appointment was modified by another user while you were viewing it. Your action was not applied, and the latest schedule is shown below."*

### 5. Appointment History & Complete Audit Trail (Problem 2)
When questions arise regarding why an appointment moved (e.g. from Monday to Wednesday), who changed it, and when:
- **Answers Every Clinical & Operational Question**:
  - **When did it change?** -> Exact timestamp recorded on every modification (`created_at`).
  - **Who changed it?** -> Logged as either `PATIENT` or `PROVIDER` (`actor_role`).
  - **What was it before?** -> Prior date/time (`previous_date_time`) and prior status (`previous_status`) are preserved without overwriting history.
  - **What action was taken?** -> Recorded as `REQUESTED`, `CONFIRMED`, `RESCHEDULED`, or `CANCELLED` with a human-readable detail summary.
- **Dedicated History UI**:
  - In both Patient and Provider views, each row features a **"History"** button.
  - Clicking "History" opens an interactive modal displaying the full chronological timeline from newest to oldest.

### 6. Fault-Tolerant Patient Notifications (Problem 3)
When a provider confirms an appointment, the patient is notified via an abstracted `NotificationService`:
- **Interface Abstraction**:
  - `NotificationService` interface defines `notifyAppointmentConfirmed(Appointment appointment)`.
- **Stub Implementation**:
  - `StubNotificationService` logs the action:
    ```
    [NOTIFICATION STUB] Would send confirmation notification to Patient for appointment #2 with Dr. Alice Smith at 2026-09-14 10:00.
    ```
- **Fault Tolerance & Independence**:
  - Confirming an appointment cannot get slow or fail just because the notification step fails or encounters latency.
  - In `AppointmentService.confirmAppointment()`, the notification dispatch is isolated in a protective `try-catch` block. If the notification service throws an error or experiences downtime, the error is safely logged and the database transaction commits successfully.


## Production Considerations: What We'd Change for Real Traffic

In our lightweight prototype, notifications are simulated synchronously with error isolation. For a **real-world, high-volume production healthcare system**, here is the architectural blueprint to handle scale, latency, and reliability:

```
[Provider Action] 
       │
       ▼
[Appointment Service] ──(In same DB Transaction)──► [Save Appointment & Outbox Event]
                                                               │
                                                               ▼ (Debezium / Poller)
                                                     [Message Broker (Kafka/RabbitMQ)]
                                                               │
                                                               ▼
                                                  [Notification Worker Service]
                                                   ├── Retries & Exponential Backoff
                                                   ├── Dead Letter Queue (DLQ)
                                                   └── Rate-Limited Gateways (SendGrid/Twilio)
```

1. **Decoupling via Asynchronous Message Brokers**:
   - Instead of in-process execution, confirmation actions should publish an `AppointmentConfirmedEvent` to a reliable message broker such as **Apache Kafka**, **RabbitMQ**, or **AWS SQS**.
   - The user-facing HTTP request finishes in milliseconds without waiting on email/SMS servers.

2. **Transactional Outbox Pattern**:
   - Simply calling a message broker inside a database transaction risks the *dual-write problem* (e.g. database commits but broker publish fails, or broker publishes but DB transaction rolls back).
   - **Solution**: Save an event record in an `outbox` table within the same database transaction as the appointment confirmation. A change-data-capture (CDC) tool like **Debezium** or an outbox publisher process reads the table and guarantees **at-least-once delivery** to the message broker.

3. **Dedicated Worker Service & Resilience**:
   - A dedicated notification microservice consumes events from the broker.
   - **Retries with Exponential Backoff**: Automatically retry transient network or API errors with jitter.
   - **Dead Letter Queue (DLQ)**: Poison pills or permanently failing notifications move to a DLQ for operational alerts and manual inspection, preventing message queue blockages.

4. **Third-Party Provider Integration & Failover**:
   - Integration with enterprise notification APIs (SendGrid, AWS SES for email; Twilio for SMS).
   - Circuit breakers (e.g. Resilience4j) and fallback providers (e.g. fallback from primary SMS gateway to secondary if error rate exceeds 5%).
   - Respecting patient communication preferences (SMS vs. Email opt-ins) and HIPAA/GDPR data masking (never logging unencrypted protected health information - PHI).

5. **Rate Limiting & Throttling**:
   - Token bucket rate limiters to respect telecom provider thresholds (e.g., Twilio carrier rate limits) during peak confirmation hours.

### 7. Preventing Overlapping Confirmed Appointments (Problem 4: Provider Overlap Prevention)
- **Problem**: A provider cannot be in two places at once, so two overlapping confirmed appointments for the same provider should never exist.
- **Challenge**:
  - **Multi-Row Write Skew**: Two pending requests for the same provider at the exact same time exist in two *different* rows (e.g. Appointment #1 and Appointment #2).
  - Single-row optimistic locking (`@Version`) checks do not conflict between separate rows.
  - If two providers click "Confirm" on #1 and #2 at the exact same instant, without coordination both checks pass and double-book the provider.
- **Solution**:
  - **Database Pessimistic Write Locking (`ProviderRepository`)**:
    - Introduced `Provider` entity and `ProviderRepository` with `@Lock(LockModeType.PESSIMISTIC_WRITE) findByNameForUpdate(providerName)`.
    - When confirming or rescheduling, the transaction acquires an exclusive database-level row lock (`SELECT ... FOR UPDATE`) on that provider.
    - Any concurrent transaction attempting to confirm or reschedule for the same provider is serialized at the database engine level.
  - **User-Friendly Error**:
    - If an overlap is detected, the transaction aborts with a clear message:
      > *"Cannot confirm appointment: Dr. Alice Smith already has a confirmed appointment at 2026-10-05 14:00. Please reschedule to another time."*
  - **Key Benefits**:
    - **Multi-Instance / Cluster Safe**: Handled directly by the database engine across all web servers and containers.
    - **Future-Proof Extensibility**: Readily supports future appointment durations, time-window overlap checks, and buffer intervals.
	
---

## Appointment State & Action Matrix

| Current Status | Patient: Cancel | Provider: Confirm | Provider: Reschedule | Next State(s) |
| :--- | :--- | :--- | :--- | :--- |
| **`PENDING`** | ❌ Disabled |  Enabled |  Enabled | `CONFIRMED` (on confirm) / `PENDING` (on reschedule) |
| **`CONFIRMED`** |  Enabled | ❌ Disabled |  Enabled | `CANCELLED` (on cancel) / `CONFIRMED` (on reschedule) |
| **`CANCELLED`** | ❌ Disabled | ❌ Disabled | ❌ Disabled | Terminal state (no further actions) |

---

## Database & H2 Console

The application uses an in-memory **H2 Database**.
- Data persists across page refreshes while the Spring Boot application is running.
- When the application server restarts, the database initializes fresh.

### Accessing the H2 Web Console:
1. Navigate to: `http://localhost:8080/h2-console`
2. Enter the following connection settings:
   - **JDBC URL**: `jdbc:h2:mem:patientportal`
   - **User Name**: `sa`
   - **Password**: *(leave empty)*
3. Click **Connect** to inspect both `APPOINTMENTS` and `APPOINTMENT_HISTORY` tables directly.

---

## Project Structure

```
patient-portal/
├── pom.xml                                              # Maven configuration & dependencies
├── README.md                                            # Project documentation
├── src/
│   ├── main/
│   │   ├── java/com/qualifacts/patient_portal/
│   │   │   ├── PatientPortalApplication.java            # Spring Boot entry point
│   │   │   ├── controller/
│   │   │   │   └── AppointmentController.java           # Web controller (routes, role switcher & history endpoint)
│   │   │   ├── model/
│   │   │   │   ├── Appointment.java                     # JPA Entity
│   │   │   │   ├── AppointmentStatus.java               # Status enum (PENDING, CONFIRMED, CANCELLED)
│   │   │   │   ├── AppointmentHistory.java              # Audit Log JPA Entity
│   │   │   │   ├── AppointmentHistoryAction.java        # History action enum
│   │   │   │   └── Provider.java                        # Provider JPA Entity (used for pessimistic schedule locking)
│   │   │   ├── repository/
│   │   │   │   ├── AppointmentRepository.java           # Spring Data JPA Repository
│   │   │   │   ├── AppointmentHistoryRepository.java    # Audit History Repository
│   │   │   │   └── ProviderRepository.java              # Repository with PESSIMISTIC_WRITE locking query
│   │   │   └── service/
│   │   │       ├── AppointmentService.java              # Business logic, state validation & audit logging
│   │   │       ├── NotificationService.java             # Notification abstraction interface
│   │   │       └── StubNotificationService.java         # Logging stub implementation with fault tolerance
│   │   └── resources/
│   │       ├── application.properties                   # Datasource, JPA & H2 configurations
│   │       └── templates/
│   │           └── appointments.html                    # Thymeleaf UI (clean CSS + reschedule & history modals)
│   └── test/
│       └── java/com/qualifacts/patient_portal/
│           ├── PatientPortalApplicationTests.java
│           ├── controller/
│           │   └── AppointmentControllerTest.java
│           └── service/
│               └── AppointmentServiceTest.java
```
