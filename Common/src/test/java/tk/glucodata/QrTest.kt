package tk.glucodata

import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer

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
            val image = ImageIO.read(file)
            val width = image.width
            val height = image.height
            val pixels = IntArray(width * height)
            image.getRGB(0, 0, width, height, pixels, 0, width)

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
