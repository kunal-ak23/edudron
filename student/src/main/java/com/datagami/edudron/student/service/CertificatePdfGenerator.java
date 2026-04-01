package com.datagami.edudron.student.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates certificate PDF documents using PDFBox and ZXing.
 * Reads template config (JSON with "fields" list and "pageSize") and renders
 * text fields, QR codes, etc. at configured positions.
 */
@Component
public class CertificatePdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(CertificatePdfGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    /**
     * Generate a certificate PDF from template config.
     *
     * @param templateConfig the template config map containing "fields" list and optional "pageSize"
     * @param studentName    student full name
     * @param courseName     course title
     * @param credentialId   unique credential ID (e.g., EDU-2026-ABCDE)
     * @param verificationUrl full verification URL for QR code
     * @param issuedAt       certificate issue date
     * @return PDF bytes
     */
    public byte[] generatePdf(Map<String, Object> templateConfig, String studentName,
                               String courseName, String credentialId,
                               String verificationUrl, OffsetDateTime issuedAt) {
        try (PDDocument document = new PDDocument()) {
            PDRectangle pageSize = resolvePageSize(templateConfig);
            PDPage page = new PDPage(pageSize);
            document.addPage(page);

            float pageHeight = pageSize.getHeight();
            float pageWidth = pageSize.getWidth();

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fields = (List<Map<String, Object>>) templateConfig.get("fields");
                if (fields == null) {
                    log.warn("Template config has no 'fields' list, generating empty PDF");
                    fields = List.of();
                }

                for (Map<String, Object> field : fields) {
                    String type = (String) field.get("type");
                    if (type == null) continue;

                    float x = floatVal(field.get("x"), 0);
                    float y = floatVal(field.get("y"), 0);
                    // Transform from top-left origin (config) to bottom-left origin (PDFBox)
                    float pdfY = pageHeight - y;

                    switch (type) {
                        case "studentName":
                            renderCenteredText(cs, studentName, x, pdfY, pageWidth, field);
                            break;
                        case "courseName":
                            renderCenteredText(cs, courseName, x, pdfY, pageWidth, field);
                            break;
                        case "credentialId":
                            renderCenteredText(cs, credentialId, x, pdfY, pageWidth, field);
                            break;
                        case "date":
                            String dateStr = issuedAt != null ? issuedAt.format(DATE_FORMATTER) : "";
                            renderCenteredText(cs, dateStr, x, pdfY, pageWidth, field);
                            break;
                        case "customText":
                            String text = (String) field.getOrDefault("text", "");
                            renderCenteredText(cs, text, x, pdfY, pageWidth, field);
                            break;
                        case "qrCode":
                            renderQrCode(document, cs, verificationUrl, x, pdfY, field);
                            break;
                        case "image":
                        case "logo":
                            // TODO: implement image/logo rendering in v2
                            log.debug("Skipping image/logo field (not implemented in v1)");
                            break;
                        default:
                            log.debug("Unknown field type '{}', skipping", type);
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to generate certificate PDF", e);
        }
    }

    private void renderCenteredText(PDPageContentStream cs, String text, float x, float pdfY,
                                     float pageWidth, Map<String, Object> field) throws IOException {
        if (text == null || text.isEmpty()) return;

        PDType1Font font = resolveFontBold(field) ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
        float fontSize = floatVal(field.get("fontSize"), 16);

        float textWidth = font.getStringWidth(text) / 1000f * fontSize;
        // Center text horizontally at x position
        float textX = x - (textWidth / 2f);
        // Clamp to page bounds
        if (textX < 10) textX = 10;

        // Set color
        float[] rgb = parseHexColor((String) field.get("color"));
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(textX, pdfY);
        cs.showText(text);
        cs.endText();
    }

    private void renderQrCode(PDDocument document, PDPageContentStream cs,
                               String url, float x, float pdfY,
                               Map<String, Object> field) throws IOException {
        if (url == null || url.isEmpty()) return;

        int size = (int) floatVal(field.get("size"), 100);

        try {
            QRCodeWriter qrWriter = new QRCodeWriter();
            BitMatrix matrix = qrWriter.encode(url, BarcodeFormat.QR_CODE, size, size);
            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(matrix);

            PDImageXObject pdImage = LosslessFactory.createFromImage(document, qrImage);
            // Position QR code centered at x, with top at pdfY
            float qrX = x - (size / 2f);
            float qrY = pdfY - size;
            cs.drawImage(pdImage, qrX, qrY, size, size);

        } catch (WriterException e) {
            log.error("Failed to generate QR code for URL: {}", url, e);
        }
    }

    @SuppressWarnings("unchecked")
    private PDRectangle resolvePageSize(Map<String, Object> config) {
        Object pageSizeObj = config.get("pageSize");

        // Handle Map format from design doc: {"width": 842, "height": 595}
        if (pageSizeObj instanceof Map) {
            Map<String, Object> sizeMap = (Map<String, Object>) pageSizeObj;
            float width = floatVal(sizeMap.get("width"), PDRectangle.A4.getHeight());
            float height = floatVal(sizeMap.get("height"), PDRectangle.A4.getWidth());
            return new PDRectangle(width, height);
        }

        // Handle String preset format
        String pageSize = pageSizeObj != null ? pageSizeObj.toString() : "A4_LANDSCAPE";
        switch (pageSize) {
            case "A4":
                return PDRectangle.A4;
            case "LETTER":
                return PDRectangle.LETTER;
            case "LETTER_LANDSCAPE":
                return new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth());
            case "A4_LANDSCAPE":
            default:
                return new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        }
    }

    private boolean resolveFontBold(Map<String, Object> field) {
        // Check "fontWeight": "bold" (design doc format)
        Object fontWeight = field.get("fontWeight");
        if (fontWeight instanceof String && "bold".equalsIgnoreCase((String) fontWeight)) {
            return true;
        }
        // Also check legacy "bold": true format
        Object bold = field.get("bold");
        if (bold instanceof Boolean) return (Boolean) bold;
        if (bold instanceof String) return "true".equalsIgnoreCase((String) bold);
        return false;
    }

    /**
     * Parse hex color string (e.g., "#1E3A5F" or "1E3A5F") to RGB floats [0..1].
     */
    private float[] parseHexColor(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new float[]{0f, 0f, 0f}; // default black
        }
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6) {
            return new float[]{0f, 0f, 0f};
        }
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new float[]{r / 255f, g / 255f, b / 255f};
        } catch (NumberFormatException e) {
            return new float[]{0f, 0f, 0f};
        }
    }

    private float floatVal(Object value, float defaultVal) {
        if (value == null) return defaultVal;
        if (value instanceof Number) return ((Number) value).floatValue();
        try {
            return Float.parseFloat(value.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
