package com.johnrodermund.castmanager.media

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.FilterInputStream
import java.io.InputStream
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Serves one [MediaFile] over HTTP at a random URL, until [stop].
 *
 * [hostAddress] is asked on each [start], because the phone's address can change between Playbacks.
 */
class MediaServer(private val hostAddress: () -> String?) {
    private var server: EmbeddedServer<*, *>? = null

    /** Replaces anything being served, and returns the new URL. Returns null if there is no address to serve from. */
    suspend fun start(file: MediaFile): String? = withContext(Dispatchers.IO) {
        stop()
        val host = hostAddress() ?: return@withContext null
        val path = randomPath()
        val started = createServer(file, path).start(wait = false)
        server = started
        "http://$host:${started.engine.resolvedConnectors().first().port}/$path"
    }

    // Outside any CoroutineScope: inside one, the server would become its child and it could never finish.
    private fun createServer(file: MediaFile, path: String) =
        embeddedServer(CIO, port = 0, host = "0.0.0.0") {
            install(AutoHeadResponse)
            install(PartialContent)
            routing {
                get("/$path") { call.respond(MediaFileContent(file)) }
            }
        }

    suspend fun stop() = withContext(Dispatchers.IO) {
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 500)
        server = null
    }

    private fun randomPath(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

private class MediaFileContent(private val file: MediaFile) : OutgoingContent.ReadChannelContent() {
    override val contentLength = file.length
    override val contentType = runCatching { ContentType.parse(file.contentType) }
        .getOrDefault(ContentType.Application.OctetStream)

    override fun readFrom(): ByteReadChannel = file.open().toByteReadChannel()

    override fun readFrom(range: LongRange): ByteReadChannel =
        LimitedInputStream(file.open().skipFully(range.first), range.last - range.first + 1).toByteReadChannel()

    private fun InputStream.skipFully(count: Long): InputStream = apply {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (read() == -1) {
                return@apply
            } else {
                remaining--
            }
        }
    }
}

private class LimitedInputStream(source: InputStream, private var remaining: Long) : FilterInputStream(source) {
    override fun read(): Int {
        if (remaining <= 0) return -1
        return super.read().also { if (it != -1) remaining-- }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0) return -1
        val read = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (read > 0) remaining -= read
        return read
    }
}
