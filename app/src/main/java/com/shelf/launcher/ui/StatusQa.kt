package com.shelf.launcher.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/*
 * 677d (owner 16 Nov, permanent QA requirements: readable status information, no duplicate UI): what the QA dump (QaClip
 * "statusBars" / "avatars") needs to grade the status bar's pieces and the profile pictures from their real bounds -
 * tools/qa/tools/statuscheck.py. Semantics only: nothing drawn or measured changes.
 */

/** The status bar's clock / icons / profile cluster. */
internal val StatusClusterTag = SemanticsPropertyKey<Boolean>("VoltcapStatusCluster")
/** A picture of the playing profile (its avatar or initial) - one per screen (the profile switcher's row is not one). */
internal val AvatarTag = SemanticsPropertyKey<String>("VoltcapAvatar")
private var SemanticsPropertyReceiver.statusClusterTag by StatusClusterTag
private var SemanticsPropertyReceiver.avatarTag by AvatarTag

internal fun Modifier.statusClusterTag(): Modifier = this.semantics { statusClusterTag = true }
/** [where]: which part draws it ("bar", "hub", "channel") - the report names the duplicate's owners. */
internal fun Modifier.avatarTag(where: String): Modifier = this.semantics { avatarTag = where }
