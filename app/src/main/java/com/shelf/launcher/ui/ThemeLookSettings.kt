package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Features.BlurArea
import com.shelf.launcher.data.Features.ColorSource
import com.shelf.launcher.ui.theme.AppTheme
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Settings › Appearance › Theme › Theme look: where this theme's colours come from (one clear choice
 * instead of the old accent / whole-app switches), blur and dim, and the scene's motion. Every theme
 * keeps its own look; changes show on the home screen behind Settings right away (live preview), and
 * Reset This Theme puts only this theme back to its original look.
 */
@Composable
internal fun ThemeLookCard(theme: AppTheme) {
    val look = Features.lookFor(theme.name)
    fun set(l: Features.ThemeLook) = Features.setLook(theme.name, l)
    ShelfCard {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { SectionLabel("Theme look · ${theme.label}") }
            VoltHelpButton(VoltTopic.THEME_COLORS)
        }
        MutedNote("Each theme keeps its own look. Changes show on the home screen right away.")
        Spacer(Modifier.height(6.dp))
        // One switch for every place that takes colours from artwork (all themes, not per theme): the game's
        // art or the album cover. It used to depend on the theme, so colours seemed to jump between the two.
        val artSrc = Features.artColorSource.state.value
        FoldGroup("Artwork colours from", summary = artSrc.label, key = "look_art_source") {
            ChipFlow {
                Features.ArtColorSource.values().forEach { src ->
                    Chip(src.label, accent = artSrc == src) { Features.artColorSource.value = src }
                }
            }
            Spacer(Modifier.height(4.dp))
            MutedNote(
                when (artSrc) {
                    Features.ArtColorSource.AUTO -> "Music themes take the album cover's colours; \"Current game\" takes the game's."
                    Features.ArtColorSource.GAME -> "Always the game you're on — music themes too."
                    Features.ArtColorSource.ALBUM -> "The song playing whenever music plays (also for \"Current game\"); the game's when nothing plays."
                }
            )
        }
        FoldGroup("Colours", summary = if (theme.recolorable) look.colorSource.label + if (theme.mostlyOwnColors) " · mostly its own" else "" else "Original colours", key = "look_colours", startOpen = true) {
            if (!theme.recolorable) {
                MutedNote(
                    when {
                        theme.motif.reactive -> "⊘ This theme keeps its original colours: it takes them from the album cover, so the colour source can't change it."
                        theme.motif == com.shelf.launcher.ui.theme.ThemeMotif.GAMING_ROOM -> "⊘ This theme is a painted room with the game's art on its monitor, so the colour source can't change it."
                        theme.motif.showcase -> "⊘ This theme keeps its original colours: it takes them from the game's own art, so the colour source can't change it."
                        else -> "⊘ This theme is a picture with its own colours, so the colour source can't change it."
                    }
                )
            } else {
                if (theme.mostlyOwnColors) {
                    MutedNote("◐ This scene paints mostly in its own fixed colours (sky, grass, stone…): the colours below only tint its lights and accents.")
                    Spacer(Modifier.height(6.dp))
                }
                ChipFlow {
                    ColorSource.values().forEach { src ->
                        Chip(src.label, accent = look.colorSource == src) { set(look.copy(colorSource = src)) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                MutedNote(look.colorSource.blurb)
                if (look.colorSource == ColorSource.CUSTOM) {
                    Spacer(Modifier.height(10.dp))
                    Text("Accent", color = ShelfTheme.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    val accentNow = theme.colors.accent.toArgb()
                    ColorChoices(ACCENT_CHOICES, look.accent) { set(look.copy(accent = it)) }
                    Spacer(Modifier.height(8.dp))
                    HueSlider(look.accent ?: accentNow) { picked ->
                        val own = look.accent != null
                        set(look.copy(accent = withHueOf(picked, accentNow, minSat = if (own) 0f else 0.45f, minVal = if (own) 0f else 0.75f)))
                    }
                    Spacer(Modifier.height(8.dp))
                    ColorFineTune("Accent", look.accent ?: accentNow) { set(look.copy(accent = it)) }
                    Spacer(Modifier.height(12.dp))
                    Text("Background", color = ShelfTheme.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    val bgNow = theme.colors.background.toArgb()
                    ColorChoices(BACKGROUND_CHOICES, look.background) { set(look.copy(background = it)) }
                    Spacer(Modifier.height(8.dp))
                    HueSlider(look.background ?: bgNow) { picked ->
                        val own = look.background != null
                        set(look.copy(background = withHueOf(picked, bgNow, minSat = if (own) 0f else 0.3f, minVal = 0f)))
                    }
                    Spacer(Modifier.height(8.dp))
                    ColorFineTune("Background", look.background ?: bgNow) { set(look.copy(background = it)) }
                    Spacer(Modifier.height(12.dp))
                    PercentSlider("Saturation (100 = the theme's)", look.saturation, { set(look.copy(saturation = it)) }, min = 0, max = 200, live = true)
                    PercentSlider("Brightness (100 = the theme's)", look.brightness, { set(look.copy(brightness = it)) }, min = 50, max = 150, live = true)
                }
            }
        }
        FoldGroup("Blur & dim", summary = "Blur ${look.blur}% · Dim ${look.dim}% · ${look.blurArea.label}", key = "look_blur") {
            ChipFlow {
                BlurArea.values().forEach { a ->
                    Chip(a.label, accent = look.blurArea == a) { set(look.copy(blurArea = a)) }
                }
            }
            MutedNote(
                if (look.blurArea == BlurArea.GAME_UI) "Only the band behind the games, their names and the categories; the rest of the theme stays as it is."
                else "The whole background behind the home screen."
            )
            Spacer(Modifier.height(8.dp))
            PercentSlider("Blur strength", look.blur, { set(look.copy(blur = it)) }, live = true)
            PercentSlider("Dim / overlay strength", look.dim, { set(look.copy(dim = it)) }, live = true)
            if (android.os.Build.VERSION.SDK_INT < 31 && look.blur > 0) MutedNote("Android 11 and older can't blur live: a frosted wash stands in.")
        }
        if (!theme.motif.showcase) FoldGroup("Scene motion", summary = "Speed ${look.sceneSpeed}% · Particles ${look.sceneParticles}%", key = "look_motion") {
            if (theme.motif.still) MutedNote("A still picture: it doesn't move, so speed and particles don't apply.")
            Text("Speed", color = ShelfTheme.textMuted, fontSize = 12.sp)
            ChipFlow {
                listOf(50 to "Slow", 75 to "Calm", 100 to "Normal", 150 to "Lively", 200 to "Fast").forEach { (v, l) ->
                    Chip(l, accent = look.sceneSpeed == v) { set(look.copy(sceneSpeed = v)) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Particles (stars, motes, sparks)", color = ShelfTheme.textMuted, fontSize = 12.sp)
            ChipFlow {
                listOf(0 to "None", 50 to "Fewer", 100 to "Normal", 160 to "More", 200 to "Lots").forEach { (v, l) ->
                    Chip(l, accent = look.sceneParticles == v) { set(look.copy(sceneParticles = v)) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Colour shift", color = ShelfTheme.textMuted, fontSize = 12.sp)
            ChipFlow {
                listOf(0 to "None", -60 to "−60°", 60 to "+60°", -120 to "−120°", 120 to "+120°", 180 to "Opposite").forEach { (v, l) ->
                    Chip(l, accent = look.sceneHue == v) { set(look.copy(sceneHue = v)) }
                }
            }
        }
        // Scenes built round one thing in the middle (Voltcap, Trophy vault, Specimen lab, the CRT room...): how high it sits.
        // They already move down by themselves when the game row is at the top; this adds to that.
        if (theme.motif == com.shelf.launcher.ui.theme.ThemeMotif.SHOWCASE_CRT || SceneArt.usesShift(theme.motif)) {
            FoldGroup("Scene position", summary = shiftLabel(look.sceneShift), key = "look_shift") {
                ChipFlow {
                    listOf(-10 to "Higher", 0 to "As designed", 8 to "A bit lower", 15 to "Lower", 25 to "Much lower").forEach { (v, l) ->
                        Chip(l, accent = look.sceneShift == v) { set(look.copy(sceneShift = v)) }
                    }
                }
                MutedNote("Moves the scene's centrepiece up or down. With the game row at the top it already moves down on its own, clear of the games; this comes on top.")
            }
        }
        // The console-era music scenes: the album's cover, or the game you're on (its hero art behind, its cover on the screen / label).
        if (theme.motif in SceneArt.ERA_MOTIFS) {
            val era = Features.eraArt.state.value
            FoldGroup("Picture in the scene", summary = era.label, key = "look_era_art") {
                ChipFlow {
                    Features.EraArt.values().forEach { e ->
                        Chip(e.label, accent = era == e) { Features.eraArt.value = e }
                    }
                }
                MutedNote("The game's art: its hero picture blurred behind, its cover on the screen, cartridge or banner. All console-era themes share this.")
            }
        }
        FoldGroup("Intensity (all themes)", summary = Features.themeIntensity.state.value.label, key = "look_intensity") {
            ChipFlow {
                Features.Intensity.values().forEach { k ->
                    Chip(k.label, accent = Features.themeIntensity.state.value == k) { Features.themeIntensity.value = k }
                }
            }
            MutedNote("How strongly every theme moves: Low is calmer and doubles as a performance mode (fewer particles, slower motion, at most Low background quality); High is livelier. Each theme's own speed and particles come on top.")
        }
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip("Reset This Theme", accent = false, enabled = Features.isLookChanged(theme.name)) { Features.resetLook(theme.name) }
        }
        MutedNote("Puts only ${theme.label}'s colours, blur, dim and motion back to the original. Nothing else changes.")
    }
}

/** Settings › Appearance › Theme › Favorite theme rotation. */
@Composable
internal fun ThemeRotationCard() {
    val mode = Features.themeRotation.state.value
    val favs = Features.favoriteThemes.state.value
    ShelfCard {
        FoldGroup("Favorite theme rotation", summary = mode.label, key = "theme_rotation") {
            MutedNote("Switches to a random favorite theme (★ in the browser above). ${favs.size} favorite${if (favs.size == 1) "" else "s"} so far.")
            Spacer(Modifier.height(6.dp))
            Features.ThemeRotation.values().forEach { r ->
                RadioLine(r.label, mode == r) { Features.themeRotation.value = r }
            }
            if (mode == Features.ThemeRotation.TIMED) {
                Spacer(Modifier.height(6.dp))
                Text("Every", color = ShelfTheme.textMuted, fontSize = 12.sp)
                ChipFlow {
                    listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 180 to "3 hours", 720 to "12 hours", 1440 to "Day").forEach { (v, l) ->
                        Chip(l, accent = Features.themeRotationMinutes.state.value == v) { Features.themeRotationMinutes.value = v }
                    }
                }
            }
            if (mode == Features.ThemeRotation.LAUNCHES) {
                Spacer(Modifier.height(6.dp))
                Text("After this many game launches", color = ShelfTheme.textMuted, fontSize = 12.sp)
                ChipFlow {
                    listOf(1, 2, 3, 5, 10).forEach { v ->
                        Chip("$v", accent = Features.themeRotationLaunches.state.value == v) { Features.themeRotationLaunches.value = v }
                    }
                }
            }
            if (mode != Features.ThemeRotation.OFF) {
                Spacer(Modifier.height(6.dp))
                Text("Only these kinds", color = ShelfTheme.textMuted, fontSize = 12.sp)
                val kinds = Features.themeRotationKinds.state.value
                ChipFlow {
                    Features.ThemeKind.values().forEach { k ->
                        Chip(k.label, accent = k.name in kinds) { Features.themeRotationKinds.toggle(k.name) }
                    }
                }
                if (favs.size < 2) MutedNote("Mark at least two favorites for rotation to have something to switch between.")
            }
        }
    }
}

private fun shiftLabel(v: Int): String = when {
    v == 0 -> "As designed"
    v < 0 -> "Higher"
    v <= 8 -> "A bit lower"
    v <= 15 -> "Lower"
    else -> "Much lower"
}
