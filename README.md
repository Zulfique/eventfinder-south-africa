# EventFinder South Africa

[![Android CI](https://github.com/Zulfique/eventfinder-south-africa/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Zulfique/eventfinder-south-africa/actions/workflows/android-ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.02-4285F4?logo=jetpackcompose&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-brightgreen)
![targetSdk](https://img.shields.io/badge/targetSdk-34-brightgreen)
![Tests](https://img.shields.io/badge/unit%20tests-115%20passing-success)

A native **Android (Kotlin + Jetpack Compose)** app that helps people across South Africa
discover, save and create local events — from Joburg jazz nights to Cape Town food markets
and Durban festivals.

EventFinder was built as the **Part 2 (final) Portfolio of Evidence** for the module.
It consumes **100% free, keyless REST APIs**, requires **no paid infrastructure**,
and runs on a physical device or emulator.

---

## Table of contents

1. [Features](#features)
2. [Screenshots](#screenshots)
3. [Tech stack](#tech-stack)
4. [Architecture](#architecture)
5. [External APIs](#external-apis)
6. [Localisation](#localisation)
7. [Offline-first behaviour](#offline-first-behaviour)
8. [Security](#security)
9. [Getting started](#getting-started)
10. [Testing](#testing)
11. [Continuous integration](#continuous-integration)
12. [Project structure](#project-structure)
13. [Requirement traceability](#requirement-traceability)
14. [Attribution & licences](#attribution--licences)

---

## Features

| Area | What it does |
| --- | --- |
| **Discover** | Browse the local event catalogue, filter by category chip and free-text keyword, sort by date / distance / name. |
| **Near me** | Requests location permission and sorts events by distance using the Haversine great-circle formula. |
| **Map** | Event locations plotted on a free OpenStreetMap map (osmdroid) — no Google Maps key or billing. |
| **Event detail** | Hero image, organiser, attendee count, venue map link, **weather forecast at the venue** on the event day, share and RSVP. |
| **Search** | Debounced keyword search with recent-search history and popular events. |
| **Create event** | A 3-step wizard (details → date/time → location) with an optional photo picked from the system photo picker. |
| **My events** | Organisers can edit or delete the events they created from the detail screen's overflow menu. |
| **Favourites** | Save events offline; favourites survive app restarts and are stored in the local Room catalogue. |
| **Profile** | Account header, activity stats (created / attending / favourites), My Events and Attending lists. |
| **Edit profile** | Update display name and email with validation. |
| **Settings** | Language switch (English / Afrikaans), biometric login toggle, event reminders, new-event alerts, plus account tools (change password, clear local cache, delete account). |
| **Auth** | Local email + password registration and login (PBKDF2-hashed), plus biometric unlock. Forgot password performs a local email-lookup and password change is available in Settings. |
| **Reminders** | `AlarmManager` + `NotificationChannel` reminders **24 hours and 1 hour** before an attended event, cancelled when the RSVP is declined. |
| **Event alerts** | On-device notifications flag newly added or changed events in the local Room catalogue — computed by a pure diff, no push service required. |

## Screenshots

Every screen below was captured from the running app on an Android 14 (API 34)
emulator at 1080 × 2340.

| Login | Register | Forgot password | Home (Discover) |
| :---: | :---: | :---: | :---: |
| ![Login](docs/screenshots/01-login.png) | ![Register](docs/screenshots/11-register.png) | ![Forgot password](docs/screenshots/12-forgot-password.png) | ![Home](docs/screenshots/02-home.png) |

| Event detail | Map | Favourites | Search |
| :---: | :---: | :---: | :---: |
| ![Event detail](docs/screenshots/03-event-detail.png) | ![Map](docs/screenshots/09-map.png) | ![Favourites](docs/screenshots/04-favorites.png) | ![Search](docs/screenshots/05-search.png) |

| Create – details | Create – date & venue | Date picker | Create – review |
| :---: | :---: | :---: | :---: |
| ![Create details](docs/screenshots/06-create-event.png) | ![Create date and venue](docs/screenshots/13-create-event-location.png) | ![Date picker](docs/screenshots/14-date-picker.png) | ![Create review](docs/screenshots/15-create-event-review.png) |

| Profile | Edit profile | Settings |
| :---: | :---: | :---: |
| ![Profile](docs/screenshots/07-profile.png) | ![Edit profile](docs/screenshots/10-edit-profile.png) | ![Settings](docs/screenshots/08-settings.png) |

## Tech stack

| Layer | Choice | Why |
| --- | --- | --- |
| Language | **Kotlin 1.9.22** | First-class Android language, coroutines, null-safety. |
| UI | **Jetpack Compose** (BOM 2024.02, Material 3) | Declarative UI, single activity, no XML layouts. |
| Architecture | **MVVM + Repository** | Testable, unidirectional data flow (`StateFlow`). |
| DI | **Manual `AppContainer`** | Transparent, dependency-free alternative to Hilt for a prototype. |
| Local DB | **Room 2.6.1** | Compile-time verified SQLite for the offline event cache. |
| Preferences | **DataStore Preferences 1.0** | Async, type-safe key/value storage for settings & session. |
| Networking | **Retrofit 2.9 + OkHttp 4.12 + Gson** | Industry standard REST client with logging interceptor. |
| Images | **Coil 2.6** | Coroutine-friendly Compose image loading. |
| Maps | **osmdroid 6.1.18** | Free OpenStreetMap tiles, no API key, no billing. |
| Security SDK | **AndroidX Biometric 1.1** | Fingerprint / face unlock. |
| Build | **AGP 8.2.2, Gradle 8.7, JDK 17** | Current stable toolchain. |
| Tests | **JUnit 4 + kotlinx-coroutines-test** | Pure JVM unit tests, no device required. |

## Architecture

EventFinder follows a clean three-layer **MVVM** architecture. The UI layer never talks to
Retrofit or Room directly — everything flows through repositories that hide the data sources.

### Layered overview

```mermaid
flowchart TB
    subgraph UI["UI layer (Jetpack Compose)"]
        Screens["Screens + Navigation<br/>Splash · Login · Register · Home · Detail<br/>Search · Create · Favourites · Profile · Settings"]
        VMs["ViewModels<br/>StateFlow&lt;UiState&gt;"]
    end

    subgraph Domain["Domain layer (pure Kotlin)"]
        Models["Models · EventCategory · EventFilterer"]
    end

    subgraph Data["Data layer (repositories)"]
        Repos["EventRepository · AuthRepository · WeatherRepository · EventDiscoveryRepository"]
    end

    subgraph Sources["Data sources"]
        Room[("Room cache<br/>events · favorites · rsvps · users")]
        DS[("DataStore<br/>session & settings")]
        OM["Open-Meteo API<br/>keyless"]
        OSM["OpenStreetMap / Overpass<br/>keyless"]
    end

    Screens --> VMs
    VMs --> Repos
    VMs --> Domain
    Repos --> Domain
    Repos --> Room
    Repos --> DS
    Repos --> OM
    Repos --> OSM
```

### Venue discovery flow

```mermaid
sequenceDiagram
    participant H as HomeViewModel
    participant R as OpenStreetMapRepository
    participant API as Overpass API

    H->>R: findNearbyVenues(lat, lng)
    R->>API: GET /api/interpreter (around query)
    API-->>R: OsmOverpassResponse
    R->>R: map to OsmVenue list
    R-->>H: nearby venues (theatres, stadiums, etc.)
```

### Navigation graph

```mermaid
flowchart LR
    Splash -->|logged out| Login
    Splash -->|logged in| Main
    Login --> Register
    Login -->|success| Main
    Register -->|success| Main

    subgraph Main["Main (bottom navigation)"]
        direction LR
        Home --> Detail
        Search --> Detail
        Favourites --> Detail
        Profile
        Create
    end

    Profile --> EditProfile
    Profile --> Settings
    Profile -->|log out| Login
```

## External APIs


EventFinder uses public services that do not require API keys or tokens:

| Service | Auth | Used for |
| --- | --- | --- |
| **Open-Meteo** | Keyless | Weather forecasts |
| **OpenStreetMap** | Keyless | Map data |
| **Overpass API** | Keyless for normal OSM data queries | Nearby venues and event-related places |
| **Ardent Africa** | Keyless (optional API key for higher limits) | Public event discovery |
| **Android Location APIs** | Device permission | Current device location |
| **Room / SQLite** | Local | Events, users, favourites and RSVPs |

### OpenStreetMap / Overpass

Overpass is used to discover nearby event-related places such as:

- theatres
- cinemas
- arts centres
- museums
- galleries
- stadiums
- sports centres
- community centres
- conference centres
- attractions
- theme parks
- zoos

Overpass provides OpenStreetMap objects rather than a commercial event calendar.
Therefore OSM venue discovery does not fabricate event dates or scheduled performances.

Actual scheduled EventFinder events are stored locally in Room and may be created by
the user.

OpenStreetMap data is © OpenStreetMap contributors.

OpenStreetMap requires appropriate attribution when using its data.

### Public JSON event feeds

EventFinder can discover real-world events from keyless public JSON feeds. The pipeline works as follows:

1. Each `EventSource` implementation fetches events from its endpoint.
2. `PublicJsonEventMapper` normalises different JSON schemas into a common `RemoteEvent` model.
3. `EventDiscoveryRepository` validates each event (future-only, valid coordinates, non-blank title).
4. Valid events are upserted into Room with `remote:` prefixed IDs to prevent collisions with user-created events.
5. Events without valid coordinates are rejected since they cannot be plotted on the map.

Currently configured sources:

| Source | URL | Key required |
| --- | --- | --- |
| **Ardent Africa** | `https://api.ardent.africa/public/v1/events` | No |

New sources can be added in `SouthAfricaEventSources.kt` without changing any other code.
The `PublicJsonEventClient` handles `{ "events": [...] }`, `{ "data": [...] }`, `{ "results": [...] }`,
and bare JSON array formats automatically.

## Localisation

The whole UI is externalised to string resources and ships in two languages:

- `res/values/strings.xml` — **English**
- `res/values-af/strings.xml` — **Afrikaans**

The language is switched at runtime from **Settings** (persisted in DataStore) and applied
through `LocaleManager` in `MainActivity.attachBaseContext`.

## Offline-first behaviour

- Events, favourites and RSVPs are cached in **Room** and observed as `Flow`s, so the UI renders instantly.
- Favourite, RSVP and event mutations are wrapped in **atomic `@Transaction` boundaries** for consistency.
- The app attempts to discover public JSON event feeds when online; discovery failure never blocks the local catalogue.
- Reminder alarms carry the owning `userId` and `ReminderReceiver` verifies the active session before posting, preventing stale-account notifications.
- The UI never blocks on the network: a failed discovery simply keeps the cached catalogue.

## Security

- Passwords are **never stored in plaintext** — they are salted and hashed with **PBKDF2WithHmacSHA256** (120 000 iterations, 256-bit key) following the OWASP Password Storage Cheat Sheet.
- **Biometric unlock** uses the AndroidX Biometric SDK and degrades gracefully when hardware is unavailable.
- Credentials never leave the device. `local.properties` and keystores are git-ignored.

## Getting started

### Prerequisites

- **Android Studio Hedgehog** (or newer) or the Android command-line tools
- **JDK 17** (`JAVA_HOME` must point at it — AGP 8.x does not support JDK 21)
- Android SDK with **API 34** platform + build-tools
- A physical device or emulator running **API 26+**

### 1. Clone

```bash
git clone https://github.com/Zulfique/eventfinder-south-africa.git
cd eventfinder-south-africa
```

### 2. Configure the SDK path

Create `local.properties` in the project root (git-ignored):

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

### 3. No API keys required

EventFinder does not require:

- API keys
- API tokens
- developer accounts
- cloud backend credentials
- secrets embedded in the APK

Open-Meteo and OpenStreetMap/Overpass are accessed directly by the Android app.

### 4. Build & install

```bash
# Linux / macOS / Git Bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Windows (PowerShell)
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Testing

The project has **115 JVM unit tests** across 14 suites, all runnable from the command line with
no emulator:

```bash
./gradlew testDebugUnitTest
```

| Suite | Covers |
| --- | --- |
| `ValidatorsTest` | Email, password strength, name and the multi-field registration form. |
| `PasswordHasherTest` | PBKDF2 hashing, salting, verification and fail-closed behaviour on malformed values. |
| `DistanceCalculatorTest` | Haversine distance, symmetry, rounding and radius checks. |
| `EventFiltererTest` | Keyword / category / radius filtering (including address and description matching), three sort orders, distance attachment. |
| `WeatherRepositoryTest` | WMO weather-code descriptions and Open-Meteo payload handling. |
| `OpenStreetMapRepositoryTest` | Overpass venue query mapping and geographic bounding. |
| `EventRepositoryTest` | Seeding, favourite/RSVP toggling, event creation, editing/deleting with ownership guard, cache clearing — using in-memory DAO fakes. |
| `SampleEventsProviderTest` | Demo catalogue integrity (unique ids, valid SA coordinates, sane dates). |
| `CreateEventValidationTest` | Per-step wizard validation (required title/description, future date, venue, coordinate ranges) that drives the inline error messages. |
| `EventAlertDetectorTest` | Pure new-event / favourite-changed diffing, including quiet first sync and past-event suppression. |
| `DateTimeUtilsTest` | Relative date helpers (today/tomorrow, day & hour offsets) and stable date formatting. |
| `LoginViewModelTest` | Password-reset flows (mismatch, success) with a fake repository. |
| `EventDiscoveryRepositoryTest` | Event discovery pipeline: valid future events inserted, missing coordinates rejected, past events rejected, source failures handled gracefully, deduplication by stableId, multi-source merging. |

HTML reports are written to `app/build/reports/tests/testDebugUnitTest/index.html`.

### Instrumented UI tests

A small **Compose UI test** suite runs on a connected device/emulator and covers the shared
widgets (`app/src/androidTest/.../ui/components/CommonComponentsTest.kt`):

```bash
./gradlew connectedDebugAndroidTest
```

| Test | Covers |
| --- | --- |
| `eventCard_displaysTitleAndVenue` | Card renders the event title, venue and metadata. |
| `eventCard_clickInvokesCallback` | Tapping the card fires its `onClick`. |
| `eventCard_favouriteToggleInvokesCallback` | The heart button reports add/remove from its content description. |
| `categoryChips_selectsACategoryAndCanClearIt` | Category chips select and clear the filter. |
| `categoryChips_selectsTheActiveChip` | The active category exposes selected semantics; the others do not. |
| `emptyState_displaysTitleAndSubtitle` | The reusable `EmptyState` renders its icon, title and subtitle. |

## Continuous integration

GitHub Actions runs on every push / PR to `main` (`.github/workflows/android-ci.yml`):

1. Validate the Gradle wrapper
2. Set up **JDK 17**
3. `./gradlew testDebugUnitTest`
4. `./gradlew lintDebug`
5. `./gradlew assembleDebug`
6. Upload the test report and the debug APK as build artifacts

CI never depends on a secret to compile — all external APIs are keyless.
The pipeline runs unit tests, lint, and assembles a debug APK.

## Project structure

```
app/src/main/java/com/eventfinder/app/
├── data/
│   ├── local/          Room entities, DAOs, database + entity↔domain mappers
│   ├── remote/         Retrofit services, DTOs, API client (Open-Meteo + Overpass + public JSON)
│   ├── remote/dto/     Public JSON event DTOs with multi-format field resolution
│   ├── remote/model/   Provider-independent RemoteEvent model
│   ├── repository/     Event / Auth / Weather / Discovery repositories + sample seed data
│   ├── sources/        EventSource interface, PublicJsonEventSource, mapper, South Africa config
│   └── store/          DataStore user preferences
├── di/                 AppContainer (manual dependency injection)
├── domain/model/       Event, User, RSVP, categories, filter/sort engine
├── notifications/      Notification channel + reminder receiver
├── security/           Biometric authentication wrapper
├── ui/
│   ├── components/     Shared Compose components + UiMessage
│   ├── navigation/     NavHost + bottom navigation
│   ├── screens/        splash · auth · home · detail · search · create · favorites · profile · editprofile · settings
│   └── theme/           Material 3 colour scheme & typography
└── utils/              Logging, date/time, distance, validation, hashing, locale, network
app/src/test/java/com/eventfinder/app/   JVM unit tests
app/src/androidTest/java/com/eventfinder/app/   Compose instrumented UI tests
docs/screenshots/                        Real device screenshots
```

## Requirement traceability

| Requirement | Where it is implemented |
| --- | --- |
| RESTful API integration | Open-Meteo (`WeatherRepository`) and OpenStreetMap/Overpass (`OpenStreetMapRepository`) — all keyless |
| External library integration | Room, Retrofit/OkHttp, DataStore, Coil, osmdroid, AndroidX Biometric |
| Native Android SDK integration | `AlarmManager` + `NotificationManager` reminders, `LocationManager`/location permissions, biometrics |
| Offline-first / robustness | Room cache + local operation journal, graceful fallbacks, validation on every form |
| Unit testing | 115 JVM tests + 6 Compose instrumented tests + GitHub Actions CI |
| Logging & comments | `AppLogger` used across data/UI layers; KDoc on every class |
| Documentation | This README with Mermaid architecture diagrams |

## Known limitations

The app is a **single-device, offline-first prototype** built entirely on free services, so a few
features that need a shared server are intentionally out of scope:

- **RSVP attendee management** — there is no way to approve or decline other people's attendance
  because accounts and events live only on the device. The RSVP counter and reminder cancellation
  work locally; a real attendee list would need a multi-user backend.
- **Google sign-in** is shown but stubbed. Password reset is a local email-lookup that updates the Room hash — no cloud backend is needed. **Security limitation:** reset performs no proof of email ownership, so it must be replaced with email/SMS verification before production use. Password change is also available in Settings.
- **Push notifications** are replaced by on-device notifications (`AlarmManager` +
  `NotificationManager`); true push would need Firebase Cloud Messaging.
- **Default city / radius** preferences exist in the data layer but have no settings UI yet.
- **Public event feeds** — the architecture supports keyless public JSON event sources via `EventDiscoveryRepository`. Ardent Africa (`https://api.ardent.africa/public/v1/events`) is configured as a verified source. Events without valid coordinates are correctly rejected since they cannot be plotted on the map. Additional sources can be added to `SouthAfricaEventSources.kt`.
- **isPublic** means "visible in this device's local catalogue only" — there is no cross-device sharing.

## Attribution & licences

- Weather data by **Open-Meteo.com** (CC-BY 4.0).
- Map data © **OpenStreetMap** contributors.
- Venue discovery powered by the **Overpass API** (ODbL).
- Haversine formula adapted from [Moveable Type Scripts](https://www.movable-type.co.uk/scripts/latlong.html) by Chris Veness.
- Password hashing guidance from the [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
