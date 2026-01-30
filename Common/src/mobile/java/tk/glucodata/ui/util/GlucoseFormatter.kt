package tk.glucodata.ui.util

import java.util.Locale

object GlucoseFormatter {

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
