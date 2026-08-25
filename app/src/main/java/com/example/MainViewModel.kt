package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AudiobookRepository
import com.example.data.PreferencesManager
import com.example.data.model.Audiobook
import com.example.data.model.Bookmark
import com.example.data.model.SleepTimerOption
import com.example.player.AudiobookPlayerManager
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Screen destinations for the single-activity navigation flow.
 */
enum class AppScreen {
    FOLDER_SELECTION,
    LIBRARY,
    PLAYER
}

/**
 * Main ViewModel managing state, folder scanning, preferences, and player coordination.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AudiobookRepository(application)
    private val prefs = PreferencesManager(application)

    private val _themeMode = MutableStateFlow(prefs.getThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.FOLDER_SELECTION)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _folderUri = MutableStateFlow<Uri?>(null)
    val folderUri: StateFlow<Uri?> = _folderUri.asStateFlow()

    private val _audiobooks = MutableStateFlow<List<Audiobook>>(emptyList())
    val audiobooks: StateFlow<List<Audiobook>> = _audiobooks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentBookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val currentBookmarks: StateFlow<List<Bookmark>> = _currentBookmarks.asStateFlow()

    // Delegate player states from AudiobookPlayerManager
    val currentPlayingBook = AudiobookPlayerManager.currentBook
    val isPlaying = AudiobookPlayerManager.isPlaying
    val currentPositionMs = AudiobookPlayerManager.currentPositionMs
    val durationMs = AudiobookPlayerManager.durationMs
    val playbackSpeed = AudiobookPlayerManager.playbackSpeed
    val activeSleepOption = AudiobookPlayerManager.activeSleepOption
    val sleepTimerRemainingSeconds = AudiobookPlayerManager.sleepTimerRemainingSeconds
    val errorMessage = AudiobookPlayerManager.errorMessage

    init {
        AudiobookPlayerManager.initialize(application)
        checkSavedFolder()
    }

    /**
     * Checks if a folder was previously selected and persisted.
     */
    private fun checkSavedFolder() {
        val savedFolderString = prefs.getFolderUri()
        if (!savedFolderString.isNullOrBlank()) {
            val uri = Uri.parse(savedFolderString)
            _folderUri.value = uri
            _currentScreen.value = AppScreen.LIBRARY
            loadAudiobooks(uri)
        } else {
            _currentScreen.value = AppScreen.FOLDER_SELECTION
        }
    }

    /**
     * Handles user folder selection from the SAF picker.
     */
    fun onFolderSelected(context: Context, uri: Uri) {
        try {
            // Persist read permissions so folder remains accessible across app restarts
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.w("MainViewModel", "Could not take persistable URI permission: ${e.message}")
        }

        prefs.saveFolderUri(uri.toString())
        _folderUri.value = uri
        _currentScreen.value = AppScreen.LIBRARY
        loadAudiobooks(uri)
    }

    /**
     * Scans and loads audio files from the given folder URI.
     */
    fun loadAudiobooks(uri: Uri? = _folderUri.value) {
        if (uri == null) return
        viewModelScope.launch {
            _isLoading.value = true
            val books = repository.loadAudiobooksFromFolder(uri)
            _audiobooks.value = books
            _isLoading.value = false

            // If we have a last played book, update bookmarks for it
            val lastPlayedId = prefs.getLastPlayedBookId()
            if (lastPlayedId != null && AudiobookPlayerManager.currentBook.value == null) {
                val matchingBook = books.find { it.id == lastPlayedId }
                if (matchingBook != null) {
                    _currentBookmarks.value = prefs.getBookmarks(matchingBook.id)
                }
            }
        }
    }

    /**
     * Opens player for the selected book and begins playback (or resumes from saved point).
     */
    fun playBook(context: Context, book: Audiobook) {
        AudiobookPlayerManager.playBook(context, book)
        _currentBookmarks.value = prefs.getBookmarks(book.id)
        _currentScreen.value = AppScreen.PLAYER
    }

    /**
     * Navigates to the Player screen from the mini-player bar.
     */
    fun openPlayer() {
        val book = AudiobookPlayerManager.currentBook.value
        if (book != null) {
            _currentBookmarks.value = prefs.getBookmarks(book.id)
            _currentScreen.value = AppScreen.PLAYER
        }
    }

    fun navigateBackToLibrary() {
        _currentScreen.value = AppScreen.LIBRARY
    }

    fun navigateToFolderSelection() {
        _currentScreen.value = AppScreen.FOLDER_SELECTION
    }

    fun togglePlayPause(context: Context) {
        AudiobookPlayerManager.togglePlayPause(context)
    }

    fun seekTo(positionMs: Long) {
        AudiobookPlayerManager.seekTo(positionMs)
    }

    fun skipBackward15s() {
        AudiobookPlayerManager.skip(-15_000L)
    }

    fun skipBackward1m() {
        AudiobookPlayerManager.skip(-60_000L)
    }

    fun skipForward15s() {
        AudiobookPlayerManager.skip(15_000L)
    }

    fun skipForward1m() {
        AudiobookPlayerManager.skip(60_000L)
    }

    fun setPlaybackSpeed(speed: Float) {
        AudiobookPlayerManager.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(option: SleepTimerOption, context: Context) {
        AudiobookPlayerManager.setSleepTimer(option, context)
    }

    fun addBookmark(title: String) {
        val currentBook = AudiobookPlayerManager.currentBook.value ?: return
        val pos = AudiobookPlayerManager.currentPositionMs.value
        val bookmark = Bookmark(
            id = System.currentTimeMillis().toString(),
            audiobookId = currentBook.id,
            positionMs = pos,
            title = title
        )
        prefs.addBookmark(bookmark)
        _currentBookmarks.value = prefs.getBookmarks(currentBook.id)
    }

    fun selectBookmark(context: Context, bookmark: Bookmark) {
        val currentBook = AudiobookPlayerManager.currentBook.value
        if (currentBook != null && currentBook.id == bookmark.audiobookId) {
            AudiobookPlayerManager.seekTo(bookmark.positionMs)
            if (!AudiobookPlayerManager.isPlaying.value) {
                AudiobookPlayerManager.resume(context)
            }
        } else {
            val targetBook = _audiobooks.value.find { it.id == bookmark.audiobookId }
            if (targetBook != null) {
                AudiobookPlayerManager.playBook(context, targetBook, bookmark.positionMs)
                _currentBookmarks.value = prefs.getBookmarks(targetBook.id)
            }
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        prefs.deleteBookmark(bookmark.audiobookId, bookmark.id)
        _currentBookmarks.value = prefs.getBookmarks(bookmark.audiobookId)
    }

    fun getSavedPosition(bookId: String): Long {
        return prefs.getPlaybackPosition(bookId)
    }

    fun dismissError() {
        AudiobookPlayerManager.clearErrorMessage()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.saveThemeMode(mode)
    }
}
