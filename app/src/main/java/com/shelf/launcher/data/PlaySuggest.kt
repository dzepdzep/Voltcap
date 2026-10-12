package com.shelf.launcher.data

/**
 * "What should I play?": scores every game in the category you're in and gives the best few, each
 * with the reason it was picked. Finished, dropped and mastered games are left out; a little
 * randomness keeps the answer fresh, and [exclude] lets "Something else" move on.
 */
object PlaySuggest {
    data class Pick(val game: Game, val reason: String)

    fun pick(
        games: List<Game>,
        lastPlayed: Map<String, Long>,
        playtime: Map<String, Long>,
        favorites: Set<String>,
        statuses: Map<String, StatusEntry>,
        raCompletion: Map<Int, RaCompletionEntry>,
        exclude: Set<String> = emptySet(),
        count: Int = 3,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): List<Pick> {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        return games.asSequence()
            // Games only: plain apps (Settings, a browser…) are never a suggestion.
            .filter { it.stableKey !in exclude && (it.sourceType != SourceType.APP || it.platform != "App") }
            .mapNotNull { g ->
                val status = statuses[g.stableKey]?.status
                if (status == PlayStatus.FINISHED || status == PlayStatus.DROPPED) return@mapNotNull null
                val ra = g.raGameId?.let { raCompletion[it] }
                val award = RaAward.from(ra?.HighestAwardKind)
                if (award == RaAward.MASTERED || award == RaAward.COMPLETED) return@mapNotNull null
                val last = lastPlayed[g.stableKey]
                val ms = playtime[g.stableKey] ?: 0L
                val reasons = ArrayList<Pair<Float, String>>()
                if (status == PlayStatus.PLAYING) reasons += 3f to "You're playing it"
                if (status == PlayStatus.BACKLOG) reasons += 2.5f to "Next on your backlog"
                if (status == PlayStatus.WISHLIST) reasons += 1.5f to "On your wishlist"
                val max = ra?.MaxPossible ?: 0
                val earned = maxOf(ra?.NumAwarded ?: 0, ra?.NumAwardedHardcore ?: 0)
                if (max > 0 && earned > 0) {
                    val pct = earned * 100 / max
                    reasons += (1f + pct / 50f) to "$pct % of the achievements done"
                }
                if (last != null) {
                    val ago = (now - last) / day
                    when {
                        ago < 1 -> reasons += (-1.5f) to "Played today"
                        ago in 3..21 -> reasons += 1.6f to "Last played $ago days ago"
                        ago > 60 -> reasons += 1f to "Not touched in ${ago / 30} months"
                    }
                } else reasons += 0.9f to "You haven't started it yet"
                if (ms > 60L * 60 * 1000 && last != null && (now - last) / day > 2) reasons += 0.8f to "${ms / 3_600_000} h in already"
                if (g.stableKey in favorites) reasons += 1.2f to "One of your favourites"
                val score = reasons.sumOf { it.first.toDouble() }.toFloat() + random.nextFloat() * 1.4f
                val why = reasons.filter { it.first > 0f }.maxByOrNull { it.first }?.second ?: "A change of pace"
                Triple(g, score, why)
            }
            .sortedByDescending { it.second }
            .take(count)
            .map { Pick(it.first, it.third) }
            .toList()
    }
}
