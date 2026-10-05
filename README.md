# Campora — Demand-Oriented Campus Mobility

> MCA project · Guru Ghasidas Vishwavidyalaya, Bilaspur
> Java 21 · Spring Boot 3.5 · Spring MVC / Security / Data JPA · Hibernate · Thymeleaf · Bootstrap 5 · PostgreSQL

Campora is **demand-oriented, not booking-oriented**. Students don't book an auto one by one.
Everyone going the same way (for example *Engineering Block → Rajkishore Nagar*) joins one live
**hotspot**. Drivers see the anonymous totals and reserve seats against the hotspot. When no demand
is left, the hotspot closes by itself.

```
 10 students ─► hotspot (10 seats) ─► Driver A reserves 5 ─► 5 left
                                    ─► Driver B reserves 5 ─► hotspot closed
```

---

## 1. Quick start (no database setup)

**Requirements:** JDK 21 and Maven 3.9+. IntelliJ IDEA and STS both ship with Maven.

```bash
cd campora
mvn spring-boot:run
```

Open **http://localhost:8080**. The default `dev` profile uses a file-based H2 database
(`./data/`) running in PostgreSQL mode. On first start it seeds demo data, and OTP codes appear on
screen.

| Role     | Mobile       | Password      | Notes                                   |
|----------|--------------|---------------|-----------------------------------------|
| Student  | `9000000011` | `student@123` | No request yet. Use it to try the flow. |
| Driver A | `9000000021` | `driver@123`  | Verified, online                        |
| Driver B | `9000000022` | `driver@123`  | Verified, online                        |
| Driver C | `9000000023` | `driver@123`  | Awaiting admin verification             |
| Admin    | `9000000001` | `admin@123`   | Transport-office control room           |

Students `9000000012`–`9000000018` already have open requests: 10 seats from Engineering Block to
Rajkishore Nagar, plus one from Main Gate to Bilaspur Railway Station. Student `9000000019` is
hosting a companion ride.

**Demo script (about 3 minutes)**
1. Sign in as **Driver A**. Two hotspots are listed. Reserve **5** seats on *Engineering Block → Rajkishore Nagar*.
   The flash message confirms that 5 seats are still waiting.
2. Sign in as **Driver B** in a private window. Reserve 5 on the same route, and the hotspot closes.
3. Back as Driver A: open the trip to see the passenger names and numbers, which appear only after acceptance.
   Then **Start** and **Complete** the trip.
4. Sign in as **Student 9000000011**. Raise a request, watch the live-status line, and join the companion ride.
5. Sign in as **Admin**. Verify Driver C, review stop suggestions and pause or add stops.

To reset the demo, stop the app, delete the `data/` folder and start it again.

### Run in IntelliJ IDEA
*File → Open →* select the `campora` folder (it imports as a Maven project). Set the Project SDK to
21 and run `CamporaApplication`.

---

## 2. Production (PostgreSQL)

```bash
docker compose up -d            # optional: starts PostgreSQL 16 locally
mvn clean package
java -jar target/campora.jar --spring.profiles.active=prod
```

| Variable         | Default                                      |
|------------------|----------------------------------------------|
| `DB_URL`         | `jdbc:postgresql://localhost:5432/campora`   |
| `DB_USER`        | `campora`                                    |
| `DB_PASSWORD`    | `campora`                                    |
| `DB_POOL_SIZE`   | `10`                                         |
| `COOKIE_SECURE`  | `true` (set `false` for plain-HTTP testing)  |
| `SEED_DEMO_DATA` | `false`                                      |

Flyway creates the schema on first start. On PostgreSQL it also adds partial unique indexes that
enforce the business rules at the database level (see `docs/DATABASE.md`).

To send real SMS, add a bean that implements `identity.service.SmsGateway` (MSG91, Twilio, AWS SNS…)
in place of `LoggingSmsGateway`.

---

## 3. Features

| Area | What it does |
|---|---|
| **Onboarding** | Register → OTP verification → choose role → complete profile. Each step is enforced by `OnboardingInterceptor`. |
| **Students** | Raise a request (pickup, destination, *Auto / Companion / Any*, 1–4 seats). A live four-station status line follows it, with auto-refresh. |
| **Demand engine** | Clusters open requests by route into hotspots. Seat reservation is FIFO first-fit and never splits a group. Row locks prevent double allocation. |
| **Drivers** | Toggle online, see a live hotspot board (JSON polling every 15 s) and reserve seats. Start, complete or cancel trips. A cancelled trip puts its students back on the board. |
| **Anonymity** | Drivers see only route totals. Names and mobile numbers are loaded only after a request is accepted. |
| **Companion rides** | Any student can offer seats or join a ride. A join fulfils the student's open request on the same route. |
| **Stop suggestions** | Students propose missing stops. The admin approves (the stop is added to the network) or rejects. |
| **Admin** | Live stats, driver verification, stop network and departments. |
| **History** | A unified journey history (auto, companion and hosted rides) plus request history. |
| **Expiry** | A scheduled sweep expires stale requests so hotspots always show real, current demand. |

---

## 4. Project structure

```
src/main/java/in/ggv/campora
├── CamporaApplication.java
├── common/        BaseEntity (UUID, audit, soft delete, @Version), config, exceptions, web helpers
├── identity/      User, Role, OTP, registration, onboarding, Spring Security
├── campus/        Department, Destination (stop network), DestinationRequest
├── profile/       StudentProfile, DriverProfile
├── mobility/      TravelRequest, DriverResponse, MatchingService (demand engine), expiry job
├── trip/          Trip lifecycle, journey history
├── companion/     CompanionRide, CompanionRideMember
├── admin/         Control-room service and controller
├── seed/          Demo data (dev profile only)
└── web/           Student / Driver / Companion controllers, form objects, JSON API
src/main/resources
├── db/migration/  Flyway: common/ (schema + reference data), postgresql/ (partial indexes)
├── templates/     Thymeleaf views (layout fragments + one folder per role)
└── static/        campora.css (transit design system), campora.js, logo
```

Each module owns its entities, repositories and services, which keeps the monolith easy to split
into microservices later without redesigning the database.

---

## 5. Tests

```bash
mvn test
```

* `SeatAllocatorTest`: unit tests for the allocation policy (FIFO, no split groups, capacity).
* `MatchingServiceIntegrationTest`: the full demand engine on H2, covering the design-doc scenario,
  hotspot closing, cancellation re-queue, companion-only filtering, one trip per driver, and two
  drivers racing for the same requests.
* `WebSecurityTest`: public pages, role protection, CSRF, the registration → OTP redirect and bad login.

---

## 6. Documentation

* [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): layers, the demand-engine algorithm, concurrency, state machines, security
* [`docs/DATABASE.md`](docs/DATABASE.md): schema, ER diagram, indexes, and how it maps to the design-doc ERD
* [`docs/API.md`](docs/API.md): every route and the JSON API
