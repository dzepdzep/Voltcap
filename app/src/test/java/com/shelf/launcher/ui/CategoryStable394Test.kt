package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 394: a category change never visually reloads an unchanged layout (CLAUDE.md permanent rule). */
class CategoryStable394Test {
    @Test
    fun theCategoryChangeWindowOpensOnMarkAndCloses() {
        CategorySlide.changedAt = Long.MIN_VALUE / 2
        assertFalse(CategorySlide.justChanged())
        CategorySlide.markChange()
        assertTrue(CategorySlide.justChanged())
        CategorySlide.changedAt = System.nanoTime() / 1_000_000L - 5_000L
        assertFalse(CategorySlide.justChanged())
    }

    private fun sources(): List<File> {
        val roots = listOf("src_copy", "../src_copy", "../../src_copy", "app/src/main/java/com/shelf/launcher", "src/main/java/com/shelf/launcher")
        val root = roots.map { File(it) }.firstOrNull { File(it, "ui/Motion.kt").exists() } ?: return emptyList()
        return root.walkTopDown().filter { it.extension == "kt" && it.name != "ThemeShaders.kt" }.toList()
    }

    @Test
    fun noLayoutShellIsSlidedByACategoryChange() {
        val bad = sources().flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l -> if (Regex("""BoxWithConstraints\(.*\.categorySlide\(\)""").containsMatchIn(l)) "${f.name}:${i + 1}" else null }
        }
        assertTrue("categorySlide() on a layout shell: $bad", bad.isEmpty())
    }

    @Test
    fun categoryKeyedContentSwapsAtOnce() {
        val bad = sources().flatMap { f ->
            val s = f.readText()
            Regex("""AnimatedContent\(""").findAll(s).mapNotNull { m ->
                val head = s.substring(m.range.first, minOf(s.length, m.range.first + 260))
                val body = s.substring(m.range.first, minOf(s.length, m.range.first + 1100))
                if (Regex("""targetState\s*=[^\n]*\b(categoryLabel|platformFilter|specialFilter|sortCategoryKey)\b""").containsMatchIn(head) &&
                    !body.contains("tween(0)") && !body.contains("None togetherWith") && !body.contains("categoryQuiet()")) f.name else null
            }.toList()
        }
        assertTrue("category-keyed AnimatedContent animates: $bad", bad.isEmpty())
    }
}
