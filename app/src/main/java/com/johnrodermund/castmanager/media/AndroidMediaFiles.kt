package com.johnrodermund.castmanager.media

import android.content.ContentResolver
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
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
