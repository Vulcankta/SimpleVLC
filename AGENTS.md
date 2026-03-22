# AGENTS.md - SimpleVLC Development Guide

This file provides guidance for agentic coding agents working in this repository.

## Project Overview

**SimpleVLC** is an Android video player application built with Kotlin, using libvlc-all for video playback.
- **Language**: Kotlin 1.9.x | **Java**: 17 | **Min SDK**: 29 | **Target SDK**: 34
- **Build System**: Gradle with Kotlin DSL (AGP 8.2.0)

## Build Commands

```bash
# Build debug/release
./gradlew assembleDebug        # Debug APK
./gradlew assembleRelease      # Release APK (requires signing)
./gradlew clean assembleDebug  # Clean rebuild

# Install to device
./gradlew installDebug

# Lint
./gradlew lint                 # Run lint analysis
./gradlew lintBaseline         # Run with baseline (suppresses known issues)

# Tests (none currently exist)
./gradlew test --tests "com.simplevlc.TestClassName"

# Code quality
./gradlew check                 # Format + lint checks
```

## Project Structure

```
app/src/main/java/com/simplevlc/
├── MainActivity.kt          # Video list screen
├── PlayerActivity.kt        # Video playback screen
├── SimpleVLCApp.kt          # Application class (LibVLC init)
├── ThemeManager.kt          # Theme switching
├── adapter/                 # RecyclerView adapters
├── model/                   # Data classes
├── player/                  # PlayerController, ProgressUpdater
├── repository/              # Video, Bookmark, PlaybackHistory repos
├── service/                 # PlaybackService, SleepTimerManager
├── ui/quicklist/            # Quick list fragment
└── utils/                   # TimeUtils
```

## Code Style

### Naming Conventions
| Element | Convention | Example |
|---------|------------|---------|
| Classes/Files | PascalCase | `PlayerActivity.kt`, `VideoAdapter` |
| Functions/Properties | camelCase | `videoList`, `playNext()` |
| Constants | SCREAMING_SNAKE_CASE | `EXTRA_VIDEO_PATH` |
| Boolean | `is*` prefix | `isPlaying`, `isDarkMode` |
| Interfaces | PascalCase + `Callback` suffix | `PlayerController.Callback` |
| Layouts/Views | snake_case | `activity_player.xml`, `text_view_name` |
| Strings/Colors | snake_case | `video_list_empty`, `color_primary` |

### Imports (in order)
1. `android.*` - Android framework
2. `kotlin.*` - Kotlin stdlib
3. `org.videolan.*`, `com.google.*` - Third-party
4. `androidx.*` - AndroidX
5. `com.simplevlc.*` - Project imports

Use explicit imports; avoid wildcard `.*` except for kotlin stdlib.

### Formatting
- **Indentation**: 4 spaces | **Line length**: 120 chars max
- **Braces**: Same-line (Kotlin style) | **Blank lines**: Single between sections
- **Documentation**: KDoc (`/** */`) for public APIs and complex functions

## Types & Patterns

### Data Classes
```kotlin
data class Video(
    val id: Long,
    val displayName: String,
    val path: String,
    val dateAdded: Long,
    val duration: Long,
    val size: Long,
    val uri: Uri
)
```

### Sealed Classes / Interfaces
- Use sealed classes for restricted hierarchies
- Use interfaces for callbacks (e.g., `PlayerController.Callback`)
- Use `lateinit` for properties initialized in `onCreate()`
- Use nullable types (`?`) only when value can legitimately be null

### ViewBinding
```kotlin
private lateinit var binding: ActivityMainBinding
binding = ActivityMainBinding.inflate(layoutInflater)
setContentView(binding.root)
```

### RecyclerView
- Use `ListAdapter` with `DiffUtil` for efficient updates
- Cancel async work (thumbnails) in `onViewRecycled()`

## Error Handling
```kotlin
try {
    videoRepository.getVideos(sortOrder)
} catch (e: Exception) {
    Toast.makeText(this, "Load error: ${e.message}", Toast.LENGTH_LONG).show()
    e.printStackTrace()
}
// Use finally blocks or Kotlin's use() for resource cleanup
```

## Coroutines
- `Dispatchers.Main` for UI updates | `Dispatchers.IO` for file/content operations
- Use `SupervisorJob()` for independent coroutines
- Cancel coroutines when views are recycled | Use `lifecycleScope` for scoped lifecycles

## LibVLC Integration
- Initialize LibVLC once in `SimpleVLCApp` (singleton via `instance`)
- Use `MediaPlayer` from `org.videolan.libvlc`
- Use `fd://` URIs for content resolver playback:
  ```kotlin
  val fdUri = AndroidUtil.LocationToUri("fd://${fileDescriptor.fd}")
  ```

## Common Issues
- **Hardcoded strings**: Use `@string` resources
- **Accessibility**: Add `contentDescription` to image views
- **Memory leaks**: Cancel coroutines and remove callbacks in `onDestroy()`
- **Null checks**: Use `?.` and `?:`, check `isInitialized` for `lateinit`
- **Main thread**: Move I/O to `Dispatchers.IO`

## Lint Baseline
Project uses `lint-baseline.xml` to suppress known issues. Fix real bugs; add acceptable patterns to baseline.

## Version Info
- Gradle/Kotlin config in `gradle.properties`
- AndroidX enabled | Non-transitive R class
- JDK 17 required