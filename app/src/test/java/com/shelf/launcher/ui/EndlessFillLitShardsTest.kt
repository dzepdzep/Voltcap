package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 675a: EndlessFillLitRoboTest's rows are split into shard classes so the heavy test task can give each a fresh JVM. The
 * shards together must be every row exactly once - none lost, none run twice - and each shard must have rows.
 */
class EndlessFillLitShardsTest {
    private fun key(r: Array<Any>) = r.joinToString("|")

    @Test fun shardsCoverEveryRowOnce() {
        val all = EndlessFillLitRoboTest.cases().map(::key)
        val shards = (0 until EndlessFillLitRoboTest.SHARDS).map { k -> EndlessFillLitRoboTest.shard(k).map(::key) }
        val joined = shards.flatten()
        assertEquals("rows run twice", joined.size, joined.toSet().size)
        assertEquals("rows lost or added", all.toSet(), joined.toSet())
        assertEquals(all.size, joined.size)
        shards.forEachIndexed { k, s -> assertTrue("shard $k is empty", s.isNotEmpty()) }
        // (the six shard classes really are the six shards)
        val classes = listOf(EndlessFillLitRoboTestShard0.rows(), EndlessFillLitRoboTestShard1.rows(), EndlessFillLitRoboTestShard2.rows(),
            EndlessFillLitRoboTestShard3.rows(), EndlessFillLitRoboTestShard4.rows(), EndlessFillLitRoboTestShard5.rows())
        assertEquals(EndlessFillLitRoboTest.SHARDS, classes.size)
        classes.forEachIndexed { k, rows -> assertEquals("shard class $k", shards[k], rows.map(::key)) }
    }
}
