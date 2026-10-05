# Campora — Database

Migrations live in `src/main/resources/db/migration`:

| File | Purpose |
|---|---|
| `common/V1__schema.sql` | All 12 tables, foreign keys, check constraints, indexes |
| `common/V2__reference_data.sql` | Roles, 12 departments, 16 stops (8 on campus, 8 in Bilaspur) |
| `postgresql/V3__integrity_rules.sql` | Partial unique indexes (PostgreSQL only) |
| `h2/V3__integrity_rules.sql` | No-op, so migration history matches on both databases |

## Conventions (every table)
`id UUID PK` · `created_at`, `updated_at`, `created_by`, `updated_by` (JPA auditing) ·
`deleted BOOLEAN` (soft delete via `@SQLRestriction`) · `version BIGINT` (optimistic locking).

## Entity–relationship diagram

```
app_role 1───∞ app_user 1───1 student_profile ∞───1 department
                    │
                    └──1───1 driver_profile

destination (stop) 1───∞ travel_request ∞───1 student_profile
     │                        │
     │                        1
     │                        │
     │                        ∞
     │                  driver_response ∞───1 driver_profile
     │                        ∞
     │                        │
     │                        1
     └───────────1───∞ trip ──1───1 companion_ride ∞───1 student_profile (host)
                                          │
                                          1───∞ companion_ride_member ∞───1 student_profile
                                                          └── 0..1 travel_request (fulfilled)

student_profile 1───∞ destination_request
```

## Tables

| Table | Key columns | Notes |
|---|---|---|
| `app_role` | `name` (STUDENT/DRIVER/ADMIN) | |
| `app_user` | `role_id?`, `first_name`, `last_name`, `mobile_number` ⋆unique, `password_hash`, `status`, `mobile_verified_at` | `role_id` is null until onboarding |
| `department` | `code` ⋆unique, `name`, `active` | |
| `destination` | `name` ⋆unique, `area`, `type` (CAMPUS_POINT/CITY), `active`, `sort_order` | Used for both pickup and destination |
| `student_profile` | `user_id` ⋆unique, `department_id`, `enrollment_no` ⋆unique, `semester` | |
| `driver_profile` | `user_id` ⋆unique, `vehicle_type`, `vehicle_number` ⋆unique, `seat_capacity`, `native_area`, `availability`, `verified` | |
| `travel_request` | `student_id`, `pickup_id`, `destination_id`, `request_type`, `seats_required`, `status`, `expires_at` | a.k.a. MobilityRequest |
| `driver_response` | `request_id`, `driver_id`, `trip_id`, `seats_reserved`, `status` | ≤ 1 ACCEPTED per request |
| `trip` | `trip_type`, `driver_id?`, `companion_ride_id?` ⋆unique, `pickup_id`, `destination_id`, `seats_reserved`, `status`, `start_time`, `end_time` | |
| `companion_ride` | `host_id`, `pickup_id`, `destination_id`, `departure_time`, `total_seats`, `available_seats`, `status`, `note` | |
| `companion_ride_member` | `ride_id`, `student_id`, `travel_request_id?`, `seats`, `status` | |
| `destination_request` | `student_id`, `proposed_name`, `area`, `reason`, `status`, `review_note`, `reviewed_at` | |

## Integrity rules

| Rule | Where |
|---|---|
| Pickup ≠ destination | `CHECK` on `travel_request`, `companion_ride` |
| Seats within bounds | `CHECK` on requests, rides, driver capacity, semester |
| One ACCEPTED response per request | `ux_response_one_accepted` (partial unique, PG) + service locks |
| One live request per student | `ux_request_one_active_per_student` (PG) + service check |
| One membership per ride per student | `ux_member_once_per_ride` (PG) + service check |

## Indexes

| Index | Serves |
|---|---|
| `ix_request_cluster (status, pickup_id, destination_id, created_at)` | Hotspot aggregation and seat reservation |
| `ix_request_student (student_id, status)` | Student dashboard |
| `ix_request_expiry (status, expires_at)` | Expiry sweep |
| `ix_response_request`, `ix_response_trip` | Match lookup, trip passengers |
| `ix_trip_driver (driver_id, status)` | Driver's active trip |
| `ix_ride_status`, `ix_ride_host`, `ix_member_ride`, `ix_member_student` | Companion pages |
| `ix_destreq_status`, `ix_user_role` | Admin |

## Refinements over the design-document ERD

The design-doc ERD is kept, with three deliberate refinements:

1. **`travel_request.pickup_id`**: the design doc's example ("Engineering Block → Rajkishore
   Nagar") clusters on a *route*, so each request stores both ends. Both reference `destination`,
   which now models every stop in the network.
2. **`driver_response.trip_id`** in place of `trip.request_id` / `trip.response_id`: one auto trip
   carries several students, so a trip groups many responses. This is what turns ten requests into
   two trips.
3. **`trip.companion_ride_id`**: companion journeys also create a `trip` row when they start, so
   history and reporting treat both modes the same way.
