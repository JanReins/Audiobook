package com.janreins.audiobook

import android.net.Uri
import com.janreins.audiobook.data.FolderAccess
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FolderAccessTest {
    private val folder = "content://books/tree/library"

    @Test fun matchingReadPermissionGrantsAccess() {
        assertTrue(FolderAccess.hasReadAccess(folder, listOf(folder to true)))
    }

    @Test fun missingPermissionDeniesAccess() {
        assertFalse(FolderAccess.hasReadAccess(folder, emptyList()))
    }

    @Test fun writeOnlyPermissionDeniesAccess() {
        assertFalse(FolderAccess.hasReadAccess(folder, listOf(folder to false)))
    }

    @Test fun differentUriDeniesAccess() {
        assertFalse(FolderAccess.hasReadAccess(folder, listOf("content://books/tree/other" to true)))
    }

    @Test fun resolverWithoutPersistedPermissionDeniesAccess() {
        assertFalse(FolderAccess.hasPersistedReadAccess(
            RuntimeEnvironment.getApplication().contentResolver, Uri.parse(folder)))
    }
}
