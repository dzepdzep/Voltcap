package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 682r (RP Classic, p2review p2r13b at x1.3): the Arcade's HIGH SCORES page showed no score row - the order key, unbounded in
 * its row, took the whole width and the title beside it went to 0 px, one letter a line, 360 px tall. The KONKR's 640 x 427 dp
 * never showed it. The same first-achievement check (AchFirstRowRoboTest) on the RP Classic's screen: 1240 x 1032 px at
 * density 2 = 620 x 516 dp, every native-page layout, a dozen achievements, x1.3.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w620dp-h516dp-land-xhdpi")
class AchFirstRowRpc682rRoboTest(layout: HomeLayout, big: Boolean, count: Int) : AchFirstRowRoboTest(layout, big, count) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1} n={2}")
        fun cases(): List<Array<Any>> = AchFirstRowRoboTest.NATIVE.map { arrayOf<Any>(it, true, 12) }
    }
}
