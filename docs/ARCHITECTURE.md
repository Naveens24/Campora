# Campora — Architecture

## 1. Style: modular monolith

```
 Browser (Thymeleaf + Bootstrap 5 + campora.js)     Future: Flutter app
            │  HTML forms (CSRF)       │ JSON (/api/v1)        │
            ▼                          ▼                       ▼
 ┌──────────────────────── Spring MVC controllers ─────────────────────────┐
 │  web.*  identity.web.*  admin.web.*  web.api.*                          │
 ├──────────────────────── Services (@Transactional) ──────────────────────┤
 │  identity   campus   profile   mobility   trip   companion   admin      │
 ├──────────────────────── Spring Data JPA repositories ───────────────────┤
 │  Hibernate 6 · Flyway migrations                                        │
 └─────────────────────────────────────────────────────────────────────────┘
                              PostgreSQL (prod) / H2 (dev, tests)
```

**Layering rules**
* Controllers are thin. They validate the form, call **one** service method, and redirect with a
  flash message (Post/Redirect/Get).
* Services own transactions and business rules. Entities own state transitions. For example,
  `TravelRequest.markMatched()` refuses to run unless the request is `OPEN`.
* Views receive **DTO records** built inside read-only transactions. `open-in-view` is **disabled**,
  so no lazy loading happens during rendering and no connection is held for the length of a request.
* Domain errors (`BusinessException`) become a friendly flash message on the page the user came from
  (`WebExceptionAdvice`). The JSON API returns RFC 9457 `ProblemDetail`s instead.

**Patterns used:** layered architecture, repository, service, DTO, and a rich domain model with
guarded transitions. SOLID applies throughout: `SeatAllocator` is a pure policy class, and
`SmsGateway` is an interface with a pluggable implementation.

## 2. The demand engine (`mobility.service.MatchingService`)

### Read side: hotspots
A single aggregate query, served by index `ix_request_cluster`, groups open requests by
`(pickup, destination)`:

```sql
SELECT pickup, destination, COUNT(*), SUM(seats_required), MIN(created_at)
FROM travel_request
WHERE status = 'OPEN' AND request_type <> 'COMPANION' AND expires_at > now()
GROUP BY pickup, destination
ORDER BY SUM(seats_required) DESC, MIN(created_at)
```

The result (`HotspotView`) contains **no student identity**. The driver board fetches it again
every 15 s through `/api/v1/driver/hotspots`, and polling pauses while the browser tab is hidden.

### Write side: reserving seats
```
reserveSeats(driver, route, seats)
 1. lock driver row                       → stops a double-clicked "Reserve" creating two trips
 2. check: verified, online, no active trip, 1 ≤ seats ≤ capacity
 3. SELECT … FOR UPDATE open requests on the route, oldest first
 4. drop rows that are no longer OPEN      → a competing driver took them while we waited
 5. SeatAllocator.allocate(queue, seats)   → FIFO first-fit, groups never split
 6. create one Trip + one ACCEPTED DriverResponse per chosen request; mark requests MATCHED
 7. return seats filled + demand still waiting (0 ⇒ hotspot closed)
```

**Allocation policy (FIFO first-fit).** The engine walks the queue oldest first and takes every
party that still fits. A group of 2 is never split across two autos. If a group doesn't fit, it
stays at the front of the queue for the next driver, and smaller parties behind it can fill the gap.

### Concurrency guarantees
| Threat | Defence |
|---|---|
| Two drivers reserve the same route at once | Row locks (step 3). The second driver waits, then sees only what is left. |
| Stale data after the wait | Status re-check (step 4), plus `@Version` optimistic locking on every row. |
| "Only one accepted response per request" (business rule #7) | Enforced under the locks, **and** by PostgreSQL partial unique index `ux_response_one_accepted`. |
| Two students take the last companion seat | `CompanionRide` row is locked on join (`findByIdForUpdate`). |
| Deadlocks | A single global lock order: **trip / ride → request**. |

A lost race never corrupts data. The loser gets a clear message ("hotspot just closed" or
"someone else just changed this").

## 3. State machines

```
TravelRequest   OPEN ─► MATCHED ─► IN_TRIP ─► COMPLETED
                 │        └──► OPEN (driver cancelled / companion ride cancelled / rider left)
                 ├──► CANCELLED (student)      └──► EXPIRED (TTL sweep, every 60 s)

Trip            SCHEDULED ─► IN_PROGRESS ─► COMPLETED
                 └──► CANCELLED (before start; passengers go back on the board)

CompanionRide   OPEN ⇄ FULL ─► STARTED ─► COMPLETED
                 └──► CANCELLED (before start)
```

A request that is returned to the pool gets at least 10 more minutes before it expires
(`TravelRequest.REOPEN_GRACE`).

## 4. Companion rides

A companion is **not a role**. It is a student who offers seats: same account, different action.
When a student joins a ride on the same route as their open request, the seat **fulfils** that
request (it becomes `MATCHED`), so demand is never counted twice and auto drivers stop seeing it.

## 5. Security

* **Authentication:** mobile number and BCrypt (cost 11) password through Spring Security form
  login. OTP verification of the mobile number is required before a role can be chosen.
* **OTP:** 6 digits from `SecureRandom`, stored only as a SHA-256 hash. It expires after 5 minutes,
  allows at most 5 attempts, and resends are throttled to one every 30 s.
* **Authorisation:** URL rules (`/student/**`, `/companion/**` → STUDENT; `/driver/**` → DRIVER;
  `/admin/**` → ADMIN). Every service also checks ownership (for example, a driver can act only on
  their own trip). Records that aren't the user's return 404, not 403, so their existence isn't leaked.
* **Onboarding gate:** `OnboardingInterceptor` redirects until verify → role → profile are all done.
  `AuthenticationRefresher` re-issues the session (with a new session ID) after each step.
* **Web hardening:** CSRF on all forms, strict Content-Security-Policy (scripts only from `'self'`,
  no inline JS), `X-Frame-Options: DENY`, referrer policy, HttpOnly + SameSite session cookie,
  Secure cookie in prod, no stack traces in error pages, open-redirect-safe "go back" handling.
* **Privacy:** hotspots are aggregates, and passenger or driver contact details are loaded only for
  accepted matches.

## 6. Performance and optimisation

* `open-in-view=false`, DTO projections and `join fetch` queries: every page renders from a fixed,
  small number of queries with no N+1 lazy loads.
* Composite indexes on each hot path: hotspot aggregation, per-student lookups, expiry sweep, trip
  and ride status.
* Hibernate JDBC batching (`batch_size=25`, ordered inserts/updates) and `default_batch_fetch_size=32`.
* Stop list and department list are cached in memory (`@Cacheable`) and evicted on admin edits.
* The expiry sweep is a single bulk `UPDATE`. It never loads entities.
* Static assets are content-hashed (`/css/campora-<hash>.css`), cached for 30 days, and gzip-compressed.
  Bootstrap and the icons are served locally from WebJars.
* The hotspot poller skips requests while the tab is hidden and uses `Cache-Control: no-store` for live data.
* HikariCP is tuned in the prod profile.

## 7. Designed to evolve

* **Microservices:** module boundaries match future services (identity, mobility, trip, companion),
  and cross-module links are plain foreign keys to UUIDs.
* **Mobile app:** the `/api/v1` JSON endpoints are the start of the Flutter contract. Next step:
  token auth (Spring Authorization Server or JWT) alongside the session login.
* **Scale-out:** move OTP state from memory to Redis, and move hotspot push from polling to
  WebSocket/SSE.
