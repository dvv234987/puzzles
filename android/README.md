# Puzzles for Android

Android port of this repository's iOS app. The C puzzle code in the repository
root is used unchanged; the Android project only adds a frontend, mirroring the
iOS user interface (`GameView.m`, `GameListViewController.m`, ...).

## Build

1. Open the `android/` directory in Android Studio (Ladybug or newer). It
   installs the NDK and CMake 3.22.1 on request and creates the Gradle wrapper.
   Command line: `cd android && gradle wrapper --gradle-version 8.9 && ./gradlew assembleDebug`
2. Icons, help pages and game descriptions are copied from the repository root
   at build time (task `preparePuzzleResources`), so nothing is duplicated.

minSdk 21, targetSdk 35, Kotlin; dependencies: AppCompat, RecyclerView, Core.

## How it maps to the iOS project

| iOS file | Android |
|---|---|
| `GameView.m` (C drawing callbacks `ios_drawing`) | `app/src/main/cpp/android_frontend.c` (JNI) + `GameCanvasView.kt` (`n*` methods) |
| `GameView.m` (toolbars, menu, touch, timer, generation overlay) | `GameFragment.kt`, `GameCanvasView.kt` |
| `GameViewController.m`, `AppDelegate.m` | `GameFragment.kt`, `MainActivity.kt` (reopens the last game) |
| `GameListViewController.m` | `GameListFragment.kt` |
| `GameTypeController.m` | `GameTypeFragment.kt` |
| `GameSettingsController.m`, `GameSettingsChoiceController.m` | `GameSettingsFragment.kt`, `GameSettingsChoiceFragment.kt` |
| `GameHelpController.m` | `HelpFragment.kt` (WebView over the bundled HTML help) |
| source list of `Puzzles.xcodeproj` (`-DCOMBINED`) | `app/src/main/cpp/CMakeLists.txt` |

## Status

The C core was checked on a desktop host (all games generate, draw, save and
reload through the same platform hooks) and the Kotlin sources were
type-checked against the Android SDK. The Gradle/NDK build and running on a
device have not been tried yet.

## Known differences from the iOS app

- Long press gives haptic feedback instead of the iOS key-click sound.
- Lines are drawn half a pixel offset so they stay crisp on the Android canvas.
- `get_random_seed` includes nanoseconds (iOS uses `time()` only).
- Light theme only.
