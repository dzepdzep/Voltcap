package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** GPU allocation ANR: Flip 2, v0.6.32, Stacks with full 3D cartridge carousel. */
class StacksGpuBudget314Test {
    @Test fun stacksFull3dCartridgesBoundTheirSimultaneousLayers() {
        assertEquals(5, stacks3dCartridgeReach(48, stacks = true, full3dCartridges = true))
        assertEquals(5, stacks3dCartridgeReach(9, stacks = true, full3dCartridges = true))
        assertEquals(3, stacks3dCartridgeReach(3, stacks = true, full3dCartridges = true))
        assertEquals(0, stacks3dCartridgeReach(0, stacks = true, full3dCartridges = true))
    }

    @Test fun otherCarouselsAreUnaffected() {
        assertEquals(48, stacks3dCartridgeReach(48, stacks = false, full3dCartridges = true))
        assertEquals(48, stacks3dCartridgeReach(48, stacks = true, full3dCartridges = false))
        assertEquals(3, stacks3dCartridgeReach(3, stacks = false, full3dCartridges = false))
    }
}
