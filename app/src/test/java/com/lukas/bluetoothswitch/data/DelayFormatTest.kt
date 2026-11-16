package com.lukas.bluetoothswitch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DelayFormatTest {
    @Test
    fun `format uses mm ss below one hour`() {
        assertEquals("0:00", DelayFormat.format(0))
        assertEquals("0:10", DelayFormat.format(10))
        assertEquals("1:00", DelayFormat.format(60))
        assertEquals("59:59", DelayFormat.format(3599))
    }

    @Test
    fun `format includes hours when needed`() {
        assertEquals("1:01:01", DelayFormat.format(3661))
        assertEquals("18:07:59", DelayFormat.format(Protocol.MAX_DELAY_SECONDS))
    }

    @Test
    fun `formatFull always keeps the hour field`() {
        assertEquals("0:00:00", DelayFormat.formatFull(0))
        assertEquals("0:00:10", DelayFormat.formatFull(10))
        assertEquals("0:01:00", DelayFormat.formatFull(60))
        assertEquals("1:01:01", DelayFormat.formatFull(3661))
        assertEquals("18:07:59", DelayFormat.formatFull(Protocol.MAX_DELAY_SECONDS))
    }

    @Test
    fun `parse accepts seconds minutes and hours`() {
        assertEquals(90, DelayFormat.parse("90"))
        assertEquals(60, DelayFormat.parse("1:00"))
        assertEquals(3660, DelayFormat.parse("1:01:00"))
        assertEquals(7260, DelayFormat.parse("2:01:00"))
    }

    @Test
    fun `parse rejects invalid components and overflow`() {
        assertNull(DelayFormat.parse(""))
        assertNull(DelayFormat.parse("1:60"))
        assertNull(DelayFormat.parse("1:00:60"))
        assertNull(DelayFormat.parse("19:00:00"))
        assertNull(DelayFormat.parse("1:2:3:4"))
    }

    @Test
    fun `sanitize keeps digits and colon`() {
        assertEquals("2:01:00", DelayFormat.sanitizeInput("2：01:00"))
        assertEquals("120", DelayFormat.sanitizeInput("1a2b0"))
    }
}
