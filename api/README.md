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

Configure a local PostgreSQL connection string before running:

```powershell
$env:ConnectionStrings__Default = "Host=localhost;Port=5432;Database=eventfinder;Username=postgres;Password=YOUR_LOCAL_PASSWORD"
$env:ASPNETCORE_ENVIRONMENT = "Development"
dotnet run --project .\api\EventFinder.Api\EventFinder.Api.csproj
```

The API listens on the configured URL. Pending migrations are applied automatically
during production startup; Development tests create their schema directly.

## Tests

```bash
cd api
dotnet test EventFinder.Api.Tests/EventFinder.Api.Tests.csproj
```

## Deploy to Render (free)

Configure `ConnectionStrings__Default` in Render's dashboard using your Neon
PostgreSQL connection string. Also configure Google, Brevo and Firebase
credentials there instead of committing them.

Free-tier notes: instances sleep after 15 minutes, and production data lives in
Neon PostgreSQL rather than the Render container filesystem.
