# SimpleVLC

A lightweight Android video player built with Kotlin and libvlc.

## Features

- **Video Playback** - Play local video files using libvlc-all
- **Video Library** - Browse and sort videos by name, date, size, or duration
- **Quick List** - Save and manage a playlist of videos
- **Playback History** - Automatically track recently played videos
- **Bookmarks** - Mark specific positions in videos for quick resume
- **Sleep Timer** - Set a timer to stop playback automatically
- **Background Playback** - Continue playing audio/video in the background with notification controls
- **Theme Support** - Switch between light and dark mode

## Tech Stack

- **Language**: Kotlin 1.9.20
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 34 (Android 14)
- **Video Player**: libvlc-all 3.6.0
- **Architecture**: MVVM with Repository pattern
- **UI**: ViewBinding, Material Design 3
- **Async**: Kotlin Coroutines

## Project Structure

```
app/src/main/java/com/simplevlc/
├── MainActivity.kt          # Video list screen
├── PlayerActivity.kt        # Video playback screen
├── SimpleVLCApp.kt          # Application class (LibVLC init)
├── ThemeManager.kt          # Theme switching
├── adapter/                 # RecyclerView adapters
├── model/                   # Data classes
├── player/                  # Playback logic
├── repository/              # Data access layer
├── service/                 # Background services
├── ui/quicklist/            # Quick list feature
└── utils/                   # Utility classes
```

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Install to device
./gradlew installDebug

# Release APK (requires signing config)
./gradlew assembleRelease
```

## Permissions

- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_VIDEO` - Access video files
- `FOREGROUND_SERVICE` - Background playback
- `WAKE_LOCK` - Keep screen awake during playback
- `POST_NOTIFICATIONS` - Playback notification controls

## License

MIT License
