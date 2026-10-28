package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 375: one confirmable layout picker (R1 hold, per-category manager); the new layouts have their own pictures. */
class LayoutPicker375Test {
    private fun src(name: String): String {
        val roots = listOf("src/main/java/com/shelf/launcher/", "app/src/main/java/com/shelf/launcher/")
        return roots.map { File(it + name) }.first { it.exists() }.readText()
    }

    @Test fun pickerOnlyAppliesThroughTheApplyButton() {
        val s = src("ui/LayoutPicker.kt")
        // A press chooses a candidate; onApply is only called by the Apply chip.
        assertTrue(s.contains("candidate = l; shown = l"))
        assertTrue(s.split("onApply(").size - 1 == 1)
        assertTrue(s.contains("\"Cancel\""))
        assertFalse(s.contains("setAppearance"))
    }

    @Test fun r1HoldAndCategoryManagerUseThePicker() {
        val qs = src("ui/QuickSettings.kt")
        val popup = qs.substring(qs.indexOf("private fun LayoutListPopup"), qs.indexOf("private fun GameSizePopup"))
        assertTrue(popup.contains("LayoutPickerDialog("))
        assertFalse(popup.contains("onDismiss()") && popup.contains("Chip("))
        // R1 single tap still cycles at once.
        assertTrue(qs.contains("all[(all.indexOf(a.homeLayout) + 1) % all.size]"))
        val sd = src("ui/SettingsDialog.kt")
        val card = sd.substring(sd.indexOf("private fun CategoryLayoutsCard"), sd.indexOf("private fun CategoryGameLooksCard"))
        assertTrue(card.contains("LayoutPickerDialog(") && card.contains("CategoryLayoutActions.apply("))
    }

    @Test fun editingAnotherCategoryNeverSwitchesTheHome() {
        val vm = src("LauncherViewModel.kt")
        val f = vm.substring(vm.indexOf("fun applyCategoryLayout"), vm.indexOf("fun applyCategoryLayout") + 400)
        assertTrue(f.contains("if (key == currentCategoryKey()) setCategoryLayout(layout)"))
        assertTrue(f.contains("CategoryLayouts.set(key, layout"))
    }

    @Test fun newLayoutsHaveTheirOwnPictures() {
        val p = src("ui/LayoutPreviews.kt")
        assertTrue(p.contains("HomeLayout.COVERDETAILS -> R.drawable.lp_coverdetails"))
        assertTrue(p.contains("HomeLayout.ARTLIB -> R.drawable.lp_artlib"))
        for (n in listOf("lp_coverdetails", "lp_artlib")) {
            val roots = listOf("src/main/res/drawable-nodpi/", "app/src/main/res/drawable-nodpi/")
            assertTrue(n, roots.any { File("$it$n.webp").exists() })
        }
        assertTrue(HomeLayout.COVERDETAILS in HomeLayout.pickable)
    }
}
