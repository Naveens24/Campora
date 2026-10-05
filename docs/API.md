# Campora — Routes & API

All form posts need the CSRF token, which Thymeleaf adds automatically. Every action follows
Post/Redirect/Get, so success and error messages arrive as flash messages.

## Public
| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Landing page |
| GET/POST | `/login` | Sign in (`mobile`, `password`) |
| GET/POST | `/register` | Create account → signs in → `/verify` |
| POST | `/logout` | Sign out |
| GET | `/actuator/health` | Health check |

## Onboarding (signed in, not yet complete)
| Method | Path | Purpose |
|---|---|---|
| GET/POST | `/verify` | Enter OTP (`code`) |
| POST | `/verify/resend` | New OTP (throttled) |
| GET/POST | `/onboarding/role` | Choose `STUDENT` or `DRIVER` |
| GET | `/onboarding/profile` | Profile form for the chosen role |
| POST | `/onboarding/profile/student` | `departmentId`, `enrollmentNo`, `semester` |
| POST | `/onboarding/profile/driver` | `vehicleType`, `vehicleNumber`, `seatCapacity`, `nativeArea` |
| GET | `/dashboard` | Redirects to the role console |

## Student (`ROLE_STUDENT`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/student` | Dashboard: live request, companion suggestions |
| POST | `/student/requests` | `pickupId`, `destinationId`, `requestType` (AUTO/COMPANION/ANY), `seatsRequired` |
| POST | `/student/requests/{id}/cancel` | Cancel (OPEN or MATCHED) |
| GET | `/student/history` | Journeys and requests |
| GET/POST | `/student/suggest` | Propose a new stop (`name`, `area`, `reason`) |
| GET | `/companion` | Offer, join or manage companion rides |
| POST | `/companion/rides` | Offer: `pickupId`, `destinationId`, `departureTime` (yyyy-MM-ddTHH:mm), `totalSeats`, `note` |
| POST | `/companion/rides/{id}/join` | `seats` |
| POST | `/companion/rides/{id}/leave` | |
| POST | `/companion/rides/{id}/start` · `/complete` · `/cancel` | Host actions |

## Driver (`ROLE_DRIVER`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/driver` | Availability, current trip, hotspot board |
| POST | `/driver/availability` | `online=true/false` (verified drivers only) |
| POST | `/driver/reserve` | `pickupId`, `destinationId`, `seats` |
| POST | `/driver/trips/{id}/start` · `/complete` · `/cancel` | Trip lifecycle |
| GET | `/driver/history` | Past trips |

## Admin (`ROLE_ADMIN`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/admin` | Stats, drivers, suggestions, network, departments |
| POST | `/admin/drivers/{id}/verify` · `/revoke` | Driver verification |
| POST | `/admin/suggestions/{id}/approve` (`type`) · `/reject` (`note`) | Review stop suggestions |
| POST | `/admin/locations` · `/admin/locations/{id}/toggle` | Stop network |
| POST | `/admin/departments` · `/admin/departments/{id}/toggle` | Departments |

## JSON API (`/api/v1`, session-authenticated)

### `GET /api/v1/driver/hotspots` (DRIVER)
```json
{
  "generatedAt": "2026-09-25T09:30:00Z",
  "capacity": 5,
  "canReserve": true,
  "hotspots": [
    {
      "pickupId": "…", "pickupName": "Engineering Block",
      "destinationId": "…", "destinationName": "Rajkishore Nagar",
      "requests": 6, "seatsDemanded": 10,
      "waitingSince": "2026-09-25T09:12:41Z"
    }
  ]
}
```

### `GET /api/v1/student/request` (STUDENT)
`200` with the live request, or `204 No Content` when the student has none.
```json
{
  "id": "…", "pickup": "Engineering Block", "destination": "Rajkishore Nagar",
  "type": "ANY", "seats": 2, "status": "MATCHED",
  "createdAt": "…", "expiresAt": "…", "othersOnRoute": 0,
  "match": { "kind": "AUTO", "name": "Ramesh Kumar", "mobile": "9000000021",
             "vehicle": "Auto rickshaw · CG10AB1234", "detail": "Driver is heading to pickup" }
}
```

### Errors
The API returns RFC 9457 problem details, for example:
```json
{ "type": "about:blank", "title": "Unprocessable Entity", "status": 422,
  "detail": "This hotspot has just closed — no open demand is left on that route." }
```
