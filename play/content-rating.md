# Play Console — Content rating questionnaire (IARC)

The app has no first-party violence, sexual content, profanity, drugs, gambling or horror
content. The rating is driven almost entirely by **user-generated events, reviews and
group-plan messages**, and by the optional **location** permission.

## Category to pick

**Social networking / Communication** is the closest fit for a UGC app where people post events,
review them and message each other in group plans. If Play's questionnaire offers
**"Utility, Productivity, Communication or Other"**, choose that instead and answer the UGC
questions below the same way.

## Answers

| Question | Answer | Why |
|---|---|---|
| Does the app contain user-generated content? | **Yes** | Events, reviews, collections and group-plan messages |
| Can users interact or communicate with each other? | **Yes** | Event reviews, organiser profiles, group planning |
| Does the app share the user's location with other users? | **No** | Location is used to sort events; public events show the venue the organiser typed, not the viewer's position |
| Does the app allow users to share personal information? | **Yes** — organiser contact details can appear in event descriptions | Mitigated by the in-app report flow |
| Does the app contain ads? | **No** | No ad SDK |
| Does the app allow purchases? | **No** | "Get Tickets" links to organiser-provided ticketing; no in-app billing |
| Violence, sexual content, profanity, drugs, gambling, horror | **No** for first-party content | UGC is unmoderated at post time, which is why review + reporting exist |
| Does the app let users report content? | **Yes** | Report event / report organiser screens |

## Expected outcome

- **Google Play:** Teen / 13+ (UGC and user interaction are the drivers, not the content itself)
- **PEGI:** 12
- **USK / ESRB:** 6 / Everyone 10+

## Mitigations to describe if Play asks for evidence

The repository contains the mechanisms Play looks for when UGC is present:

- `ReportScamScreen` and the report entry points on event and organiser screens
- In-app **Delete account** (Settings) that also removes the user's server-side data
- A published privacy policy at `docs/privacy-policy.html`

**Gap worth closing before/just after launch:** there is no moderation queue or block-user
feature in the app yet, and Play may ask how reported content is acted on. Have the answer ready
(manual review of reports through the API/admin side) rather than claiming automated moderation.
