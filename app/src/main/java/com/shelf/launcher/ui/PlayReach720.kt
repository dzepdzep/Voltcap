package com.shelf.launcher.ui

/*
 * 720a (owner 23 Nov 22:43, Task 1 item 3): a safe QA path for the real Edit Home D-pad movement of the Voltcap row's added
 * Play part (715a). Two QA-only outputs, both written only in the isolated QA app / with the hooks file (QaTrace / TestHooks):
 *  - voltcap_qa_edit: "EDIT <part being moved>" while Edit Home is open, "HOME" otherwise - tools/qa/device/play720.sh presses
 *    a D-pad key in Edit Home only after reading "EDIT CLASSIC:h_play" there, so no real key can land on the home (or a game).
 *  - `--ez voltcap_test_reach true`: one `reach ...` line - where the added Play part is saved / being moved / drawn, what the
 *    D-pad decides from that (prepareReach: Play above or below the games) and which zone the D-pad is on now.
 * Nothing here changes how Voltcap behaves.
 */
internal object PlayReach720 {
    /** The Voltcap row's added "Play / Continue" part (715a: what ▲ / ▼ reach with the game header off). */
    val playId: String get() = HomeWidgets.overlayId(com.shelf.launcher.data.HomeLayout.CLASSIC, HomeWidgets.H_PLAY)

    /** voltcap_qa_edit's content. */
    fun editMark(active: Boolean, selected: String): String = if (active) "EDIT $selected" else "HOME"

    private fun y(v: Float?): String = v?.let { "%.3f".format(java.util.Locale.US, it) } ?: "none"

    private fun zones(z: List<HomeReach.Zone>): String = z.joinToString(",") { it.name }.ifEmpty { "-" }

    /**
     * `reach <layout> dock=<place> header=on|off added=yes|no saved=<y> live=<y> drawn=<y> above=<true|false> up=<zones>
     * down=<zones> buttons=<n> zone=<zone|none> edit=on|off` - y fractions (0 = top), `none` when there is none.
     */
    fun line(
        layout: String, dock: String, headerOn: Boolean, added: Boolean, savedY: Float?, liveY: Float?, drawnY: Float?,
        up: List<HomeReach.Zone>, down: List<HomeReach.Zone>, buttons: Int, zone: HomeReach.Zone?, edit: Boolean
    ): String =
        "$layout dock=$dock header=${if (headerOn) "on" else "off"} added=${if (added) "yes" else "no"} saved=${y(savedY)} " +
            "live=${y(liveY)} drawn=${y(drawnY)} above=${HomeReach.Zone.HEADER in up} up=${zones(up)} down=${zones(down)} " +
            "buttons=$buttons zone=${zone?.name ?: "none"} edit=${if (edit) "on" else "off"}"
}
