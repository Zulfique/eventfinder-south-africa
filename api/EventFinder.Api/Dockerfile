# Multi-stage build for the EventFinder ASP.NET Core 8 Minimal API.
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

COPY EventFinder.Api.csproj .
RUN dotnet restore EventFinder.Api.csproj

COPY . .
RUN dotnet publish EventFinder.Api.csproj -c Release -o /app/publish /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS runtime
WORKDIR /app

# SQLite database directory (mount a Render disk here for persistence).
RUN mkdir -p /app/data && chown -R app:app /app/data
USER app

COPY --from=build /app/publish .

ENV ASPNETCORE_URLS=http://+:8080
ENV ASPNETCORE_ENVIRONMENT=Production
EXPOSE 8080

ENTRYPOINT ["dotnet", "EventFinder.Api.dll"]
