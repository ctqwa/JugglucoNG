// JugglucoNG — MQ/Glutec CGM Driver
// MQConstants.kt — Nordic UART Service UUIDs, packet header, command bytes
//
// Protocol: Nordic UART Service (NUS) frames in the form
//   {0x5A, 0xA5, CMD, LEN, ...payload..., CRC_HI, CRC_LO}
// No encryption. CRC16-Modbus (poly 0xA001).
// Separate transmitter + disposable sensor patch.
//
// Reverse-engineered from MQ/Glutec app (com.ruapp.glutec v1.0.1.6).

package tk.glucodata.drivers.mq

import java.util.Locale
import java.util.UUID

object MQConstants {

    const val TAG = "MQ"
    const val DEFAULT_DISPLAY_NAME = "Glutec CGM"
    const val PROVISIONAL_SENSOR_PREFIX = "MQ-"
    const val MAX_NATIVE_SENSOR_ID_CHARS = 16

    // ---- BLE UUIDs (Nordic UART Service) ----

    /** Nordic UART Service */
    val NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    /** TX characteristic (transmitter → phone, NOTIFY). */
    val NUS_TX_NOTIFY: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    /** RX characteristic (phone → transmitter, WRITE / WRITE_NO_RESPONSE). */
    val NUS_RX_WRITE: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

    /** Client Characteristic Configuration Descriptor (standard). */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ---- Packet framing ----

    const val HEADER0: Byte = 0x5A
    const val HEADER1: Byte = -0x5B // 0xA5
    val HEADER: ByteArray = byteArrayOf(HEADER0, HEADER1)

    /** Fixed overhead bytes per framed packet: 2 header + 1 cmd + 1 len + 2 CRC. */
    const val FRAME_OVERHEAD = 6

    /** Offsets within a framed packet. */
    const val OFFSET_CMD = 2
    const val OFFSET_LEN = 3
    const val OFFSET_PAYLOAD = 4

    // ---- Commands (byte[2] in every framed packet) ----

    /** TX → RX heartbeat while the transmitter has no new data. */
    const val CMD_NOTIFY_WORKING: Byte = 0x01

    /** RX → TX acknowledgement of a NOTIFY_BG_DATA frame. */
    const val CMD_WRITE_BG_DATA_CONFIRM: Byte = 0x02

    /** RX → TX continue without re-initializing the transmitter. */
    const val CMD_WRITE_CONFIRM_WITHOUT_INIT: Byte = 0x03

    /** TX → RX blood-glucose data (one or more 6-byte records). */
    const val CMD_NOTIFY_BG_DATA: Byte = 0x04

    /** TX → RX "BG data acknowledged, session continuing" marker. Observed
     *  after every successful 0x04 ack round-trip (21 occurrences in the
     *  official HCI log). Silent — no response required from us. */
    const val CMD_NOTIFY_BG_COMPLETE: Byte = 0x05

    /** TX → RX transmitter just started work (reset / fresh session). */
    const val CMD_NOTIFY_BEGIN_WORK: Byte = 0x06

    /** RX → TX acknowledge initialization phase. */
    const val CMD_WRITE_CONFIRM_WITH_INIT: Byte = 0x08

    /** RX → TX transmitter reset. */
    const val CMD_WRITE_CONFIRM_RESET: Byte = 0x11

    // ---- BG record layout (inside payload of 0x04 packets) ----
    //
    // Official service parse on W25101399:
    //   byte 0  : record marker (0x40 on observed BG records)
    //   byte 1  : packet index low byte
    //   byte 2  : packet index high byte
    //   byte 3  : sample current low byte
    //   byte 4  : sample current high byte
    //   byte 5  : battery percent
    //
    // Record count = LEN / 6. These records do NOT contain final glucose.

    const val BG_RECORD_SIZE = 6
    const val BG_RECORD_MARKER = 0x40
    const val BG_OFFSET_MARKER = 0
    const val BG_OFFSET_PACKET_LO = 1
    const val BG_OFFSET_PACKET_HI = 2
    const val BG_OFFSET_CURRENT_LO = 3
    const val BG_OFFSET_CURRENT_HI = 4
    const val BG_OFFSET_BATTERY = 5

    /** Low-battery warning threshold (percent). */
    const val BATTERY_WARN_PERCENT = 30

    // ---- Algorithm defaults / limits ----

    /** Lowest glucose (mmol/L) kept after offset subtraction; matches app. */
    const val ALGO_MMOL_FLOOR = 3.0

    /** Final output clamp (mmol/L × 10 units). App limits to [1.7, 40.0] mmol/L. */
    const val ALGO_MMOL_MIN_TIMES10 = 17.0
    const val ALGO_MMOL_MAX_TIMES10 = 400.0

    /** Unit conversion used when bridging vendor mmol data into the app's mg/dL path. */
    const val MMOL_TO_MGDL = 18.0182

    /** Warmup guard trigger: packet index >= 19. */
    const val ALGO_WARMUP_PACKET_THRESHOLD = 19.0

    /** Default multiplier from SystemInformation.GuardianFriendsID. */
    const val ALGO_DEFAULT_MULTIPLIER = 1.1

    /** Default packages-per-session from SystemInformation.AndroidVersion. */
    const val ALGO_DEFAULT_PACKAGES = 720

    /** Default init-time guard from the vendor service. */
    const val ALGO_DEFAULT_INIT_TIME_MINUTES = 24.0

    /** Default algorithm version from SystemInformation.spare03. */
    const val ALGO_DEFAULT_VERSION = 0

    /**
     * Official sensitivity / solved K values are in current-per-mmol space.
     * Sub-1 values are not physically plausible for the observed firmware and
     * are a strong indicator of bogus legacy fallback state.
     */
    const val ALGO_MIN_VALID_K = 1.0

    /** Default server-derived deviation if no server response is cached. */
    const val SERVER_DEFAULT_DEVIATION = 0

    /** Default server-derived transmitter10 flag. */
    const val SERVER_DEFAULT_TRANSMITTER10 = 0

    /** Default server-derived protocol type (1 = Protocol01, 2 = Protocol02). */
    const val SERVER_DEFAULT_PROTOCOL_TYPE = 1

    // ---- Lifetime / cadence ----

    /** Sensor rated lifetime in days. */
    const val DEFAULT_RATED_LIFETIME_DAYS = 16

    /** Reading cadence observed on W25101399 in field logs. */
    const val DEFAULT_READING_INTERVAL_MINUTES = 3

    /** Warmup window before reliable values. */
    const val DEFAULT_WARMUP_MINUTES = 60

    // ---- Vendor bootstrap endpoints ----

    const val VENDOR_BASE_URL = "http://monitor.glutec.ru/jeecg-boot/v3"
    const val VENDOR_LOGIN_WITH_PASSWORD_URL = "$VENDOR_BASE_URL/user/monitorUser/loginWithPhoneAndPassword"
    const val VENDOR_QR_CODE_EFFECTIVE_URL = "$VENDOR_BASE_URL/user/monitorUser/checkQrCodeNew"
    const val VENDOR_FIND_BY_BLE_ID_URL = "$VENDOR_BASE_URL/emitterBluetooth/findByBleId"
    const val VENDOR_QUERY_NEWEST_URL = "$VENDOR_BASE_URL/snapshot/snapshot/queryNewest"
    const val VENDOR_VIEW_ALL_SNAPSHOT_DETAIL_URL = "$VENDOR_BASE_URL/databags/databags/viewAllAppSnapshotDetailCondense"
    const val VENDOR_TIMEOUT_MS = 5_000
    const val VENDOR_BOOTSTRAP_RETRY_MS = 15 * 60 * 1000L

    // ---- Device name patterns ----

    /**
     * Advertised device names we treat as MQ/Glutec.
     *
     * The app scans with the generic BLE scanner then disambiguates by NUS service
     * UUID. We keep a small list of known prefixes to support the sensor-picker
     * UI before the GATT service discovery finishes.
     */
    val KNOWN_PREFIXES = arrayOf("Glutec", "GLUTEC", "MQ", "RU-")

    @JvmStatic
    fun isMqDevice(name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        if (KNOWN_PREFIXES.any { trimmed.startsWith(it, ignoreCase = true) }) return true
        return isLikelyPersistedSensorName(trimmed)
    }

    @JvmStatic
    fun isLikelyPersistedSensorName(name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        if (isProvisionalSensorId(trimmed)) return true
        return Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$", RegexOption.IGNORE_CASE).matches(trimmed) ||
            Regex("^[0-9A-F]{12,16}$", RegexOption.IGNORE_CASE).matches(trimmed)
    }

    @JvmStatic
    fun isProvisionalSensorId(name: String?): Boolean =
        name?.trim()?.startsWith(PROVISIONAL_SENSOR_PREFIX, ignoreCase = true) == true

    @JvmStatic
    fun canonicalSensorId(sensorId: String?): String {
        val trimmed = sensorId?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        // BLE MAC address — collapse separators and uppercase.
        if (Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$", RegexOption.IGNORE_CASE).matches(trimmed)) {
            return trimmed.uppercase(Locale.US).replace(":", "")
        }
        // Already a canonical hex id.
        if (Regex("^[0-9A-F]{12,16}$", RegexOption.IGNORE_CASE).matches(trimmed)) {
            return trimmed.uppercase(Locale.US).take(MAX_NATIVE_SENSOR_ID_CHARS)
        }
        return trimmed
    }

    @JvmStatic
    fun deriveInitialSensorId(
        deviceName: String?,
        address: String?,
        qrCodeContent: String? = null,
    ): String {
        qrCodeContent?.trim().orEmpty().takeIf { it.isNotEmpty() }?.let { qr ->
            val hex = Regex("[0-9A-F]+", RegexOption.IGNORE_CASE)
                .findAll(qr.uppercase(Locale.US))
                .joinToString("") { it.value }
            if (hex.length in 12..MAX_NATIVE_SENSOR_ID_CHARS) return hex
        }
        val addressCanonical = canonicalSensorId(address)
        if (addressCanonical.isNotEmpty() &&
            Regex("^[0-9A-F]{12,16}$").matches(addressCanonical)
        ) {
            return addressCanonical
        }
        val fallback = deviceName?.trim().orEmpty()
            .uppercase(Locale.US)
            .filter { it.isLetterOrDigit() }
            .take(MAX_NATIVE_SENSOR_ID_CHARS - PROVISIONAL_SENSOR_PREFIX.length)
            .ifEmpty { "PENDING" }
        return PROVISIONAL_SENSOR_PREFIX + fallback
    }

    @JvmStatic
    fun matchesCanonicalOrKnownNativeAlias(a: String?, b: String?): Boolean {
        val ca = canonicalSensorId(a)
        val cb = canonicalSensorId(b)
        if (ca.isEmpty() || cb.isEmpty()) return false
        return ca.equals(cb, ignoreCase = true)
    }

    // ---- SharedPreferences keys ----

    const val PREF_SENSORS_KEY = "mq_sensors"
    const val PREF_PROTOCOL_TYPE_PREFIX = "mq_protocol_type_"
    const val PREF_DEVIATION_PREFIX = "mq_deviation_"
    const val PREF_TRANSMITTER10_PREFIX = "mq_transmitter10_"
    const val PREF_WARMUP_STARTED_AT_PREFIX = "mq_warmup_started_at_"
    const val PREF_SENSOR_START_AT_PREFIX = "mq_sensor_start_at_"
    const val PREF_K_VALUE_PREFIX = "mq_k_value_"
    const val PREF_B_VALUE_PREFIX = "mq_b_value_"
    const val PREF_SENSITIVITY_PREFIX = "mq_sensitivity_"
    const val PREF_ALGORITHM_VERSION_PREFIX = "mq_algorithm_version_"
    const val PREF_MULTIPLIER_PREFIX = "mq_multiplier_"
    const val PREF_PACKAGES_PREFIX = "mq_packages_"
    const val PREF_LAST_PROCESSED_PREFIX = "mq_last_processed_"
    const val PREF_LAST_PACKET_INDEX_PREFIX = "mq_last_packet_"
    const val PREF_SNAPSHOT_ID_PREFIX = "mq_snapshot_id_"
    const val PREF_LOCAL_RESET_PENDING_PREFIX = "mq_local_reset_pending_"
    const val PREF_QR_CONTENT_PREFIX = "mq_qr_content_"
    const val PREF_AUTH_TOKEN_KEY = "mq_auth_token"
    const val PREF_AUTH_PHONE_KEY = "mq_auth_phone"
    const val PREF_AUTH_PASSWORD_KEY = "mq_auth_password"
}
