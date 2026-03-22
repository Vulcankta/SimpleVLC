# AGENTS.md - SimpleVLC Development Guide

This file provides guidance for agentic coding agents working in this repository.

## Project Overview

**SimpleVLC** is an Android video player application built with Kotlin, using libvlc-all for video playback.
- **Language**: Kotlin 1.9.20
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 34 (Android 14)
- **Build System**: Gradle with Kotlin DSL (AGP 8.2.0)

## Build Commands

### Build
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease

# Clean and build
./gradlew clean assembleDebug
```

### Run/Debug
- Install debug APK to connected device:
  ```bash
  ./gradlew installDebug
  ```

### Lint
```bash
# Run lint analysis
./gradlew lint

# Run lint with baseline (suppresses known issues)
./gradlew lintBaseline
```

### Single Test (if tests exist)
```bash
# Run a single test class
./gradlew test --tests "com.simplevlc.TestClassName"

# Run a single test method
./gradlew test --tests "com.simplevlc.TestClassName.testMethodName"
```

### Code Quality
```bash
# Format code (ktlint if configured)
./gradlew format

# Check formatting without modifying
./gradlew check
```

## Code Style Guidelines

### Project Structure
```
app/src/main/java/com/simplevlc/
├── MainActivity.kt          # Video list screen
├── PlayerActivity.kt        # Video playback screen
├── SimpleVLCApp.kt          # Application class (LibVLC init)
├── ThemeManager.kt          # Theme switching (light/dark)
├── adapter/                 # RecyclerView adapters
├── model/                   # Data classes
├── player/                  # Playback logic (PlayerController, ProgressUpdater)
├── repository/              # Data access (Video, Bookmark, PlaybackHistory)
├── service/                 # Background services (PlaybackService, SleepTimerManager)
├── ui/quicklist/            # Quick list fragment
└── utils/                   # Utility classes (TimeUtils)
```

### Naming Conventions

**Kotlin Files & Symbols:**
- Classes: `PascalCase` (e.g., `PlayerActivity`, `VideoAdapter`)
- Functions/properties: `camelCase` (e.g., `videoList`, `playNext()`)
- Constants in companion object: `SCREAMING_SNAKE_CASE` (e.g., `EXTRA_VIDEO_PATH`)
- Boolean properties/methods: `is*` prefix (e.g., `isPlaying`, `isDarkMode`)
- Interfaces: `PascalCase` with `Callback` suffix where appropriate

**XML Resources:**
- Layouts: `snake_case` (e.g., `activity_player.xml`, `item_video.xml`)
- Views in layouts: `snake_case` (e.g., `text_view_name`, `button_play_pause`)
- Drawables: `snake_case` with semantic names (e.g., `ic_play`, `ic_pause`)
- Colors: `snake_case` (e.g., `color_primary`)
- Strings: `snake_case` (e.g., `video_list_empty`)

**Package Names:**
- All lowercase: `com.simplevlc.adapter`, `com.simplevlc.player`

### Imports Organization

Organize imports in this order (IDE typically handles this):
1. Android framework imports (`android.*`)
2. Kotlin standard library (`kotlin.*`)
3. Third-party libraries (`org.videolan.*`, `com.google.*`)
4. AndroidX imports (`androidx.*`)
5. Project imports (`com.simplevlc.*`)

Use explicit imports rather than wildcard (`.*`) except for standard library.

### Formatting

- **Indentation**: 4 spaces (Kotlin default)
- **Line length**: 120 characters max
- **Braces**: Same-line braces for functions/classes (Kotlin style)
- **Blank lines**: Single blank line between logical sections
- **Documentation**: Use KDoc (`/** */`) for public APIs and complex functions

### Types

- Use **data classes** for simple data containers:
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

- Use **sealed classes** for restricted hierarchies when applicable
- Use **interfaces** for callbacks and contracts (e.g., `PlayerController.Callback`)
- Use **nullable types** (`?`) only when a value can legitimately be null
- Prefer **`lateinit`** for properties initialized in `onCreate()` or similar initialization methods

### Visibility Modifiers

- Default to **package-private** (no modifier) for internal classes and functions
- Use **`private`** for implementation details
- Use **`public`** (default) for APIs intended for external use
- Use **`protected`** only when inheritance is needed

### Error Handling

- Use **try-catch** for operations that may fail (file I/O, content resolver):
  ```kotlin
  try {
      videoRepository.getVideos(sortOrder)
  } catch (e: Exception) {
      Toast.makeText(this, "Load error: ${e.message}", Toast.LENGTH_LONG).show()
      e.printStackTrace()
  }
  ```

- Use **nullable results** (`null`) for recoverable failures
- Log errors with `Log.e(TAG, "message", e)` for debugging
- Show user-friendly error messages via Toast or AlertDialog
- Close resources in **`finally`** blocks or use Kotlin's `use` extension

### Coroutines

- Use **Dispatchers.Main** for UI updates
- Use **Dispatchers.IO** for file/content operations
- Use **`SupervisorJob()`** for independent coroutines that shouldn't fail together
- Cancel coroutines when views are recycled (see `VideoAdapter.onViewRecycled`)
- Use **`lifecycleScope`** or scoped coroutines for Activity/Fragment lifecycles

### Android-Specific Patterns

**ViewBinding:**
```kotlin
private lateinit var binding: ActivityMainBinding
// In onCreate:
binding = ActivityMainBinding.inflate(layoutInflater)
setContentView(binding.root)
```

**RecyclerView Adapters:**
- Use `ListAdapter` with `DiffUtil` for efficient list updates
- Implement `ViewHolder` pattern with binding
- Cancel async work (thumbnails) in `onViewRecycled()`

**Lifecycle:**
- Use `lifecycle-runtime-ktx` for lifecycle-aware components
- Release resources in `onDestroy()` / `onPause()`
- Save state in `onPause()`, restore in `onResume()`

### LibVLC Integration

- Initialize LibVLC once in `SimpleVLCApp` (singleton pattern via `instance`)
- Use `MediaPlayer` from `org.videolan.libvlc`
- Handle surface attachment via `SurfaceHolder.Callback`
- Use `fd://` URIs for content resolver playback:
  ```kotlin
  val fdUri = AndroidUtil.LocationToUri("fd://${fileDescriptor.fd}")
  ```

### Lint Baseline

The project uses a lint baseline (`lint-baseline.xml`) to suppress known issues.
When adding new lint warnings, either:
1. Fix the issue if it's a real bug
2. Add to baseline if it's an acceptable pattern

### Common Issues to Avoid

- **Hardcoded strings**: Use `@string` resources for user-facing text
- **Missing contentDescription**: Add for accessibility on image views
- **Memory leaks**: Cancel coroutines and remove callbacks in `onDestroy()`
- **Null checks**: Use `?.` and `?: ` operators, check `isInitialized` for `lateinit`
- **Main thread blocking**: Move I/O to `Dispatchers.IO`

## Documentation

- Document public APIs with KDoc comments
- Comment complex business logic
- Keep comments up-to-date when code changes
- Use meaningful variable/function names to reduce need for comments

## Version Information

- **Gradle**: Configured in `gradle.properties`
- **Kotlin**: Official code style (`kotlin.code.style=official`)
- **AndroidX**: Enabled (`android.useAndroidX=true`)
- **R Class**: Non-transitive (`android.nonTransitiveRClass=true`)
