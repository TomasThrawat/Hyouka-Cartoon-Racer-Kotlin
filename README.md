# Hyouka Cartoon Racer Kotlin

Native Android cartoon racing game built entirely with Kotlin and the Android Canvas API.

## Constraints
- Native Kotlin Android APK
- No HTML
- No WebView
- No paid SDKs or assets
- Procedural cartoon visuals drawn at runtime

## Gameplay
- 3-lap arcade race
- Player vs 3 cartoon AI racers
- Three-lane perspective road
- Touch steering and boost
- Collision handling
- Position, speed, lap and timer HUD
- Finish and restart screens

## Build
JDK 17 and Gradle 8.10.2 are used by GitHub Actions.

\`gradle :app:assembleDebug\`

The workflow builds the debug APK, runs unit tests, verifies the APK exists, and uploads it as an artifact.
