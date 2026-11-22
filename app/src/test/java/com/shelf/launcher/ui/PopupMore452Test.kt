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
        // (P2 681c: Info's picture + facts sit in the panel's page area - on Info it gives way (fill = false) exactly as the row did.)
        val top = v.indexOf("NativePages(ctx, Modifier.weight(1f, fill = ctx.page != WinPage.MAIN)")
        val keys = v.indexOf("KeyRow(sceneKeys(ctx)")
        assertTrue("picture + facts give way", top in 0 until keys)
    }
}
