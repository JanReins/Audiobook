package com.janreins.audiobook

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.janreins.audiobook.ui.screens.FolderSelectionScreen
import com.janreins.audiobook.ui.screens.LibraryScreen
import com.janreins.audiobook.ui.screens.PlayerScreen
import com.janreins.audiobook.ui.theme.AudiobookTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()

            AudiobookTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AudiobookApp(
                        viewModel = viewModel,
                        currentThemeMode = themeMode
                    )
                }
            }
        }
    }
}

@Composable
fun AudiobookApp(
    viewModel: MainViewModel,
    currentThemeMode: com.janreins.audiobook.ui.theme.AppThemeMode
) {
    val context = LocalContext.current

    val currentScreen by viewModel.currentScreen.collectAsState()
    val audiobooks by viewModel.audiobooks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentPlayingBook by viewModel.currentPlayingBook.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val activeSleepOption by viewModel.activeSleepOption.collectAsState()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsState()
    val currentBookmarks by viewModel.currentBookmarks.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Storage Access Framework Folder Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onFolderSelected(context, uri)
        }
    }

    // Android 13+ Notification Permission Launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied handled gracefully */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Handle system back button when in Player screen
    BackHandler(enabled = currentScreen == AppScreen.PLAYER) {
        viewModel.navigateBackToLibrary()
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            AppScreen.FOLDER_SELECTION -> {
                FolderSelectionScreen(
                    currentThemeMode = currentThemeMode,
                    onSelectThemeMode = { viewModel.setThemeMode(it) },
                    onSelectFolderClick = { folderPickerLauncher.launch(null) }
                )
            }

            AppScreen.LIBRARY -> {
                LibraryScreen(
                    audiobooks = audiobooks,
                    isLoading = isLoading,
                    currentPlayingBook = currentPlayingBook,
                    isPlaying = isPlaying,
                    currentThemeMode = currentThemeMode,
                    onSelectThemeMode = { viewModel.setThemeMode(it) },
                    getSavedPosition = { id -> viewModel.getSavedPosition(id) },
                    onBookClick = { book -> viewModel.playBook(context, book) },
                    onMiniPlayerClick = { viewModel.openPlayer() },
                    onTogglePlayPause = { viewModel.togglePlayPause(context) },
                    onChangeFolderClick = { folderPickerLauncher.launch(null) },
                    onRefresh = { viewModel.loadAudiobooks() }
                )
            }

            AppScreen.PLAYER -> {
                val book = currentPlayingBook
                if (book != null) {
                    PlayerScreen(
                        audiobook = book,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        playbackSpeed = playbackSpeed,
                        activeSleepOption = activeSleepOption,
                        sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                        bookmarks = currentBookmarks,
                        errorMessage = errorMessage,
                        currentThemeMode = currentThemeMode,
                        onSelectThemeMode = { viewModel.setThemeMode(it) },
                        onBackClick = { viewModel.navigateBackToLibrary() },
                        onTogglePlayPause = { viewModel.togglePlayPause(context) },
                        onSeek = { pos -> viewModel.seekTo(pos) },
                        onSkipBackward15s = { viewModel.skipBackward15s() },
                        onSkipBackward1m = { viewModel.skipBackward1m() },
                        onSkipForward15s = { viewModel.skipForward15s() },
                        onSkipForward1m = { viewModel.skipForward1m() },
                        onSetPlaybackSpeed = { speed -> viewModel.setPlaybackSpeed(speed) },
                        onSetSleepTimer = { option -> viewModel.setSleepTimer(option, context) },
                        onAddBookmark = { title -> viewModel.addBookmark(title) },
                        onSelectBookmark = { bookmark -> viewModel.selectBookmark(context, bookmark) },
                        onDeleteBookmark = { bookmark -> viewModel.deleteBookmark(bookmark) },
                        onDismissError = { viewModel.dismissError() }
                    )
                } else {
                    // Fallback to library if no book is selected
                    viewModel.navigateBackToLibrary()
                }
            }
        }
    }
}
