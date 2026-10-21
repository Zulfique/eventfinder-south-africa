# EventFinder API

ASP.NET Core 8 **Minimal API** backend for the EventFinder South Africa Android
app. 100% free to run: .NET is open source, PostgreSQL is provided by Neon's
free plan, and the service deploys to Render's free web tier (no credit card).

## What it provides

- Health check, JWT authentication, Google sign-in verification, password reset, profile and account deletion.
- Public event catalogue, owner-guarded event CRUD, and multi-user RSVPs.
- EF Core 8 + Npgsql migrations applied automatically at startup in production.

## Stack

- **.NET 8** / C# minimal APIs
- **EF Core 8 + Npgsql** (PostgreSQL)
- **JWT bearer** auth
- **Docker** multi-stage Dockerfile
- **xUnit + WebApplicationFactory** integration tests

## Run locally

A **valid PostgreSQL connection string is required** — the API has no SQLite fallback
any more, and `api/EventFinder.Api/appsettings.json` ships an empty
`ConnectionStrings:Default` on purpose so that no credentials are committed.

Configure a local PostgreSQL connection string before running:

```powershell
$env:ConnectionStrings__Default = "Host=localhost;Port=5432;Database=eventfinder;Username=postgres;Password=YOUR_LOCAL_PASSWORD"
$env:ASPNETCORE_ENVIRONMENT = "Development"
dotnet run --project .\api\EventFinder.Api\EventFinder.Api.csproj
```

The API listens on the configured URL (`http://localhost:5078` by default). Pending
migrations are applied automatically on startup for every PostgreSQL target, so only the
database itself has to exist. The integration tests replace the provider with an isolated
in-memory SQLite database and create their schema directly.

Without `ConnectionStrings:Default` the API refuses to start and reports
`ConnectionStrings:Default must be configured.` instead of failing later inside Npgsql.

> **Upgrading an existing local database:** migrations are now applied on startup for
> every PostgreSQL target. A database that an older build created directly from the
> model (no `__EFMigrationsHistory` table) makes the first migration fail with
> "relation already exists" — drop and recreate it once:
> `dotnet ef database drop --force --project api/EventFinder.Api` (or
> `DROP DATABASE eventfinder; CREATE DATABASE eventfinder;`).

## Tests

```bash
cd api
dotnet test EventFinder.Api.Tests/EventFinder.Api.Tests.csproj
```

## Deploy to Render (free)

Configure `ConnectionStrings__Default` in Render's dashboard using your Neon
PostgreSQL connection string. Use the Npgsql key/value format (not a `postgresql://` URL):

```
Host=YOUR_NEON_HOST;Database=YOUR_DATABASE;Username=YOUR_USERNAME;Password=YOUR_PASSWORD;SSL Mode=Require
```

Also configure Google, Brevo and Firebase credentials there instead of committing them.

Free-tier notes: instances sleep after 15 minutes, and production data lives in
Neon PostgreSQL rather than the Render container filesystem.
