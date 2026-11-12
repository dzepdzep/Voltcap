package com.shelf.launcher.ui
import androidx.compose.material.icons.filled.PlayArrow

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Popup motion work, Storage boxes: native pages, the B focus contract, L1 / R1 order (WinPageFlow.kt, WinScenes.kt). */
class PopupNativeStorageTest {
    private fun src(name: String): String = listOf(
        File("../src_copy/ui/$name"), File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name")
    ).first { it.exists() }.readText()

    @Test fun backOnTheFirstPageGoesToTheOpenerNeverPlay() {
        assertEquals(PageFocus.OPENER, pageFocusTarget(WinPage.MAIN, fromPerf = false, hasOpener = true))
        // Performance keeps its own way back
        assertEquals(PageFocus.PERF, pageFocusTarget(WinPage.MAIN, fromPerf = true, hasOpener = true))
        // opened with no opener (the window's first open): Play
        assertEquals(PageFocus.PLAY, pageFocusTarget(WinPage.MAIN, fromPerf = false, hasOpener = false))
        assertEquals(PageFocus.LIST, pageFocusTarget(WinPage.TROPHIES, fromPerf = false, hasOpener = true))
        assertEquals(PageFocus.LIST, pageFocusTarget(WinPage.OPTIONS, fromPerf = false, hasOpener = false))
        assertEquals(PageFocus.PLAY, pageFocusTarget(WinPage.DETAILS, fromPerf = false, hasOpener = true))
    }

    @Test fun theOpenerIsNotPrecededByPlay() {
        val gw = src("GameWindow.kt")
        // the old order: Play asked at 60 ms, the opener only at 120 ms (Play lit in between; a quick A started the game)
        assertFalse(gw.contains("kotlinx.coroutines.delay(120)\n        runCatching { f.requestFocus() }"))
        val effect = gw.substringAfter("PageFocus.OPENER -> {").substringBefore("PageFocus.PLAY ->")
        assertFalse("the opener branch must not ask for Play first", effect.substringBefore("focusWhenAttached(opener").contains("playFocus"))
    }

    @Test fun storageKeepsItsOwnPages() {
        assertTrue(nativePages(WinScene.STORAGE_RECORD))
        assertFalse(nativePages(WinScene.CASE_MEDIA))
        assertFalse(nativePages(null))
        val gw = src("GameWindow.kt")
        // (Phase 3: the condition lives in sharedPageFor, WinPageFlow.kt - one decision for every scene.)
        assertTrue(gw.contains("if (sharedPageFor(scene, page)) SceneExpandedPage("))
        assertTrue(src("WinPageFlow.kt").contains("page != WinPage.MAIN && scene != WinScene.CROSS_OPTIONS && !nativePages(scene)"))
        val st = src("WinScenes.kt").substringAfter("private fun StorageRecord(").substringBefore("internal val STORAGE_TABS")
        assertTrue("Storage draws every page inside its record", st.contains("StorageRecordPage(ctx, p, red, live)"))
        assertFalse(st.contains("SceneExpandedPage"))
    }

    @Test fun l1R1WalkTheTabsBothWays() {
        val t = STORAGE_TABS
        assertEquals(WinPage.DETAILS, pageStep(t, WinPage.MAIN, 1))
        assertEquals(WinPage.OPTIONS, pageStep(t, WinPage.MAIN, -1))
        assertEquals(WinPage.MAIN, pageStep(t, WinPage.OPTIONS, 1))
        assertEquals(WinPage.TROPHIES, pageStep(t, WinPage.OPTIONS, -1))
        // a page that isn't a tab (Performance) steps from the first tab
        assertEquals(WinPage.DETAILS, pageStep(t, WinPage.PERF, 1))
        assertEquals(1, pageDirection(t, WinPage.MAIN, WinPage.TROPHIES))
        assertEquals(-1, pageDirection(t, WinPage.OPTIONS, WinPage.DETAILS))
        assertEquals(-1, pageDirection(t, WinPage.DETAILS, WinPage.MAIN))
    }

    @Test fun tabsOpenThroughTheirButtonsSoBReturnsThere() {
        val gw = src("GameWindow.kt")
        // a tab opens as its button does (opener recorded); a page turned to from another page keeps the first opener
        assertTrue(gw.contains("fun opener(f: FocusRequester, act: () -> Unit): () -> Unit = { if (page == WinPage.MAIN) pageOpener[0] = f; act() }"))
        val st = src("WinScenes.kt").substringAfter("private fun StorageRecord(").substringBefore("internal val STORAGE_TABS")
        assertTrue(st.contains("ctx.open(t)"))
    }

    @Test fun theLeavingSheetHoldsNoFocus() {
        val a = WinAction("Play", androidx.compose.material.icons.Icons.Filled.PlayArrow, {}, focus = androidx.compose.ui.focus.FocusRequester(), primary = true)
        val i = a.inert()
        assertTrue(i.focus == null && i.primary && i.label == "Play")
    }

    @Test fun nightDrawsItsOwnPagesAlongTheSlant() {
        assertTrue(nativePages(WinScene.NIGHT_STATUS))
        assertEquals(listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS), NIGHT_TABS)
        val n = src("WinScenes.kt").substringAfter("private fun NightStatus(").substringBefore("internal val NIGHT_TABS")
        assertTrue(n.contains("NightPagePanel(ctx, p, live"))                     // the page in the menu's own big slanted panel
        assertTrue(n.contains("Key.ButtonL1 -> -1; Key.ButtonR1 -> 1"))           // L1 / R1 walk the pages
        assertTrue(n.contains("ctx.open(to)"))                                     // ... through their commands (B returns there)
        assertTrue(n.contains("kotlin.math.tan(slant)"))                           // panels move along the slant
        assertFalse(n.contains("ctx.pageBody("))                                   // no page squeezed into the stats panel
        // the banner is drawn after the panels (a panel sweeping up the slant passes under it)
        assertTrue(n.indexOf("NightBannerWipe(") > n.indexOf("AnimatedContent("))
        val wipe = src("WinScenes.kt").substringAfter("private fun NightBannerWipe(").substringBefore("private fun NightPagePanel(")
        assertTrue(wipe.contains("drawRect(Color(0xFF0B1424)"))                    // the dark band at the wipe's edge
        assertTrue(wipe.contains("if (!anim) { shown = page"))                     // animations off: the new words at once
    }

    @Test fun tilesTurnTheirCardForEachPage() {
        assertTrue(nativePages(WinScene.TILE_CARD))
        val t = src("WinSceneFamilies.kt").substringAfter("internal fun TileLiftCard(").substringBefore("internal val TILE_TABS")
        assertTrue(t.contains("rotationY = turn"))                                   // the writing turns like a tile
        assertTrue(t.contains("-90f * turnDir[0]") && t.contains("90f * turnDir[0]")) // forward and back turn opposite ways
        assertTrue(t.contains("TileLiftPage(ctx, p, live"))
        assertTrue(t.contains("Key.ButtonL1 -> -1; Key.ButtonR1 -> 1") && t.contains("ctx.open(to)"))
        assertTrue(t.contains("if (!anim) snap()"))                                  // animations off: no turn
    }

    @Test fun bladesLayerAPageBladeAndKeepTheEdgeReadable() {
        assertTrue(nativePages(WinScene.BLADE_GUIDE))
        val b = src("WinScenesConsole.kt").substringAfter("internal fun BladeGuide(").substringBefore("internal val BLADE_TABS")
        // forward: the new blade slides in on top; back: the blade you were on slides away on top of the one under it
        assertTrue(b.contains("apply { targetContentZIndex = 1f }") && b.contains("apply { targetContentZIndex = -1f }"))
        assertTrue(b.contains("BladePageBlade(ctx, pg, pg == ctx.page"))
        assertTrue(b.contains("BladeEdgeName(ctx.name"))                              // the covered game blade keeps its name
        assertFalse(b.contains("ctx.pageBody("))
        // no state written while composing (the first page's commands are kept in a plain holder)
        assertFalse(b.contains("mainRows.value ="))
        val edge = src("WinScenesConsole.kt").substringAfter("private fun BladeEdgeName(").substringBefore("private fun BladePageBlade(")
        assertTrue(edge.contains("maxLines = 2"))                                     // two lines across the strip, never cut
        assertEquals(listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS), BLADE_TABS)
    }
}

/** Popup-first (owner, 12 Nov): A opens the game's window on every layout that has one, unless someone saved "A plays". */
class PopupFirstDefaultsTest {
    private fun src(name: String): String = listOf(
        java.io.File("../src_copy/data/$name"), java.io.File("src/main/java/com/shelf/launcher/data/$name"), java.io.File("app/src/main/java/com/shelf/launcher/data/$name")
    ).first { it.exists() }.readText()

    @Test fun aPlaysIsOffUnlessSaved() {
        val f = src("Features.kt")
        // same keys (a saved "on" is still read), new defaults
        assertTrue(f.contains("val cardsAPlays = BoolPref(\"cards_a_plays\", false)"))
        assertTrue(f.contains("val pqAPlays = BoolPref(\"pq_a_plays\", false)"))
        // no other layout switch starts a game on A
        val aPlays = Regex("BoolPref\\(\"[a-z_]*a_plays[0-9]*\", true\\)").findAll(f).toList()
        assertTrue(aPlays.map { it.value }.toString(), aPlays.isEmpty())
    }
}

/** Spine flicker work (12 Nov): a 3D case's spine is found again by its picture's address, not only by the decoded copy. */
class SpineByAddressTest {
    private fun src(): String = listOf(
        java.io.File("../src_copy/ui/Case3D.kt"), java.io.File("src/main/java/com/shelf/launcher/ui/Case3D.kt"), java.io.File("app/src/main/java/com/shelf/launcher/ui/Case3D.kt")
    ).first { it.exists() }.readText()

    @Test fun spineChainIsKeptByAddress() {
        val s = src()
        // found by address before the picture is decoded again
        assertTrue(s.contains("src?.let { DerivedArt.peek<T>(it, kind) } ?: model?.let { DerivedArt.peekModel<T>(it, kind) }"))
        assertTrue(s.contains("if (v != null && model != null) DerivedArt.putModel(model, kind, v)"))
        // the upright spine and every fitting of it, and the strip cut from the box scan
        assertTrue(s.contains("rememberDerived(ownBmp, if (twinsPossible) \"spine|1\" else \"spine|0\", model = ownKey)"))
        assertEquals(3, Regex("rememberDerived\\(ownUpright, [^\\n]*model = ownKey\\?\\.let").findAll(s).count())
        assertTrue(s.contains("rememberDerived(cutBmp, \"spine|1\", model = cutKey)"))
        assertTrue(s.contains("rememberDerived(cutUpright, \"spineFit2|\$depth\", model = cutKey)"))
        // never another game's: the address is the model itself (a cut model carries its cut), at the size it was decoded
        assertTrue(s.contains("val ownKey = own?.let { \"\$it|600\" }"))
        // a list of pictures is counted at its real size (the cache stays bounded)
        assertTrue(s.contains("is List<*> -> value.sumOf"))
    }
}

/** Shelf flash work (12 Nov): the case flight's hand-over and the window's copy of the case. */
class ShelfCaseHandOverTest {
    private fun src(name: String): String = listOf(
        java.io.File("../src_copy/ui/$name"), java.io.File("src/main/java/com/shelf/launcher/ui/$name"), java.io.File("app/src/main/java/com/shelf/launcher/ui/$name")
    ).first { it.exists() }.readText()

    @Test fun theHomeCopyStaysUntilTheFlyingCopyIsDrawn() {
        // before the hand-over the home's case shows, whatever the reveal
        assertEquals(0f, caseHomeHiddenGated(0.3f, ready = false))
        // after it, as before (hidden for the whole flight)
        assertEquals(1f, caseHomeHiddenGated(0.3f, ready = true))
        assertEquals(0f, caseHomeHiddenGated(0f, ready = true))
    }

    @Test fun theRevealWaitsForTheHandOver() {
        // the window opens (the flight starts) only once the flying case is drawn, at most MAX_WAIT_MS
        val gw = src("GameWindow.kt")
        val open = gw.substringAfter("LaunchedEffect(target != null) {").substringBefore("// Update 506 (motion polish")
        assertTrue(open.contains("if (caseFlight && anim) androidx.compose.runtime.snapshotFlow { CaseFlightGate.ready.value }.first { it }"))
        assertTrue(open.indexOf("CaseFlightGate.ready.value }.first") < open.indexOf("grow.animateTo(1f"))
        assertTrue(gw.contains("kotlinx.coroutines.delay(CaseFlightGate.MAX_WAIT_MS); CaseFlightGate.release(winToken)"))
        // (Phase 4: no visible pause - a scan the shelf drew is complete at once (ShownScans); the wait is for a cold scan only.)
        assertTrue(CaseFlightGate.MAX_WAIT_MS in 1..160)
    }

    @Test fun theWindowsCaseIsTheShelfsCase() {
        val f = src("WinSceneFamilies.kt")
        assertTrue(f.contains("PulledOutCase(ctx.g, ctx.cover, ctx.state.physicalMedia[ctx.g.stableKey], false, modifier, waitForCase = true, onArtReady = onArtReady)"))
        assertTrue(f.contains("if (gated) { alpha = 0f; return@graphicsLayer }"))
        val gw = src("GameWindow.kt")
        assertTrue(gw.contains("caseHomeHiddenGated(gv, ready)"))
        assertTrue(gw.contains("CaseFlightGate.release(winToken); if (MediaInspect.key"))   // released on dispose
        val cc = src("CoverCases.kt")
        assertTrue(cc.contains("onError = { onFail(); onShown?.invoke() }"))                  // a failing scan never holds it
    }
}

/** Shelf fitting work (physical-media audit 5a): the window's achievements line keeps its total. */
class WinAchievementsLineTest {
    @Test fun totalIsNeverCutByTheBar() {
        val s = listOf(java.io.File("src/main/java/com/shelf/launcher/ui/GameWindow.kt"), java.io.File("app/src/main/java/com/shelf/launcher/ui/GameWindow.kt")).first { it.exists() }.readText()
        val row = s.substringAfter("if (extras.at(WinPart.ACHIEVEMENTS, slot)) {").substringBefore("val summary = meta?.summary")
        assertTrue(row.contains("\$earned\\u00A0/\\u00A0\$total"))                  // "141 / 160" never broken
        assertTrue(row.contains("maxLines = 2") && row.contains("modifier = Modifier.weight(1f, fill = false)"))
        assertFalse(row.contains("fontWeight = FontWeight.SemiBold, maxLines = 1, fontFamily = family"))
    }
}

/** Popup motion work: L1 / R1 walk only the pages a window has (no achievements list / game menu opened by a bumper). */
class BumpersWalkRealPagesTest {
    @Test fun unavailablePagesAreSkipped() {
        val have = setOf(WinPage.MAIN, WinPage.DETAILS, WinPage.OPTIONS)   // no achievements page for this game
        val t = STORAGE_TABS.filter { it in have }
        assertEquals(WinPage.OPTIONS, pageStep(t, WinPage.DETAILS, 1))
        assertEquals(WinPage.DETAILS, pageStep(t, WinPage.OPTIONS, -1))
        assertEquals(WinPage.MAIN, pageStep(t, WinPage.OPTIONS, 1))
    }

    @Test fun everyNativeWindowFiltersItsTabs() {
        fun src(n: String) = listOf(java.io.File("src/main/java/com/shelf/launcher/ui/$n"), java.io.File("app/src/main/java/com/shelf/launcher/ui/$n")).first { it.exists() }.readText()
        assertEquals(2, Regex("pageStep\\(tabs\\.filter \\{ it in ctx\\.pages \\}").findAll(src("WinScenes.kt")).count())     // Storage, Night
        assertTrue(src("WinSceneFamilies.kt").contains("pageStep(TILE_TABS.filter { it in ctx.pages }"))
        assertTrue(src("WinScenesConsole.kt").contains("pageStep(BLADE_TABS.filter { it in ctx.pages }"))
        val gw = src("GameWindow.kt")
        assertTrue(gw.contains("if (list.isNotEmpty() && LocalSceneLayout.current?.let { crossTrophiesOn(it) } != true) add(WinPage.TROPHIES)"))
        assertTrue(gw.contains("if (options.isNotEmpty()) add(WinPage.OPTIONS)"))
    }
}

/** Popup motion work: a covered / leaving page's controls can't take the D-pad. */
class InertControlsTest {
    @Test fun inertIsNotFocusable() {
        val a = WinAction("Play", androidx.compose.material.icons.Icons.Filled.PlayArrow, {}, focus = androidx.compose.ui.focus.FocusRequester(), primary = true)
        assertTrue(a.inert().inert && !a.inert)
        val s = listOf(java.io.File("src/main/java/com/shelf/launcher/ui/WinScenes.kt"), java.io.File("app/src/main/java/com/shelf/launcher/ui/WinScenes.kt")).first { it.exists() }.readText()
        // every scene control goes through SceneControl; a disabled clickable is neither focusable nor clickable
        assertTrue(s.contains(".clickable(enabled = !a.inert, interactionSource = source, indication = null)"))
    }
}

/** Popup motion work: no ignored first press while a native page change runs. */
class NativePageFocusTimingTest {
    @Test fun focusIsNotHeldBackForTheMotion() {
        val gw = listOf(java.io.File("src/main/java/com/shelf/launcher/ui/GameWindow.kt"), java.io.File("app/src/main/java/com/shelf/launcher/ui/GameWindow.kt")).first { it.exists() }.readText()
        val effect = gw.substringAfter("when (pageFocusTarget(page, fromPerf, opener != null)) {").substringBefore("var target by remember")
        assertFalse(effect.contains("NATIVE_PAGE_MS"))
    }
}

/** Phase 4 (12 Nov): the Shelf hand-over gate under rapid opens / closes, and the shared scan bitmap. */
class ShelfHandOverRobustTest {
    private fun src(name: String): String = listOf(
        java.io.File("src/main/java/com/shelf/launcher/ui/$name"), java.io.File("app/src/main/java/com/shelf/launcher/ui/$name")
    ).first { it.exists() }.readText()

    @org.junit.After fun reset() { CaseFlightGate.drawn() }

    @Test fun aClosingWindowNeverReleasesTheNextOnesGate() {
        val first = Any(); val second = Any()
        CaseFlightGate.hold(first)
        assertFalse(CaseFlightGate.ready.value)
        // rapid B, A: the next window holds the gate before the first one's dispose / timer runs
        CaseFlightGate.hold(second)
        CaseFlightGate.release(first)                     // first's dispose or its late timer
        assertFalse(CaseFlightGate.ready.value)           // second still waits for its own flying copy
        CaseFlightGate.release(second)                    // its timer / dispose
        assertTrue(CaseFlightGate.ready.value)
    }

    @Test fun drawnAlwaysReleasesAndAReleaseTwiceIsHarmless() {
        val w = Any()
        CaseFlightGate.hold(w); CaseFlightGate.drawn()
        assertTrue(CaseFlightGate.ready.value)
        CaseFlightGate.release(w); CaseFlightGate.release(w)
        assertTrue(CaseFlightGate.ready.value)
        // a window that was never holding (a non-case window) never closes the gate
        CaseFlightGate.release(Any()); assertTrue(CaseFlightGate.ready.value)
    }

    @Test fun shownScansIsBoundedAndLatestWins() {
        for (i in 0 until ShownScans.MAX + 40) ShownScans.note("scan$i", coil.memory.MemoryCache.Key("k$i"))
        assertTrue(ShownScans.size() <= ShownScans.MAX)
        assertEquals(null, ShownScans.keyFor("scan0"))                         // the eldest went
        assertEquals(coil.memory.MemoryCache.Key("k${ShownScans.MAX + 39}"), ShownScans.keyFor("scan${ShownScans.MAX + 39}"))
        ShownScans.note("scan5x", coil.memory.MemoryCache.Key("a")); ShownScans.note("scan5x", coil.memory.MemoryCache.Key("b"))
        assertEquals(coil.memory.MemoryCache.Key("b"), ShownScans.keyFor("scan5x"))
    }

    @Test fun theWindowsCopyStartsFromTheShelfsBitmap() {
        val cc = src("CoverCases.kt")
        val scan = cc.substringAfter("internal fun ScanCase(").substringBefore("internal object ShownScans")
        // only the window's copy (onShown) borrows; never a probing (unmeasured, maybe unfolded) scan
        assertTrue(scan.contains("val heldKey = if (onShown != null && !probing) ShownScans.inMemory(ctxNow, shownKey) else null"))
        assertTrue(scan.contains("mutableStateOf(heldKey != null)"))
        assertTrue(scan.contains("placeholderMemoryCacheKey(heldKey)"))
        assertTrue(scan.contains("r.result.memoryCacheKey?.let { ShownScans.note(shownKey, it) }"))
    }
}

/** Phase 6 (12 Nov): the spine flicker diagnostics tell an empty texture, another texture and a new copy apart. */
class SpineTraceTest {
    @org.junit.Before fun clear() = SpineTrace.reset()

    @Test fun classifiesEveryKindOfChange() {
        assertEquals("FIRST waiting", SpineTrace.note("g", SpineTrace.WAITING, 1))
        assertEquals("FILLED own", SpineTrace.note("g", "own", 2))
        assertEquals(null, SpineTrace.note("g", "own", 2))                       // the same frame again: nothing
        assertEquals("REDECODE own", SpineTrace.note("g", "own", 3))            // a new copy of the same spine
        assertEquals("EMPTY own->waiting", SpineTrace.note("g", SpineTrace.WAITING, 1))   // the dark strip came back: a flicker
        assertEquals("FILLED console", SpineTrace.note("g", "console", 4))
        assertEquals("SWAP console->own", SpineTrace.note("g", "own", 2))       // another texture
        assertEquals("FIRST template", SpineTrace.note("h", "template", 9))     // per game
    }

    @Test fun bounded() {
        for (i in 0 until 1000) SpineTrace.note("g$i", "own", i)
        assertEquals("FIRST own", SpineTrace.note("g0", "own", 0))              // the eldest was let go
    }

    @Test fun onlyWithTheQaTrace() {
        val c = listOf(java.io.File("src/main/java/com/shelf/launcher/ui/Case3D.kt"), java.io.File("app/src/main/java/com/shelf/launcher/ui/Case3D.kt")).first { it.exists() }.readText()
        val at = c.indexOf("SpineTrace.note(g.stableKey")
        assertTrue(at > 0 && c.lastIndexOf("if (QaTrace.on) {", at) > at - 900)
    }
}
