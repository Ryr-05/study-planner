<div align="center">

<img src="docs/logo.svg" width="112" alt="Noctil logo: a full moon with three craters">

# Noctil

A free task planner and calendar for students. Works offline. Made for Android.

<a href="https://github.com/Ryr-05/study-planner/releases/latest/download/Noctil.apk">
  <img src="https://img.shields.io/badge/Download-APK-B9A3FF?style=for-the-badge&logo=android&logoColor=1B1033&labelColor=1A1427" alt="Download APK" height="56">
</a>

<sub>Always the newest version. Android 8.0 or newer.</sub>

</div>

---

## Install on your phone

1. On your Android phone, tap **Download APK** above.
2. Open the downloaded file. If Android asks, allow your browser to **install unknown apps** (one time only).
3. Tap **Install**, then **Open**.

**Updating:** tap the button again and install. It updates in place and keeps your data.
You can also open the app, go to **Me > Check for updates**.

> If Google Play Protect shows a warning, tap **More details > Install anyway**.
> It appears because the app is not on the Play Store, not because something is wrong.

## What works right now

| Area | Status |
|---|---|
| Today list with overdue, today, coming up, completed | Working (sample tasks) |
| Quick add with the **+** button | Working (adds to today) |
| Month calendar with task markers | Working |
| Focus timer (25 / 5 / 15 minutes) | Working |
| Saving tasks, reminders, widgets, lockscreen card | Next updates |

See [PLAN.md](PLAN.md) for the full roadmap.

## How the APK gets built

Every push to `main` triggers [GitHub Actions](.github/workflows/build.yml). It builds and signs the APK,
then publishes it as the newest [Release](https://github.com/Ryr-05/study-planner/releases).
The download button always points at that newest release.

## Tech

Kotlin, Jetpack Compose, Material 3, [Haze](https://github.com/chrisbanes/haze) for the glass blur.
Font: [Geist](https://github.com/vercel/geist-font) (SIL Open Font License, see `licenses/`).
