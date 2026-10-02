package com.aicontrol.launcher.assets

/** Conservative allowlist for freely reusable assets; excludes NC, ND, and unknown licenses. */
object AssetLicensePolicy {
    fun isReusable(license: String): Boolean {
        val normalized = license.trim().lowercase().replace('_', ' ').replace('-', ' ')
        if (normalized.isBlank()) return false
        if (Regex("\\bcc by nc\\b|\\bcc by nd\\b|non commercial|no derivatives").containsMatchIn(normalized)) return false
        return normalized == "cc0" || normalized.startsWith("cc0 ") ||
            normalized.startsWith("public domain") || normalized.startsWith("pd ") ||
            Regex("^cc by (?:sa )?[1-4](?:\\.0)?$").matches(normalized)
    }
}
