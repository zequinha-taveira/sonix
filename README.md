<p align="center">
  <img src="https://img.shields.io/badge/Kotlin%2FJS-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin/JS" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/License-MIT-22c55e?style=for-the-badge" alt="MIT License" />
</p>

# 🎵 Sonix

**Open-source music player with offline capability — for the web and Android.**

Sonix lets you stream instrumental and lofi tracks, download them for offline playback, and enjoy a real-time audio visualizer — all ad-free and open source.

---

## ✨ Features

| Feature | Web | Android |
|---|:---:|:---:|
| Stream music | ✅ | ✅ |
| Offline playback (download & cache) | ✅ | ✅ |
| Shuffle & repeat modes | ✅ | ✅ |
| Search tracks by title or artist | ✅ | ✅ |
| Real-time circular audio visualizer | ✅ | — |
| Volume control & mute | ✅ | ✅ |
| Service Worker (PWA-ready) | ✅ | — |
| Material 3 UI | — | ✅ |

---

## 🏗️ Tech Stack

### Web (`src/jsMain`)

- **Language:** Kotlin/JS (IR compiler)
- **Build tool:** Gradle 8.5 with Kotlin Multiplatform plugin
- **Rendering:** DOM manipulation via `kotlinx.browser`
- **Styling:** Vanilla CSS with glassmorphism, gradients, and micro-animations
- **Fonts:** [Outfit](https://fonts.google.com/specimen/Outfit) via Google Fonts
- **Icons:** [Font Awesome 6](https://fontawesome.com/)
- **Audio:** HTML5 `<audio>` + Web Audio API (visualizer)
- **Offline:** Service Worker + Cache API

### Android (`android/`)

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Media:** AndroidX Media3 / ExoPlayer
- **Image loading:** Coil
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34

---

## 🚀 Getting Started

### Prerequisites

- **JDK 17+**
- **Node.js** (optional — only used for the `package.json` convenience scripts)
- **Android Studio** (for the Android app)

### Web — Development

```bash
# Clone the repository
git clone https://github.com/zequinha-taveira/sonix.git
cd sonix

# Start the dev server with hot-reload
npm run dev
# — or directly —
gradle-8.5/bin/gradle jsBrowserDevelopmentRun --continuous
```

The app will be served at `http://localhost:8080`.

### Web — Production Build

```bash
npm run build
# — or directly —
gradle-8.5/bin/gradle jsBrowserProductionWebpack
```

Output is written to `build/dist/js/productionExecutable/`.

### Android

```bash
cd android
./gradlew assembleDebug
```

The debug APK will be at `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## 📁 Project Structure

```
sonix/
├── src/jsMain/
│   ├── kotlin/
│   │   └── Main.kt              # Web app — full UI, player, visualizer, offline manager
│   └── resources/
│       ├── index.html            # HTML entry point
│       ├── styles.css            # All styling (dark theme, glassmorphism, animations)
│       └── sw.js                 # Service Worker for offline caching
├── android/
│   └── app/
│       └── src/                  # Android app (Jetpack Compose + Media3)
├── .github/workflows/
│   └── build-android.yml         # CI — builds Android debug APK on push/PR
├── build.gradle.kts              # Root Gradle config (Kotlin Multiplatform / JS IR)
├── settings.gradle.kts           # Root project settings
└── package.json                  # npm scripts (dev / build shortcuts)
```

---

## 🎨 Screenshots

> _Coming soon — contributions welcome!_

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

1. Fork the repo
2. Create your branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
