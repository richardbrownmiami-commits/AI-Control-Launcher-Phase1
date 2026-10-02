package com.aicontrol.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local, launcher-private placements for shortcuts and Android AppWidgetHost IDs. */
class WorkspaceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("launcher_state", Context.MODE_PRIVATE)

    data class Shortcut(
        val label: String,
        val packageName: String,
        val activityName: String,
        val page: Int,
        val order: Int
    )

    fun shortcuts(page: Int? = null): List<Shortcut> = runCatching {
        val values = JSONArray(prefs.getString("home_shortcuts", "[]") ?: "[]")
        (0 until values.length()).mapNotNull { index ->
            val item = values.optJSONObject(index) ?: return@mapNotNull null
            val pkg = item.optString("package")
            val activity = item.optString("activity")
            if (pkg.isBlank() || activity.isBlank()) return@mapNotNull null
            Shortcut(item.optString("label", pkg), pkg, activity, item.optInt("page", 1).coerceIn(0, 2), item.optInt("order", index))
        }.filter { page == null || it.page == page }.sortedWith(compareBy<Shortcut> { it.page }.thenBy { it.order }.thenBy { it.label.lowercase() })
    }.getOrDefault(emptyList())

    fun addOrMove(label: String, packageName: String, activityName: String, page: Int) {
        require(label.isNotBlank() && packageName.isNotBlank() && activityName.isNotBlank()) { "A shortcut needs an app label and launch activity." }
        val destination = page.coerceIn(0, 2)
        val items = shortcuts().toMutableList()
        val previous = items.firstOrNull { it.packageName == packageName && it.activityName == activityName }
        items.removeAll { it.packageName == packageName && it.activityName == activityName }
        val end = (items.filter { it.page == destination }.maxOfOrNull { it.order } ?: -1) + 1
        items += Shortcut(label, packageName, activityName, destination, previous?.order?.takeIf { previous.page == destination } ?: end)
        save(items)
    }

    fun remove(packageName: String, activityName: String) = save(
        shortcuts().filterNot { it.packageName == packageName && it.activityName == activityName }
    )

    fun move(packageName: String, activityName: String, page: Int) {
        val item = shortcuts().firstOrNull { it.packageName == packageName && it.activityName == activityName } ?: return
        addOrMove(item.label, item.packageName, item.activityName, page)
    }

    /** Moves a shortcut one slot within its current page; bounds are intentionally no-ops. */
    fun shift(packageName: String, activityName: String, delta: Int) {
        val all = shortcuts().toMutableList()
        val itemIndex = all.indexOfFirst { it.packageName == packageName && it.activityName == activityName }
        if (itemIndex < 0) return
        val item = all[itemIndex]
        val siblings = all.filter { it.page == item.page }.sortedBy { it.order }.toMutableList()
        val from = siblings.indexOfFirst { it.packageName == packageName && it.activityName == activityName }
        val to = (from + delta.sign()).coerceIn(0, siblings.lastIndex)
        if (from == to) return
        val moved = siblings.removeAt(from)
        siblings.add(to, moved)
        val reordered = all.filterNot { it.page == item.page } + siblings.mapIndexed { index, shortcut -> shortcut.copy(order = index) }
        save(reordered)
    }

    fun widgetIds(): Set<Int> = prefs.getStringSet("app_widget_ids", emptySet())
        ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    fun widgetPage(id: Int): Int = runCatching {
        JSONObject(prefs.getString("widget_pages", "{}") ?: "{}").optInt(id.toString(), 1).coerceIn(0, 2)
    }.getOrDefault(1)

    fun addWidget(id: Int, page: Int) {
        val ids = widgetIds().toMutableSet().apply { add(id) }
        val pages = runCatching { JSONObject(prefs.getString("widget_pages", "{}") ?: "{}") }.getOrDefault(JSONObject())
        pages.put(id.toString(), page.coerceIn(0, 2))
        prefs.edit().putStringSet("app_widget_ids", ids.map(Int::toString).toSet()).putString("widget_pages", pages.toString()).apply()
    }

    fun removeWidget(id: Int) {
        val ids = widgetIds().toMutableSet().apply { remove(id) }
        val pages = runCatching { JSONObject(prefs.getString("widget_pages", "{}") ?: "{}") }.getOrDefault(JSONObject())
        pages.remove(id.toString())
        prefs.edit().putStringSet("app_widget_ids", ids.map(Int::toString).toSet()).putString("widget_pages", pages.toString()).apply()
    }

    private fun save(items: List<Shortcut>) {
        val normalized = items.groupBy { it.page }.flatMap { (_, pageItems) ->
            pageItems.sortedBy { it.order }.mapIndexed { index, item -> item.copy(order = index) }
        }
        val json = JSONArray()
        normalized.forEach { item ->
            json.put(JSONObject().put("label", item.label).put("package", item.packageName)
                .put("activity", item.activityName).put("page", item.page).put("order", item.order))
        }
        prefs.edit().putString("home_shortcuts", json.toString()).apply()
    }

    private fun Int.sign() = when { this < 0 -> -1; this > 0 -> 1; else -> 0 }
}
