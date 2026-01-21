package com.datagami.edudron.content.service.pdf;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.draw.LineSeparator;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight markdown -> PDF renderer for AI-generated course content.
 *
 * Supported (initial version):
 * - Headings: ##, ###, ####
 * - Paragraphs (with basic inline cleanup)
 * - Bullet and numbered lists
 * - Code fences (``` ... ```)
 * - Blockquotes (> ...)
 * - Horizontal rule (---)
 *
 * Intentionally does NOT try to fully implement Markdown.
 */
public class MarkdownPdfRenderer {

    private static final Pattern NUMBERED_ITEM = Pattern.compile("^\\s*(\\d+)\\.\\s+(.*)$");

    private final Font h2;
    private final Font h3;
    private final Font h4;
    private final Font body;
    private final Font mono;
    private final Font quote;

    public MarkdownPdfRenderer() {
        this.h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        this.h3 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        this.h4 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        this.body = FontFactory.getFont(FontFactory.HELVETICA, 11);
        this.mono = FontFactory.getFont(FontFactory.COURIER, 9);
        this.quote = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, new Color(60, 60, 60));
    }

    public void render(Document document, String markdown) throws DocumentException {
        if (markdown == null || markdown.trim().isEmpty()) {
            return;
        }

        List<String> lines = splitLines(markdown);

        boolean inCodeBlock = false;
        List<String> codeLines = new ArrayList<>();

        org.openpdf.text.List activeList = null;
        boolean activeListIsNumbered = false;

        List<String> paragraphLines = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;

            // Code fences
            if (line.trim().startsWith("```")) {
                flushParagraph(document, paragraphLines);
                activeList = flushList(document, activeList);

                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeLines.clear();
                } else {
                    // Close block
                    inCodeBlock = false;
                    addCodeBlock(document, codeLines);
                    codeLines.clear();
                }
                continue;
            }

            if (inCodeBlock) {
                codeLines.add(line);
                continue;
            }

            // Horizontal rule
            if (line.trim().equals("---")) {
                flushParagraph(document, paragraphLines);
                activeList = flushList(document, activeList);
                LineSeparator sep = new LineSeparator(1f, 100f, new Color(200, 200, 200), Element.ALIGN_CENTER, 0);
                document.add(sep);
                document.add(new Paragraph(" "));
                continue;
            }

            // Blank line => end paragraph / list
            if (line.trim().isEmpty()) {
                flushParagraph(document, paragraphLines);
                activeList = flushList(document, activeList);
                continue;
            }

            // Headings
            if (line.startsWith("#### ")) {
                flushParagraph(document, paragraphLines);
                activeList = flushList(document, activeList);
                addHeading(document, stripInlineMarkdown(line.substring(5).trim()), h4);
                continue;
            }
            if (line.startsWith("### ")) {
                flushParagraph(document, paragraphLines);
                activeList = flushList(document, activeList);
                addHeading(document, stripInlineMarkdown(line.substring(4).trim()), h3);
                continue;
            }
            if (line.startsWith("## ")) {
                flushParagraph(document, paragraphLines);
                activeList = flushList(document, activeList);
                addHeading(document, stripInlineMarkdown(line.substring(3).trim()), h2);
                continue;
            }

            // Blockquote
            if (line.startsWith("> ")) {
                flushParagraph(document, paragraphLines);
                activeList = flushList(document, activeList);
                addBlockquote(document, stripInlineMarkdown(line.substring(2).trim()));
                continue;
            }

            // Bullet list
            if (startsWithBullet(line)) {
                flushParagraph(document, paragraphLines);
                String itemText = stripInlineMarkdown(line.trim().substring(2).trim());
                if (activeList == null || activeListIsNumbered) {
                    activeList = new org.openpdf.text.List(org.openpdf.text.List.UNORDERED);
                    activeList.setIndentationLeft(18f);
                    activeListIsNumbered = false;
                }
                activeList.add(new org.openpdf.text.ListItem(new Phrase(itemText, body)));
                continue;
            }

            // Numbered list
            Matcher m = NUMBERED_ITEM.matcher(line);
            if (m.matches()) {
                flushParagraph(document, paragraphLines);
                String itemText = stripInlineMarkdown(m.group(2).trim());
                if (activeList == null || !activeListIsNumbered) {
                    activeList = new org.openpdf.text.List(org.openpdf.text.List.ORDERED);
                    activeList.setIndentationLeft(18f);
                    activeListIsNumbered = true;
                }
                activeList.add(new org.openpdf.text.ListItem(new Phrase(itemText, body)));
                continue;
            }

            // Otherwise: paragraph line (we'll join wrapped lines)
            paragraphLines.add(line);
        }

        // Flush remaining buffers
        if (inCodeBlock) {
            addCodeBlock(document, codeLines);
        }
        flushParagraph(document, paragraphLines);
        flushList(document, activeList);
    }

    private static List<String> splitLines(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] parts = normalized.split("\n", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) out.add(p);
        return out;
    }

    private void addHeading(Document document, String text, Font font) throws DocumentException {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(10f);
        p.setSpacingAfter(6f);
        document.add(p);
    }

    private void addBlockquote(Document document, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, quote);
        p.setIndentationLeft(18f);
        p.setSpacingBefore(6f);
        p.setSpacingAfter(6f);
        document.add(p);
    }

    private void addCodeBlock(Document document, List<String> codeLines) throws DocumentException {
        String code = String.join("\n", codeLines);
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(6f);
        table.setSpacingAfter(8f);

        PdfPCell cell = new PdfPCell(new Phrase(code, mono));
        cell.setBackgroundColor(new Color(245, 245, 245));
        cell.setBorderColor(new Color(230, 230, 230));
        cell.setPaddingTop(6f);
        cell.setPaddingBottom(6f);
        cell.setPaddingLeft(8f);
        cell.setPaddingRight(8f);
        cell.setUseAscender(true);
        cell.setUseDescender(true);
        table.addCell(cell);

        document.add(table);
    }

    private void flushParagraph(Document document, List<String> paragraphLines) throws DocumentException {
        if (paragraphLines.isEmpty()) return;
        String joined = joinWrappedLines(paragraphLines);
        joined = stripInlineMarkdown(joined);
        Paragraph p = new Paragraph(joined, body);
        p.setSpacingBefore(2f);
        p.setSpacingAfter(6f);
        document.add(p);
        paragraphLines.clear();
    }

    private static org.openpdf.text.List flushList(Document document, org.openpdf.text.List list) throws DocumentException {
        if (list != null && list.size() > 0) {
            document.add(list);
            document.add(new Paragraph(" "));
        }
        return null;
    }

    private static boolean startsWithBullet(String line) {
        String t = line.trim();
        return t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ");
    }

    private static String joinWrappedLines(List<String> lines) {
        // Join lines with spaces, preserving intentional paragraph breaks handled elsewhere.
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            String trimmed = l.trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(trimmed);
        }
        return sb.toString();
    }

    private static String stripInlineMarkdown(String text) {
        if (text == null) return "";
        String out = text;

        // Links: [text](url) -> text (url)
        out = out.replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)", "$1 ($2)");

        // Inline code
        out = out.replace("`", "");

        // Bold/italic markers (best-effort)
        out = out.replace("**", "");
        out = out.replace("*", "");
        out = out.replace("__", "");
        out = out.replace("_", "");

        // Avoid weird control chars
        out = out.replaceAll("\\p{Cntrl}&&[^\n\t]", "");

        return out.trim();
    }
}

