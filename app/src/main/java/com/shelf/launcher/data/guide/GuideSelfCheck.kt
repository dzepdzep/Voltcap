package com.shelf.launcher.data.guide

import com.shelf.launcher.data.RaAchievementDetail

/** Small deterministic checks of the parser, locator, spoilers and missables. Empty list = all passed. */
internal object GuideSelfCheck {
    private const val MD = """# Pokemon Red Walkthrough

Welcome to the guide. ![cover](x.png) <br> Have fun!

## Pallet Town
Grab your starter from the Professor. Then head north onto Route 1.

## Route 1
Wild Pidgey and Rattata live here. Follow the path north to Viridian City. Get the Potion from the [Mart clerk](http://x).

## Viridian City
Pick up the Parcel at the Mart. The gym is closed for now.

## Viridian Forest
Watch out for Weedle. Trainers hide in the grass. Do not miss the Antidote item, it is easy to skip.

## Pewter City
Brock's gym is here. Beat him for the Boulder Badge.
"""

    fun run(): List<String> {
        val fails = ArrayList<String>()
        fun check(ok: Boolean, msg: String) { if (!ok) fails.add(msg) }

        val secs = GuideParser.parseMarkdown(MD, "Fallback")
        check(secs.size == 6, "expected 6 sections, got ${secs.size}")
        check(secs.map { it.title } == listOf("Pokemon Red Walkthrough", "Pallet Town", "Route 1", "Viridian City", "Viridian Forest", "Pewter City"), "titles: ${secs.map { it.title }}")
        check(secs.getOrNull(4)?.id == "pokemon-red-walkthrough/viridian-forest", "id: ${secs.getOrNull(4)?.id}")
        check(secs.getOrNull(0)?.text?.contains("![") == false && secs[0].text.contains("Have fun"), "image/html not stripped: ${secs.getOrNull(0)?.text}")
        check(secs.getOrNull(2)?.text?.contains("Mart clerk") == true && secs[2].text.contains("http") == false, "link text not kept")

        val txt = GuideParser.parsePlainText("INTRO\n\nHello.\n\nRoute 1\n=======\nGo north.\n\n2.1 Viridian City\nBuy stuff.\n", "T")
        check(txt.map { it.title } == listOf("INTRO", "Route 1", "Viridian City"), "txt titles: ${txt.map { it.title }}")

        val guide = Guide("t", "g", "Pokemon Red Walkthrough", "local", secs, 0L, "test", null)
        fun id(title: String) = secs.first { it.title == title }.id
        val loc = GuideLocator(guide)

        // 1. Location named in Rich Presence.
        val r1 = loc.locate(LocatorInput("Exploring Viridian Forest"))
        check(r1.best?.sectionId == id("Viridian Forest"), "r1 best=${r1.best?.sectionId}")
        check(r1.best?.reason == "Rich Presence mentions 'Viridian Forest'", "r1 reason=${r1.best?.reason}")
        check((r1.best?.confidence ?: 0f) > 0.8f, "r1 conf=${r1.best?.confidence}")

        // 2. Moving on, with a badge count that must not matter.
        val forest = id("Viridian Forest")
        val r2 = loc.locate(LocatorInput("Walking in Pewter City | 1 Badge", currentSectionId = forest, furthestSectionId = forest))
        check(r2.best?.sectionId == id("Pewter City"), "r2 best=${r2.best?.sectionId}")
        check(r2.best?.isRevisit == false, "r2 should not be a revisit")

        // 3. Revisit: back at Route 1 after reaching Pewter City. Allowed, furthest untouched by the locator.
        val pewter = id("Pewter City")
        val r3 = loc.locate(LocatorInput("In a battle on Route 1", currentSectionId = pewter, furthestSectionId = pewter))
        check(r3.best?.sectionId == id("Route 1"), "r3 best=${r3.best?.sectionId}")
        check(r3.best?.isRevisit == true, "r3 should be a revisit")

        // 4. Nothing to go on -> unsure, so the UI keeps the last position.
        check(loc.locate(LocatorInput("Title Screen", currentSectionId = pewter)).unsure, "title screen should be unsure")
        check(loc.locate(LocatorInput("Exploring Cerulean Cape")).unsure, "unknown place should be unsure")
        check(loc.locate(LocatorInput(null)).unsure, "null RP should be unsure")
        check(loc.locate(LocatorInput("Exploring Viridian")).unsure, "ambiguous Viridian should be unsure")

        // 5. Manual correction wins, whatever the text says or the badge count is.
        val key = GuideLocator.correctionKey("Exploring Viridian Forest")
        val corr = mapOf(key to id("Route 1"))
        val r5 = loc.locate(LocatorInput("EXPLORING VIRIDIAN FOREST! | 2 Badges", corrections = corr))
        check(r5.best?.sectionId == id("Route 1") && r5.best?.fromCorrection == true, "r5 best=${r5.best?.sectionId}")

        // 6. A recent unlock alone is not enough, but tips a tie.
        val r6 = loc.locate(LocatorInput(null, recentUnlockTitles = listOf("Antidote item")))
        check(r6.unsure && r6.candidates.firstOrNull()?.sectionId == forest, "r6 candidates=${r6.candidates.map { it.sectionId }}")

        // 7. Spoiler levels.
        val ordF = guide.orderOf(forest)
        check(GuideVisibility.of(SpoilerLevel.LIGHT, ordF + 1, ordF, ordF) == SectionVisibility.COLLAPSED, "light after")
        check(GuideVisibility.of(SpoilerLevel.LIGHT, ordF - 1, ordF, ordF) == SectionVisibility.FULL, "light before")
        check(GuideVisibility.of(SpoilerLevel.STRICT, ordF - 1, ordF, ordF) == SectionVisibility.TITLE_ONLY, "strict before")
        check(GuideVisibility.of(SpoilerLevel.STRICT, ordF + 1, ordF, ordF) == SectionVisibility.HIDDEN, "strict after")
        check(!GuideVisibility.achievementDescriptionVisible(SpoilerLevel.STRICT, false), "strict hides locked description")

        // 8. Missables.
        fun ach(id: Int, title: String, type: String?, got: Boolean) =
            RaAchievementDetail(id, title, "", 5, null, type, if (got) "2024-01-01" else null, null)
        val list = listOf(ach(1, "Antidote item", "missable", false), ach(2, "Boulder Badge", null, false), ach(3, "Route 1", "missable", true))
        val rep = GuideMissables.report(guide, list, id("Viridian City"), 1)
        check(rep.missablesUpcoming.map { it.achievement.id } == listOf(1), "missables upcoming=${rep.missablesUpcoming.map { it.achievement.id }}")
        check(rep.lockedUpcoming.map { it.achievement.id } == listOf(1), "locked upcoming")
        val rep2 = GuideMissables.report(guide, list, forest, 1)
        check(rep2.missablesCurrent.map { it.achievement.id } == listOf(1) && rep2.lockedUpcoming.map { it.achievement.id } == listOf(2), "rep2")
        return fails
    }
}
