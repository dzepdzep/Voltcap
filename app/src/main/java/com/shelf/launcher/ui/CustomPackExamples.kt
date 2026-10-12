package com.shelf.launcher.ui

/**
 * The two example packs shown in the tutorial (under the round "?" in Settings › Theme › Your own
 * themes & visualizers). They are real packs: "Add the examples" saves them, and they pass the same
 * checks as anything you import, so they're a safe place to start from.
 */
internal object CustomPackExamples {
    const val THEME = """// voltpack: 1
// name: Tide lines
// kind: theme
// author: Voltcap
// Soft bands drifting across the screen in the theme's own colours. Copy it and change it.

float3 voltScene(float2 uv, float asp, float t) {
    // uv: x runs 0..asp, y runs 0..1 from the top. t: seconds.
    float3 col = mix(uBg.rgb, uSurf.rgb, uv.y);
    for (int i = 0; i < 5; i++) {
        float k = float(i);
        float y = 0.35 + 0.1 * k + 0.05 * sin(uv.x * (3.0 + k) + t * (0.3 + 0.1 * k));
        float band = glow(abs(uv.y - y), 0.012);
        col += mix(uAcc.rgb, uSec.rgb, k / 4.0) * band * 0.18;
    }
    col += uAcc.rgb * dots(uv, 8.0, float2(t * 0.05, 0.0), 0.5, 0.08, 3.0) * 0.4;
    return col;
}
"""

    const val VISUALIZER = """// voltpack: 1
// name: Soft bars
// kind: visualizer
// author: Voltcap
// 24 bars that follow the music. eqAt(0.0 .. 1.0) is the spectrum: low notes first, high notes last.

float3 voltViz(float2 uv, float asp, float t) {
    float3 col = paletteField(uv, asp, t, uBass, uBg.rgb, uAcc.rgb, uSec.rgb);
    float rm = room(0.11);                      // the height this visualizer may use
    float base = uWaveY + rm * 0.8;             // the line the bars stand on
    float x = (uv.x / asp - 0.08) / 0.84;
    float inRange = step(0.0, x) * step(x, 1.0);
    float id = floor(x * 24.0);
    float fx = fract(x * 24.0);
    float e = eqAt((id + 0.5) / 24.0) * uPlaying;
    float h = 0.01 + rm * 1.6 * e * (0.5 + 0.5 * uIntensity);
    float up = base - uv.y;
    float bar = step(0.15, fx) * step(fx, 0.85) * inRange;
    float lit = bar * step(0.0, up) * step(up, h);
    col = mix(col, mix(uAcc.rgb, uSec.rgb, id / 24.0), lit * 0.9);
    col += uAcc.rgb * exp(-abs(up) * 400.0) * inRange * (0.3 + exp(-uBeatAge * 5.0) * uPlaying);
    return col;
}
"""
}
