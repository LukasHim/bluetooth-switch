package com.lukas.bluetoothswitch.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test
    fun `read commands match protocol`() {
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0x01, 0xA0.toByte()), Protocol.readStatus())
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0x01, 0xA5.toByte()), Protocol.readPassword())
    }

    @Test
    fun `immediate switch command`() {
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x03, 0xA1.toByte(), 0x01, 0xFF.toByte()),
            Protocol.setChannel(1, true),
        )
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x03, 0xA1.toByte(), 0x01, 0x00),
            Protocol.setChannel(1, false),
        )
    }

    @Test
    fun `delay command uses 255 based high and low bytes`() {
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x05, 0xA2.toByte(), 0x01, 0xFF.toByte(), 0x00, 0x0A),
            Protocol.setDelay(1, true, 10),
        )
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x05, 0xA2.toByte(), 0x01, 0x00, 0x01, 0x00),
            Protocol.setDelay(1, false, 255),
        )
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x05, 0xA2.toByte(), 0x01, 0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte()),
            Protocol.setDelay(1, true, Protocol.MAX_DELAY_SECONDS),
        )
    }

    @Test
    fun `clear delay command`() {
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x05, 0xA2.toByte(), 0x02, 0xFF.toByte(), 0x00, 0x00),
            Protocol.clearDelay(2, true),
        )
    }

    @Test
    fun `password command converts each digit to one byte`() {
        assertArrayEquals(
            byteArrayOf(
                0xAA.toByte(),
                0x07,
                0xA4.toByte(),
                0x01,
                0x02,
                0x03,
                0x04,
                0x05,
                0x06,
            ),
            Protocol.setPassword("123456"),
        )
    }

    @Test
    fun `parse immediate status notification`() {
        val messages = Protocol.parseNotification(
            byteArrayOf(0xA0.toByte(), 0x01, 0xA1.toByte(), 0xFF.toByte(), 0x00, 0x00),
        )
        assertEquals(1, messages.size)
        val status = messages.single() as IncomingMessage.ChannelStatus
        assertEquals(1, status.channel)
        assertEquals(SwitchControl.IMMEDIATE, status.control)
        assertTrue(status.isOn)
        assertEquals(0, status.delaySeconds)
    }

    @Test
    fun `parse delayed status notification`() {
        val messages = Protocol.parseNotification(
            byteArrayOf(0xA0.toByte(), 0x02, 0xA2.toByte(), 0x00, 0x01, 0x2C),
        )
        val status = messages.single() as IncomingMessage.ChannelStatus
        assertEquals(2, status.channel)
        assertEquals(SwitchControl.DELAYED, status.control)
        assertEquals(false, status.isOn)
        assertEquals(299, status.delaySeconds)
    }

    @Test
    fun `parse password notification`() {
        val messages = Protocol.parseNotification(
            byteArrayOf(0xA5.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06),
        )
        val password = messages.single() as IncomingMessage.Password
        assertEquals("010203040506", password.hex)
    }

    @Test
    fun `parse ascii hex notification`() {
        val messages = "A001A1FF0000".encodeToByteArray().let(Protocol::parseNotification)
        val status = messages.single() as IncomingMessage.ChannelStatus
        assertEquals(1, status.channel)
        assertTrue(status.isOn)
        assertEquals(SwitchControl.IMMEDIATE, status.control)
    }

    @Test
    fun `parse multiple frames and ignore leading noise`() {
        val data = byteArrayOf(
            0x00,
            0x55,
            0xA0.toByte(),
            0x01,
            0xA1.toByte(),
            0xFF.toByte(),
            0x00,
            0x00,
            0xA5.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
        )
        val messages = Protocol.parseNotification(data)
        assertEquals(2, messages.size)
        assertEquals(1, (messages[0] as IncomingMessage.ChannelStatus).channel)
        assertEquals("FFFFFFFFFFFF", (messages[1] as IncomingMessage.Password).hex)
    }

    @Test
    fun `hex string formatter uses spaces and upper case`() {
        assertEquals(
            "AA 03 A1 01 FF",
            Protocol.toHexString(Protocol.setChannel(1, true)),
        )
    }
}
