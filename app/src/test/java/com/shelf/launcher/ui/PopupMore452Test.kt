package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 452 (D156): the Voltcap row's window never cuts its keys off when More adds lines (RP Classic). */
class PopupMore452Test {
    @Test fun voltPanelKeysAlwaysFit() {
        val src = listOf("src/main/java/com/shelf/launcher/ui/WinScenesHomes2.kt", "app/src/main/java/com/shelf/launcher/ui/WinScenesHomes2.kt")
            .map(::File).first { it.exists() }.readText()
        val v = src.substring(src.indexOf("internal fun VoltPanel(ctx: WinSceneCtx)"))
        val top = v.indexOf("Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.Top)")
        val keys = v.indexOf("KeyRow(sceneKeys(ctx))")
        assertTrue("picture + facts give way", top in 0 until keys)
    }
}
