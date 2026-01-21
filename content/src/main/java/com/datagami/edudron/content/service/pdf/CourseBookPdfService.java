package com.datagami.edudron.content.service.pdf;

import com.datagami.edudron.content.domain.LectureContent;
import com.datagami.edudron.content.dto.CourseDTO;
import com.datagami.edudron.content.dto.LectureContentDTO;
import com.datagami.edudron.content.dto.LectureDTO;
import com.datagami.edudron.content.dto.SectionDTO;
import com.datagami.edudron.content.service.CourseService;
import com.datagami.edudron.content.service.LectureService;
import com.datagami.edudron.content.service.SectionService;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Chunk;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfAction;
import org.openpdf.text.pdf.PdfOutline;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.draw.DottedLineSeparator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseBookPdfService {

    private final CourseService courseService;
    private final SectionService sectionService;
    private final LectureService lectureService;
    private final MarkdownPdfRenderer markdownPdfRenderer;

    private final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22);
    private final Font h1Font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private final Font h2Font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private final Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

    public CourseBookPdfService(
            CourseService courseService,
            SectionService sectionService,
            LectureService lectureService
    ) {
        this.courseService = courseService;
        this.sectionService = sectionService;
        this.lectureService = lectureService;
        this.markdownPdfRenderer = new MarkdownPdfRenderer();
    }

    public byte[] generateCourseBookPdf(String courseId) {
        try {
            CourseDTO course = courseService.getCourseById(courseId);
            List<SectionDTO> sections = sectionService.getSectionsByCourse(courseId);
            for (SectionDTO section : sections) {
                List<LectureDTO> lectures = lectureService.getLecturesBySection(section.getId());
                section.setLectures(lectures);
            }

            return generate(course, sections);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate course book PDF: " + e.getMessage(), e);
        }
    }

    private byte[] generate(CourseDTO course, List<SectionDTO> sections) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 60, 55);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        writer.setPageEvent(new PageNumberFooter());

        document.open();

        // Title page
        addTitlePage(document, course);
        document.newPage();

        // Build TOC entries up-front
        List<TocEntry> tocEntries = buildTocEntries(sections);

        // TOC placeholders
        Map<String, PdfTemplate> tocTemplatesByKey = new LinkedHashMap<>();
        addTableOfContents(document, writer, tocEntries, tocTemplatesByKey);
        document.newPage();

        // Body with recorded page numbers
        Map<String, Integer> pageNumbersByKey = new LinkedHashMap<>();
        addBody(document, writer, course, sections, pageNumbersByKey);

        // Fill TOC templates
        fillTocTemplates(writer, tocEntries, tocTemplatesByKey, pageNumbersByKey);

        document.close();
        return baos.toByteArray();
    }

    private void addTitlePage(Document document, CourseDTO course) throws DocumentException {
        Paragraph title = new Paragraph(
                safe(course.getTitle()),
                titleFont
        );
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(180f);
        title.setSpacingAfter(18f);
        document.add(title);

        if (course.getDescription() != null && !course.getDescription().isBlank()) {
            Paragraph desc = new Paragraph(course.getDescription().trim(), bodyFont);
            desc.setAlignment(Element.ALIGN_CENTER);
            desc.setSpacingAfter(24f);
            document.add(desc);
        }

        String generatedAt = "Generated at: " + OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        Paragraph meta = new Paragraph(generatedAt, FontFactory.getFont(FontFactory.HELVETICA, 9));
        meta.setAlignment(Element.ALIGN_CENTER);
        document.add(meta);
    }

    private List<TocEntry> buildTocEntries(List<SectionDTO> sections) {
        List<TocEntry> entries = new ArrayList<>();
        int sectionNo = 0;
        for (SectionDTO section : sections) {
            sectionNo++;
            String sectionKey = "S:" + section.getId();
            entries.add(new TocEntry(sectionKey, sectionNo + ". " + safe(section.getTitle())));

            List<LectureDTO> lectures = section.getLectures() != null ? section.getLectures() : List.of();
            int lectureNo = 0;
            for (LectureDTO lecture : lectures) {
                lectureNo++;
                String lectureKey = "L:" + lecture.getId();
                entries.add(new TocEntry(lectureKey, "   " + sectionNo + "." + lectureNo + " " + safe(lecture.getTitle())));
            }
        }
        return entries;
    }

    private void addTableOfContents(
            Document document,
            PdfWriter writer,
            List<TocEntry> entries,
            Map<String, PdfTemplate> tocTemplatesByKey
    ) throws DocumentException {
        Paragraph h = new Paragraph("Table of Contents", h1Font);
        h.setSpacingBefore(10f);
        h.setSpacingAfter(12f);
        document.add(h);

        PdfContentByte cb = writer.getDirectContent();

        for (TocEntry entry : entries) {
            DottedLineSeparator dots = new DottedLineSeparator();
            dots.setGap(2f);

            PdfTemplate template = cb.createTemplate(42, 12);
            tocTemplatesByKey.put(entry.key(), template);
            Image img = Image.getInstance(template);
            // Place at baseline (y=0) with slight downward offset for nicer alignment
            Chunk pageNumberPlaceholder = new Chunk(img, 0, -2);
            pageNumberPlaceholder.setLocalGoto(entry.key());

            Chunk labelChunk = new Chunk(entry.label(), bodyFont);
            labelChunk.setLocalGoto(entry.key());

            Paragraph line = new Paragraph();
            line.setFont(bodyFont);
            line.add(labelChunk);
            line.add(new Chunk(dots));
            line.add(pageNumberPlaceholder);
            line.setSpacingBefore(2f);
            line.setSpacingAfter(2f);
            document.add(line);
        }
    }

    private void addBody(
            Document document,
            PdfWriter writer,
            CourseDTO course,
            List<SectionDTO> sections,
            Map<String, Integer> pageNumbersByKey
    ) throws DocumentException {
        Paragraph h = new Paragraph(safe(course.getTitle()) + " — Course Book", h1Font);
        h.setSpacingAfter(10f);
        document.add(h);

        if (course.getDescription() != null && !course.getDescription().isBlank()) {
            Paragraph desc = new Paragraph(course.getDescription().trim(), bodyFont);
            desc.setSpacingAfter(14f);
            document.add(desc);
        }

        boolean firstSection = true;
        int sectionNo = 0;
        PdfOutline rootOutline = writer.getRootOutline();
        PdfOutline currentSectionOutline = null;

        for (SectionDTO section : sections) {
            sectionNo++;

            // Ensure each section starts on a fresh page (stable TOC page numbers)
            if (!firstSection) {
                document.newPage();
            }
            firstSection = false;

            String sectionKey = "S:" + section.getId();
            pageNumbersByKey.put(sectionKey, writer.getPageNumber());

            String sectionTitle = sectionNo + ". " + safe(section.getTitle());
            Chunk sectionChunk = new Chunk(sectionTitle, h1Font);
            sectionChunk.setLocalDestination(sectionKey);
            Paragraph sectionHeading = new Paragraph(sectionChunk);
            sectionHeading.setSpacingAfter(8f);
            document.add(sectionHeading);

            // Bookmark/outline (LaTeX-like navigation panel)
            currentSectionOutline = new PdfOutline(
                    rootOutline,
                    PdfAction.gotoLocalPage(sectionKey, false),
                    sectionTitle
            );

            if (section.getDescription() != null && !section.getDescription().isBlank()) {
                markdownPdfRenderer.render(document, section.getDescription());
            }

            List<LectureDTO> lectures = section.getLectures() != null ? section.getLectures() : List.of();
            int lectureNo = 0;
            for (LectureDTO lecture : lectures) {
                lectureNo++;

                // Start each lecture on a fresh page for deterministic page numbers
                document.newPage();

                String lectureKey = "L:" + lecture.getId();
                pageNumbersByKey.put(lectureKey, writer.getPageNumber());

                String lectureTitle = sectionNo + "." + lectureNo + " " + safe(lecture.getTitle());
                Chunk lectureChunk = new Chunk(lectureTitle, h2Font);
                lectureChunk.setLocalDestination(lectureKey);
                Paragraph lectureHeading = new Paragraph(lectureChunk);
                lectureHeading.setSpacingAfter(6f);
                document.add(lectureHeading);

                if (currentSectionOutline != null) {
                    new PdfOutline(
                            currentSectionOutline,
                            PdfAction.gotoLocalPage(lectureKey, false),
                            lectureTitle
                    );
                } else {
                    new PdfOutline(
                            rootOutline,
                            PdfAction.gotoLocalPage(lectureKey, false),
                            lectureTitle
                    );
                }

                if (lecture.getDescription() != null && !lecture.getDescription().isBlank()) {
                    markdownPdfRenderer.render(document, lecture.getDescription());
                }

                // Lecture body: include TEXT lecture contents
                List<LectureContentDTO> contents = lecture.getContents() != null ? lecture.getContents() : List.of();
                for (LectureContentDTO content : contents) {
                    if (content == null) continue;
                    if (content.getContentType() != LectureContent.ContentType.TEXT) continue;
                    if (content.getTextContent() == null || content.getTextContent().isBlank()) continue;

                    if (content.getTitle() != null && !content.getTitle().isBlank()) {
                        Paragraph contentTitle = new Paragraph(safe(content.getTitle()), h2Font);
                        contentTitle.setSpacingBefore(10f);
                        contentTitle.setSpacingAfter(6f);
                        document.add(contentTitle);
                    }

                    markdownPdfRenderer.render(document, content.getTextContent());
                }
            }
        }
    }

    private void fillTocTemplates(
            PdfWriter writer,
            List<TocEntry> entries,
            Map<String, PdfTemplate> tocTemplatesByKey,
            Map<String, Integer> pageNumbersByKey
    ) {
        for (TocEntry entry : entries) {
            PdfTemplate template = tocTemplatesByKey.get(entry.key());
            if (template == null) continue;
            Integer pageNumber = pageNumbersByKey.get(entry.key());
            if (pageNumber == null) pageNumber = 0;

            Phrase phrase = new Phrase(String.valueOf(pageNumber), bodyFont);
            ColumnText.showTextAligned(template, Element.ALIGN_RIGHT, phrase, 42, 2, 0);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private record TocEntry(String key, String label) { }

    private static class PageNumberFooter extends PdfPageEventHelper {
        private final Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            int page = writer.getPageNumber();
            String text = String.valueOf(page);
            float x = (document.right() + document.left()) / 2;
            float y = document.bottom() - 20;
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER, new Phrase(text, footerFont), x, y, 0);
        }
    }
}

