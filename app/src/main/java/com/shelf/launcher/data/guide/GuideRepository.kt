package com.shelf.launcher.data.guide

import android.content.Context
import com.shelf.launcher.data.RaAchievementDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An achievement unlocked recently (from RA polling / UnlockNotifier). Either field may be null. */
data class RecentUnlock(val id: Int?, val title: String?)

/** Where the player is in a guide. */
data class GuidePosition(
    val guide: Guide,
    /** Section to show; null only if the guide has no sections. */
    val current: GuideSection?,
    /** Furthest section reached so far (may be after [current] on a revisit). */
    val furthest: GuideSection?,
    /** Locator output for the latest Rich Presence (null for continueGuide). */
    val located: LocatorResult? = null,
    /** True when the locator was unsure and the previous position was kept. */
    val keptPrevious: Boolean = false
)

/**
 * Facade for the walkthrough feature. Everything runs on Dispatchers.IO, so the UI layer can call
 * these straight from a coroutine.
 *
 * Typical flow: guidesFor(game) -> user picks (or the first TEXT candidate) -> selectGuide ->
 * on every Rich Presence change locate(...) -> show position.current.
 */
class GuideRepository(context: Context) {
    private val app = context.applicationContext
    val store = GuideProgressStore(app)
    private val cache = GuideCache(app)
    private val registry = GuideRegistry(
        listOf(
            LocalFolderGuideProvider(app) { store.localFolder() },
            RetroAchievementsGuideProvider(app, cache),
            StrategyWikiGuideProvider(app, cache),
            FandomGuideProvider(app, cache),
            GuideSiteLinksProvider(app)
        ),
        store
    )

    /** Candidate guides for a game: the user's choice first, then local files, then RA's guide/link. */
    suspend fun guidesFor(game: GuideGameRef): List<GuideCandidate> = withContext(Dispatchers.IO) { registry.guidesFor(game) }

    suspend fun load(guideId: String, gameKey: String = ""): Guide? = withContext(Dispatchers.IO) { registry.load(guideId, gameKey) }

    suspend fun load(candidate: GuideCandidate): Guide? = withContext(Dispatchers.IO) { registry.load(candidate) }

    /** Remembers the user's guide for this game (and resets nothing else). */
    suspend fun selectGuide(gameKey: String, guideId: String?) = withContext(Dispatchers.IO) {
        store.update(gameKey) { it.copy(chosenGuideId = guideId) }; Unit
    }

    /** The chosen guide loaded, or null if none chosen / unavailable. */
    suspend fun chosenGuide(gameKey: String): Guide? = withContext(Dispatchers.IO) {
        store.get(gameKey).chosenGuideId?.let { registry.load(it, gameKey) }
    }

    /**
     * Estimates the section for the current Rich Presence and saves the new position when confident.
     * Returns null when no guide is chosen. If unsure, the previous position is kept (keptPrevious).
     */
    suspend fun locate(gameKey: String, richPresence: String?, recentUnlocks: List<RecentUnlock> = emptyList()): GuidePosition? =
        withContext(Dispatchers.IO) {
            val guide = chosenGuide(gameKey) ?: return@withContext null
            val p = store.get(gameKey)
            val result = GuideLocator(guide).locate(
                LocatorInput(
                    richPresence, recentUnlocks.mapNotNull { it.title }, recentUnlocks.mapNotNull { it.id }.toSet(),
                    p.currentSectionId, p.furthestSectionId, p.corrections
                )
            )
            val best = result.best
            var np = p.copy(lastRichPresence = richPresence ?: p.lastRichPresence)
            if (best != null) np = np.copy(currentSectionId = best.sectionId, furthestSectionId = furthestOf(guide, p.furthestSectionId, best.sectionId))
            if (np != p) store.update(gameKey) { np }
            position(guide, np, result, keptPrevious = best == null)
        }

    /** "Continue guide": the saved position (current, else furthest, else the first section). */
    suspend fun continueGuide(gameKey: String): GuidePosition? = withContext(Dispatchers.IO) {
        val guide = chosenGuide(gameKey) ?: return@withContext null
        position(guide, store.get(gameKey), null, false)
    }

    /** The user says "this Rich Presence means this section". Always wins from now on. */
    suspend fun correct(gameKey: String, richPresence: String, sectionId: String) = withContext(Dispatchers.IO) {
        val guide = chosenGuide(gameKey)
        if (guide == null || guide.orderOf(sectionId) < 0) return@withContext
        val key = GuideLocator.correctionKey(richPresence)
        store.update(gameKey) {
            it.copy(
                corrections = if (key.isEmpty()) it.corrections else it.corrections + (key to sectionId),
                currentSectionId = sectionId,
                furthestSectionId = furthestOf(guide, it.furthestSectionId, sectionId)
            )
        }
        Unit
    }

    /** Moves the position by hand without saving a Rich Presence correction. */
    suspend fun setPosition(gameKey: String, sectionId: String) = withContext(Dispatchers.IO) {
        val guide = chosenGuide(gameKey) ?: return@withContext
        if (guide.orderOf(sectionId) < 0) return@withContext
        store.update(gameKey) { it.copy(currentSectionId = sectionId, furthestSectionId = furthestOf(guide, it.furthestSectionId, sectionId)) }
        Unit
    }

    suspend fun clearCorrections(gameKey: String) = withContext(Dispatchers.IO) { store.update(gameKey) { it.copy(corrections = emptyMap()) }; Unit }

    /** Missable / locked achievements near the current section (use with missablesMode). */
    suspend fun missables(gameKey: String, achievements: List<RaAchievementDetail>, lookahead: Int = 3): MissableReport? =
        withContext(Dispatchers.IO) {
            val guide = chosenGuide(gameKey) ?: return@withContext null
            val p = store.get(gameKey)
            GuideMissables.report(guide, achievements, p.currentSectionId ?: p.furthestSectionId, lookahead)
        }

    // Settings
    suspend fun progress(gameKey: String): GameGuideProgress = withContext(Dispatchers.IO) { store.get(gameKey) }
    suspend fun spoilerLevel(gameKey: String): SpoilerLevel = progress(gameKey).spoilerLevel
    suspend fun setSpoilerLevel(gameKey: String, level: SpoilerLevel) = withContext(Dispatchers.IO) { store.update(gameKey) { it.copy(spoilerLevel = level) }; Unit }
    suspend fun missablesMode(gameKey: String): Boolean = progress(gameKey).missablesMode
    suspend fun setMissablesMode(gameKey: String, on: Boolean) = withContext(Dispatchers.IO) { store.update(gameKey) { it.copy(missablesMode = on) }; Unit }
    suspend fun localFolder(): String? = withContext(Dispatchers.IO) { store.localFolder() }
    suspend fun setLocalFolder(folder: String?) = withContext(Dispatchers.IO) { store.setLocalFolder(folder) }

    private fun furthestOf(guide: Guide, old: String?, candidate: String): String =
        if (old == null || guide.orderOf(candidate) > guide.orderOf(old)) candidate else old

    private fun position(guide: Guide, p: GameGuideProgress, result: LocatorResult?, keptPrevious: Boolean): GuidePosition {
        val far = guide.section(p.furthestSectionId)
        val cur = guide.section(p.currentSectionId) ?: far ?: guide.sections.firstOrNull()
        return GuidePosition(guide, cur, far, result, keptPrevious)
    }
}
