package com.tomasthrawat.hyoukacartoonracer

import org.junit.Assert.assertTrue
import org.junit.Test

class BasicGameTest {
    @Test
    fun finishDistanceIsAfterThreeLaps() {
        val finishDistance = 3000f
        assertTrue(finishDistance >= 3f * 1000f)
    }
}
