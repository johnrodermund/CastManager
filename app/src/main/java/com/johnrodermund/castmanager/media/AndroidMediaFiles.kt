package com.johnrodermund.castmanager.media

import android.content.ContentResolver
import android.content.Context
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
        MediaFile(cursor.getString(0), contentType, cursor.getLong(1)) {
            openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        }
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
