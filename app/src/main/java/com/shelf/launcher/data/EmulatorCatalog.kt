package com.shelf.launcher.data

import android.content.Intent

/*
 * GENERATED from Daijishou's open platform database (github.com/TapiocaFox/Daijishou, platforms/ folder, one JSON per console),
 * which lists tested `am start` launch commands for Android emulators. It widens what Voltcap recognises:
 * every standalone emulator below becomes a picker suggestion and gets a launch profile when Voltcap has
 * no hand-written one, and the RetroArch core lists fill in consoles CoreRegistry doesn't cover.
 * Hand-written entries in LaunchProfiles / CoreRegistry always win.
 */
internal object EmulatorCatalog {
    /** Update 326: Eden Duo's package (its duo.gradle: applicationId 'dev.igawa6.edenduo'; '.dev' for its dev builds). */
    const val EDEN_DUO = "dev.igawa6.edenduo"

    class Entry(
        val name: String, val packageName: String, val activity: String, val action: String,
        val romExtra: String?, val format: RomFormat, val asData: Boolean, val boolExtras: Map<String, Boolean>
    )

    val standalone: Map<String, List<Entry>> = mapOf(
        "3DS" to listOf(
            Entry("azahar", "io.github.lime3ds.android", "org.citra.citra_emu.activities.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("azahar (vanilla)", "org.azahar_emu.azahar", "org.citra.citra_emu.activities.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("azahar plus", "io.github.azaharplus.android", "org.citra.citra_emu.activities.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Borked 3DS", "io.github.borked3ds.android", "io.github.borked3ds.android.activities.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Citra Nightly", "org.citra.citra_emu", "org.citra.citra_emu.activities.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Citra Canary", "org.citra.citra_emu.canary", "org.citra.citra_emu.activities.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Lemonade Alpha", "org.gamerytb.lemonade.canary", "org.citra.citra_emu.activities.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Mandarine", "io.github.mandarine3ds.mandarine", "io.github.mandarine3ds.mandarine.activities.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("org.citra.emu - antutu", "com.antutu.ABenchMark", "org.citra.emu.ui.EmulationActivity", "android.intent.action.VIEW", "GamePath", RomFormat.FILE_PATH, false, mapOf()),
            Entry("org.citra.emu", "org.citra.emu", "org.citra.emu.ui.EmulationActivity", "android.intent.action.VIEW", "GamePath", RomFormat.FILE_PATH, false, mapOf()),
        ),
        "Arcade" to listOf(
            Entry("com.seleuco.mame4droid", "com.seleuco.mame4droid", "com.seleuco.mame4droid.MAME4droid", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.seleuco.mame4droid 2024", "com.seleuco.mame4d2024", "com.seleuco.mame4droid.MAME4droid", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("NEO.EMU", "com.explusalpha.neoemu", "com.imagine.BaseActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "Atari2600" to listOf(
            Entry("com.explusalpha.A2600Emu", "com.explusalpha.A2600Emu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.androidemu.atari", "com.androidemu.atari", "com.androidemu.atari.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "Atomiswave" to listOf(
            Entry("io.recompiled.redream", "io.recompiled.redream", "io.recompiled.redream.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.reicast.emulator", "com.reicast.emulator", "com.reicast.emulator.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.flycast.emulator", "com.flycast.emulator", "com.flycast.emulator.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "C64" to listOf(
            Entry("com.explusalpha.C64Emu", "com.explusalpha.C64Emu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "Dreamcast" to listOf(
            Entry("Redream", "io.recompiled.redream", "io.recompiled.redream.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Reicast", "com.reicast.emulator", "com.reicast.emulator.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Flycast", "com.flycast.emulator", "com.flycast.emulator.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("EmuCoreH", "com.sbro.emucoreh", "com.sbro.emucoreh.MainActivity", "com.sbro.emucoreh.extra.GAME_PATH", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "GB" to listOf(
            Entry("Pizza Boy C Basic", "it.dbtecno.pizzaboy", "it.dbtecno.pizzaboy.MainActivity", "android.intent.action.MAIN", "rom_uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Pizza Boy C Pro", "it.dbtecno.pizzaboypro", "it.dbtecno.pizzaboypro.MainActivity", "android.intent.action.MAIN", "rom_uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("SkyEmu", "com.sky.SkyEmu", "com.sky.SkyEmu.EnhancedNativeActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.explusalpha.GbcEmu", "com.explusalpha.GbcEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.fastemulator.gbcfree", "com.fastemulator.gbcfree", "com.fastemulator.gbcfree.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.fastemulator.gbc", "com.fastemulator.gbc", "com.fastemulator.gbc.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Linkboy", "com.pixelrespawn.linkboy", "com.pixelrespawn.linkboy.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "GBA" to listOf(
            Entry("Pizza Boy GBA Basic", "it.dbtecno.pizzaboygba", "it.dbtecno.pizzaboygba.MainActivity", "android.intent.action.MAIN", "rom_uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Pizza Boy A Pro", "it.dbtecno.pizzaboygbapro", "it.dbtecno.pizzaboygbapro.MainActivity", "android.intent.action.MAIN", "rom_uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("SkyEmu", "com.sky.SkyEmu", "com.sky.SkyEmu.EnhancedNativeActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.explusalpha.GbaEmu", "com.explusalpha.GbaEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.fastemulator.gbafree", "com.fastemulator.gbafree", "com.fastemulator.gba.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.fastemulator.gba", "com.fastemulator.gba", "com.fastemulator.gba.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Linkboy", "com.pixelrespawn.linkboy", "com.pixelrespawn.linkboy.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "GBC" to listOf(
            Entry("Pizza Boy C Basic", "it.dbtecno.pizzaboy", "it.dbtecno.pizzaboy.MainActivity", "android.intent.action.MAIN", "rom_uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Pizza Boy C Pro", "it.dbtecno.pizzaboypro", "it.dbtecno.pizzaboypro.MainActivity", "android.intent.action.MAIN", "rom_uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("SkyEmu", "com.sky.SkyEmu", "com.sky.SkyEmu.EnhancedNativeActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.explusalpha.GbcEmu", "com.explusalpha.GbcEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.fastemulator.gbcfree", "com.fastemulator.gbcfree", "com.fastemulator.gbcfree.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.fastemulator.gbc", "com.fastemulator.gbc", "com.fastemulator.gbc.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Linkboy", "com.pixelrespawn.linkboy", "com.pixelrespawn.linkboy.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "GameCube" to listOf(
            Entry("Dolphin (Official)", "org.dolphinemu.dolphinemu", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.MAIN", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Dolphin handheld", "org.dolphinemu.handheld", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Dolphin org.mm.jr fork", "org.mm.jr", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.FILE_PATH, false, mapOf()),
            Entry("Dolphin org.mm.j fork", "org.mm.j", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.FILE_PATH, false, mapOf()),
            Entry("Dolphin mmjr", "org.dolphinemu.mmjr", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Dolphin mmjr3", "org.dolphinemu.mmjr3", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Dolphin Ishiiruka", "org.dolphin.ishiirukadark", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.FILE_PATH, false, mapOf()),
            Entry("Dolphin (Primehack)", "org.dolphinemu.primehack", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.FILE_PATH, false, mapOf()),
            Entry("Dolphin (Primehack - Shiiion)", "org.shiiion.primehack", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.FILE_PATH, false, mapOf()),
            Entry("Dolphin (Debug Build)", "org.dolphinemu.dolphinemu.debug", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.MAIN", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("DolphinCS", "com.joeyos.dolphinemu", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.MAIN", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
        ),
        "GameGear" to listOf(
            Entry("com.androidemu.gg", "com.androidemu.gg", "com.androidemu.gg.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("MasterGear", "com.fms.mg", "com.fms.emulib.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Pizza Boy SC Pro", "it.dbtecno.pizzaboyscpro", "it.dbtecno.pizzaboyscpro.MainActivity", "android.intent.action.MAIN", "rom_uri", RomFormat.SHARED_URI, false, mapOf()),
        ),
        "Genesis" to listOf(
            Entry("com.explusalpha.MdEmu", "com.explusalpha.MdEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.androidemu.gens", "com.androidemu.gens", "com.androidemu.gens.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Pizza Boy SC Pro", "it.dbtecno.pizzaboyscpro", "it.dbtecno.pizzaboyscpro.MainActivity", "android.intent.action.MAIN", "rom_uri", RomFormat.SHARED_URI, false, mapOf()),
        ),
        "Jaguar" to listOf(
            Entry("IrataJaguar", "ru.vastness.altmer.iratajaguar", "ru.vastness.altmer.iratajaguar.MainActivity", "android.intent.action.MAIN", "rom", RomFormat.FILE_PATH, false, mapOf()),
        ),
        "Lynx" to listOf(
            Entry("com.explusalpha.LynxEmu", "com.explusalpha.LynxEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "MSX" to listOf(
            Entry("com.explusalpha.MsxEmu", "com.explusalpha.MsxEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "MasterSystem" to listOf(
            Entry("com.explusalpha.MdEmu", "com.explusalpha.MdEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Pizza Boy SC Pro", "it.dbtecno.pizzaboyscpro", "it.dbtecno.pizzaboyscpro.MainActivity", "android.intent.action.MAIN", "rom_uri", RomFormat.SHARED_URI, false, mapOf()),
        ),
        "N64" to listOf(
            Entry("org.mupen64plusae.v3.fzurita", "org.mupen64plusae.v3.fzurita", "paulscode.android.mupen64plusae.SplashActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("org.mupen64plusae.v3.alpha", "org.mupen64plusae.v3.alpha", "paulscode.android.mupen64plusae.SplashActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita.pro", "paulscode.android.mupen64plusae.SplashActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("paulscode.android.mupen64plusae", "paulscode.android.mupen64plusae", "paulscode.android.mupen64plusae.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "NDS" to listOf(
            Entry("MelonDS", "me.magnum.melonds", "me.magnum.melonds.ui.emulator.EmulatorActivity", "me.magnum.melonds.LAUNCH_ROM", "uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Drastic", "com.dsemu.drastic", "com.dsemu.drastic.DraSticActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("MelonDS Nightly", "me.magnum.melonds.nightly", "me.magnum.melonds.ui.emulator.EmulatorActivity", "me.magnum.melonds.nightly.LAUNCH_ROM", "uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("MelonDS Dev (Dual Screen Fork)", "me.magnum.melonds.dev", "me.magnum.melonds.ui.emulator.EmulatorActivity", "me.magnum.melonds.dev.LAUNCH_ROM", "uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("MelonDualDS", "me.magnum.melondualds", "me.magnum.melonds.ui.emulator.EmulatorActivity", "me.magnum.melonds.dev.LAUNCH_ROM", "uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("NooDS", "com.hydra.noods", "com.hydra.noods.FileBrowser", "android.intent.action.MAIN", "LaunchPath", RomFormat.FILE_PATH, false, mapOf()),
            Entry("SkyEmu", "com.sky.SkyEmu", "com.sky.SkyEmu.EnhancedNativeActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "NES" to listOf(
            Entry("com.explusalpha.NesEmu", "com.explusalpha.NesEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("com.androidemu.nes", "com.androidemu.nes", "com.androidemu.nes.EmulatorActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "Naomi" to listOf(
            Entry("com.flycast.emulator", "com.flycast.emulator", "com.flycast.emulator.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "NeoGeo" to listOf(
            Entry("NEO.EMU", "com.explusalpha.NeoEmu", "com.imagine.BaseActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "NeoGeoPocket" to listOf(
            Entry("NGP.EMU", "com.explusalpha.NgpEmu", "com.imagine.BaseActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "PCEngine" to listOf(
            Entry("com.PceEmu", "com.PceEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "PCEngineCD" to listOf(
            Entry("com.PceEmu", "com.PceEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "PICO8" to listOf(
            Entry("PICO-8 Android", "io.wip.pico8", "com.godot.game.GodotAppLauncher", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("PicPic", "com.sa_moo_rai.picpic", "com.sa_moo_rai.picpic.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Infinity P8 Player", "me.dt2dev.infinity", "me.dt2dev.infinity.SchemeActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "PS1" to listOf(
            Entry("Duckstation", "com.github.stenzek.duckstation", "com.github.stenzek.duckstation.EmulationActivity", "android.intent.action.MAIN", "bootPath", RomFormat.SHARED_URI, false, mapOf()),
            Entry("ARMSX1", "com.nanodata.armsx", "com.armsx2.Main", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("EmuCoreR", "com.sbro.emucorer", "com.sbro.emucorer.MainActivity", "com.sbro.emucorer.action.LAUNCH_GAME", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("FPse", "com.emulator.fpse", "com.emulator.fpse.Main", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("FPse64", "com.emulator.fpse64", "com.emulator.fpse64.Main", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("ePSXe", "com.epsxe.ePSXe", "com.epsxe.ePSXe.ePSXe", "android.intent.action.MAIN", "com.epsxe.ePSXe.isoName", RomFormat.FILE_PATH, false, mapOf()),
        ),
        "PS2" to listOf(
            Entry("Aethersx2", "xyz.aethersx2.android", "xyz.aethersx2.android.EmulationActivity", "android.intent.action.MAIN", "bootPath", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Aethersx2 Turnip", "xyz.aethersx2.custom", "xyz.aethersx2.custom.EmulationActivity", "android.intent.action.MAIN", "bootPath", RomFormat.SHARED_URI, false, mapOf()),
            Entry("NetherSX2 Turnip", "xyz.aethersx2.tturnip", "xyz.aethersx2.android.EmulationActivity", "android.intent.action.MAIN", "bootPath", RomFormat.SHARED_URI, false, mapOf()),
            Entry("NetherSX2 Turnip (Classic)", "xyz.aethersx2.cturnip", "xyz.aethersx2.android.EmulationActivity", "android.intent.action.MAIN", "bootPath", RomFormat.SHARED_URI, false, mapOf()),
            Entry("ARMSX2 (Google Play)", "come.nanodata.armsx2", "com.armsx2.Main", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("ARMSX2 (Nightly)", "come.nanodata.armsx2.debug", "kr.co.iefriends.pcsx2.activities.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("ARMSX2 (Refresh/GitHub)", "com.armsx2", "com.armsx2.Main", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("EmuCoreX", "com.sbro.emucorex", "com.sbro.emucorex.MainActivity", "com.sbro.emucorex.action.LAUNCH_GAME", null, RomFormat.SHARED_URI, false, mapOf()),
        ),
        "PS3" to listOf(
            Entry("aPS3e", "aenu.aps3e", "aenu.aps3e.EmulatorActivity", "aenu.intent.action.APS3E", "iso_uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("ARMSX3", "com.armsx3", "com.armsx2.Main", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("EmuCoreC", "com.sbro.emucorec", "com.sbro.emucorec.core.ps3.Emulator", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "PSP" to listOf(
            Entry("PPSSPP", "org.ppsspp.ppsspp", "org.ppsspp.ppsspp.PpssppActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("PPSSPP Gold", "org.ppsspp.ppssppgold", "org.ppsspp.ppsspp.PpssppActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("PPSSPP Legacy", "org.ppsspp.ppsspplegacy", "org.ppsspp.ppsspp.PpssppActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("EmuCoreA", "com.sbro.emucorea", "com.sbro.emucorea.MainActivity", "com.sbro.emucorea.extra.GAME_PATH", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "PSVita" to listOf(
            Entry("vita3k", "org.vita3k.emulator", "org.vita3k.emulator.Emulator", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, false, mapOf()),
            Entry("vita3k zx", "org.vita3k.emulator.ikhoeyZX", "org.vita3k.emulator.Emulator", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, false, mapOf()),
            Entry("vita3k+", "org.vita3kplus.emulator", "org.vita3k.emulator.Emulator", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, false, mapOf()),
            Entry("EmuCoreV", "com.sbro.emucorev", "com.sbro.emucorev.core.vita.Emulator", "com.sbro.emucorev.action.LAUNCH", null, RomFormat.SHARED_URI, false, mapOf()),
        ),
        "SNES" to listOf(
            Entry("com.explusalpha.Snes9xPlus", "com.explusalpha.Snes9xPlus", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "Saturn" to listOf(
            Entry("YabaSanshiro 2 Pro", "org.devmiyax.yabasanshioro2.pro", "org.uoyabause.android.Yabause", "android.intent.action.MAIN", "org.uoyabause.android.FileNameUri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("YabaSanshiro 2", "org.devmiyax.yabasanshioro2", "org.uoyabause.android.Yabause", "android.intent.action.VIEW", "org.uoyabause.android.FileNameUri", RomFormat.FILE_PATH, false, mapOf()),
            Entry("YabaSanshiro Pro", "org.uoyabause.android.pro", "org.uoyabause.android.Yabause", "android.intent.action.VIEW", "org.uoyabause.android.FileNameEx", RomFormat.FILE_PATH, false, mapOf()),
            Entry("YabaSanshiro", "org.uoyabause.android", "org.uoyabause.android.Yabause", "android.intent.action.VIEW", "org.uoyabause.android.FileNameEx", RomFormat.FILE_PATH, false, mapOf()),
            Entry("Saturn.EMU", "com.explusalpha.SaturnEmu", "com.imagine.BaseActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "ScummVM" to listOf(
            Entry("scummvm", "org.scummvm.scummvm", "org.scummvm.scummvm.SplashActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, false, mapOf()),
            Entry("scummvm debug build", "org.scummvm.scummvm.debug", "org.scummvm.scummvm.SplashActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, false, mapOf()),
        ),
        "SegaCD" to listOf(
            Entry("com.explusalpha.MdEmu", "com.explusalpha.MdEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "Switch" to listOf(
            Entry("Skyline", "skyline.emu", "emu.skyline.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Strato (Official)", "org.stratoemu.strato", "org.stratoemu.strato.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("eden", "dev.eden.eden_emulator", "org.yuzu.yuzu_emu.activities.EmulationActivity", "android.nfc.action.TECH_DISCOVERED", null, RomFormat.SHARED_URI, true, mapOf()),
            // Update 326 (not from Daijishou): Eden Duo, the dual-screen Eden fork (github.com/igawa6/eden-duo). Same
            // EmulationActivity and intent filters as Eden (checked in its AndroidManifest / EmulationFragment: a content
            // URI with a .nsp / .xci / … name on the intent's data starts the game); its companion runs on the second screen.
            Entry("Eden Duo", EDEN_DUO, "org.yuzu.yuzu_emu.activities.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Eden Duo (Dev)", EDEN_DUO + ".dev", "org.yuzu.yuzu_emu.activities.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("eden (Optimized)", "com.miHoYo.Yuanshen", "org.yuzu.yuzu_emu.activities.EmulationActivity", "android.nfc.action.TECH_DISCOVERED", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("eden (Legacy)", "dev.legacy.eden_emulator", "org.yuzu.yuzu_emu.activities.EmulationActivity", "android.nfc.action.TECH_DISCOVERED", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("eden (Nightly)", "dev.eden.eden_nightly", "org.yuzu.yuzu_emu.activities.EmulationActivity", "android.nfc.action.TECH_DISCOVERED", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("eden (Nightly New)", "dev.eden.eden_emulator.nightly", "org.yuzu.yuzu_emu.activities.EmulationActivity", "android.nfc.action.TECH_DISCOVERED", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Yuzu", "org.yuzu.yuzu_emu", "org.yuzu.yuzu_emu.activities.EmulationActivity", "android.nfc.action.TECH_DISCOVERED", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Yuzu (early access)", "org.yuzu.yuzu_emu.ea", "org.yuzu.yuzu_emu.activities.EmulationActivity", "android.nfc.action.TECH_DISCOVERED", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Suyu", "dev.suyu.suyu_emu.relWithDebInfo", "dev.suyu.suyu_emu.activities.EmulationActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Sudachi (Mainline)", "org.sudachi.sudachi_emu", "org.sudachi.sudachi_emu.activities.EmulationActivity", "android.nfc.action.TECH_DISCOVERED", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Sudachi (Early Access)", "org.sudachi.sudachi_emu.ea", "org.sudachi.sudachi_emu.activities.EmulationActivity", "android.nfc.action.TECH_DISCOVERED", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Citron", "org.citron.citron_emu", "org.citron.citron_emu.activities.EmulationActivity", "android.nfc.action.TECH_DISCOVERED", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Kenji-NX", "org.kenjinx.android", "org.kenjinx.android.MainActivity", "org.kenjinx.android.LAUNCH_GAME", "bootPath", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Benji-SC", "org.benjisc.android", "org.kenjinx.android.MainActivity", "org.kenjinx.android.LAUNCH_GAME", "bootPath", RomFormat.SHARED_URI, false, mapOf()),
        ),
        "VirtualBoy" to listOf(
            Entry("vvs", "com.simongellis.vvb", "com.simongellis.vvb.MainActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "Wii" to listOf(
            Entry("Dolphin (Official)", "org.dolphinemu.dolphinemu", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.MAIN", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Dolphin handheld", "org.dolphinemu.handheld", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Dolphin org.mm.jr fork", "org.mm.jr", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.FILE_PATH, false, mapOf()),
            Entry("Dolphin org.mm.j fork", "org.mm.j", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.FILE_PATH, false, mapOf()),
            Entry("Dolphin mmjr", "org.dolphinemu.mmjr", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Dolphin mmjr3", "org.dolphinemu.mmjr3", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Dolphin Ishiiruka", "org.dolphin.ishiirukadark", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.VIEW", "AutoStartFile", RomFormat.FILE_PATH, false, mapOf()),
            Entry("Dolphin (Debug Build)", "org.dolphinemu.dolphinemu.debug", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.MAIN", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Dolphin (Primehack)", "org.dolphinemu.primehack", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.MAIN", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Dolphin (Primehack - Shiiion)", "org.shiiion.primehack", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.MAIN", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
            Entry("DolphinCS", "com.joeyos.dolphinemu", "org.dolphinemu.dolphinemu.ui.main.MainActivity", "android.intent.action.MAIN", "AutoStartFile", RomFormat.SHARED_URI, false, mapOf()),
        ),
        "WiiU" to listOf(
            Entry("CEMU", "info.cemu.cemu", "info.cemu.cemu.emulation.EmulationActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "WonderSwan" to listOf(
            Entry("com.explusalpha.SwanEmu", "com.explusalpha.SwanEmu", "com.imagine.BaseActivity", "android.intent.action.VIEW", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "Xbox" to listOf(
            Entry("X1 Box", "com.izzy2lost.x1box", "com.izzy2lost.x1box.LauncherActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("hakuX", "com.rfandango.haku_x", "com.rfandango.haku_x.LauncherActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Xenra (Gold)", "Ali.Xanite", "Ali.Xanite.LauncherActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Xenra (Green)", "Ali.Xanite.green", "Ali.Xanite.LauncherActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
        "Xbox360" to listOf(
            Entry("ax360e", "aenu.ax360e", "aenu.ax360e.EmulatorActivity", "aenu.intent.action.AX360E", "game_uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("ax360e (Free)", "aenu.ax360e.free", "aenu.ax360e.EmulatorActivity", "aenu.intent.action.AX360E", "game_uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("X360 Mobile (Old)", "emu.x360.mobile", "emu.x360.mobile.X360MobileGameLaunchActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("X360 Mobile (Launch URL)", "emu.x360mobile.com", "emu.x360mobile.com.MainActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, false, mapOf()),
            Entry("xendroid", "xendroid.compose", "xendroid.compose.EmulatorHostActivity", "android.intent.action.MAIN", "game_uri", RomFormat.SHARED_URI, false, mapOf()),
            Entry("Xenra (Gold)", "Ali.Xanite", "Ali.Xanite.LauncherActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
            Entry("Xenra (Green)", "Ali.Xanite.green", "Ali.Xanite.LauncherActivity", "android.intent.action.MAIN", null, RomFormat.SHARED_URI, true, mapOf()),
        ),
    )

    /** RetroArch cores per console (ids as in `<id>_libretro_android.so`). */
    val cores: Map<String, List<LibretroCore>> = mapOf(
        "NES" to listOf(LibretroCore("nestopia", "Nestopia UE"), LibretroCore("mesen", "Mesen"), LibretroCore("fceumm", "FCEUmm"), LibretroCore("quicknes", "QuickNES"), LibretroCore("bnes", "bnes")),
        "SNES" to listOf(LibretroCore("snes9x", "Snes9x"), LibretroCore("snes9x2010", "Snes9x 2010"), LibretroCore("snes9x2005", "Snes9x 2005"), LibretroCore("snes9x2005_plus", "Snes9x 2005 Plus"), LibretroCore("snes9x2002", "Snes9x 2002"), LibretroCore("bsnes", "bsnes"), LibretroCore("bsnes_hd_beta", "bsnes HD"), LibretroCore("bsnes_mercury_performance", "bsnes-mercury Performance"), LibretroCore("bsnes_mercury_balanced", "bsnes-mercury Balanced"), LibretroCore("bsnes_mercury_accuracy", "bsnes-mercury Accuracy"), LibretroCore("mesen-s", "Mesen-S"), LibretroCore("mednafen_supafaust", "Beetle Supafaust")),
        "GB" to listOf(LibretroCore("gambatte", "Gambatte"), LibretroCore("sameboy", "SameBoy"), LibretroCore("gearboy", "Gearboy"), LibretroCore("tgbdual", "TGB Dual"), LibretroCore("mgba", "mGBA")),
        "GBC" to listOf(LibretroCore("gambatte", "Gambatte"), LibretroCore("sameboy", "SameBoy"), LibretroCore("gearboy", "Gearboy"), LibretroCore("tgbdual", "TGB Dual"), LibretroCore("mgba", "mGBA")),
        "GBA" to listOf(LibretroCore("mgba", "mGBA"), LibretroCore("vba_next", "VBA Next"), LibretroCore("vbam", "VBA-M"), LibretroCore("gpsp", "gpSP"), LibretroCore("mednafen_gba", "Beetle GBA"), LibretroCore("meteor", "Meteor")),
        "N64" to listOf(LibretroCore("mupen64plus_next_gles3", "Mupen64Plus-Next GLES3"), LibretroCore("mupen64plus_next_gles2", "Mupen64Plus-Next GLES2"), LibretroCore("parallel_n64", "ParaLLEl N64")),
        "NDS" to listOf(LibretroCore("melonds", "melonDS"), LibretroCore("melondsds", "melonDS DS"), LibretroCore("desmume", "DeSmuME"), LibretroCore("desmume2015", "DeSmuME 2015"), LibretroCore("noods", "NooDS")),
        "3DS" to listOf(LibretroCore("citra", "Citra")),
        "VirtualBoy" to listOf(LibretroCore("mednafen_vb", "Beetle VB")),
        "GameCube" to listOf(LibretroCore("dolphin", "Dolphin")),
        "Wii" to listOf(LibretroCore("dolphin", "Dolphin")),
        "PS1" to listOf(LibretroCore("swanstation", "SwanStation"), LibretroCore("pcsx_rearmed", "PCSX ReARMed"), LibretroCore("mednafen_psx_hw", "Beetle PSX HW"), LibretroCore("mednafen_psx", "Beetle PSX")),
        "PS2" to listOf(LibretroCore("play", "Play!")),
        "PSP" to listOf(LibretroCore("ppsspp", "PPSSPP")),
        "Genesis" to listOf(LibretroCore("genesis_plus_gx", "Genesis Plus GX"), LibretroCore("genesis_plus_gx_wide", "Genesis Plus GX Wide"), LibretroCore("picodrive", "PicoDrive")),
        "MegaDrive" to listOf(LibretroCore("genesis_plus_gx", "Genesis Plus GX"), LibretroCore("genesis_plus_gx_wide", "Genesis Plus GX Wide"), LibretroCore("picodrive", "PicoDrive")),
        "MasterSystem" to listOf(LibretroCore("genesis_plus_gx", "Genesis Plus GX"), LibretroCore("picodrive", "PicoDrive"), LibretroCore("smsplus", "SMS Plus GX"), LibretroCore("gearsystem", "Gearsystem")),
        "GameGear" to listOf(LibretroCore("genesis_plus_gx", "Genesis Plus GX"), LibretroCore("smsplus", "SMS Plus GX"), LibretroCore("gearsystem", "Gearsystem"), LibretroCore("picodrive", "PicoDrive")),
        "SegaCD" to listOf(LibretroCore("genesis_plus_gx", "Genesis Plus GX"), LibretroCore("picodrive", "PicoDrive")),
        "32X" to listOf(LibretroCore("picodrive", "PicoDrive")),
        "Saturn" to listOf(LibretroCore("mednafen_saturn", "Beetle Saturn"), LibretroCore("yabasanshiro", "YabaSanshiro"), LibretroCore("yabause", "Yabause"), LibretroCore("kronos", "Kronos")),
        "Dreamcast" to listOf(LibretroCore("flycast", "Flycast")),
        "Naomi" to listOf(LibretroCore("flycast", "Flycast")),
        "Atomiswave" to listOf(LibretroCore("flycast", "Flycast")),
        "Arcade" to listOf(LibretroCore("fbneo", "FinalBurn Neo"), LibretroCore("mame2003_plus", "MAME 2003-Plus"), LibretroCore("mame2003", "MAME 2003"), LibretroCore("mame2010", "MAME 2010"), LibretroCore("mame2000", "MAME 2000"), LibretroCore("mame", "MAME (current)"), LibretroCore("fbalpha2012", "FB Alpha 2012")),
        "NeoGeo" to listOf(LibretroCore("fbneo", "FinalBurn Neo"), LibretroCore("geolith", "Geolith")),
        "NeoGeoCD" to listOf(LibretroCore("neocd", "NeoCD")),
        "NeoGeoPocket" to listOf(LibretroCore("mednafen_ngp", "Beetle NeoPop"), LibretroCore("race", "RACE")),
        "PCEngine" to listOf(LibretroCore("mednafen_pce_fast", "Beetle PCE Fast"), LibretroCore("mednafen_pce", "Beetle PCE"), LibretroCore("mednafen_supergrafx", "Beetle SuperGrafx"), LibretroCore("geargrafx", "Geargrafx")),
        "PCEngineCD" to listOf(LibretroCore("mednafen_pce_fast", "Beetle PCE Fast"), LibretroCore("mednafen_pce", "Beetle PCE"), LibretroCore("geargrafx", "Geargrafx")),
        "WonderSwan" to listOf(LibretroCore("mednafen_wswan", "Beetle WonderSwan")),
        "Atari2600" to listOf(LibretroCore("stella", "Stella"), LibretroCore("stella2014", "Stella 2014")),
        "Atari7800" to listOf(LibretroCore("prosystem", "ProSystem")),
        "Lynx" to listOf(LibretroCore("handy", "Handy"), LibretroCore("mednafen_lynx", "Beetle Lynx")),
        "Jaguar" to listOf(LibretroCore("virtualjaguar", "Virtual Jaguar")),
        "3DO" to listOf(LibretroCore("opera", "Opera")),
        "DOS" to listOf(LibretroCore("dosbox_pure", "DOSBox Pure"), LibretroCore("dosbox_core", "DOSBox-core"), LibretroCore("dosbox_svn", "DOSBox-SVN")),
        "ScummVM" to listOf(LibretroCore("scummvm", "ScummVM")),
        "MSX" to listOf(LibretroCore("bluemsx", "blueMSX"), LibretroCore("fmsx", "fMSX")),
        "C64" to listOf(LibretroCore("vice_x64", "VICE x64"), LibretroCore("vice_x64sc", "VICE x64sc"), LibretroCore("frodo", "Frodo")),
        "Amiga" to listOf(LibretroCore("puae", "PUAE"), LibretroCore("puae2021", "PUAE 2021"), LibretroCore("uae4arm", "UAE4ARM")),
        "PICO8" to listOf(LibretroCore("retro8", "Retro8"), LibretroCore("fake08", "FAKE-08")),
        "PokemonMini" to listOf(LibretroCore("pokemini", "PokeMini")),
        "ColecoVision" to listOf(LibretroCore("gearcoleco", "Gearcoleco"), LibretroCore("bluemsx", "blueMSX")),
        "Intellivision" to listOf(LibretroCore("freeintv", "FreeIntv")),
        "Vectrex" to listOf(LibretroCore("vecx", "vecx")),
    )

    /**
     * Builds that borrow a real app's package name (a game's or a benchmark's, for the vendor's speed
     * tweaks): only an emulator when the installed app really has the emulator's activity, so the real
     * game or app with that name isn't taken for an emulator.
     */
    val BORROWED_NAMES = setOf("com.miHoYo.Yuanshen", "com.antutu.ABenchMark")

    /** False for an installed app that only shares a catalog emulator's package name ([BORROWED_NAMES]). Blocking. */
    fun isRealEmulator(pm: android.content.pm.PackageManager, packageName: String): Boolean {
        if (packageName !in BORROWED_NAMES) return true
        val activities = standalone.values.asSequence().flatten().filter { it.packageName == packageName }.map { it.activity }.toSet()
        return activities.any { a ->
            runCatching { pm.getActivityInfo(android.content.ComponentName(packageName, a), 0) }.isSuccess
        }
    }

    /** A launch profile for [packageName] built from the catalog (first console that lists it). */
    fun profileFor(packageName: String): LaunchProfile? {
        val e = standalone.values.asSequence().flatten().firstOrNull { it.packageName == packageName } ?: return null
        return LaunchProfile(
            packages = listOf(e.packageName),
            activities = listOf(e.activity),
            action = e.action,
            romExtra = e.romExtra,
            format = e.format,
            alsoAsData = e.asData && e.romExtra != null,
            boolExtras = e.boolExtras
        )
    }
}
