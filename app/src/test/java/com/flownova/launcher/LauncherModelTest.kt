package com.flownova.launcher

import com.flownova.launcher.data.PreferenceStorage
import com.flownova.launcher.model.FolderItem
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherModelTest {
    @Test
    fun folderSerializationRoundTrip_isAccurate() {
        val original = listOf(FolderItem("1", "Trabalho", listOf("com.whatsapp", "com.google.android.gm")))
        val json = PreferenceStorage.serializeFolders(original)
        val restored = PreferenceStorage.parseFolders(json)
        assertEquals(1, restored.size)
        assertEquals("Trabalho", restored[0].name)
        assertEquals(2, restored[0].packages.size)
    }
}
