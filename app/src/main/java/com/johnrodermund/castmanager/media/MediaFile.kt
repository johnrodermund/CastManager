package com.johnrodermund.castmanager.media

import java.io.InputStream

class MediaFile(
    val name: String,
    val contentType: String,
    val length: Long,
    val open: () -> InputStream,
)
