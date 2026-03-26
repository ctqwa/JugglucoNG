package tk.glucodata;

import static java.lang.String.format;

final class ExchangeGlucosePayload {
    private static final double MGDL_PER_MMOLL = 18.0182;

    final String sensorId;
    final String primaryText;
    final double primaryDisplayValue;
    final int primaryMgdl;
    final float rate;
    final long timeMillis;
    final int sensorGen;

    private ExchangeGlucosePayload(
            String sensorId,
            String primaryText,
            double primaryDisplayValue,
            int primaryMgdl,
            float rate,
            long timeMillis,
            int sensorGen) {
        this.sensorId = sensorId;
        this.primaryText = primaryText;
        this.primaryDisplayValue = primaryDisplayValue;
        this.primaryMgdl = primaryMgdl;
        this.rate = rate;
        this.timeMillis = timeMillis;
        this.sensorGen = sensorGen;
    }

    String getSensorId() {
        return sensorId;
    }

    String getPrimaryText() {
        return primaryText;
    }

    double getPrimaryDisplayValue() {
        return primaryDisplayValue;
    }

    int getPrimaryMgdl() {
        return primaryMgdl;
    }

    float getRate() {
        return rate;
    }

    long getTimeMillis() {
        return timeMillis;
    }

    int getSensorGen() {
        return sensorGen;
    }

    static ExchangeGlucosePayload resolve(
            String preferredSensorId,
            double fallbackDisplayValue,
            float fallbackRate,
            long fallbackTimeMillis,
            int fallbackSensorGen,
            String fallbackPrimaryText) {
        CurrentDisplaySource.Snapshot current = null;
        try {
            current = CurrentDisplaySource.resolveCurrent(Notify.glucosetimeout, preferredSensorId);
        } catch (Throwable th) {
            if (Log.doLog) {
                Log.i("ExchangeGlucosePayload", "resolveCurrent failed " + th);
            }
        }

        if (current != null) {
            final double primaryDisplayValue = isFinitePositive(current.getPrimaryValue())
                    ? current.getPrimaryValue()
                    : (isFinitePositive(current.getSharedDisplayValue())
                            ? current.getSharedDisplayValue()
                            : fallbackDisplayValue);
            final String sensorId = notBlank(current.getSensorId()) ? current.getSensorId() : preferredSensorId;
            final String primaryText = formatPrimary(primaryDisplayValue, current.getPrimaryStr());
            final float rate = Float.isFinite(current.getRate()) ? current.getRate() : fallbackRate;
            final long timeMillis = current.getTimeMillis() > 0L ? current.getTimeMillis() : fallbackTimeMillis;
            final int sensorGen = current.getSensorGen() != 0 ? current.getSensorGen() : fallbackSensorGen;
            final int primaryMgdl = toMgdl(primaryDisplayValue);
            return new ExchangeGlucosePayload(
                    sensorId,
                    primaryText,
                    primaryDisplayValue,
                    primaryMgdl,
                    rate,
                    timeMillis,
                    sensorGen);
        }

        return new ExchangeGlucosePayload(
                preferredSensorId,
                formatPrimary(fallbackDisplayValue, fallbackPrimaryText),
                fallbackDisplayValue,
                toMgdl(fallbackDisplayValue),
                fallbackRate,
                fallbackTimeMillis,
                fallbackSensorGen);
    }

    private static boolean isFinitePositive(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isEmpty();
    }

    private static int toMgdl(double displayValue) {
        if (!isFinitePositive(displayValue)) {
            return 0;
        }
        return (int) Math.round(Applic.unit == 1 ? displayValue * MGDL_PER_MMOLL : displayValue);
    }

    private static String formatPrimary(double displayValue, String fallbackPrimaryText) {
        if (notBlank(fallbackPrimaryText)) {
            return fallbackPrimaryText;
        }
        final String displayFormat = Notify.pureglucoseformat != null
                ? Notify.pureglucoseformat
                : (Applic.unit == 1 ? "%.1f" : "%.0f");
        return format(Applic.usedlocale, displayFormat, displayValue);
    }
}
