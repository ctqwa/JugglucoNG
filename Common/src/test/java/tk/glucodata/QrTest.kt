package tk.glucodata

import android.graphics.BitmapFactory
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Ignore
import org.junit.Test
import java.io.File

@Ignore("Manual local QR decode helper; not part of the Android unit test suite")
class QrTest {
    @Test
    fun decodeUserImage() {
        val path = "/Users/ctqwa/.gemini/antigravity/brain/9fdb3a04-4326-4e3c-ab59-a31488c782f8/uploaded_image_1767722491881.jpg"
        val file = File(path)
        if (!file.exists()) {
            println("File not found: $path")
            return
        }

        try {
            val image = BitmapFactory.decodeFile(path) ?: run {
                println("Could not decode image: $path")
                return
            }
            val width = image.width
            val height = image.height
            val pixels = IntArray(width * height)
            image.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = MultiFormatReader().decode(binaryBitmap)
            
            println("DECODED RAW: \"${result.text}\"")
            println("LENGTH: ${result.text.length}")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
