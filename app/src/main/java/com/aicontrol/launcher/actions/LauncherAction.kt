package com.aicontrol.launcher.actions

sealed class LauncherAction {
    data class HideApps(val packages: List<String>): LauncherAction()
    data class ShowApps(val packages: List<String>): LauncherAction()
    data class SetTheme(val name: String): LauncherAction()
    data class SetLayout(val name: String): LauncherAction()
    data class CreateWorkspace(val name: String, val packages: List<String> = emptyList()): LauncherAction()
}
