package com.shelf.launcher.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 682t (H16 survey x1.3, Night's stats panel): a value whose longest word cannot fit beside the label column even at the
 * readable minimum goes under its label instead of breaking inside the word ("PlayStatio / n 2"). The window-level proof is
 * WindowWordsSurvey682Test[NIGHT x1.3], which failed before this change.
 */
class NightValueStack682tTest {
    @Test fun aWordWiderThanTheRoomBesideTheLabelStacks() =
        assertTrue(nightValueStacks(66.dp, 61.dp))

    @Test fun aWordThatFitsStaysBesideItsLabel() =
        assertFalse(nightValueStacks(50.dp, 61.dp))

    @Test fun roundingAtTheEdgeIsNoReasonToStack() =
        assertFalse(nightValueStacks(59.5.dp, 61.dp))
}
