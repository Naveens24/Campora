# Campora — Routes & API

All form posts need the CSRF token, which Thymeleaf adds automatically. Every action follows
Post/Redirect/Get, so success and error messages arrive as flash messages.

## Public
| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Landing page |
| GET | `/rides` (`from`, `to`) | Search rides. No names, numbers or notes |
| GET | `/rides/{id}` · `/rides/request` (`from`, `to`) · `/rides/offer` (`from`, `to`) | Continue to join, request or offer. Signs in first if needed, then opens the student page; books nothing by itself |
| GET/POST | `/login` | Sign in (`mobile`, `password`) |
| GET/POST | `/register` | Create account → signs in → `/verify` |
| POST | `/logout` | Sign out |
| GET/POST | `/account/password` | Replace a temporary password (signed in) |
| GET | `/actuator/health` | Health check |

## Onboarding (signed in, not yet complete)
| Method | Path | Purpose |
|---|---|---|
| GET/POST | `/verify` | Enter OTP (`code`) |
| GET/POST | `/forgot` | Email a password reset code (`email`) |
| GET/POST | `/reset` | Set a new password (`code`, `password`, `confirmPassword`) |
| POST | `/verify/email` | Set or correct the unverified email |
| GET/POST | `/onboarding/contact` | Add `mobile` (Google accounts) |
| GET | `/oauth2/authorization/google` | Google sign-in |
| POST | `/verify/resend` | New OTP (throttled) |
| GET/POST | `/onboarding/role` | Choose `STUDENT` or `DRIVER` |
| GET | `/onboarding/profile` | Profile form for the chosen role |
| POST | `/onboarding/profile/student` | `departmentId`, `semester` |
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
| POST | `/driver/availability` | `online=true/false` (unless blocked) |
| POST | `/driver/reserve` | `pickupId`, `destinationId`, `seats` |
| POST | `/driver/trips/{id}/start` · `/complete` · `/cancel` | Trip lifecycle |
| GET | `/driver/history` | Past trips |

## Admin (`ROLE_ADMIN`)
Admins manage students and drivers. Only owners (`ADMIN_EMAIL`) manage admins; nobody else can change an owner.

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin` | Overview |
| GET | `/admin/users` (`q`, `show`, `page`) | Find accounts |
| GET/POST | `/admin/users/new` | Open an account (`firstName`, `lastName`, `mobile`, `email`, `role`) |
| GET | `/admin/users/{id}` (`tab=profile/rides/access/history`) | One account |
| POST | `/admin/users/{id}/account` · `/student` · `/driver` | Correct details |
| POST | `/admin/users/{id}/temporary-password` · `/verify` · `/sign-out` · `/disconnect-google` | Help them sign in |
| POST | `/admin/users/{id}/role` (`role`) · `/driving` (`blocked`) · `/suspend` (`reason`) · `/reactivate` | Role and status |
| POST | `/admin/users/{id}/clear-rides` | Free someone stuck in a ride |
| GET/POST | `/admin/users/{id}/delete` (`confirm=DELETE`) | Erase an account |
| GET | `/admin/rides` | Live rides and waiting requests |
| POST | `/admin/rides/trips/{id}/end` · `/companion/{id}/end` · `/requests/{id}/cancel` | Close a ride |
| GET | `/admin/stops` · `/admin/departments` · `/admin/activity` | Network and log |
| POST | `/admin/suggestions/{id}/approve` (`type`) · `/reject` (`note`) | Review stop suggestions |
| POST | `/admin/stops` · `/admin/stops/{id}` · `/admin/stops/{id}/toggle` | Add, edit, pause stops |
| POST | `/admin/departments` · `/admin/departments/{id}` · `/admin/departments/{id}/toggle` | Add, edit, pause departments |

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
