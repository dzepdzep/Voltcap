package com.shelf.launcher.data.guide

enum class SpoilerLevel(val label: String) {
    /** Everything visible. */
    NONE("Show everything"),
    /** Sections after the furthest one are collapsed (title only, tap to expand). */
    LIGHT("Collapse what's ahead"),
    /** Only the current section in full and earlier titles; later sections and locked achievement descriptions hidden. */
    STRICT("Current section only")
}

enum class SectionVisibility { FULL, COLLAPSED, TITLE_ONLY, HIDDEN }

/** Pure visibility rules for a spoiler level. Orders are section.order; -1 = unknown position. */
object GuideVisibility {
    fun of(level: SpoilerLevel, sectionOrder: Int, currentOrder: Int, furthestOrder: Int): SectionVisibility = when (level) {
        SpoilerLevel.NONE -> SectionVisibility.FULL
        SpoilerLevel.LIGHT ->
            if (sectionOrder <= maxOf(currentOrder, furthestOrder)) SectionVisibility.FULL else SectionVisibility.COLLAPSED
        SpoilerLevel.STRICT -> {
            // Without a known position, start at the first section rather than showing nothing.
            val cur = if (currentOrder < 0) 0 else currentOrder
            when {
                sectionOrder == cur -> SectionVisibility.FULL
                sectionOrder < cur -> SectionVisibility.TITLE_ONLY
                else -> SectionVisibility.HIDDEN
            }
        }
    }

    /** Sections to list, each with how much of it to show (HIDDEN ones left out). */
    fun visibleSections(level: SpoilerLevel, guide: Guide, currentId: String?, furthestId: String?): List<Pair<GuideSection, SectionVisibility>> {
        val cur = guide.orderOf(currentId)
        val far = guide.orderOf(furthestId)
        return guide.sections.map { it to of(level, it.order, cur, far) }.filter { it.second != SectionVisibility.HIDDEN }
    }

    /** Whether a locked achievement's description may be shown. Unlocked ones always may. */
    fun achievementDescriptionVisible(level: SpoilerLevel, unlocked: Boolean) = level != SpoilerLevel.STRICT || unlocked
}
