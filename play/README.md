# Play Store submission pack

Everything in this folder is generated from the released build (`versionCode 4`, `1.2.0`,
target API 36). Nothing here is fabricated: features listed are implemented in this repository.

```
play/
├── listing.md                 store listing copy, category, contact details
├── data-safety.md             Data safety form answers (matches what the code sends)
├── content-rating.md          IARC questionnaire answers for a UGC events app
└── assets/
    ├── icon-512.png                       512 × 512 store icon
    ├── feature-graphic-1024x500.png       1024 × 500 feature graphic
    └── phone-01-login.png … 05-profile    1080 × 2160 screenshots, no device frames
```

## Checklist

| Step | State |
|---|---|
| Target API 36 | Done in `b701bab` |
| Signed AAB built and attached to GitHub release `v1.2.0` | Done |
| Release APK installed and exercised on Android 16 | Done |
| Privacy policy published | `docs/privacy-policy.html` — served via GitHub Pages |
| Store listing copy | `listing.md` |
| Graphics and screenshots | `assets/` |
| Data safety answers | `data-safety.md` |
| Content rating answers | `content-rating.md` |
| Upload AAB to Console and enrol Play App Signing | **You** — needs Play Console access |
| Back up `release.keystore` + `keystore.properties` | **You** — move the zip off this machine |
| Physical-device and Android 8–15 test pass | **You** — see gaps below |

## Regenerating the assets

The screenshots were captured from the signed release APK on the `Pixel_5_API_36` emulator at
1080 × 2340 and cropped to 1080 × 2160 to satisfy Play's 2:1 aspect limit:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell pm clear com.eventfinder.app && adb shell pm grant com.eventfinder.app android.permission.POST_NOTIFICATIONS
adb shell am start -n com.eventfinder.app/.MainActivity
adb exec-out screencap -p > raw_login.png
```

The icon re-renders the same geometry as `res/drawable/ic_launcher_foreground.xml`
(white pin body, `#007749` head, `#FFB81C` roof) on the `#00A97C` adaptive-icon background, so
the store icon matches the launcher icon.

## Remaining honesty notes

- **Only English and Afrikaans exist.** Play users on other locales get English.
- **No analytics or crash SDK is bundled**, which is why the Data safety form does not tick
  Analytics, even though Settings has an opt-in switch.
- **No moderation tooling** beyond in-app reporting exists yet; have an answer ready for how
  reported content is reviewed.
