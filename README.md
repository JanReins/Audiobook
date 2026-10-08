# My Private Audiobook Player

A simple, reliable, 100% offline and privacy-first audiobook player for Android.

## Download the APK

Every push to `main` and every pull request builds a debug APK in GitHub Actions. While signed in to GitHub, open the **Actions** tab → an **Android CI** run → **Artifacts** → **app-debug-apk** to download a zip containing `app-debug.apk`.

Pushing a tag like `v1.0.0` creates a GitHub Release with the APK attached under **Releases**. This is a debug-signed build; enable **Install unknown apps** on your Android device to sideload it.

A signed release build needs signing secrets (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, and optional `KEY_ALIAS`, which defaults to `upload`) and is not built by CI yet.

## Features

1. **Folder Selection & Recursive Sub-Folder Scanning**
   - Tap "Choose Audiobook Folder" on first launch to pick any folder containing your audiobooks.
   - Automatically scans sub-folders (up to 3 levels deep) and treats each sub-folder as one audiobook, with tracks played in natural filename order and automatic advance. Files directly in the chosen folder are single books.
   - You can easily switch to a different folder at any time using the folder icon at the top of the Library.
   - Supports popular audiobook formats: MP3, M4A, M4B, AAC, FLAC, OGG, WAV, WMA, and OPUS.

2. **Clean Book Library**
   - Automatically formats file names into clean, readable book titles.
   - Displays duration and listening progress percentage on every book. Durations are cached for faster library loading.
   - Instant search and filtering bar to quickly locate books across directories.

3. **Dedicated Player Screen**
   - **Prominent Center Play / Pause** button.
   - **Interactive Seek Bar** with elapsed time and total duration.
   - **4 Skip Controls**:
     - Rewind 1 minute (`-1m`)
     - Rewind 15 seconds (`-15s`)
     - Fast forward 30 seconds (`+30s`)
     - Fast forward 1 minute (`+1m`)
   - Configurable inner skip intervals: 5, 10, 15, 30, 45, or 60 seconds (defaults: back 15, forward 30).
   - Previous/next track buttons, a track counter, and a track list with titles and durations for multi-file books. Tap a track to play from its start. The seek bar and skip controls operate within the current track.
   - **Playback Speed Selector**: 0.5x–3x in 0.05x steps, with a slider, presets, and reset.
   - **Sleep Timer**: Choose 5, 10, 15, 30, 45, 60, or 90 minutes, or end of track. Live countdown, +5 minute extension, and optional fade-out over the last 30 seconds (enabled by default).
   - **Playback settings**: Adjust skip intervals, smart rewind, and sleep fade-out from the player gear button. Smart rewind is enabled by default: resumes within the current track by 0/2/5/10/20 seconds depending on pause length.

4. **Bookmarks**
   - Save your exact position at any moment with a custom or auto-generated note.
   - Open the bookmarks list to see all saved timestamps for the current book.
   - Tap any bookmark to jump straight to that moment, or delete bookmarks you no longer need.

5. **Progress Memory**
   - Automatically remembers the track and position for every book, even when choosing a different parent folder containing the same book sub-folders.
   - Seamlessly resumes right where you left off when you open a book again.
   - Stored 100% privately on your device using Android SharedPreferences.

6. **Background Playback & Media3 Media Controls**
   - Background playback with notification and lock-screen controls for play/pause, configured skip intervals and -1m/+1m (button visibility depends on Android/device).
   - Supports headset/Bluetooth media buttons and auto-pauses on headphone disconnect and calls.

7. **100% Private & Pure Offline**
   - Zero internet permissions in the Android Manifest.
   - App data is not included in Android cloud backup; folder access grants are device-specific.
   - No tracking, no analytics, no accounts, and no cloud dependencies.
   - Light, dark, and system-adaptive Material Design 3 themes.

---

## How to Open and Run (for Beginners)

To build from the command line with JDK 17+, run `./gradlew assembleDebug` from the project's root folder.

1. **Open in Android Studio**:
   - Launch **Android Studio** (Giraffe, Hedgehog, Iguana, Jellyfish, Ladybug or newer).
   - Click **Open** and select this project's root folder.
   - Allow Gradle to finish syncing (this usually takes 1–2 minutes on first load).

2. **Run on a Phone or Emulator**:
   - Connect your Android phone with USB debugging enabled, or start an Android Emulator from the Device Manager.
   - Click the green **Run (▶)** button in the top toolbar of Android Studio.

3. **Using the App**:
   - Copy your audiobook files (`.mp3`, `.m4a`, `.aac`, etc.) into a folder on your phone (e.g., `Download` or `Audiobooks`), organized directly or inside sub-folders.
   - Open the app and tap **Choose Audiobook Folder**.
   - Select your folder and tap **"Use this folder"** (and grant permission).
   - Tap any book in your library to start listening!
