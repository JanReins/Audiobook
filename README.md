# My Private Audiobook Player

A simple, reliable, 100% offline and privacy-first audiobook player for Android.

## Features

1. **Folder Selection & Recursive Sub-Folder Scanning**
   - Tap "Choose Audiobook Folder" on first launch to pick any folder containing your audiobooks.
   - Automatically scans sub-folders (up to 3 levels deep) and aggregates all audio tracks into a clean, unified library.
   - You can easily switch to a different folder at any time using the folder icon at the top of the Library.
   - Supports popular audiobook formats: MP3, M4A, M4B, AAC, FLAC, OGG, WAV, WMA, and OPUS.

2. **Clean Book Library**
   - Automatically formats file names into clean, readable book titles.
   - Displays duration and listening progress percentage on every track.
   - Instant search and filtering bar to quickly locate books across directories.

3. **Dedicated Player Screen**
   - **Prominent Center Play / Pause** button.
   - **Interactive Seek Bar** with elapsed time and total duration.
   - **4 Skip Controls**:
     - Rewind 1 minute (`-1m`)
     - Rewind 15 seconds (`-15s`)
     - Fast forward 15 seconds (`+15s`)
     - Fast forward 1 minute (`+1m`)
   - **Playback Speed Selector**: 0.75x, 1.0x, 1.25x, and 1.5x speeds.
   - **Sleep Timer**: Choose 15, 30, 45, or 60 minutes. Shows a live countdown badge and automatically pauses audio when time is up.

4. **Bookmarks**
   - Save your exact position at any moment with a custom or auto-generated note.
   - Open the bookmarks list to see all saved timestamps for the current book.
   - Tap any bookmark to jump straight to that moment, or delete bookmarks you no longer need.

5. **Progress Memory**
   - Automatically remembers your listening position for every book.
   - Seamlessly resumes right where you left off when you open a book again.
   - Stored 100% privately on your device using Android SharedPreferences.

6. **Background Playback & 5-Button Notification Controls**
   - Keeps playing seamlessly in the background when your screen is locked or when using other apps.
   - Media style notification with **5 quick playback actions**:
     - Rewind 1 minute (`-1m`)
     - Rewind 15 seconds (`-15s`)
     - Play / Pause toggle
     - Fast forward 15 seconds (`+15s`)
     - Fast forward 1 minute (`+1m`)

7. **100% Private & Pure Offline**
   - Zero internet permissions in the Android Manifest.
   - No tracking, no analytics, no accounts, and no cloud dependencies.
   - Light, dark, and system-adaptive Material Design 3 themes.

---

## Codebase Cleanup Note

In this release, all unused template dependencies (including Firebase, Google Services plugin, OkHttp, Retrofit, Room, and Moshi) were removed from `app/build.gradle.kts`. The app relies exclusively on standard AndroidX libraries (`androidx.media`, `androidx.documentfile`, Jetpack Compose, Coroutines, and ViewModel) to ensure minimal APK size, fast builds, and guaranteed offline privacy.

---

## How to Open and Run (for Beginners)

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
