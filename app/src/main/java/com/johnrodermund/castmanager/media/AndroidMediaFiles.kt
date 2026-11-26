package com.johnrodermund.castmanager.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException
import java.net.Inet4Address

/** Reads the details of a file from the Android file picker. Returns null if they can't be read. */
fun ContentResolver.mediaFile(uri: Uri): MediaFile? {
    val contentType = getType(uri) ?: return null
    val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    return query(uri, columns, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst() || cursor.isNull(1)) return null
        holdAccess(uri)
        MediaFile(cursor.getString(0), contentType, cursor.getLong(1), release = { releaseAccess(uri) }) {
            openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        }
    }
}

/** Releases access that an earlier Playback kept, so the app holds no record of past files. */
fun ContentResolver.releaseAllMediaFiles() {
    persistedUriPermissions.forEach { releaseAccess(it.uri) }
}

// The picker's access ends with the Activity that asked, but Playback outlives the Activity.
private fun ContentResolver.holdAccess(uri: Uri) {
    try {
        takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // Not every provider offers this. Playback still works until the app is swiped away.
    }
}

private fun ContentResolver.releaseAccess(uri: Uri) {
    try {
        releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // There was no access to release.
    }
}

/** Reads the format of a file from the Android file picker. Returns null if it can't be inspected. */
fun Context.mediaFormat(uri: Uri, contentType: String): MediaFormat? {
    val extractor = MediaExtractor()
    val retriever = MediaMetadataRetriever()
    return try {
        extractor.setDataSource(this, uri, null)
        retriever.setDataSource(this, uri)
        val tracks = (0 until extractor.trackCount).mapNotNull {
            extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)
        }
        MediaFormat(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: contentType, tracks)
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null // MediaMetadataRetriever throws these for files it can't parse.
    } finally {
        extractor.release()
        retriever.release()
    }
}

/** The phone's current address on Wi-Fi, or null if it isn't on Wi-Fi. */
fun Context.wifiAddress(): String? {
    val connectivity = getSystemService(ConnectivityManager::class.java)
    val network = connectivity.activeNetwork ?: return null
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
    if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
    return connectivity.getLinkProperties(network)?.linkAddresses
        ?.map { it.address }
        ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        ?.hostAddress
}
