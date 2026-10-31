package com.fitflow.timer.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DurationFormatTest {

    @Test
    fun `formats durations as zero padded minutes and seconds`() {
        assertEquals("00:00", DurationFormat.formatMinSec(0))
        assertEquals("00:05", DurationFormat.formatMinSec(5))
        assertEquals("00:59", DurationFormat.formatMinSec(59))
        assertEquals("01:00", DurationFormat.formatMinSec(60))
        assertEquals("01:30", DurationFormat.formatMinSec(90))
        assertEquals("10:00", DurationFormat.formatMinSec(600))
        assertEquals("59:59", DurationFormat.formatMinSec(3599))
        assertEquals("60:00", DurationFormat.formatMinSec(3600))
    }

    @Test
    fun `splits and combines durations`() {
        assertEquals(1 to 30, DurationFormat.split(90))
        assertEquals(60 to 0, DurationFormat.split(3600))
        assertEquals(90, DurationFormat.toSeconds(1, 30))
        assertEquals(0, DurationFormat.toSeconds(0, 0))
    }

    @Test
    fun `normalizes seconds above fifty nine`() {
        assertEquals(150, DurationFormat.toSeconds(1, 90))
        assertEquals("02:30", DurationFormat.formatMinSec(DurationFormat.toSeconds(1, 90)))
    }

    @Test
    fun `parses formatted and plain seconds`() {
        assertEquals(90, DurationFormat.parseMinSec("01:30"))
        assertEquals(90, DurationFormat.parseMinSec("1:30"))
        assertEquals(90, DurationFormat.parseMinSec("90"))
        assertEquals(0, DurationFormat.parseMinSec("00:00"))
        assertNull(DurationFormat.parseMinSec(""))
        assertNull(DurationFormat.parseMinSec("abc"))
        assertNull(DurationFormat.parseMinSec("1:aa"))
    }
}
