// JugglucoNG — AiDex Native Kotlin Driver
// AiDexCommandBuilder.kt — F002 command construction + CRC + encryption
//
// Builds encrypted commands to write to F002 characteristic.

package tk.glucodata.drivers.aidex.native.protocol

import tk.glucodata.drivers.aidex.native.crypto.Crc16CcittFalse

/**
 * Builds encrypted F002 commands using the session key from key exchange.
 */
class AiDexCommandBuilder(private val keyExchange: AiDexKeyExchange) {

    /**
     * Build an encrypted F002 command.
     * Adds CRC-16 trailer, then encrypts with session key + SN IV.
     */
    fun buildEncrypted(opcode: Int, vararg params: Int): ByteArray? {
        val plaintext = Crc16CcittFalse.makeCommand(opcode, *params)
        return keyExchange.encrypt(plaintext)
    }

    // -- Convenience Methods --

    fun getDeviceInfo() = buildEncrypted(AiDexOpcodes.GET_DEVICE_INFO)

    fun getStartTime() = buildEncrypted(AiDexOpcodes.GET_START_TIME)

    fun getHistoryRange() = buildEncrypted(AiDexOpcodes.GET_HISTORY_RANGE)

    fun getHistoriesRaw(offset: Int) = buildEncrypted(
        AiDexOpcodes.GET_HISTORIES_RAW,
        offset and 0xFF, (offset shr 8) and 0xFF
    )

    fun getHistories(offset: Int) = buildEncrypted(
        AiDexOpcodes.GET_HISTORIES,
        offset and 0xFF, (offset shr 8) and 0xFF
    )

    /**
     * SET_NEW_SENSOR: activate a new sensor with the given datetime.
     * Payload: [year_lo, year_hi, month, day, hour, minute, second, tz_quarters, dst_quarters]
     */
    fun setNewSensor(
        year: Int, month: Int, day: Int,
        hour: Int, minute: Int, second: Int,
        tzQuarters: Int, dstQuarters: Int
    ) = buildEncrypted(
        AiDexOpcodes.SET_NEW_SENSOR,
        year and 0xFF, (year shr 8) and 0xFF,
        month, day, hour, minute, second,
        tzQuarters, dstQuarters
    )

    /**
     * SET_CALIBRATION: send blood glucose reference to sensor.
     * Wire: [0x25, offset_lo, offset_hi, glucose_lo, glucose_hi, CRC16_lo, CRC16_hi]
     * Note: offset FIRST, glucose SECOND (confirmed by disassembly).
     */
    fun setCalibration(offsetMinutes: Int, glucoseMgDl: Int) = buildEncrypted(
        AiDexOpcodes.SET_CALIBRATION,
        offsetMinutes and 0xFF, (offsetMinutes shr 8) and 0xFF,
        glucoseMgDl and 0xFF, (glucoseMgDl shr 8) and 0xFF
    )

    fun getCalibrationRange() = buildEncrypted(AiDexOpcodes.GET_CALIBRATION_RANGE)

    fun getCalibration(index: Int) = buildEncrypted(
        AiDexOpcodes.GET_CALIBRATION,
        index and 0xFF, (index shr 8) and 0xFF
    )

    fun deleteBond() = buildEncrypted(AiDexOpcodes.DELETE_BOND)

    fun reset() = buildEncrypted(AiDexOpcodes.RESET)

    /** Alias for [reset] — used by AiDexBleManager.resetSensor(). */
    fun resetSensor() = reset()
}
