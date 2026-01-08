package tk.glucodata;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import tk.glucodata.Log;

public class StatusIcon {
    final private static String LOG_ID = "StatusIcon";
    final static int size = 96; // High res

    StatusIcon() {
    }

    Icon getIcon(String value) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);

        Typeface font = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        paint.setTypeface(font);

        // 1. Measure at test size
        float testSize = 100f;
        paint.setTextSize(testSize);
        Rect bounds = new Rect();
        paint.getTextBounds(value, 0, value.length(), bounds);

        // 2. Calculate Scale
        float textW = bounds.width();
        float textH = bounds.height();

        // Target: Fill 95% of width (minimal padding) and 96% of height
        float targetW = size * 0.95f;
        float targetH = size * 0.96f;

        float scaleW = targetW / textW;
        float scaleH = targetH / textH;

        // 2b. Calculate Max Scale (based on "88" to match '2 numbers' consistency)
        paint.getTextBounds("88", 0, 2, bounds);
        float refW = bounds.width();
        // Use the same targetW for consistency
        float maxScale = targetW / refW;

        // Use the smaller of the two scales (Auto-fit vs Max-cap)
        float scale = Math.min(Math.min(scaleW, scaleH), maxScale);

        float finalSize = testSize * scale;
        paint.setTextSize(finalSize);

        // 3. Re-measure for exact centering
        paint.getTextBounds(value, 0, value.length(), bounds);

        // Center X
        float x = (size - bounds.width()) / 2f - bounds.left;

        // Center Y
        float y = (size - bounds.height()) / 2f - bounds.top;

        canvas.drawText(value, x, y, paint);

        return Icon.createWithBitmap(bitmap);
    }
}
