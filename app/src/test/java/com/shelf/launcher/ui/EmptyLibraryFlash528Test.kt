package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Sprint 528: a cold start said "No games yet" for a moment while the saved library was still being read (Flip 2, Intro OFF). */
class EmptyLibraryFlash528Test {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText()

    @Test fun nothingIsSaidAboutAnEmptyShelfBeforeTheLibraryIsRead() {
        assertTrue(src("LauncherUiState.kt").contains("val libraryLoaded: Boolean = false,"))
        val vm = src("LauncherViewModel.kt")
        // set when the saved list has been read, whatever came out - in the same step as the games it brings
        assertTrue(vm.contains("val cached = try { withContext(Dispatchers.IO) { loadRomCache() } } finally { _uiState.update { it.copy(libraryLoaded = true) } }"))
        assertTrue(src("ui/LauncherScreen.kt").contains("val emptyLine = if (!state.libraryLoaded && state.searchQuery.isBlank()) \"\" else"))
        // the empty state itself is unchanged once the library is in
        assertTrue(EmptyShelf.textFor("", libraryEmpty = true).startsWith("No games yet"))
    }
}
