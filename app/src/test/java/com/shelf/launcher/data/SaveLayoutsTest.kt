package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** #79: SaveLayouts — 3DS / Wii title-folder path parsing and storage classification. */
class SaveLayoutsTest {

    // ---- 3DS ----

    @Test
    fun n3dsFullCitraPath() {
        val u = SaveLayouts.n3ds("Nintendo 3DS/0123abcd/4567ef00/title/00040000/0011c500/data/00000001/main")
        assertNotNull(u)
        u!!
        assertEquals("3ds", u.platform)
        assertEquals("Nintendo 3DS/0123abcd/4567ef00/title/00040000/0011c500/data", u.prefix)
        assertEquals("00040000", u.high)
        assertEquals("0011c500", u.low)
        assertEquals("Nintendo 3DS/0123abcd/4567ef00", u.profile)
        assertEquals("000400000011C500", u.titleId)
    }

    @Test
    fun n3dsMetadataFileBelongsToTheSameUnit() {
        val u = SaveLayouts.n3ds("Nintendo 3DS/a/b/title/00040000/0011c500/data/00000001.metadata")
        assertEquals("Nintendo 3DS/a/b/title/00040000/0011c500/data", u?.prefix)
    }

    @Test
    fun n3dsDemoTitleAccepted() {
        assertEquals("00040002", SaveLayouts.n3ds("title/00040002/00123400/data/00000001/save.dat")?.high)
    }

    @Test
    fun n3dsCaseInsensitiveFolderNamesAndHex() {
        val u = SaveLayouts.n3ds("TITLE/00040000/0011C500/DATA/00000001/main")
        assertNotNull(u)
        assertEquals("0011c500", u!!.low)
        assertEquals("000400000011C500", u.titleId)
        assertEquals("", u.profile)
    }

    @Test
    fun n3dsFolderPickedAtTitle() {
        val u = SaveLayouts.n3ds("00040000/0011c500/data/00000001/main")
        assertNotNull(u)
        assertEquals("00040000/0011c500/data", u!!.prefix)
        assertEquals("", u.profile)
    }

    @Test
    fun n3dsNonGameTitleTypesRejected() {
        // Update, DLC, system title.
        assertNull(SaveLayouts.n3ds("title/0004000e/0011c500/data/00000001/main"))
        assertNull(SaveLayouts.n3ds("title/0004000E/0011c500/data/00000001/main"))
        assertNull(SaveLayouts.n3ds("title/0004008c/0011c500/data/00000001/main"))
        assertNull(SaveLayouts.n3ds("title/00040010/00020000/data/00000001/main"))
        assertNull(SaveLayouts.n3ds("0004000e/0011c500/data/00000001/main"))
    }

    @Test
    fun n3dsMalformedOrShortPaths() {
        assertNull(SaveLayouts.n3ds(""))
        assertNull(SaveLayouts.n3ds("title"))
        assertNull(SaveLayouts.n3ds("title/00040000/0011c500/data")) // the folder itself, no file
        assertNull(SaveLayouts.n3ds("title/00040000/0011c50/data/00000001/main")) // 7 hex digits
        assertNull(SaveLayouts.n3ds("title/00040000/0011g500/data/00000001/main")) // not hex
        assertNull(SaveLayouts.n3ds("title/00040000/0011c500/content/00000000.app")) // installed game, not a save
        assertNull(SaveLayouts.n3ds("Nintendo 3DS/a/b/extdata/00000000/00000123/user/x"))
        assertNull(SaveLayouts.n3ds("title/00040000/0011c500/data/.nomedia"))
    }

    // ---- Wii ----

    @Test
    fun wiiInsideTitleFolder() {
        val u = SaveLayouts.wii("00010000/524d4345/data/banner.bin")
        assertNotNull(u)
        u!!
        assertEquals("wii", u.platform)
        assertEquals("00010000/524d4345/data", u.prefix)
        assertEquals("00010000", u.high)
        assertEquals("524d4345", u.low)
        assertEquals("", u.profile)
        assertEquals("00010000524D4345", u.titleId)
    }

    @Test
    fun wiiWithTitlePrefix() {
        val u = SaveLayouts.wii("Wii/title/00010001/57415645/data/sub/save.dat")
        assertNotNull(u)
        assertEquals("Wii/title/00010001/57415645/data", u!!.prefix)
        assertEquals("Wii/title", u.profile)
    }

    @Test
    fun wiiCaseInsensitiveDataAndHex() {
        val u = SaveLayouts.wii("TITLE/00010004/524D4345/DATA/x")
        assertNotNull(u)
        assertEquals("524d4345", u!!.low)
    }

    @Test
    fun wiiNegatives() {
        assertNull(SaveLayouts.wii("00010000/524d4345/data")) // too short
        assertNull(SaveLayouts.wii("00010002/48414141/data/x")) // system channel type
        assertNull(SaveLayouts.wii("foo/00010000/524d4345/data/x")) // not under "title"
        assertNull(SaveLayouts.wii("00010000/524d434/data/x")) // 7 hex digits
        assertNull(SaveLayouts.wii("00010000/524d4345/content/x")) // not data
        assertNull(SaveLayouts.wii("00010000/524d4345/data/title.tmd"))
        assertNull(SaveLayouts.wii("00010000/524d4345/data/00000000.APP"))
        assertNull(SaveLayouts.wii("00010000/524d4345/data/.DS_Store"))
    }

    @Test
    fun titleUnitPrefersEitherPlatform() {
        assertEquals("3ds", SaveLayouts.titleUnit("title/00040000/0011c500/data/00000001/main")?.platform)
        assertEquals("wii", SaveLayouts.titleUnit("title/00010000/524d4345/data/banner.bin")?.platform)
        assertNull(SaveLayouts.titleUnit("Pokemon Red.srm"))
    }

    // ---- storageOf ----

    @Test
    fun storageOfTitleFolders() {
        assertEquals(SaveLayouts.Storage.TITLE_FOLDER,
            SaveLayouts.storageOf(SaveKind.CITRA, "title/00040000/0011c500/data/00000001/main"))
        assertEquals(SaveLayouts.Storage.TITLE_FOLDER,
            SaveLayouts.storageOf(SaveKind.GENERIC, "00010000/524d4345/data/banner.bin"))
    }

    @Test
    fun storageOfUpdateTitleIsNotATitleFolder() {
        assertEquals(SaveLayouts.Storage.UNKNOWN,
            SaveLayouts.storageOf(SaveKind.CITRA, "title/0004000e/0011c500/data/00000001/main"))
    }

    @Test
    fun storageOfSaveStates() {
        assertEquals(SaveLayouts.Storage.SAVE_STATE, SaveLayouts.storageOf(SaveKind.RETROARCH, "states/Pokemon Red.state"))
        assertEquals(SaveLayouts.Storage.SAVE_STATE, SaveLayouts.storageOf(SaveKind.RETROARCH, "Pokemon Red.state1"))
        assertEquals(SaveLayouts.Storage.SAVE_STATE, SaveLayouts.storageOf(SaveKind.PSP_SAVEDATA, "ULUS10041_1.00_0.ppst"))
    }

    @Test
    fun storageOfPs2FolderCard() {
        assertEquals(SaveLayouts.Storage.FOLDER_CARD,
            SaveLayouts.storageOf(SaveKind.PS2_MEMCARDS, "Mcd001.ps2/BASLUS-20312/icon.sys"))
        assertEquals(SaveLayouts.Storage.FOLDER_CARD,
            SaveLayouts.storageOf(SaveKind.PS2_MEMCARDS, "MCD001.PS2/BASLUS-20312/icon.sys"))
    }

    @Test
    fun storageOfPspFolderSave() {
        assertEquals(SaveLayouts.Storage.FOLDER_SAVE,
            SaveLayouts.storageOf(SaveKind.PSP_SAVEDATA, "ULUS10041DATA00/PARAM.SFO"))
        // A picked folder above SAVEDATA: still the save folder, even for a file called DATA.BIN.
        assertEquals(SaveLayouts.Storage.FOLDER_SAVE,
            SaveLayouts.storageOf(SaveKind.GENERIC, "PSP/SAVEDATA/ULUS10041/DATA.BIN"))
    }

    @Test
    fun storageOfWiiExport() {
        assertEquals(SaveLayouts.Storage.EXPORT_ARCHIVE, SaveLayouts.storageOf(SaveKind.GENERIC, "data.bin"))
        assertEquals(SaveLayouts.Storage.EXPORT_ARCHIVE, SaveLayouts.storageOf(SaveKind.GENERIC, "private/wii/title/RMCE/DATA.BIN"))
    }

    @Test
    fun storageOfCardImages() {
        assertEquals(SaveLayouts.Storage.MEMORY_CARD_IMAGE, SaveLayouts.storageOf(SaveKind.PS1_MEMCARDS, "epsxe000.mcr"))
        assertEquals(SaveLayouts.Storage.MEMORY_CARD_IMAGE, SaveLayouts.storageOf(SaveKind.PS2_MEMCARDS, "Mcd001.ps2"))
        assertEquals(SaveLayouts.Storage.MEMORY_CARD_IMAGE, SaveLayouts.storageOf(SaveKind.DREAMCAST_VMU, "vmu_save_A1.bin"))
        assertEquals(SaveLayouts.Storage.MEMORY_CARD_IMAGE, SaveLayouts.storageOf(SaveKind.DOLPHIN_GC, "MemoryCardA.USA.raw"))
    }

    @Test
    fun storageOfInGameFiles() {
        assertEquals(SaveLayouts.Storage.IN_GAME_FILE, SaveLayouts.storageOf(SaveKind.RETROARCH, "Pokemon Red.srm"))
        assertEquals(SaveLayouts.Storage.IN_GAME_FILE, SaveLayouts.storageOf(SaveKind.RETROARCH, "POKEMON RED.SAV"))
        assertEquals(SaveLayouts.Storage.IN_GAME_FILE, SaveLayouts.storageOf(SaveKind.NDS, "Mario Kart DS.dsv"))
    }

    @Test
    fun storageOfUnknown() {
        assertEquals(SaveLayouts.Storage.UNKNOWN, SaveLayouts.storageOf(SaveKind.GENERIC, "readme.txt"))
        assertEquals(SaveLayouts.Storage.UNKNOWN, SaveLayouts.storageOf(SaveKind.GENERIC, "noextension"))
        assertEquals(SaveLayouts.Storage.UNKNOWN, SaveLayouts.storageOf(SaveKind.GENERIC, ""))
    }

    @Test
    fun unitOfGroupsMultiFileSaves() {
        assertEquals("title/00040000/0011c500/data",
            SaveLayouts.unitOf(SaveKind.CITRA, "title/00040000/0011c500/data/00000001/main"))
        assertEquals("ULUS10041DATA00", SaveLayouts.unitOf(SaveKind.PSP_SAVEDATA, "ULUS10041DATA00/ICON0.PNG"))
        assertNull(SaveLayouts.unitOf(SaveKind.RETROARCH, "Pokemon Red.srm"))
        assertNull(SaveLayouts.unitOf(SaveKind.PS2_MEMCARDS, "Mcd001.ps2/BASLUS-20312/icon.sys"))
    }
}
