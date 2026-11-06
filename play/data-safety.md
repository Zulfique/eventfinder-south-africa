# Play Console — Data safety answers

Fill these in under **Policy → App content → Data safety**. They describe what the released app
(`versionCode 4`, target API 36) actually does, based on the code in this repository. If you add
an analytics or crash SDK later, this form must change.

## Section 1 — Overview

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** — HTTPS only, `usesCleartextTraffic=false` |
| Do you provide a way for users to request that their data is deleted? | **Yes** — Settings → Delete account, plus privacy@eventfinder.co.za |

## Section 2 — Data types

For each row: *Collected* = sent off the device. *Shared* = passed to a third party for their own
purposes (Firebase and Google Sign-In act as service providers on your behalf, so they are
processors, not "sharing").

| Data type | Collected | Shared | Optional? | Purpose | Notes |
|---|---|---|---|---|---|
| **Name** | Yes | No | No for accounts; guests are "Guest" | Account management | Editable in Profile |
| **Email address** | Yes | No | Google sign-in returns it; email sign-in requires it | Account management, app functionality | Guests have an empty email |
| **User IDs** | Yes | No | No | Account management | Local UUID, plus Firebase/Google account id when signed in |
| **Approximate location** | Yes | No | **Yes** — permission optional | App functionality (nearby events) | Only city + radius is used; app works if denied |
| **Other user-generated content** | Yes | No | Yes | App functionality | Events, tickets, collections, reviews, reports, group-plan messages |
| **Other in-app actions** | Yes | No | No | App functionality | Favourites, saved city/radius, reminder preferences |
| **Device or other IDs** | Yes | No | Yes (notifications) | App functionality | Firebase Cloud Messaging token, only for push |

Explicitly **not** collected: precise location, contacts, calendar, SMS/call logs, photos or
media library, health data, financial info, browsing history, crash logs, diagnostics,
advertising ID. No `com.google.android.gms.permission.AD_ID` is declared.

## Section 3 — Purposes to tick

- App functionality
- Account management
- **Not** ticked: Advertising or marketing, Analytics, Personalisation, Fraud prevention.

## Section 4 — Security practices

- Data encrypted in transit: **Yes**
- Users can request data deletion: **Yes**
- Committed to Play Families Policy: **No** (not a children's app)
- Independent security review: **No**

## Section 5 — Things to keep honest

1. There is an **"anonymous usage analytics" toggle** in Settings, but no analytics SDK is
   bundled. Do not tick Analytics on this form while that is true.
2. Event cover images load from **Unsplash/Pixabay** and maps from **OpenStreetMap**. Those
   requests expose the user's IP address and user-agent to those providers. That is normal
   third-party content delivery and does not need its own Data safety row, but it is why the
   privacy policy names them.
3. If the API host is reconfigured through `EVENTFINDER_API_URL`, confirm it is still HTTPS
   before submitting.
