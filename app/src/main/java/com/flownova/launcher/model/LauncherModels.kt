package com.flownova.launcher.model

enum class ThemeMode(val label: String) {
    SYSTEM("Automático (Sistema)"),
    DARK("Escuro (AMOLED)"),
    LIGHT("Claro")
}

enum class PalettePreset(val label: String, val primaryHex: Long, val containerHex: Long) {
    DYNAMIC("Material You (Dinâmico)", 0xFF8AB4F8, 0xFF1E3A5F),
    SAPPHIRE("Azul Safira S23", 0xFF64B5F6, 0xFF16324F),
    VIOLET("Roxo Galaxy", 0xFFB388FF, 0xFF311B4E),
    EMERALD("Verde Esmeralda", 0xFF69F0AE, 0xFF143D29),
    AMBER("Âmbar Premium", 0xFFFFD54F, 0xFF423210)
}

enum class IndicatorStyle(val label: String) {
    DOTS("Pontos clássicos"),
    PILL("Cápsula moderna"),
    HIDDEN("Oculto")
}

enum class GestureAction(val label: String) {
    APP_DRAWER("Abrir gaveta de apps"),
    NOTIFICATIONS("Abrir painel de notificações"),
    SEARCH("Pesquisa rápida de apps"),
    SETTINGS("Configurações do Launcher"),
    NONE("Desativado")
}

data class AppItem(
    val label: String,
    val packageName: String,
    val className: String
)

data class FolderItem(
    val id: String,
    val name: String,
    val packages: List<String>
)

data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColors: Boolean = true,
    val palettePreset: PalettePreset = PalettePreset.DYNAMIC,
    val dynamicIconsGlobal: Boolean = true,
    val homeColumns: Int = 4,
    val homeRows: Int = 5,
    val drawerColumns: Int = 5,
    val drawerRows: Int = 6,
    val iconScalePercent: Int = 100,
    val indicatorStyle: IndicatorStyle = IndicatorStyle.DOTS,
    val swipeUpAction: GestureAction = GestureAction.APP_DRAWER,
    val swipeDownAction: GestureAction = GestureAction.NOTIFICATIONS,
    val doubleTapAction: GestureAction = GestureAction.SEARCH,
    val respectReducedMotion: Boolean = true,
    val locationLabel: String = "Cachoeiro de Itapemirim",
    val keepOriginalIconPackages: Set<String> = emptySet(),
    val favoritePackages: List<String> = emptyList(),
    val dockPackages: List<String> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val appWidgetIds: List<Int> = emptyList()
)
