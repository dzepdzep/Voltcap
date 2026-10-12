package com.shelf.launcher.data

/**
 * A copy of the details with the facts a view doesn't show left out (Settings › Appearance › Category screen
 * › Game details per view). [facts] holds names of [Features.GameFact]. Scores and playtimes stay.
 */
fun GameMetadata?.shownIn(facts: Set<String>): GameMetadata? = this?.copy(
    developer = developer.takeIf { Features.GameFact.DEVELOPER.name in facts },
    genres = if (Features.GameFact.GENRE.name in facts) genres else emptyList(),
    releaseYear = releaseYear.takeIf { Features.GameFact.RELEASED.name in facts },
    publisher = publisher.takeIf { Features.GameFact.PUBLISHER.name in facts },
    summary = summary.takeIf { Features.GameFact.DESCRIPTION.name in facts }
)
