package com.flownova.launcher

import com.flownova.launcher.model.FolderItem
import com.flownova.launcher.model.LauncherSettings
import com.flownova.launcher.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherModelTest {
    @Test
    fun defaultSettingsAndFolders_areConsistent() {
        val folder = FolderItem("1", "Trabalho", listOf("com.whatsapp", "com.google.android.gm"))
        val settings = LauncherSettings(
            themeMode = ThemeMode.SYSTEM,
            folders = listOf(folder)
        )
        assertTrue(settings.dynamicIconsGlobal)
        assertEquals(1, settings.folders.size)
        assertEquals("Trabalho", settings.folders[0].name)
        assertEquals(2, settings.folders[0].packages.size)
    }
}
