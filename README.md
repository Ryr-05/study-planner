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

**Type tasks the way you'd say them.** Tap **+** and write something like
`chem lab fri 5pm !1 #lab ~Chemistry every monday`. Noctil picks out the date, time,
priority, tag, list and repeat, and highlights them as you type.

| Area | What you can do |
|---|---|
| Tasks | Notes, due date and time, priority, colored tags, subtasks (nested), checklists |
| Lists | Lists for each class, folders to group them, plus Today, Next 7 days, High priority, All, Completed and Inbox |
| Repeat | Daily, weekdays, weekly on chosen days, every few days, monthly, yearly |
| Reminders | Notification at the time you choose, with **Done** and **Snooze 10 min** buttons. Survives restarts |
| Speed | Swipe right to finish, swipe left to move to tomorrow, Undo after every change |
| Search | Titles, notes, checklist items, tags and list names |
| Exams | Tag a task `#exam` (or name it "midterm", "quiz"...) to get a countdown on Today |
| Calendar | Day, week, month and year views. Classes, phone events and tasks together |
| Time blocking | Tap an empty time to plan a task there. Long-press a task and drag it to a new time or day |
| Timetable | Enter each class once (days, time, room, color) and it shows every week |
| Phone calendars | See your Google, Samsung or Outlook events. Optionally copy dated tasks into one of them, two-way |
| Focus | Pomodoro and stopwatch, linked to a task. Countdown on the lockscreen (and a Live Update chip on Android 16). Soft rain, white or brown noise |
| Habits | Daily or chosen days, goals like 8 glasses of water, streaks, 20-week heatmap, reminders, home screen widget |
| Statistics | Tasks finished, focus time and habit consistency by week, month or year |
| Backup | Back up to a file (save it to Google Drive), restore it, or export tasks as CSV. Android's automatic backup is on too |
| Widgets | Today list (finish tasks from the home screen), month calendar, exam countdown, habits, quick add bar |
| Lockscreen | A silent Today card on your lockscreen, with an Add task button. Turn it off in Me |
| Shortcuts | Quick Settings tile "Add task", and long-press the app icon for Add task, Today and Focus |
| Sync | Optional Google sign-in (Me tab). Tasks, lists, habits, timetable and focus history sync across your devices |
| Offline | Everything is saved on your phone first. No account or internet needed; changes sync when you're back online |

**Adding widgets:** open **Me > Lockscreen and widgets** and tap **Add** next to any widget.

All planned phases (1 to 5) are in. Cloud sync uses a Firebase project; how it was set up is in [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md).

See [PLAN.md](PLAN.md) for the full roadmap.

## How the APK gets built

Every push to `main` triggers [GitHub Actions](.github/workflows/build.yml). It builds and signs the APK,
then publishes it as the newest [Release](https://github.com/Ryr-05/study-planner/releases).
The download button always points at that newest release.

## Tech

Kotlin, Jetpack Compose, Material 3, [Haze](https://github.com/chrisbanes/haze) for the glass blur.
Font: [Geist](https://github.com/vercel/geist-font) (SIL Open Font License, see `licenses/`).
