# MyMeditation

A meditation timer and session log app for Android 12+.

## Features

- **Session-based timer**: Configure multiple meditation sessions with different settings
- **Session types**: Closed (countdown) or Open (stopwatch-style)
- **Preparation time**: Configurable prep period before sitting begins
- **Triggers**: Schedule sounds at specific times during meditation
  - Ring internal bell or play MP3 track
  - Configurable volume, repeat interval, execution count, and gap
  - Test button to preview trigger sounds
- **Reminders**: Daily notifications with threshold (only shown if meditation < X minutes)
- **Statistics**: Track total meditation time (today, week, month, all time)
- **Settings**:
  - Remember and restore volume between sessions
  - Play sounds as alarms (more reliable, bypasses Do Not Disturb)
  - Export/import database for backup

## SDK Setup

### Prerequisites

1. **JDK 17** (required for Android Gradle Plugin 8.x)
   - Download from [Adoptium](https://adoptium.net/)
   - Set `JAVA_HOME` environment variable

2. **Android SDK**
   - Install via [Android Studio](https://developer.android.com/studio) (recommended), OR
   - Install command-line tools only:
     ```batch
     :: Download commandlinetools from https://developer.android.com/studio#command-tools
     :: Extract to e.g. C:\Android\sdk\cmdline-tools\latest\
     
     :: Accept licenses
     C:\Android\sdk\cmdline-tools\latest\bin\sdkmanager --licenses
     
     :: Install required packages
     C:\Android\sdk\cmdline-tools\latest\bin\sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
     ```
   - Set `ANDROID_HOME` to your SDK path (e.g. `C:\Android\sdk`)

3. **Create local.properties**
   ```batch
   echo sdk.dir=C:\\Android\\sdk > local.properties
   ```
   (Adjust path to your actual SDK location)

### Building

```batch
:: Generate Gradle wrapper (if not already present)
gradle wrapper

:: Build debug APK
gradlew assembleDebug

:: Install on connected device
gradlew installDebug

:: The APK will be at app\build\outputs\apk\debug\app-debug.apk
```

### Using Android Studio (easiest)

1. Install [Android Studio](https://developer.android.com/studio)
2. Open this project folder
3. Android Studio will auto-download SDK and Gradle
4. Click Run ▶

## Project Structure

```
app/src/main/java/com/mymeditation/app/
├── data/
│   ├── entities/     # Room entities (Session, Trigger, LogEntry, Reminder)
│   ├── dao/          # Data access objects
│   └── AppDatabase   # Room database singleton
├── service/
│   └── TimerService  # Foreground service for meditation timer
├── receiver/
│   └── ReminderReceiver  # Alarm-based reminder notifications
├── ui/
│   ├── main/         # Main timer screen
│   ├── sessions/     # Session & trigger configuration
│   ├── reminders/    # Reminder configuration
│   ├── settings/     # App settings
│   └── stats/        # Meditation statistics
└── util/
    ├── AudioHelper   # Bell & MP3 playback, volume management
    ├── SettingsManager  # SharedPreferences wrapper
    └── DatabaseExporter  # Export/import database
```

## Permissions

- `POST_NOTIFICATIONS` - Timer and reminder notifications (Android 13+)
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` - Timer service
- `WAKE_LOCK` - Keep timer running when screen off
- `SCHEDULE_EXACT_ALARM` - Precise reminder scheduling (Android 12+)
- `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` - MP3 file access
- `VIBRATE` - Haptic feedback
