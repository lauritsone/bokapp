package app.shelfmark.ui.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.shelfmark.data.Isbn
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Reads EAN-13 barcodes on-device with ML Kit and reports the first valid ISBN once.
 * Callbacks arrive on the main thread.
 */
class IsbnAnalyzer(private val onIsbn: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).build(),
    )

    /** Set to false to pause scanning (e.g. while taking a cover photo). */
    @Volatile var active = true

    @Volatile private var delivered = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (!active || delivered || media == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val isbn = codes.firstNotNullOfOrNull { code -> code.rawValue?.let(Isbn::normalize) }
                if (isbn != null && active && !delivered) {
                    delivered = true
                    onIsbn(isbn)
                }
            }
            .addOnCompleteListener { image.close() }
    }

    fun close() = scanner.close()
}
