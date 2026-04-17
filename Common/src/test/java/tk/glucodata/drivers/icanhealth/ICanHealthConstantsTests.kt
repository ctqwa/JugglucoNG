package tk.glucodata.drivers.icanhealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ICanHealthConstantsTests {

    @Test
    fun canonicalSensorId_normalizesHexIdsToUppercase() {
        assertEquals(
            "8760080A00070000",
            ICanHealthConstants.canonicalSensorId("8760080a00070000")
        )
    }

    @Test
    fun nativeShortSensorAlias_returnsTrailingNativeAliasForCanonicalId() {
        assertEquals(
            "80A00070000",
            ICanHealthConstants.nativeShortSensorAlias("8760080A00070000")
        )
    }

    @Test
    fun matchesCanonicalOrKnownNativeAlias_acceptsCanonicalAndShortAlias() {
        assertTrue(
            ICanHealthConstants.matchesCanonicalOrKnownNativeAlias(
                "8760080A00070000",
                "80A00070000"
            )
        )
    }

    @Test
    fun matchesCanonicalOrKnownNativeAlias_rejectsUnrelatedIds() {
        assertFalse(
            ICanHealthConstants.matchesCanonicalOrKnownNativeAlias(
                "8760080A00070000",
                "X-222227JR7C"
            )
        )
    }

    @Test
    fun normalizePseudoRawCurrentMgdl_stripsVendorPrefixAndScalesCurrent() {
        assertEquals(
            33.63f,
            ICanHealthConstants.normalizePseudoRawCurrentMgdl(2_503_363f),
            0.001f
        )
    }

    @Test
    fun normalizePseudoRawCurrentMgdl_scalesPlainDecodedCurrent() {
        assertEquals(
            33.63f,
            ICanHealthConstants.normalizePseudoRawCurrentMgdl(3_363f),
            0.001f
        )
    }

    @Test
    fun normalizePseudoRawCurrentMgdl_rejects_invalid_values() {
        assertTrue(ICanHealthConstants.normalizePseudoRawCurrentMgdl(Float.NaN).isNaN())
        assertTrue(ICanHealthConstants.normalizePseudoRawCurrentMgdl(0f).isNaN())
    }
}
