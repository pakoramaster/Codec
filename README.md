**Description**
- A compact Android application written in Kotlin using Jetpack Compose. It focuses on media handling (images, GIFs, audio/video) and modern Android app patterns: Compose UI, Media3 for playback, Coil for image loading (including GIFs), WorkManager for background jobs, and networking with OkHttp + Gson. See the app module at [app/build.gradle.kts](app/build.gradle.kts) and the dependency catalog at [gradle/libs.versions.toml](gradle/libs.versions.toml).

**Interesting techniques**
- **Jetpack Compose UI**: declarative, state-driven UI with composable functions — see the official docs: https://developer.android.com/jetpack/compose
- **Compose BOM**: aligns Compose artifact versions via a Bill of Materials — https://developer.android.com/jetpack/compose#bom and see [gradle/libs.versions.toml](gradle/libs.versions.toml)
- **Efficient image loading & GIFs with Coil**: modern, coroutine-friendly image loader with GIF support via `coil-gif` — https://coil-kt.github.io/coil/
- **Media playback with Media3**: ExoPlayer successor with media UI components and integration points — https://developer.android.com/guide/topics/media/media3
- **Background work with WorkManager**: reliable, battery-friendly background jobs and constraints-aware scheduling — https://developer.android.com/topic/libraries/architecture/workmanager
- **Low-level HTTP control with OkHttp + Gson**: fast HTTP client and compact JSON (de)serialization — OkHttp: https://square.github.io/okhttp/ — Gson: https://github.com/google/gson
- **Gradle Version Catalog**: centralized dependency coordinates in `gradle/libs.versions.toml` for safer upgrades — https://docs.gradle.org/current/userguide/platforms.html#version-catalog

**Non-obvious / noteworthy technologies**
- Gradle Version Catalog (`gradle/libs.versions.toml`) for centralized dependency management.
- Compose BOM usage to avoid mismatched Compose artifact versions.
- `coil-gif` for explicit GIF decoding and rendering in Compose.
- `androidx.media3:media3-ui` for prebuilt playback UI components.
- `androidx.core:core-splashscreen` for modern splash screen behavior across OS versions.
- Kotlin 2.x with the Compose Kotlin plugin — up-to-date Kotlin toolchain and compiler plugin.

**Project structure**
```
/ (repo root)
app/
gradle/
.github/
build/
local.properties
settings.gradle.kts
gradlew
gradlew.bat
```
- `app/`: Android application module. Sources live under `app/src/main/` and module config is at [app/build.gradle.kts](app/build.gradle.kts).
- `gradle/`: Gradle metadata and the version catalog at [gradle/libs.versions.toml](gradle/libs.versions.toml).
- `.github/`: CI workflows; see [.github/workflows/build_apk.yml](.github/workflows/build_apk.yml).
- `build/`: build outputs (generated, ignored in VCS).
- `local.properties`, `gradlew`, and `settings.gradle.kts`: standard Android/Gradle tooling and workspace config.

<img src="assets/code-ui.jpeg" alt="ui" style="width:30%; height:auto;" />