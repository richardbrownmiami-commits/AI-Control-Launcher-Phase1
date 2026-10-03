package com.aicontrol.launcher.assets

import java.net.URI

/** Only Wikimedia Commons' own image delivery hosts and namespace are accepted for Commons art. */
object CommonsAssetPolicy {
    val imageHosts: Set<String> = setOf("upload.wikimedia.org", "thumb.wikimedia.org")

    fun isImageUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase() in imageHosts &&
            (uri.port == -1 || uri.port == 443) &&
            uri.userInfo == null &&
            uri.path.startsWith("/wikipedia/commons/")
    }.getOrDefault(false)
}
