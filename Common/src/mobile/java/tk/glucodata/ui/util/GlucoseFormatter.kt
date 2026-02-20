package tk.glucodata.ui.util

import java.util.Locale

object GlucoseFormatter {

    const val MGDL_TO_MMOL = 0.0555f

    /**
     * Format a glucose value based on the unit type.
     * mg/dL -> No decimals (e.g. "100")
     * mmol/L -> One decimal (e.g. "5.5")
     */
    fun format(value: Float, isMmol: Boolean): String {
        return if (isMmol) {
            String.format(Locale.getDefault(), "%.1f", value)
        } else {
            String.format(Locale.getDefault(), "%.0f", value)
        }
    }

    /**
     * Convert mg/dL to mmol/L
     */
    fun mgToMmol(mgDl: Float): Float = mgDl * MGDL_TO_MMOL

    /**
     * Convert mmol/L to mg/dL
     */
    fun mmolToMg(mmol: Float): Float = mmol / MGDL_TO_MMOL

    /**
     * Convert an internal mg/dL value to the currently displayed unit value.
     */
    fun displayFromMgDl(valueMgDl: Float, isMmol: Boolean): Float {
        return if (isMmol) mgToMmol(valueMgDl) else valueMgDl
    }

    /**
     * Format a value stored in mg/dL to the selected display unit.
     */
    fun formatFromMgDl(valueMgDl: Float, isMmol: Boolean): String {
        return format(displayFromMgDl(valueMgDl, isMmol), isMmol)
    }

    /**
     * Format for CSV (Always US Locale / Dot separator)
     */
    fun formatCsv(value: Float, unit: String): String {
        val isMmol = isMmol(unit)
        return if (isMmol) {
            String.format(Locale.US, "%.1f", value)
        } else {
            String.format(Locale.US, "%.0f", value)
        }
    }

    /**
     * Check if unit string implies mmol/L.
     */
    fun isMmol(unit: String?): Boolean {
        return unit?.contains("mmol", ignoreCase = true) == true
    }

    /**
     * Check global app state for mmol/L preference.
     */
    fun isMmolApp(): Boolean {
        return tk.glucodata.Applic.unit == 1
    }
}
