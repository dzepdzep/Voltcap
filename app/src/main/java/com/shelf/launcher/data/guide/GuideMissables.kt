package com.shelf.launcher.data.guide

import com.shelf.launcher.data.RaAchievementDetail

data class AchievementHint(val section: GuideSection, val achievement: RaAchievementDetail)

/** Achievement-hunting view for the current spot in a guide. */
data class MissableReport(
    /** Locked missable achievements whose title the current section mentions. */
    val missablesCurrent: List<AchievementHint>,
    /** Locked missables in the next [lookahead] sections. */
    val missablesUpcoming: List<AchievementHint>,
    /** All locked achievements mentioned in the current section (missable ones included). */
    val lockedCurrent: List<AchievementHint>,
    val lockedUpcoming: List<AchievementHint>
)

object GuideMissables {
    /** Sections mentioning each achievement: by linked id, or by its title as a whole-phrase in the section text. */
    fun mapToSections(guide: Guide, achievements: List<RaAchievementDetail>): Map<String, List<RaAchievementDetail>> {
        val padded = guide.sections.map { GuideText.normalizeLines(it.title + "\n" + it.text) }
        val out = LinkedHashMap<String, MutableList<RaAchievementDetail>>()
        for (a in achievements) {
            val t = a.title?.let { GuideText.normalize(it) }?.takeIf { it.length >= 4 }
            guide.sections.forEachIndexed { i, s ->
                val hit = a.id in s.mentionedAchievementIds || (t != null && padded[i].contains(" $t "))
                if (hit) out.getOrPut(s.id) { ArrayList() }.add(a)
            }
        }
        return out
    }

    /**
     * Already-unlocked missables are left out: they are no longer at risk.
     * @param currentSectionId null = no known position, everything returns empty.
     */
    fun report(guide: Guide, achievements: List<RaAchievementDetail>, currentSectionId: String?, lookahead: Int = 3): MissableReport {
        val cur = guide.orderOf(currentSectionId)
        if (cur < 0) return MissableReport(emptyList(), emptyList(), emptyList(), emptyList())
        val map = mapToSections(guide, achievements)
        fun hints(range: IntRange, missableOnly: Boolean): List<AchievementHint> = range.flatMap { i ->
            val s = guide.sections.getOrNull(i) ?: return@flatMap emptyList()
            map[s.id].orEmpty().filter { !it.isUnlocked && (!missableOnly || it.isMissable) }.map { AchievementHint(s, it) }
        }
        val cr = cur..cur
        val up = (cur + 1)..(cur + maxOf(0, lookahead))
        return MissableReport(hints(cr, true), hints(up, true), hints(cr, false), hints(up, false))
    }
}
