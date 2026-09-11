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
3. Click **Connect** to inspect the `APPOINTMENTS` table directly.

---

## Project Structure

```
patient-portal/
├── pom.xml                                      # Maven configuration & dependencies
├── README.md                                    # Project documentation
├── src/
│   ├── main/
│   │   ├── java/com/qualifacts/patient_portal/
│   │   │   ├── PatientPortalApplication.java    # Spring Boot entry point
│   │   │   ├── controller/
│   │   │   │   └── AppointmentController.java   # Web controller (routes & role switcher)
│   │   │   ├── model/
│   │   │   │   ├── Appointment.java             # JPA Entity
│   │   │   │   └── AppointmentStatus.java       # Status enum (PENDING, CONFIRMED, CANCELLED)
│   │   │   ├── repository/
│   │   │   │   └── AppointmentRepository.java   # Spring Data JPA Repository
│   │   │   └── service/
│   │   │       └── AppointmentService.java      # Business logic & state validation
│   │   └── resources/
│   │       ├── application.properties           # Datasource, JPA & H2 configurations
│   │       └── templates/
│   │           └── appointments.html            # Thymeleaf UI (HTML + clean embedded CSS + JS modal)
│   └── test/
│       └── java/com/qualifacts/patient_portal/
│           ├── PatientPortalApplicationTests.java
│           ├── controller/
│           │   └── AppointmentControllerTest.java
│           └── service/
│               └── AppointmentServiceTest.java
```
