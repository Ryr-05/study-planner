# Noctil — Build Plan

> Name: **Noctil** (from "nocturnal"). Logo: a minimalist full moon with three soft craters (`docs/logo.svg`).
> Free Android app for students: tasks, calendar, widgets, lockscreen, works offline and online.

---

## 1. Reality check (read this first)

Some things in the feature list need adjusting so the app can stay **free** and actually work well.

| Item | What's true | What we'll do |
|---|---|---|
| **Lockscreen widgets** | Android phones dropped lockscreen widgets in Android 5. Newer Android is bringing them back, but mostly on tablets and some Pixels. Samsung has lockscreen widgets, but few third-party apps can appear there. | **Main approach:** a lockscreen card (a pinned notification that shows on the lockscreen) with *Today's tasks* + "Done" buttons. **Focus timer:** a live countdown on the lockscreen (Android 16 "Live Updates"). **Bonus:** when the phone supports lockscreen widgets, our widgets will also show up there. |
| **AI features** | Cloud AI costs money for every request. | Use free **on-device** AI where the phone supports it (Gemini Nano). Add a small daily limit on cloud AI as a fallback. The smart parser ("tomorrow 2pm !1") is written by hand, so it's free and works offline. |
| **Google/Apple Calendar two-way sync** | Calling Google's API directly needs OAuth verification and is a lot of work. | Use Android's **built-in calendar system** (CalendarContract). It already has Google, Samsung and Outlook calendars on the phone. Two-way, free, works offline, no Google review. Apple/iCloud works through the free DAVx⁵ app. |
| **Alexa / Siri / Telegram / MCP / iOS / Windows / Mac** | Each one is a whole project of its own. | **Not in v1.** Maybe later: a web version, then a Telegram bot. |
| **"Premium" Timeline** | — | Free. Everything is free. |

---

## 2. Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language / UI | **Kotlin + Jetpack Compose + Material 3** | Google's current standard. Dark mode and "Material You" colors built in. |
| Local database | **Room (SQLite)** | The app runs from the phone's database, so it works offline. Widgets can read it instantly. |
| Widgets | **Jetpack Glance** | Build home-screen widgets with Compose-style code. |
| Background work | **WorkManager + AlarmManager** | Syncing, and reminders that go off on time. |
| Accounts + cloud sync | **Firebase Auth + Firestore** (free Spark plan) | Free quota is plenty for a student app. Google sign-in. Supports shared lists. Supabase was skipped because free projects pause after a week without use. |
| Voice | Android **SpeechRecognizer** | Free. Works offline on many phones. |
| Location reminders | Play Services **Geofencing** | "Remind me when I get to campus." |
| Min Android version | **Android 8.0 (API 26)** | Covers about 97% of phones. |

**Offline-first design:** the app always reads and writes the phone's database. A background sync job sends changes to the cloud whenever there's internet, and pulls down changes from your other devices. **Signing in is optional.** The app is fully usable without an account. Signing in adds backup, sync and sharing.

```
UI (Compose) ─┐
Widgets ──────┼──► Room DB (source of truth) ◄──► SyncWorker ◄──► Firestore
Lockscreen ───┘         ▲                          (only when online)
                        └── Reminders (AlarmManager)
```

Each record carries `updatedAt`, `deleted` (soft delete) and `dirty` flags. If the same item was changed in two places, the newer change wins.

---

## 3. Making it VERY user friendly

**Navigation: 4 bottom tabs, nothing hidden**

| Tab | What's there |
|---|---|
| 📋 **Today** | Overdue → Today → Tomorrow → Upcoming. Lists/subjects in a side drawer. |
| 📅 **Calendar** | Month/Week/Day. Tap a day to see its tasks. Drag a task onto a time slot to block time. |
| ⏱️ **Focus** | Big start button. Pick a task (optional). Pomodoro or stopwatch. |
| 🙂 **Me** | Habits, stats, settings, account. |

**Rules we stick to**
- One big **+** button on every screen. You type "Math hw fri 5pm !1 #calc" and see chips appear live: 📅 Fri 5:00 PM · 🔴 High · #calc. Tap a chip to fix it.
- **Swipe right = done, swipe left = reschedule.** Every action has an **Undo**.
- List / Kanban / Matrix / Timeline are a **view switch on a list**, not extra screens.
- Onboarding is 3 screens at most and starts with example tasks, so the app never opens empty.
- Big tap targets, readable fonts, dark mode, and works with TalkBack (screen reader).
- Advanced features (recurrence rules, location, batch edit) stay out of sight until you need them.

**Visual design: dark purple + liquid glass**
- **Scene:** students at a desk or in bed at night, a phone held close. Dark makes sense there, so the app is dark by design.
- **Palette:** deep aubergine ground (`#0E0A17`), solid surfaces (`#1A1427`, `#241C36`), one lavender accent (`#B9A3FF`). A faint, still aurora sits behind everything. Rose, amber and blue appear only for task priority.
- **Glass, used the way Apple uses it:** only on controls that float above content (tab bar, + button, quick-add sheet). Content stays on solid surfaces so it's easy to read. Glass = live blur of what's behind (Android 12+) + purple tint + a bright rim that fades downward + a soft top highlight. On Android 8 to 11 it falls back to a near-solid panel.
- **Signature motion:** the tab highlight moves like a drop of liquid. Its leading edge springs ahead and the back edge catches up. All motion snaps instantly if the phone has animations turned off.
- **Shapes:** every tappable control is a full pill or circle. Panels use 24dp corners, text fields 16dp.
- **Type:** Geist, one family. Big semibold screen titles, calm 16sp body text.
- **Text:** passes WCAG AA contrast. No emoji in the interface; icons come from Material Symbols (Rounded).

**Student touches**
- **Subjects** = lists with a color and an emoji (📐 Calculus, 🧪 Chem).
- **Class timetable** shown on the calendar. Enter it once, it repeats every week.
- **Exam countdown** ("Chem final in 12 days") on the widget and the Today screen.
- "**Due tonight**" section, plus a reminder the evening before by default.

---

## 4. Features by phase

Every phase ends with a new APK you install and test on your phone.

### Phase 0 — Foundation *(do this first)*
- GitHub repo, empty app, automatic APK builds, "Download APK" button in the README
- App icon, theme, 4-tab navigation shell
- ✅ Done when: you tap the button on your phone and the app installs

### Phase 1 — Core tasks (offline)
- Tasks with title, notes, due date/time, priority, tags with colors
- Subtasks (multiple levels) and checklists
- Lists, folders, smart lists (Today, Next 7 Days, High Priority, By tag)
- **Smart parser:** dates/times ("tomorrow at 2pm", "next fri"), priority (`!1`–`!3`), recurrence ("every Monday"), tags (`#calc`), list (`~Chem`)
- Reminders + notifications with Done / Snooze buttons
- Search

### Phase 2 — Widgets + lockscreen
- Widgets: **Today list** (check things off from the widget), **Month calendar**, **Quick add**, **Exam countdown** (Habit check-in widget moves to Phase 4, where habits are built)
- Lockscreen Today card (pinned notification) — can be turned on/off in settings
- Quick Settings tile: "Add task"

### Phase 3 — Calendar
- Day / Week / Month / Year views
- Time blocking (drag tasks into time slots)
- Class timetable
- Two-way sync with phone calendars (Google, Samsung, Outlook, iCloud via DAVx⁵)

### Phase 4 — Focus & habits
- Pomodoro (25/5 or custom), stopwatch, linked to a task
- White noise (rain, café, library) — free sound files packaged with the app
- Live countdown on the lockscreen while focusing
- Habit tracker: streaks, % complete, heatmap, plus the habit check-in widget
- Stats: tasks done, hours focused, habit consistency (week/month/year charts)

### Phase 5 — Accounts + cloud sync
- Optional Google sign-in
- Background sync, works across devices, safe offline editing
- Backup / restore / export to CSV

### Phase 6 — More views + power tools
- Kanban board (To Do / Doing / Done, custom columns)
- Eisenhower Matrix (sorted automatically by priority + due date)
- Timeline / Gantt (start → due date bars)
- Batch select: complete, move, tag or reschedule many tasks at once
- "Keep reminding me" alarm that repeats until you deal with the task
- Location reminders

### Polish (after Phase 6) — done
- Layouts that hold up with the phone's biggest text size (screenshot-tested at 2x)
- Centered + button; Me opens from the profile button on each screen
- Full-screen alarms with phone tones or your own MP3s
- Much smarter typing (next-week days, ranges, lengths, typos, numeric dates, spoken times)
- Empty screens with tappable example tasks

### Phase 7 — Sharing
- Share a list by link (group projects, study groups)
- Assign tasks to members, see group progress
- See who changed what

### Phase 8 — AI & voice
- Voice add: "Read chapter 4 tomorrow and finish lab report Friday" → **2 tasks** with dates already set
- AI breakdown: "Write research paper" → suggested subtasks with dates
- "Plan my day" suggestions from overdue and due-soon tasks
- Record audio → transcript → summary (on-device where possible)

### Later / maybe
Web app → Telegram bot → MCP server. iOS, Alexa and Siri are not planned.

---

## 5. GitHub + "Download APK" button

**How it works**
1. Code lives in a GitHub repo.
2. Every push to `main` makes **GitHub Actions** (free) build and sign the APK in the cloud. Your Mac doesn't need to build anything. The version number goes up automatically (`0.1.<build number>`).
3. The APK is attached to a new GitHub **Release** with a fixed file name (`Noctil.apk`).
4. The README button always points at the newest one:

```markdown
[![Download APK](https://img.shields.io/badge/Download-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/<you>/<repo>/releases/latest/download/app.apk)
```

**Important details**
- **Same signing key every release.** Without it, your phone refuses to update and you'd have to uninstall (and lose local data). The key is stored as an encrypted GitHub Secret. Back it up somewhere safe.
- Every push also builds a test APK (under the Actions tab) for quick tries.
- On your phone: open the button link → download → allow "Install unknown apps" for your browser (one time) → install.
- Optional: the app checks GitHub for a newer version and shows an "Update available" banner.

---

## 6. What you need

| Thing | Needed for | Status on this Mac |
|---|---|---|
| GitHub account | Repo + APK builds | ? |
| Git | Uploading code | ✅ installed |
| **Android Studio** (free) | Writing code, emulator, quick test installs | ❌ not installed |
| GitHub CLI (`gh`) | Optional: lets Claude create the repo/releases for you | ❌ not installed |
| Firebase project (free) | Phase 5+ only | later |
| An Android phone | Testing | ✅ |

Android Studio includes Java and the Android SDK, so it's the only big install. Until Phase 0 is set up, GitHub Actions can build APKs without it.

---

## 7. Project structure (planned)

```
app1/
├── .github/workflows/
│   ├── build.yml          # every push → test APK
│   └── release.yml        # tag v* → signed APK on Releases
├── app/src/main/java/.../
│   ├── data/              # Room entities, DAOs, repositories, sync
│   ├── parser/            # natural-language quick-add parser (+ unit tests)
│   ├── reminders/         # alarms, notifications, lockscreen card
│   ├── widget/            # Glance widgets
│   ├── ui/today|calendar|focus|me/
│   └── ui/components/     # shared buttons, chips, task row
├── README.md              # with the Download APK button
└── PLAN.md                # this file
```
