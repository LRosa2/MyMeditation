# MyMeditation

A beautiful, distraction-free meditation timer for Android. No ads, no tracking — just a simple companion for your sitting practice.

<!-- Uncomment and replace when you have screenshots -->
<!-- ![App Screenshots](screenshots/combined.png) -->

## Features

### Timer
- **Multiple sessions** — Create and switch between different meditation configurations
- **Closed & Open sessions** — Set a fixed duration or meditate until you decide to stop
- **Preparation time** — Configurable lead-in before the session begins
- **Background service** — Timer keeps running even when the screen is off
- **Alarm mode** — Play sounds as alarms to bypass Do Not Disturb

### Triggers
- Schedule bell sounds or MP3 tracks at specific moments
- Configure repeat intervals, execution counts, and gaps
- Preview any trigger before starting your session

### Reminders
- Daily reminder notifications
- Smart threshold — only reminds if you meditated less than your goal

### Statistics & History
- Track total meditation time (today, week, month, all time)
- **Chain tracking** — See your consecutive-day streaks
- **Sitting log** — Full history with CSV export/import
- **Backup** — Export and import your complete database

### Design
- Clean, minimal interface
- Material Design 3 components
- Dark theme support
- No ads, no analytics, no distractions

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI | Android Views + Material Design 3 |
| Database | Room (SQLite) |
| Async | Kotlin Coroutines |
| Minimum SDK | Android 12 (API 31) |
| Target SDK | Android 14 (API 34) |

## Getting Started

### Prerequisites
- **JDK 17**
- **Android Studio** (recommended) or Android SDK command-line tools

### Build & Run

**With Android Studio:**
1. Open the project folder
2. Let Gradle sync
3. Click **Run ▶**

**From command line:**
```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### SDK Setup (manual)

If not using Android Studio, create `local.properties`:
```properties
sdk.dir=C:\\Android\\sdk
```

Then install the required SDK packages:
```batch
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

## Project Structure

```
app/src/main/java/com/mysimplemeditation/app/
├── data/           # Room entities, DAOs, database
├── service/        # TimerService (foreground meditation timer)
├── receiver/       # ReminderReceiver (alarm-based notifications)
├── ui/
│   ├── main/       # Timer screen
│   ├── sessions/   # Session & trigger editor
│   ├── reminders/  # Reminder configuration
│   ├── settings/   # App preferences
│   └── stats/      # Statistics, chains, sitting log
└── util/           # Audio, settings, database export
```

## Permissions

| Permission | Purpose |
|------------|---------|
| `POST_NOTIFICATIONS` | Timer & reminder notifications (Android 13+) |
| `FOREGROUND_SERVICE` | Keep timer running in background |
| `WAKE_LOCK` | Prevent timer from stopping when screen is off |
| `SCHEDULE_EXACT_ALARM` | Precise reminder scheduling |
| `READ_MEDIA_AUDIO` | Optional MP3 tracks for triggers |
| `VIBRATE` | Haptic feedback |

## Version History

| Version | Notes |
|---------|-------|
| 1.1 | Timer screen-off bug fix, About dialog, UI refinements |
| 1.0 | Initial release |

## Author

**Luis Rosa**
- Support: luisribeirosa at gmail.com

## License

This project is open source. Feel free to use, modify, and share.
