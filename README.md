<img src="./metadata/en-US/images/featureGraphicDark.png" alt="Feature graphic" width="500">

# Open Video Editor

[![GitHub Release](https://img.shields.io/github/v/release/devhyper/open-video-editor?style=for-the-badge&logo=github&label=GitHub)](https://github.com/devhyper/open-video-editor/releases/latest)
[![IzzyOnDroid](https://img.shields.io/badge/IzzyOnDroid-Repo-blue?style=for-the-badge&logo=android)](https://apt.izzysoft.de/fdroid/index/apk/io.github.devhyper.openvideoeditor)
[![Google Play](https://img.shields.io/badge/Google_Play-Store-green?style=for-the-badge&logo=google-play)](https://play.google.com/store/apps/details?id=io.github.devhyper.openvideoeditor)
[![F-Droid](https://img.shields.io/f-droid/v/io.github.devhyper.openvideoeditor?style=for-the-badge&logo=f-droid&logoColor=white)](https://f-droid.org/en/packages/io.github.devhyper.openvideoeditor)

## About

Open Video Editor is an Android video editor focused on fast, offline edits with a simple workflow for trimming, filters, and exports.

## Developer install (from source)

```bash
git clone https://github.com/devhyper/open-video-editor.git
cd open-video-editor
./gradlew assembleDebug
```

## Codespaces

This repository includes a devcontainer configuration. Create a new Codespace and run:

```bash
./gradlew assembleDebug
```

## Features

- Trim
- Grayscale
- Resolution
- Scale
- Rotate
- Export with configurable quality options
- Automatic segmented export for long videos (resume-friendly)

## Build from source

```bash
./gradlew assembleDebug
```

## CLI Development Workflow

You can fully develop, build, and test this app using only the terminal.

### 1. Build APK

Compiles the application and generates the debug APK.

```bash
./gradlew assembleDebug
```

*Output location:* `app/build/outputs/apk/debug/app-debug.apk`

### 2. Install on Device

Connect your device via USB (ensure USB Debugging is enabled) and install the generated APK.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Debugging (Logcat)

View real-time logs from the running application to debug issues.

```bash
adb logcat -s "io.github.devhyper.openvideoeditor"
```

## Contributing

- Please open an issue for major changes before creating a pull request.
- Translations are managed via Weblate.

## Translations

[![Translations](https://img.shields.io/badge/Translations-Weblate-brightgreen?style=for-the-badge&logo=weblate)](https://hosted.weblate.org/engage/open-video-editor)

## Roadmap

[![Roadmap](https://img.shields.io/badge/Roadmap-Milestones-blue?style=for-the-badge&logo=github)](https://github.com/devhyper/open-video-editor/milestones)
