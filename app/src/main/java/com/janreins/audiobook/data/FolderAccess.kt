package com.janreins.audiobook.data

import android.content.ContentResolver
import android.net.Uri

object FolderAccess {
    fun hasReadAccess(folderUri: String, persisted: List<Pair<String, Boolean>>): Boolean =
        persisted.any { (uri, read) -> uri == folderUri && read }

    fun hasPersistedReadAccess(contentResolver: ContentResolver, uri: Uri): Boolean =
        hasReadAccess(uri.toString(), contentResolver.persistedUriPermissions.map {
            it.uri.toString() to it.isReadPermission
        })
}
