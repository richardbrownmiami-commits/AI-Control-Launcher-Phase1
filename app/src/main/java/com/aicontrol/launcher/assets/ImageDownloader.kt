package com.aicontrol.launcher.assets

import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ImageDownloader {
    data class DownloadResult(val file: File, val mimeType: String, val bytes: Long)

    fun download(urlString: String, destination: File, maxBytes: Long = 10L * 1024L * 1024L): Result<DownloadResult> = runCatching {
        require(urlString.startsWith("https://")) { "Only HTTPS URLs are allowed" }

        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AI-Control-Launcher/1.0")
        }

        try {
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val mime = connection.contentType?.substringBefore(";")?.lowercase()
                ?: "application/octet-stream"
            require(mime == "image/jpeg" || mime == "image/png" || mime == "image/webp")
            require(connection.contentLengthLong <= 0L || connection.contentLengthLong <= maxBytes)

            destination.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= maxBytes) { "Image exceeds size limit" }
                        output.write(buffer, 0, read)
                    }
                }
            }

            require(BitmapFactory.decodeFile(destination.absolutePath) != null) {
                "Downloaded file is not a valid image"
            }

            DownloadResult(destination, mime, destination.length())
        } finally {
            connection.disconnect()
        }
    }
}
