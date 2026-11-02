package com.johnrodermund.castmanager.media

import java.io.InputStream

class MediaFile(
    val name: String,
    val contentType: String,
    val length: Long,
    /** Called when the app no longer needs the file. */
    val release: () -> Unit = {},
    val open: () -> InputStream,
)
