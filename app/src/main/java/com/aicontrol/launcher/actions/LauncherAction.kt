package com.aicontrol.launcher.actions

sealed class LauncherAction {
    data class HideApps(val packages: List<String>): LauncherAction()
    data class ShowApps(val packages: List<String>): LauncherAction()
    data class SetTheme(val name: String): LauncherAction()
    data class SetLayout(val name: String): LauncherAction()
    data class CreateWorkspace(val name: String, val packages: List<String> = emptyList()): LauncherAction()
    data class DownloadWallpaper(val url: String, val apply: Boolean = true): LauncherAction()
    data class DownloadImage(val url: String, val name: String): LauncherAction()
    data class DownloadIcon(val url: String, val packageName: String): LauncherAction()
    data class ClearIconOverride(val packageName: String): LauncherAction()
}
