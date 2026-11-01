package com.flownova.launcher.data

import android.content.Context
import com.flownova.launcher.model.*
import org.json.JSONArray
import org.json.JSONObject

class PreferenceStorage(context: Context) {
    private val prefs = context.getSharedPreferences("flow_nova_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): LauncherSettings {
        val themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString("themeMode", ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)

        val palettePreset = runCatching {
            PalettePreset.valueOf(prefs.getString("palettePreset", PalettePreset.DYNAMIC.name)!!)
        }.getOrDefault(PalettePreset.DYNAMIC)

        val indicatorStyle = runCatching {
            IndicatorStyle.valueOf(prefs.getString("indicatorStyle", IndicatorStyle.DOTS.name)!!)
        }.getOrDefault(IndicatorStyle.DOTS)

        val swipeUp = runCatching {
            GestureAction.valueOf(prefs.getString("swipeUpAction", GestureAction.APP_DRAWER.name)!!)
        }.getOrDefault(GestureAction.APP_DRAWER)

        val swipeDown = runCatching {
            GestureAction.valueOf(prefs.getString("swipeDownAction", GestureAction.NOTIFICATIONS.name)!!)
        }.getOrDefault(GestureAction.NOTIFICATIONS)

        val doubleTap = runCatching {
            GestureAction.valueOf(prefs.getString("doubleTapAction", GestureAction.SEARCH.name)!!)
        }.getOrDefault(GestureAction.SEARCH)

        return LauncherSettings(
            themeMode = themeMode,
            useDynamicColors = prefs.getBoolean("useDynamicColors", true),
            palettePreset = palettePreset,
            dynamicIconsGlobal = prefs.getBoolean("dynamicIconsGlobal", true),
            homeColumns = prefs.getInt("homeColumns", 4),
            homeRows = prefs.getInt("homeRows", 5),
            drawerColumns = prefs.getInt("drawerColumns", 5),
            drawerRows = prefs.getInt("drawerRows", 6),
            iconScalePercent = prefs.getInt("iconScalePercent", 100),
            indicatorStyle = indicatorStyle,
            swipeUpAction = swipeUp,
            swipeDownAction = swipeDown,
            doubleTapAction = doubleTap,
            respectReducedMotion = prefs.getBoolean("respectReducedMotion", true),
            locationLabel = prefs.getString("locationLabel", "Cachoeiro de Itapemirim") ?: "Cachoeiro de Itapemirim",
            keepOriginalIconPackages = prefs.getStringSet("keepOriginalIcons", emptySet()) ?: emptySet(),
            favoritePackages = parseStringList(prefs.getString("favorites", "[]") ?: "[]"),
            dockPackages = parseStringList(prefs.getString("dock", "[]") ?: "[]"),
            folders = parseFolders(prefs.getString("folders", "[]") ?: "[]"),
            appWidgetIds = parseIntList(prefs.getString("widgetIds", "[]") ?: "[]")
        )
    }

    fun saveSettings(s: LauncherSettings) {
        prefs.edit().apply {
            putString("themeMode", s.themeMode.name)
            putBoolean("useDynamicColors", s.useDynamicColors)
            putString("palettePreset", s.palettePreset.name)
            putBoolean("dynamicIconsGlobal", s.dynamicIconsGlobal)
            putInt("homeColumns", s.homeColumns)
            putInt("homeRows", s.homeRows)
            putInt("drawerColumns", s.drawerColumns)
            putInt("drawerRows", s.drawerRows)
            putInt("iconScalePercent", s.iconScalePercent)
            putString("indicatorStyle", s.indicatorStyle.name)
            putString("swipeUpAction", s.swipeUpAction.name)
            putString("swipeDownAction", s.swipeDownAction.name)
            putString("doubleTapAction", s.doubleTapAction.name)
            putBoolean("respectReducedMotion", s.respectReducedMotion)
            putString("locationLabel", s.locationLabel)
            putStringSet("keepOriginalIcons", s.keepOriginalIconPackages)
            putString("favorites", JSONArray(s.favoritePackages).toString())
            putString("dock", JSONArray(s.dockPackages).toString())
            putString("folders", serializeFolders(s.folders))
            putString("widgetIds", JSONArray(s.appWidgetIds).toString())
            apply()
        }
    }

    companion object {
        fun serializeFolders(folders: List<FolderItem>): String {
            val arr = JSONArray()
            folders.forEach { folder ->
                val obj = JSONObject()
                obj.put("id", folder.id)
                obj.put("name", folder.name)
                obj.put("packages", JSONArray(folder.packages))
                arr.put(obj)
            }
            return arr.toString()
        }

        fun parseFolders(json: String): List<FolderItem> {
            return runCatching {
                val arr = JSONArray(json)
                val list = mutableListOf<FolderItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pkgsArr = obj.getJSONArray("packages")
                    val pkgs = List(pkgsArr.length()) { pkgsArr.getString(it) }
                    list.add(FolderItem(obj.getString("id"), obj.getString("name"), pkgs))
                }
                list
            }.getOrDefault(emptyList())
        }

        private fun parseStringList(json: String): List<String> = runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())

        private fun parseIntList(json: String): List<Int> = runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getInt(it) }
        }.getOrDefault(emptyList())
    }
}
