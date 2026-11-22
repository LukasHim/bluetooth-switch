package com.lukas.bluetoothswitch.data

import java.util.Locale

enum class SwitchControl {
    IMMEDIATE,
    DELAYED,
}

sealed interface IncomingMessage {
    data class Password(val hex: String) : IncomingMessage

    data class ChannelStatus(
        val channel: Int,
        val control: SwitchControl,
        val isOn: Boolean,
        val delaySeconds: Int,
        val rawHex: String,
    ) : IncomingMessage
}

/**
 * Encoder/decoder for the plain-text Hex protocol described in protocol.md.
 *
 * TX and RX payloads are real bytes on the wire (not ASCII characters), but the
 * parser also accepts ASCII hex frames because some BLE modules expose a text
 * bridge instead of a transparent byte pipe.
 */
object Protocol {
    const val MAX_DELAY_SECONDS = 255 * 255 + 254

    private const val FRAME_HEADER = 0xAA
    private const val OP_READ_PASSWORD = 0xA5
    private const val OP_READ_STATUS = 0xA0
    private const val CMD_IMMEDIATE = 0xA1
    private const val CMD_DELAYED = 0xA2
    private const val CMD_PASSWORD = 0xA4

    private const val RX_PASSWORD_HEADER = 0xA5
    private const val RX_STATUS_HEADER = 0xA0

    private const val PASSWORD_BYTES = 6
    private const val PRINTABLE_ASCII_MIN = 0x20
    private const val PRINTABLE_ASCII_MAX = 0x7E
    private val PASSWORD_PADDING: Byte = 0xFF.toByte()

    fun readPassword(): ByteArray = byteArrayOf(
        FRAME_HEADER.toByte(),
        0x01,
        OP_READ_PASSWORD.toByte(),
    )

    fun readStatus(): ByteArray = byteArrayOf(
        FRAME_HEADER.toByte(),
        0x01,
        OP_READ_STATUS.toByte(),
    )

    fun setChannel(channel: Int, on: Boolean): ByteArray {
        require(channel in 0..255) { "channel must fit in one byte" }
        return byteArrayOf(
            FRAME_HEADER.toByte(),
            0x03,
            CMD_IMMEDIATE.toByte(),
            channel.toByte(),
            stateByte(on),
        )
    }

    fun setDelay(channel: Int, on: Boolean, seconds: Int): ByteArray {
        require(channel in 0..255) { "channel must fit in one byte" }
        require(seconds in 0..MAX_DELAY_SECONDS) { "delay out of range" }
        val high = seconds / 255
        val low = seconds % 255
        return byteArrayOf(
            FRAME_HEADER.toByte(),
            0x05,
            CMD_DELAYED.toByte(),
            channel.toByte(),
            stateByte(on),
            high.toByte(),
            low.toByte(),
        )
    }

    /**
     * Cancels a pending delay without changing the relay's current state.
     *
     * The MCU interprets the state byte of a clear command as the opposite of
     * the state that should remain active (protocol.md §2.5). Callers pass the
     * current state and the encoder sends its inverse, so the device keeps the
     * relay exactly as it is.
     */
    fun clearDelay(channel: Int, currentOn: Boolean): ByteArray {
        require(channel in 0..255) { "channel must fit in one byte" }
        return byteArrayOf(
            FRAME_HEADER.toByte(),
            0x05,
            CMD_DELAYED.toByte(),
            channel.toByte(),
            stateByte(!currentOn),
            0x00,
            0x00,
        )
    }

    fun setPassword(password: String): ByteArray {
        require(password.length in 1..PASSWORD_BYTES) {
            "password must contain 1 to $PASSWORD_BYTES characters"
        }
        require(password.all { it.code in PRINTABLE_ASCII_MIN..PRINTABLE_ASCII_MAX }) {
            "password must contain printable ASCII characters"
        }
        val payload = ByteArray(PASSWORD_BYTES) { PASSWORD_PADDING }
        password.forEachIndexed { index, char ->
            payload[index] = passwordCharToByte(char)
        }
        return byteArrayOf(
            FRAME_HEADER.toByte(),
            0x07,
            CMD_PASSWORD.toByte(),
        ) + payload
    }

    fun resetPassword(): ByteArray = byteArrayOf(
        FRAME_HEADER.toByte(),
        0x07,
        CMD_PASSWORD.toByte(),
        PASSWORD_PADDING,
        PASSWORD_PADDING,
        PASSWORD_PADDING,
        PASSWORD_PADDING,
        PASSWORD_PADDING,
        PASSWORD_PADDING,
    )

    fun isPasswordUnset(hex: String): Boolean =
        hex.length == PASSWORD_BYTES * 2 &&
            hex.all { char -> char == 'F' || char == 'f' }

    /**
     * Decodes the byte payload of a password frame back to text.
     *
     * Bytes 0x00–0x09 map back to the digits '0'–'9' (the wire format swaps the
     * digit characters with their numeric values). Trailing 0xFF bytes are
     * padding and stop decoding.
     */
    fun decodePasswordAscii(hex: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index + 1 < hex.length) {
            val value = hex.substring(index, index + 2).toIntOrNull(16) ?: break
            if (value == 0xFF) break
            val char = when {
                value in 0..9 -> ('0'.code + value).toChar()
                value in PRINTABLE_ASCII_MIN..PRINTABLE_ASCII_MAX -> value.toChar()
                else -> '.'
            }
            builder.append(char)
            index += 2
        }
        return builder.toString()
    }

    fun parseNotification(data: ByteArray): List<IncomingMessage> {
        if (data.isEmpty()) return emptyList()

        val bytes = if (looksLikeAsciiHex(data)) asciiHexToBytes(data) ?: data else data
        val messages = mutableListOf<IncomingMessage>()
        var index = 0

        while (index < bytes.size) {
            when (bytes[index].toInt() and 0xFF) {
                RX_PASSWORD_HEADER -> {
                    if (index + 7 > bytes.size) break
                    val hex = bytes.copyOfRange(index + 1, index + 7).toCompactHex()
                    messages += IncomingMessage.Password(hex)
                    index += 7
                }

                RX_STATUS_HEADER -> {
                    if (index + 6 > bytes.size) break
                    val channel = bytes[index + 1].toInt() and 0xFF
                    val control = when (bytes[index + 2].toInt() and 0xFF) {
                        CMD_DELAYED -> SwitchControl.DELAYED
                        else -> SwitchControl.IMMEDIATE
                    }
                    val isOn = (bytes[index + 3].toInt() and 0xFF) == 0xFF
                    val high = bytes[index + 4].toInt() and 0xFF
                    val low = bytes[index + 5].toInt() and 0xFF
                    messages += IncomingMessage.ChannelStatus(
                        channel = channel,
                        control = control,
                        isOn = isOn,
                        delaySeconds = 255 * high + low,
                        rawHex = bytes.copyOfRange(index, index + 6).toCompactHex(),
                    )
                    index += 6
                }

                else -> index++
            }
        }

        return messages
    }

    fun toHexString(bytes: ByteArray): String =
        bytes.joinToString(separator = " ") { byte -> "%02X".format(Locale.ROOT, byte.toInt() and 0xFF) }

    private fun stateByte(on: Boolean): Byte = if (on) 0xFF.toByte() else 0x00

    private fun passwordCharToByte(char: Char): Byte =
        if (char in '0'..'9') (char - '0').toByte() else char.code.toByte()

    private fun looksLikeAsciiHex(data: ByteArray): Boolean {
        if (data.size < 2 || data.size % 2 != 0) return false
        return data.all { byte ->
            val value = byte.toInt() and 0xFF
            value in '0'.code..'9'.code ||
                value in 'A'.code..'F'.code ||
                value in 'a'.code..'f'.code
        }
    }

    private fun asciiHexToBytes(data: ByteArray): ByteArray? {
        if (data.size % 2 != 0) return null
        return runCatching {
            ByteArray(data.size / 2) { index ->
                val pair = data.copyOfRange(index * 2, index * 2 + 2)
                    .map { byte -> (byte.toInt() and 0xFF).toChar() }
                    .joinToString("")
                pair.toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun ByteArray.toCompactHex(): String {
        val builder = StringBuilder(size * 2)
        for (byte in this) {
            builder.append("%02X".format(Locale.ROOT, byte.toInt() and 0xFF))
        }
        return builder.toString()
    }
}
