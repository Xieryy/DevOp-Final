package net.orderzone.idcard.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import net.orderzone.idcard.model.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Generates QR codes using ZXing that embed either:
 *  - a public verification URL for the profile, or
 *  - a structured vCard-style text block with student / employee details.
 */
@Service
public class QrCodeService {

    /** Base URL of this application (used for building verification URLs). */
    @Value("${app.base-url:http://localhost:8081}")
    private String appBaseUrl;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Generates a QR code that points to the public profile-verification endpoint.
     * Scanning the code will open:  {appBaseUrl}/api/profiles/uuid/{uuid}
     */
    public byte[] generateVerificationQrBytes(Profile profile, int width, int height) {
        String url = buildVerificationUrl(profile.getUuid());
        return generateQrCodeBytes(url, width, height);
    }

    /** Same as {@link #generateVerificationQrBytes} but returns a Base64 data-URI. */
    public String generateVerificationQrBase64(Profile profile, int width, int height) {
        byte[] bytes = generateVerificationQrBytes(profile, width, height);
        return toDataUri(bytes);
    }

    /**
     * Generates a QR code whose payload is a compact text block containing
     * the key student / employee details (suitable for offline scanning).
     *
     * <pre>
     * NAME: John Doe
     * ID:   2026-ENG-014
     * DEPT: Engineering
     * TYPE: STUDENT
     * EXP:  2027-12-31
     * URL:  http://localhost:8081/api/profiles/uuid/xxxx
     * </pre>
     */
    public byte[] generateDetailsQrBytes(Profile profile, int width, int height) {
        String payload = buildDetailsPayload(profile);
        return generateQrCodeBytes(payload, width, height);
    }

    /** Same as {@link #generateDetailsQrBytes} but returns a Base64 data-URI. */
    public String generateDetailsQrBase64(Profile profile, int width, int height) {
        byte[] bytes = generateDetailsQrBytes(profile, width, height);
        return toDataUri(bytes);
    }

    /**
     * Low-level: generates a raw PNG byte array containing a QR Code from any text.
     * Uses error-correction level M (≈15 % restore capability) and a 2-module quiet zone.
     */
    public byte[] generateQrCodeBytes(String text, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);          // quiet-zone in modules
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height, hints);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code: " + e.getMessage(), e);
        }
    }

    /**
     * Low-level: wraps raw bytes in a Base64 PNG data-URI
     * (data:image/png;base64,…) ready for use in HTML or iText.
     */
    public String generateQrCodeBase64(String text, int width, int height) {
        return toDataUri(generateQrCodeBytes(text, width, height));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Builds the canonical profile-verification URL. */
    public String buildVerificationUrl(String uuid) {
        return appBaseUrl + "/api/profiles/uuid/" + uuid;
    }

    /** Assembles the offline-readable text payload embedded in the detail QR code. */
    private String buildDetailsPayload(Profile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("NAME: ").append(safeStr(profile.getFullName())).append("\n");
        sb.append("ID:   ").append(safeStr(profile.getRegistrationNumber())).append("\n");
        sb.append("DEPT: ").append(safeStr(profile.getDepartment())).append("\n");
        sb.append("TYPE: ").append(profile.getType() != null ? profile.getType().name() : "N/A").append("\n");
        sb.append("EXP:  ").append(profile.getExpiryDate() != null ? profile.getExpiryDate().toString() : "N/A").append("\n");
        if (profile.getUuid() != null) {
            sb.append("URL:  ").append(buildVerificationUrl(profile.getUuid()));
        }
        return sb.toString();
    }

    private String safeStr(String value) {
        return value != null ? value : "N/A";
    }

    private String toDataUri(byte[] bytes) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }
}