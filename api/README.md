# EventFinder API

ASP.NET Core 8 **Minimal API** backend for the EventFinder South Africa Android
app. 100% free to run: .NET is open source, the database is SQLite, and the
service deploys to Render's free hobby tier (no credit card).

## What it provides

| Area | Endpoints |
| --- | --- |
| **Health** | `GET /health` — used by uptime monitors and the Render deploy check |
| **Auth** | `POST /api/auth/register`, `/login`, `/google`, `/forgot-password`, `/reset-password`, `/change-password`, `GET /api/auth/me` — JWT bearer tokens, PBKDF2 password hashes, verified Google ID tokens, single-use expiring password-reset tokens emailed through Brevo |
| **Events** | `GET /api/events/public`, `GET /api/events/{id}`, `POST /api/events`, `PUT /api/events/{id}`, `DELETE /api/events/{id}` — server-generated GUID identifiers and UTC timestamps, DataAnnotations validation |
| **RSVPs** | `POST/DELETE /api/events/{id}/rsvp`, `GET /api/events/{id}/attendees`, `GET /api/events/my-rsvps` — multi-user attendee management with a maintained attendee count |

Every endpoint (except `/health`) returns the standard response envelope:

```json
{
  "success": true,
  "message": "Event created.",
  "data": { "id": "…", "title": "…" },
  "errors": null
}
```

Validation failures return HTTP 400 with `errors` keyed by field name.

## Stack

- **.NET 8** / C# minimal APIs (`MapGet`/`MapPost`/… in `Endpoints/`)
- **EF Core 8 + SQLite** with code-first migrations in `Data/Migrations/`
- **JWT bearer** auth (`Microsoft.AspNetCore.Authentication.JwtBearer`)
- **Docker** multi-stage `Dockerfile`
- **xUnit + WebApplicationFactory** integration tests (13 tests)

## Run locally

```bash
cd api/EventFinder.Api
dotnet run
# API listens on http://localhost:5078
curl http://localhost:5078/health
```

The SQLite database file is created at `data/eventfinder.db` on first run;
pending migrations are applied automatically at boot.

### Configuration (appsettings.json / environment variables)

| Key | Purpose |
| --- | --- |
| `Jwt:Secret` | HMAC signing key for bearer tokens (auto-generated on Render) |
| `ConnectionStrings:Default` | SQLite connection string |
| `Google:ServerClientId` | OAuth **web** client ID — Google sign-in tokens are rejected unless this matches |
| `Brevo:ApiKey` | Brevo SMTP API key (free plan: 300 emails/day). When empty, reset tokens are logged instead of emailed |

## Tests

```bash
cd api
dotnet test EventFinder.Api.Tests/EventFinder.Api.Tests.csproj
```

13 integration tests cover the health check, response envelope shape, request
validation, register/login, duplicate emails, the full password-reset flow
(single-use token), Google token rejection, server-generated event
identifiers/timestamps, coordinate validation, public-event visibility,
owner-only update/delete, and multi-user RSVP attendee management.

## Deploy to Render (free)

This repo includes a [`render.yaml`](../render.yaml) blueprint. In the Render
dashboard: **New → Blueprint → connect this GitHub repo**. Render builds the
Dockerfile, generates a `Jwt__Secret`, and exposes the service at
`https://eventfinder-api-….onrender.com` with `/health` as the health check.

Free-tier notes: instances sleep after 15 minutes (first request after idle
takes ~30–60 s) and the SQLite file lives on the container's ephemeral disk —
data resets on redeploy. For a demo that is acceptable; for persistence attach
a paid disk or switch the connection string to a managed Postgres.

## Project layout

```
api/
├── EventFinder.Api/
│   ├── Program.cs                 bootstrap, JWT, health endpoint
│   ├── Common/ApiResponse.cs      standard response envelope
│   ├── Data/                      EF Core entities, DbContext, migrations
│   ├── Dtos/                      requests/responses + DataAnnotations
│   ├── Endpoints/                 auth + event/RSVP minimal API modules
│   ├── Services/                  password hashing, JWT, Google verify, Brevo email
│   └── Dockerfile
└── EventFinder.Api.Tests/         xUnit integration tests (WebApplicationFactory)
```
