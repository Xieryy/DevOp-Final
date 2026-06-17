package net.orderzone.idcard.service;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import net.orderzone.idcard.model.BarcodeType;
import net.orderzone.idcard.model.Profile;
import net.orderzone.idcard.model.Template;
import net.orderzone.idcard.repository.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;

@Service
public class PdfExportService {

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private QrCodeService qrCodeService;

    @Autowired
    private BarcodeService barcodeService;

    @Value("${upload.photo-dir:uploads/photos}")
    private String photoUploadDir;

    /**
     * Helper to safely decode Hex color strings (e.g. "#1D4ED8") to iText DeviceRgb objects.
     */
    private DeviceRgb parseHexColor(String hex, String defaultHex) {
        try {
            String cleanHex = (hex != null ? hex : defaultHex).replace("#", "");
            int r = Integer.parseInt(cleanHex.substring(0, 2), 16);
            int g = Integer.parseInt(cleanHex.substring(2, 4), 16);
            int b = Integer.parseInt(cleanHex.substring(4, 6), 16);
            return new DeviceRgb(r, g, b);
        } catch (Exception e) {
            return new DeviceRgb(29, 78, 216); // Fallback royal blue
        }
    }

    public byte[] generateIdCardPdf(Long profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found with ID: " + profileId));

        Template template = profile.getTemplate();
        if (template == null) {
            template = Template.builder()
                    .organizationName("Institute of Technology of Cambodia")
                    .tagline("Science and Technology for Development")
                    .primaryColor("#003366")
                    .secondaryColor("#E0E7FF")
                    .textColor("#111827")
                    .build();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 1. Set up CR80 Dimensions (Standard ID card size: 153 x 243 points)
        PageSize customCardSize = new PageSize(153, 243);
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, customCardSize);
        document.setMargins(0, 0, 0, 0);

        // 2. Map Theme Colors
        DeviceRgb primaryColor   = parseHexColor(template.getPrimaryColor(),   "#003366");
        DeviceRgb secondaryColor = parseHexColor(template.getSecondaryColor(), "#E0E7FF");
        DeviceRgb textColor      = parseHexColor(template.getTextColor(),      "#111827");

        // 3. Render Header Banner Row Block
        Table headerTable = new Table(1).useAllAvailableWidth();
        headerTable.setBackgroundColor(primaryColor);
        headerTable.setBorderBottom(new SolidBorder(secondaryColor, 2f));
        headerTable.setPadding(8f);

        Cell titleCell = new Cell().add(new Paragraph(template.getOrganizationName())
                .setFontSize(8f).setBold()
                .setFontColor(DeviceRgb.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(0));
        titleCell.setBorder(Border.NO_BORDER);
        headerTable.addCell(titleCell);

        if (template.getTagline() != null) {
            Cell taglineCell = new Cell().add(new Paragraph(template.getTagline())
                    .setFontSize(5f)
                    .setFontColor(DeviceRgb.WHITE)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMargin(0));
            taglineCell.setBorder(Border.NO_BORDER);
            headerTable.addCell(taglineCell);
        }
        document.add(headerTable);

        // 4. Render Profile Photo Row Block
        Image photoImage;
        try {
            if (profile.getPhotoFileName() != null) {
                String fullPath = Paths.get(photoUploadDir, profile.getPhotoFileName()).toString();
                photoImage = new Image(ImageDataFactory.create(fullPath));
            } else {
                photoImage = new Image(ImageDataFactory.create(
                        "https://via.placeholder.com/120x140.png?text=No+Photo"));
            }
        } catch (Exception e) {
            photoImage = new Image(ImageDataFactory.create(new byte[0]));
        }
        photoImage.setWidth(60f).setHeight(70f)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setMarginTop(12f).setMarginBottom(8f)
                .setBorder(new SolidBorder(primaryColor, 1.5f));
        document.add(photoImage);

        // 5. Render Core Identity Body Texts
        document.add(new Paragraph(profile.getFullName())
                .setFontSize(10f).setBold()
                .setFontColor(textColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(1f));

        document.add(new Paragraph(profile.getTitle() != null ? profile.getTitle() : "")
                .setFontSize(6.5f)
                .setFontColor(new DeviceRgb(100, 116, 139))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(6f));

        // 6. Metadata Details Layout Container (Grid Table)
        Table detailsTable = new Table(new float[]{45f, 85f});
        detailsTable.setWidth(130f)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setBackgroundColor(new DeviceRgb(248, 250, 252))
                .setPadding(4f);

        addDetailRow(detailsTable, "REG ID:", profile.getRegistrationNumber());
        addDetailRow(detailsTable, "DEPT:",   profile.getDepartment());
        addDetailRow(detailsTable, "EXPIRY:", profile.getExpiryDate() != null
                ? profile.getExpiryDate().toString() : "N/A");
        document.add(detailsTable);

        // -----------------------------------------------------------------------
        // 7. QR Code + Barcode Section
        //    Layout: QR code (left) | barcode strip (right) in a two-column table
        // -----------------------------------------------------------------------
        Table codeTable = new Table(new float[]{55f, 90f});
        codeTable.setWidth(145f)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setMarginTop(4f)
                .setMarginBottom(4f);

        // --- QR Code cell (left) ---
        Cell qrCell = new Cell().setBorder(Border.NO_BORDER)
                .setPadding(1f);
        try {
            if (profile.getUuid() != null && !profile.getUuid().isBlank()) {
                byte[] qrBytes = qrCodeService.generateVerificationQrBytes(profile, 120, 120);
                ImageData qrData = ImageDataFactory.create(qrBytes);
                Image qrImage = new Image(qrData)
                        .setWidth(50f).setHeight(50f)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                qrCell.add(qrImage);
                qrCell.add(new Paragraph("Verify")
                        .setFontSize(4f)
                        .setFontColor(new DeviceRgb(100, 116, 139))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(1f));
            } else {
                qrCell.add(new Paragraph("No QR")
                        .setFontSize(5f)
                        .setFontColor(new DeviceRgb(150, 150, 150))
                        .setTextAlignment(TextAlignment.CENTER));
            }
        } catch (Exception e) {
            qrCell.add(new Paragraph("QR Error")
                    .setFontSize(4f)
                    .setFontColor(new DeviceRgb(200, 50, 50)));
        }

        // --- Barcode cell (right) ---
        Cell barcodeCell = new Cell().setBorder(Border.NO_BORDER)
                .setPadding(1f)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
        try {
            if (profile.getRegistrationNumber() != null
                    && !profile.getRegistrationNumber().isBlank()) {

                BarcodeType bType = profile.getBarcodeType() != null
                        ? profile.getBarcodeType() : BarcodeType.CODE_128;

                byte[] barBytes = barcodeService.generateBarcodeBytes(
                        profile.getRegistrationNumber(), bType, 230, 60);
                ImageData barData = ImageDataFactory.create(barBytes);
                Image barImage = new Image(barData)
                        .setWidth(85f).setHeight(28f)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                barcodeCell.add(barImage);
                barcodeCell.add(new Paragraph(profile.getRegistrationNumber())
                        .setFontSize(4f)
                        .setFontColor(new DeviceRgb(51, 65, 85))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(1f));
            } else {
                barcodeCell.add(new Paragraph("No Barcode")
                        .setFontSize(5f)
                        .setFontColor(new DeviceRgb(150, 150, 150))
                        .setTextAlignment(TextAlignment.CENTER));
            }
        } catch (Exception e) {
            barcodeCell.add(new Paragraph("Barcode Error")
                    .setFontSize(4f)
                    .setFontColor(new DeviceRgb(200, 50, 50)));
        }

        codeTable.addCell(qrCell);
        codeTable.addCell(barcodeCell);
        document.add(codeTable);

        // 8. Sticky Footer Banner Block
        Table footerTable = new Table(1).useAllAvailableWidth();
        footerTable.setBackgroundColor(new DeviceRgb(241, 245, 249));
        footerTable.setBorderTop(new SolidBorder(new DeviceRgb(203, 213, 225), 0.5f));
        footerTable.setPadding(5f);
        footerTable.setFixedPosition(0, 0, 153f);

        Cell footerCell = new Cell().add(new Paragraph(profile.getType().toString() + " ID CARD SYSTEM")
                .setFontSize(5.5f).setBold()
                .setFontColor(new DeviceRgb(100, 116, 139))
                .setTextAlignment(TextAlignment.CENTER));
        footerCell.setBorder(Border.NO_BORDER);
        footerTable.addCell(footerCell);
        document.add(footerTable);

        document.close();
        return baos.toByteArray();
    }

    private void addDetailRow(Table table, String label, String value) {
        Cell labelCell = new Cell().add(new Paragraph(label)
                .setFontSize(5.5f).setBold()
                .setFontColor(new DeviceRgb(100, 116, 139)));
        labelCell.setBorder(Border.NO_BORDER);

        Cell valCell = new Cell().add(new Paragraph(value != null ? value : "N/A")
                .setFontSize(5.5f).setBold()
                .setFontColor(new DeviceRgb(51, 65, 85)));
        valCell.setBorder(Border.NO_BORDER);

        table.addCell(labelCell);
        table.addCell(valCell);
    }
}