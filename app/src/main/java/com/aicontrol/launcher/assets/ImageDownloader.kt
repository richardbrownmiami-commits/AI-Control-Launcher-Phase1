package com.aicontrol.launcher.assets

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

class ImageDownloader {
    data class DownloadResult(val file: File, val mimeType: String, val bytes: Long)

    fun download(
        urlString: String,
        destination: File,
        maxBytes: Long = 10L * 1024L * 1024L,
        allowedHosts: Set<String>? = null,
        shouldContinue: () -> Boolean = { true },
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<DownloadResult> = runCatching {
        val source = URL(urlString)
        require(source.protocol.equals("https", ignoreCase = true)) { "Only HTTPS image downloads are allowed." }
        val connection = (source.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AI-Control-Launcher/1.0")
            setRequestProperty("Accept", "image/jpeg,image/png,image/webp")
        }
        val temporary = File(destination.parentFile ?: destination.absoluteFile.parentFile, destination.name + ".partial")
        try {
            require(connection.responseCode in 200..299) { "Image server returned HTTP ${connection.responseCode}." }
            require(connection.url.protocol.equals("https", ignoreCase = true)) { "Image download redirected to an insecure URL." }
            if (allowedHosts != null) require(connection.url.host.lowercase() in allowedHosts) {
                "Image download redirected outside the approved source host."
            }
            val mime = connection.contentType?.substringBefore(";")?.trim()?.lowercase()
                ?: "application/octet-stream"
            require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
                "The source did not return a supported JPEG, PNG, or WebP image (received $mime)."
            }
            val total = connection.contentLengthLong
            require(total <= 0L || total <= maxBytes) { "Image exceeds the ${maxBytes / (1024 * 1024)} MB download limit." }
            temporary.parentFile?.mkdirs()
            temporary.delete()
            var downloaded = 0L
            onProgress(0L, total)
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (!shouldContinue()) throw CancellationException("Wallpaper download canceled.")
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        require(downloaded <= maxBytes) { "Image exceeds the ${maxBytes / (1024 * 1024)} MB download limit." }
                        output.write(buffer, 0, count)
                        onProgress(downloaded, total)
                    }
                }
            }
            if (!shouldContinue()) throw CancellationException("Wallpaper download canceled.")
            require(downloaded > 0) { "The image download was empty." }
            ImageAssetValidation.validate(temporary)
            require(temporary.renameTo(destination) || run {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
                true
            }) { "Could not save the validated image to the asset library." }
            DownloadResult(destination, mime, downloaded)
        } catch (error: Exception) {
            temporary.delete()
            destination.takeIf { it.name.endsWith(".partial") }?.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}
