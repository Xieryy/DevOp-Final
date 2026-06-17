package net.orderzone.idcard.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import net.orderzone.idcard.model.BarcodeType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Base64;

/**
 * Generates linear barcodes (Code-128 and EAN-13) as raw PNG bytes or Base64
 * data-URIs using the ZXing MultiFormatWriter.
 */
@Service
public class BarcodeService {

    /**
     * Generates a barcode image as a raw PNG byte array.
     *
     * @param data       the text / numeric data to encode
     * @param type       the symbology (CODE_128 or EAN_13)
     * @param widthPx    image width in pixels
     * @param heightPx   image height in pixels
     * @return PNG bytes of the rendered barcode
     */
    public byte[] generateBarcodeBytes(String data, BarcodeType type, int widthPx, int heightPx) {
        try {
            BarcodeFormat format = resolveFormat(type);

            // EAN-13 requires exactly 12 or 13 digits; pad / trim if necessary
            String encodedData = (format == BarcodeFormat.EAN_13) ? normalizeEan13(data) : data;

            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 2);      // quiet-zone padding (modules)

            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix bitMatrix = writer.encode(encodedData, format, widthPx, heightPx, hints);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate barcode [" + type + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a Base64 data-URI (data:image/png;base64,...) suitable for
     * embedding directly in an HTML &lt;img src="..."&gt; or iText image.
     */
    public String generateBarcodeBase64(String data, BarcodeType type, int widthPx, int heightPx) {
        byte[] bytes = generateBarcodeBytes(data, type, widthPx, heightPx);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Maps the internal BarcodeType enum to the ZXing BarcodeFormat constant. */
    private BarcodeFormat resolveFormat(BarcodeType type) {
        if (type == null) return BarcodeFormat.CODE_128;
        return switch (type) {
            case EAN_13   -> BarcodeFormat.EAN_13;
            case CODE_128 -> BarcodeFormat.CODE_128;
        };
    }

    /**
     * ZXing EAN-13 encoder requires exactly 12 digits (the check digit is added
     * automatically) or exactly 13 digits.  This helper ensures we always
     * supply a valid string regardless of what the registration-number looks like.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Strip all non-digit characters.</li>
     *   <li>If ≥ 13 digits, take the first 13.</li>
     *   <li>If &lt; 13, left-pad with zeros to reach 12 digits (ZXing appends the check digit).</li>
     * </ol>
     */
    private String normalizeEan13(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() >= 13) {
            return digits.substring(0, 13);
        }
        // Pad to 12 digits so ZXing can compute and append the 13th check digit
        return String.format("%012d", Long.parseLong(digits.isEmpty() ? "0" : digits));
    }
}
