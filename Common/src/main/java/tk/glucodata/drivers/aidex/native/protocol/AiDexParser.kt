// JugglucoNG — AiDex Native Kotlin Driver
// AiDexParser.kt — Frame parsing for F003 data and F002 responses
//
// Ported from iGlucco AiDexProtocol.swift (AiDexParser enum).

package tk.glucodata.drivers.aidex.native.protocol

import tk.glucodata.drivers.aidex.native.data.*

object AiDexParser {

    // -- F003 Frame Classification --

    enum class FrameType { DATA, STATUS, UNKNOWN }

    fun classifyFrame(data: ByteArray): FrameType = when (data.size) {
        AiDexOpcodes.DATA_FRAME_LENGTH -> FrameType.DATA
        AiDexOpcodes.STATUS_FRAME_LENGTH -> FrameType.STATUS
        else -> FrameType.UNKNOWN
    }

    // -- 17-Byte Data Frame Parsing --

    /**
     * Parse a 17-byte F003 data frame.
     *
     * Layout:
     *   byte[0]: opcode
     *   bytes[1..4]: seconds since sensor start (u32 LE), ÷ 60 = timeOffsetMinutes
     *   bytes[6..7]: glucosePacked (u16 LE), glucose = packed & 0x03FF
     *   bytes[8..9]: i1 raw channel (u16 LE / 100)
     *   bytes[10..11]: i2 raw channel (u16 LE / 100)
     *   bytes[15..16]: CRC-16 (u16 LE)
     */
    fun parseDataFrame(data: ByteArray): GlucoseFrame? {
        if (data.size != AiDexOpcodes.DATA_FRAME_LENGTH) return null

        val opcode = data[0].toInt() and 0xFF
        val glucosePacked = u16LE(data, 6)
        val rawGlucose = glucosePacked and AiDexOpcodes.GLUCOSE_MASK
        val i1Raw = u16LE(data, 8)
        val i2Raw = u16LE(data, 10)
        val crc16 = u16LE(data, 15)

        val i1 = i1Raw / 100f
        val i2 = i2Raw / 100f

        val scaling = AiDexOpcodes.scalingFactor(opcode)
        val glucoseMgDl = if (scaling != null) rawGlucose * scaling else rawGlucose.toFloat()

        val isSentinel = rawGlucose == AiDexOpcodes.SENTINEL_GLUCOSE
        val isInRange = rawGlucose >= AiDexOpcodes.MIN_VALID_GLUCOSE &&
                rawGlucose <= AiDexOpcodes.MAX_VALID_GLUCOSE
        val isValid = !isSentinel && isInRange

        return GlucoseFrame(
            opcode = opcode,
            glucoseMgDl = glucoseMgDl,
            rawGlucosePacked = glucosePacked,
            i1 = i1,
            i2 = i2,
            crc16 = crc16,
            isValid = isValid,
        )
    }

    // -- 5-Byte Status Frame Parsing --

    fun parseStatusFrame(data: ByteArray): StatusFrame? {
        if (data.size != AiDexOpcodes.STATUS_FRAME_LENGTH) return null
        return StatusFrame(header = data[0].toInt() and 0xFF)
    }

    // -- History Parsing: GET_HISTORIES_RAW (0x23) --

    /**
     * Parse a 0x23 response payload (after opcode+status bytes and before CRC).
     *
     * Format:
     *   bytes[0..1]: startOffset (u16 LE) — time offset in minutes
     *   body: N * 2-byte rows
     *     row glucose = b0 | ((b1 & 0x03) << 8) — 10-bit packed
     *     row status bit = (b1 & 0x04)
     */
    fun parseHistoryResponse(data: ByteArray): List<CalibratedHistoryEntry> {
        if (data.size < 4) return emptyList()

        val startOffset = u16LE(data, 0)
        val bodyOffset = 2
        val bodySize = data.size - bodyOffset
        val rowCount = bodySize / AiDexOpcodes.HISTORY_RAW_ROW_SIZE

        return (0 until rowCount).map { i ->
            val off = bodyOffset + i * AiDexOpcodes.HISTORY_RAW_ROW_SIZE
            val b0 = data[off].toInt() and 0xFF
            val b1 = data[off + 1].toInt() and 0xFF

            val glucose = b0 or ((b1 and 0x03) shl 8)
            val statusBit = (b1 and 0x04) != 0
            val isSentinel = glucose == AiDexOpcodes.SENTINEL_GLUCOSE

            CalibratedHistoryEntry(
                timeOffsetMinutes = startOffset + i,
                glucoseMgDl = glucose,
                statusBit = statusBit,
                isSentinel = isSentinel,
            )
        }
    }

    // -- History Parsing: GET_HISTORIES (0x24) --

    /**
     * Parse a 0x24 response payload.
     *
     * Format:
     *   bytes[0..1]: startOffset (u16 LE)
     *   body: N * 5-byte rows: [i1_lo, i1_hi, i2_lo, i2_hi, vc]
     *     i1 = u16LE / 100.0
     *     i2 = u16LE / 100.0
     *     vc = u8 / 100.0
     *     rawValue = i1 * 10 (matches Android selectVendorRawLane)
     *     sensorGlucose = i1 * 18.0182 (treating i1 as mmol/L -> mg/dL)
     *     Invalid: i1_raw == 0 && i2_raw == 0 && vc_raw == 0
     */
    fun parseBriefHistoryResponse(data: ByteArray): List<AdcHistoryEntry> {
        if (data.size < 7) return emptyList() // 2 header + at least 5 body

        val startOffset = u16LE(data, 0)
        val bodyOffset = 2
        val bodySize = data.size - bodyOffset
        val rowCount = bodySize / AiDexOpcodes.HISTORY_BRIEF_ROW_SIZE

        return (0 until rowCount).mapNotNull { i ->
            val off = bodyOffset + i * AiDexOpcodes.HISTORY_BRIEF_ROW_SIZE
            val i1Raw = u16LE(data, off)
            val i2Raw = u16LE(data, off + 2)
            val vcRaw = data[off + 4].toInt() and 0xFF

            // Skip invalid entries
            if (i1Raw == 0 && i2Raw == 0 && vcRaw == 0) return@mapNotNull null

            val i1 = i1Raw / 100f
            val i2 = i2Raw / 100f

            AdcHistoryEntry(
                timeOffsetMinutes = startOffset + i,
                i1 = i1,
                i2 = i2,
                vc = vcRaw / 100f,
                rawValue = i1 * 10f,
                sensorGlucose = i1 * 18.0182f,
            )
        }
    }

    // -- Calibration Parsing (0x27) --

    /**
     * Parse a GET_CALIBRATION response.
     *
     * Format:
     *   bytes[0..1]: startIndex (u16 LE)
     *   body: N * 8-byte rows:
     *     [0..1] timeOffsetMin (u16 LE)
     *     [2..3] referenceGlucose (u16 LE)
     *     [4..5] cf (u16 LE / 100)
     *     [6..7] offset (s16 LE / 100)
     */
    fun parseCalibrationResponse(data: ByteArray): List<CalibrationRecord> {
        if (data.size < 10) return emptyList() // 2 header + at least 8 body

        val startIndex = u16LE(data, 0)
        if (startIndex > 10000) return emptyList() // implausible

        val bodyOffset = 2
        val bodySize = data.size - bodyOffset
        if (bodySize % AiDexOpcodes.CALIBRATION_ROW_SIZE != 0) return emptyList() // corrupt

        val rowCount = bodySize / AiDexOpcodes.CALIBRATION_ROW_SIZE

        return (0 until rowCount).map { i ->
            val off = bodyOffset + i * AiDexOpcodes.CALIBRATION_ROW_SIZE
            val timeOffset = u16LE(data, off)
            val reference = u16LE(data, off + 2)
            val cfRaw = u16LE(data, off + 4)
            val offsetRaw = s16LE(data, off + 6)

            CalibrationRecord(
                index = startIndex + i,
                timeOffsetMinutes = timeOffset,
                referenceGlucoseMgDl = reference,
                calibrationFactor = cfRaw / 100f,
                calibrationOffset = offsetRaw / 100f,
            )
        }
    }

    // -- Handshake Echo Validation --

    /**
     * Validate that an F002 notify is an echo of the command we sent.
     */
    fun isHandshakeEcho(sent: ByteArray, received: ByteArray): Boolean {
        if (sent.isEmpty() || received.isEmpty()) return false
        return sent[0] == received[0]
    }

    // -- Hex Utilities --

    fun hexString(data: ByteArray): String =
        data.joinToString(" ") { "%02X".format(it) }

    fun compactHex(data: ByteArray): String =
        data.joinToString("") { "%02X".format(it) }

    fun dataFromHex(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    // -- Internal helpers --

    private fun u16LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun s16LE(data: ByteArray, offset: Int): Int {
        val unsigned = u16LE(data, offset)
        return if (unsigned >= 0x8000) unsigned - 0x10000 else unsigned
    }
}
