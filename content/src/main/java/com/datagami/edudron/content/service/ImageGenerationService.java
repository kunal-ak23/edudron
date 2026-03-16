package com.datagami.edudron.content.service;

import com.datagami.edudron.common.TenantContext;
import com.datagami.edudron.common.UlidGenerator;
import com.datagami.edudron.content.dto.ImagePlanDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ImageGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(ImageGenerationService.class);

    private static final String ANTI_TEXT_SUFFIX =
            " The image must contain absolutely no text, no labels, no letters, no words, no numbers, no written characters of any kind.";

    private final String aiServicesEndpoint;
    private final String apiKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    private FoundryAIService foundryAIService;

    @Autowired
    private MediaUploadService mediaUploadService;

    public ImageGenerationService(
            @Value("${azure.ai-services.endpoint:}") String aiServicesEndpoint,
            @Value("${azure.openai.api-key:}") String apiKey,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);  // 30s connect timeout
        factory.setReadTimeout(120_000);    // 120s read timeout (image generation can be slow)
        this.restTemplate = new RestTemplate(factory);

        if (aiServicesEndpoint == null || aiServicesEndpoint.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            logger.warn("Azure AI Services endpoint or API key not configured. Image generation will be disabled.");
            this.aiServicesEndpoint = null;
            this.apiKey = null;
        } else {
            String normalized = aiServicesEndpoint.trim();
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            this.aiServicesEndpoint = normalized;
            this.apiKey = apiKey;
            logger.info("Image generation service initialized with endpoint: {}", normalized);
        }
    }

    public boolean isConfigured() {
        return aiServicesEndpoint != null && apiKey != null;
    }

    // ==========================================
    // Smart Image Pipeline (Plan + Generate + Insert)
    // ==========================================

    /**
     * Two-phase image generation for lectures:
     * 1. Ask GPT to analyze lecture content and create an image plan (how many, where, what)
     * 2. Generate images with FLUX.2-pro and insert them at the planned positions
     *
     * @param lectureTitle   Lecture title
     * @param lectureContent Lecture markdown content (already generated)
     * @return Updated lecture content with images inserted at appropriate positions
     */
    public String planAndGenerateLectureImages(String lectureTitle, String lectureContent) {
        if (!isConfigured() || !foundryAIService.isConfigured()) {
            logger.warn("Image or GPT service not configured, returning content unchanged");
            return lectureContent;
        }

        try {
            // Phase 1: Get image plan from GPT
            ImagePlanDTO plan = createImagePlan(lectureTitle, lectureContent);

            if (plan == null || plan.getImages() == null || plan.getImages().isEmpty()) {
                logger.info("Image plan returned 0 images for lecture: {}", lectureTitle);
                return lectureContent;
            }

            logger.info("Image plan for '{}': {} image(s)", lectureTitle, plan.getImages().size());

            // Phase 2: Generate images and collect insertion points
            List<ImageInsertion> insertions = new ArrayList<>();

            for (int i = 0; i < plan.getImages().size(); i++) {
                ImagePlanDTO.ImagePlacement placement = plan.getImages().get(i);
                try {
                    logger.info("Generating image {}/{} for lecture '{}': {}",
                            i + 1, plan.getImages().size(), lectureTitle,
                            placement.getPrompt().substring(0, Math.min(80, placement.getPrompt().length())));

                    // Add anti-text suffix to FLUX prompt
                    String fluxPrompt = placement.getPrompt() + ANTI_TEXT_SUFFIX;
                    String imageUrl = generateAndUploadImage(fluxPrompt, 1024, 768);
                    String altText = placement.getAltText() != null ? placement.getAltText() : lectureTitle;
                    String imageMarkdown = "\n\n![" + altText + "](" + imageUrl + ")\n";

                    int insertPosition = findInsertPosition(lectureContent, placement.getInsertAfter());
                    insertions.add(new ImageInsertion(insertPosition, imageMarkdown));
                } catch (Exception e) {
                    logger.warn("Failed to generate image {}/{} for lecture '{}': {}",
                            i + 1, plan.getImages().size(), lectureTitle, e.getMessage());
                }
            }

            if (insertions.isEmpty()) {
                logger.warn("All image generations failed for lecture: {}", lectureTitle);
                return lectureContent;
            }

            // Sort insertions by position descending so inserting doesn't shift later positions
            insertions.sort((a, b) -> Integer.compare(b.position, a.position));

            StringBuilder result = new StringBuilder(lectureContent);
            for (ImageInsertion insertion : insertions) {
                result.insert(insertion.position, insertion.imageMarkdown);
            }

            logger.info("Inserted {} image(s) into lecture '{}'", insertions.size(), lectureTitle);
            return result.toString();

        } catch (Exception e) {
            logger.error("Failed to plan and generate images for lecture '{}': {}", lectureTitle, e.getMessage());
            return lectureContent; // Return original on failure
        }
    }

    /**
     * Use GPT to analyze lecture content and create an image placement plan.
     * Uses a structured multi-phase analysis approach inspired by educational diagram design.
     */
    private ImagePlanDTO createImagePlan(String lectureTitle, String lectureContent) {
        int contentLength = lectureContent != null ? lectureContent.length() : 0;

        String systemPrompt = "You are an expert educational visual architect. Your job is to analyze lecture content " +
                "and plan pedagogically effective illustrations that help students learn the material.\n\n" +
                "═══════════════════════════════════════════\n" +
                "PHASE 1 — CONTENT ANALYSIS (do this step-by-step before deciding)\n" +
                "═══════════════════════════════════════════\n\n" +
                "1. SUBJECT DETECTION\n" +
                "   Identify the academic domain (Technology, Marketing, Science, Business, Mathematics, " +
                "Design, History, Computer Science, Economics, etc.).\n\n" +
                "2. CONCEPT EXTRACTION\n" +
                "   List every key concept, term, entity, or process in the content.\n\n" +
                "3. RELATIONSHIP MAPPING\n" +
                "   Determine how the concepts relate. Classify each as:\n" +
                "   - Hierarchical (parent → child, category → subcategory)\n" +
                "   - Sequential (step 1 → step 2 → step 3, pipeline)\n" +
                "   - Cyclical (A → B → C → A, feedback loops)\n" +
                "   - Comparative (X vs Y, similarities/differences)\n" +
                "   - Causal (cause → effect, input → output)\n" +
                "   - Part-Whole (component → system, layers)\n" +
                "   - Spatial (geographic, architectural, positional)\n" +
                "   - Temporal (timeline, evolution, era-based)\n\n" +
                "4. VISUAL TYPE SELECTION\n" +
                "   Based on the dominant relationship, pick the best visual style for each image:\n" +
                "   | Relationship    | Best Visual Style                              |\n" +
                "   |-----------------|------------------------------------------------|\n" +
                "   | Hierarchical    | Tree/pyramid composition, nested layers        |\n" +
                "   | Sequential      | Left-to-right or top-to-bottom progression     |\n" +
                "   | Cyclical        | Circular arrangement with flowing connections   |\n" +
                "   | Comparative     | Split-scene or side-by-side contrast            |\n" +
                "   | Causal          | Domino/chain-reaction visual flow               |\n" +
                "   | Part-Whole      | Exploded view, cutaway, or assembly composition |\n" +
                "   | Spatial         | Map-like layout, bird's-eye view                |\n" +
                "   | Temporal        | Horizontal timeline progression                 |\n" +
                "   | Multi-type      | Central hub radiating outward (concept map)     |\n\n" +
                "═══════════════════════════════════════════\n" +
                "PHASE 2 — IMAGE COUNT DECISION\n" +
                "═══════════════════════════════════════════\n\n" +
                "Decide how many images (0-3) based on:\n" +
                "- Content length: <500 chars = 0, 500-1500 chars = 0-1, 1500-3000 chars = 1-2, >3000 chars = 1-3\n" +
                "- Visual opportunity: physical, spatial, process-oriented, or comparative topics deserve more\n" +
                "- Do NOT add images just for decoration. Each MUST genuinely aid understanding.\n" +
                "- Each image should illustrate a DIFFERENT concept or relationship from the content.\n\n" +
                "═══════════════════════════════════════════\n" +
                "PHASE 3 — IMAGE PROMPT GENERATION\n" +
                "═══════════════════════════════════════════\n\n" +
                "For each image, specify:\n\n" +
                "insertAfter: The EXACT text of a markdown heading (e.g. '## Introduction') or the first " +
                "8-12 words of a paragraph after which the image should appear. Choose a location where " +
                "the image is most contextually relevant to the surrounding text.\n\n" +
                "prompt: A detailed visual scene description following these rules:\n" +
                "  STYLE: Clean, modern, professional educational illustration. Use a cohesive color palette.\n" +
                "  Suggested palettes by subject:\n" +
                "    • Technology/CS: blues (#2563EB), teals (#0891B2), purples (#8B5CF6)\n" +
                "    • Business/Marketing: navy (#1E3A5F), emerald (#10B981), amber (#F59E0B)\n" +
                "    • Science: greens (#22C55E), blues (#3B82F6), orange (#F97316)\n" +
                "    • History/Social: warm browns (#92400E), deep blue (#1E3A5F), muted gold (#B45309)\n" +
                "  COMPOSITION: Describe spatial arrangement, visual hierarchy, and flow direction.\n" +
                "  METAPHORS: Use physical analogies and symbolic representations for abstract concepts.\n" +
                "    Examples: data flow as rivers, hierarchy as stacked platforms, comparison as scales.\n" +
                "  DETAIL: Include specific visual elements — shapes, objects, colors, lighting, perspective.\n\n" +
                "  ✗ HARD CONSTRAINT — NO TEXT: Do NOT include any text, words, letters, numbers, labels,\n" +
                "    captions, annotations, watermarks, or written characters. AI image generators CANNOT\n" +
                "    render readable text. Use ONLY visual elements to convey meaning.\n" +
                "  ✗ NEVER describe diagrams with labeled boxes or annotated charts.\n" +
                "  ✓ ALWAYS end each prompt with: 'No text, no labels, no letters, no words anywhere in the image.'\n\n" +
                "altText: A concise 5-15 word description for accessibility.\n\n" +
                "═══════════════════════════════════════════\n" +
                "OUTPUT FORMAT\n" +
                "═══════════════════════════════════════════\n\n" +
                "Respond with ONLY a JSON object:\n" +
                "{\n" +
                "  \"imageCount\": <number 0-3>,\n" +
                "  \"images\": [\n" +
                "    {\n" +
                "      \"insertAfter\": \"<exact heading or paragraph start>\",\n" +
                "      \"prompt\": \"<detailed visual-only prompt>\",\n" +
                "      \"altText\": \"<concise description>\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "If 0 images needed: {\"imageCount\": 0, \"images\": []}. No other text outside the JSON.";

        String userPrompt = "Lecture title: " + lectureTitle + "\n\n" +
                "Content length: " + contentLength + " characters\n\n" +
                "Full lecture content:\n" + lectureContent;

        try {
            String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
            String jsonStr = extractJsonObject(response);
            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            ImagePlanDTO plan = new ImagePlanDTO();
            plan.setImageCount(jsonNode.path("imageCount").asInt(0));

            List<ImagePlanDTO.ImagePlacement> placements = new ArrayList<>();
            JsonNode imagesNode = jsonNode.path("images");
            if (imagesNode.isArray()) {
                for (JsonNode imgNode : imagesNode) {
                    ImagePlanDTO.ImagePlacement p = new ImagePlanDTO.ImagePlacement();
                    p.setInsertAfter(imgNode.path("insertAfter").asText(""));
                    p.setPrompt(imgNode.path("prompt").asText(""));
                    p.setAltText(imgNode.path("altText").asText("Lecture illustration"));
                    if (!p.getPrompt().isEmpty()) {
                        placements.add(p);
                    }
                }
            }
            plan.setImages(placements);

            // Safety: clamp to 3 max
            if (plan.getImages().size() > 3) {
                plan.setImages(plan.getImages().subList(0, 3));
                plan.setImageCount(3);
            }

            logger.info("Image plan created for '{}': {} image(s) planned", lectureTitle, plan.getImages().size());
            return plan;

        } catch (Exception e) {
            logger.error("Failed to create image plan for lecture '{}': {}", lectureTitle, e.getMessage());
            return null;
        }
    }

    /**
     * Find the character position in the lecture content where an image should be inserted.
     * Looks for the insertAfter text and returns the position right after that line/paragraph ends.
     */
    private int findInsertPosition(String content, String insertAfter) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        if (insertAfter == null || insertAfter.isEmpty()) {
            return content.length();
        }

        // Strategy 1: Exact match
        int idx = content.indexOf(insertAfter);
        if (idx >= 0) {
            // Find the end of the paragraph containing this text
            int searchFrom = idx + insertAfter.length();

            // If this is a heading line, skip to end of the next paragraph
            if (insertAfter.trim().startsWith("#")) {
                int lineEnd = content.indexOf('\n', searchFrom);
                if (lineEnd < 0) return content.length();

                // Skip blank lines after heading
                int pos = lineEnd;
                while (pos < content.length() && content.charAt(pos) == '\n') pos++;

                // Find end of next paragraph (next double newline or next heading)
                int nextDoubleNewline = content.indexOf("\n\n", pos);
                if (nextDoubleNewline >= 0) {
                    return nextDoubleNewline;
                }
                return content.length();
            }

            // For paragraph text match, find end of that paragraph
            int nextDoubleNewline = content.indexOf("\n\n", searchFrom);
            if (nextDoubleNewline >= 0) {
                return nextDoubleNewline;
            }
            int lineEnd = content.indexOf('\n', searchFrom);
            return lineEnd >= 0 ? lineEnd : content.length();
        }

        // Strategy 2: Fuzzy match with first few words
        String[] words = insertAfter.split("\\s+");
        if (words.length >= 3) {
            String shortSearch = String.join(" ", Arrays.copyOfRange(words, 0, Math.min(5, words.length)));
            idx = content.indexOf(shortSearch);
            if (idx >= 0) {
                int nextDoubleNewline = content.indexOf("\n\n", idx);
                if (nextDoubleNewline >= 0) {
                    return nextDoubleNewline;
                }
                int lineEnd = content.indexOf('\n', idx);
                return lineEnd >= 0 ? lineEnd : content.length();
            }
        }

        // Strategy 3: Case-insensitive fuzzy match
        String lowerContent = content.toLowerCase();
        String lowerInsertAfter = insertAfter.toLowerCase();
        idx = lowerContent.indexOf(lowerInsertAfter);
        if (idx >= 0) {
            int nextDoubleNewline = content.indexOf("\n\n", idx + insertAfter.length());
            if (nextDoubleNewline >= 0) {
                return nextDoubleNewline;
            }
        }

        // Fallback: insert at end
        logger.warn("Could not find insert position for '{}', appending at end",
                insertAfter.substring(0, Math.min(50, insertAfter.length())));
        return content.length();
    }

    /**
     * Extract JSON object from a GPT response that may contain markdown code blocks or surrounding text.
     */
    private String extractJsonObject(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("Response is null or empty");
        }
        String trimmed = response.trim();

        // Remove markdown code blocks
        if (trimmed.startsWith("```json")) trimmed = trimmed.substring(7);
        else if (trimmed.startsWith("```")) trimmed = trimmed.substring(3);
        if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        trimmed = trimmed.trim();

        // Find the first '{' or '['
        int start = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '{' || c == '[') {
                start = i;
                break;
            }
        }
        if (start == -1) {
            throw new IllegalArgumentException("No JSON found in response");
        }

        // Find matching closing bracket
        char openChar = trimmed.charAt(start);
        char closeChar = (openChar == '{') ? '}' : ']';
        int depth = 0;
        int end = -1;
        boolean inString = false;
        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == openChar) depth++;
                if (c == closeChar) depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }

        if (end == -1) {
            throw new IllegalArgumentException("Unmatched JSON brackets in response");
        }

        return trimmed.substring(start, end + 1);
    }

    // Simple class for tracking image insertions
    private static class ImageInsertion {
        final int position;
        final String imageMarkdown;

        ImageInsertion(int position, String imageMarkdown) {
            this.position = position;
            this.imageMarkdown = imageMarkdown;
        }
    }

    // ==========================================
    // Core Image Generation (FLUX.2-pro)
    // ==========================================

    /**
     * Generate an image using FLUX.2-pro.
     *
     * @param prompt Image description
     * @param width  Image width (default 1024)
     * @param height Image height (default 1024)
     * @return Decoded PNG bytes
     */
    public byte[] generateImage(String prompt, int width, int height) {
        if (!isConfigured()) {
            throw new IllegalStateException("Image generation is not configured");
        }

        String url = aiServicesEndpoint + "/providers/blackforestlabs/v1/flux-2-pro?api-version=preview";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", prompt);
        body.put("width", width);
        body.put("height", height);
        body.put("n", 1);
        body.put("model", "FLUX.2-pro");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        logger.info("Calling FLUX.2-pro for image generation ({}x{}), prompt length: {}", width, height, prompt.length());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.path("data");
                if (data.isArray() && !data.isEmpty()) {
                    String b64 = data.get(0).path("b64_json").asText();
                    if (b64 != null && !b64.isEmpty()) {
                        byte[] imageBytes = Base64.getDecoder().decode(b64);
                        logger.info("Image generated successfully, size: {} bytes", imageBytes.length);
                        return imageBytes;
                    }
                }
                throw new RuntimeException("FLUX.2-pro returned empty image data");
            }
            throw new RuntimeException("FLUX.2-pro returned status: " + response.getStatusCode());
        } catch (Exception e) {
            logger.error("Failed to generate image: {}", e.getMessage(), e);
            throw new RuntimeException("Image generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate an image and upload it to Azure Blob Storage.
     *
     * @param prompt Image description
     * @param width  Image width
     * @param height Image height
     * @return Public URL of the uploaded image
     */
    public String generateAndUploadImage(String prompt, int width, int height) {
        byte[] imageBytes = generateImage(prompt, width, height);
        return uploadImageBytes(imageBytes);
    }

    // ==========================================
    // Legacy Methods (kept for standalone image generation jobs)
    // ==========================================

    /**
     * Generate images for a lecture based on its content.
     * Uses GPT to create image prompts from lecture content, then generates with FLUX.2-pro.
     * Used by the standalone image generation job endpoint (ImageGenerationController).
     *
     * @param lectureTitle   Lecture title
     * @param lectureContent Lecture text content (markdown)
     * @param count          Number of images to generate
     * @return List of uploaded image URLs
     */
    public List<String> generateLectureImages(String lectureTitle, String lectureContent, int count) {
        if (!isConfigured()) {
            logger.warn("Image generation not configured, skipping");
            return Collections.emptyList();
        }

        // Use GPT to create image prompts from lecture content
        List<String> imagePrompts = generateImagePrompts(lectureTitle, lectureContent, count);

        List<String> imageUrls = new ArrayList<>();
        for (int i = 0; i < imagePrompts.size(); i++) {
            try {
                // Add anti-text suffix for FLUX
                String fluxPrompt = imagePrompts.get(i) + ANTI_TEXT_SUFFIX;
                logger.info("Generating lecture image {}/{}: {}", i + 1, imagePrompts.size(),
                        fluxPrompt.substring(0, Math.min(80, fluxPrompt.length())));
                String url = generateAndUploadImage(fluxPrompt, 1024, 768);
                imageUrls.add(url);
            } catch (Exception e) {
                logger.error("Failed to generate lecture image {}/{}: {}", i + 1, imagePrompts.size(), e.getMessage());
            }
        }

        return imageUrls;
    }

    /**
     * Use GPT to generate descriptive image prompts from lecture content.
     */
    private List<String> generateImagePrompts(String lectureTitle, String lectureContent, int count) {
        if (!foundryAIService.isConfigured()) {
            logger.warn("GPT not configured, using lecture title as image prompt");
            return Collections.singletonList(
                    "An educational illustration for: " + lectureTitle +
                    ". No text, no labels, no letters, no words anywhere in the image.");
        }

        String systemPrompt = "You are an expert educational visual architect. Create " + count + " image prompt(s) " +
                "for an AI image generator (FLUX.2-pro) to illustrate this lecture.\n\n" +
                "ANALYSIS STEPS (do silently before generating prompts):\n" +
                "1. Identify the subject domain and key concepts\n" +
                "2. Determine dominant relationships (hierarchical, sequential, cyclical, comparative, causal)\n" +
                "3. Choose the best visual style per relationship type:\n" +
                "   - Sequential → left-to-right or top-to-bottom progression\n" +
                "   - Hierarchical → layered platforms, nested structures\n" +
                "   - Comparative → split-scene, side-by-side contrast\n" +
                "   - Cyclical → circular flowing arrangement\n" +
                "   - Causal → chain-reaction visual flow\n\n" +
                "PROMPT RULES:\n" +
                "- Describe clean, modern, professional educational illustrations\n" +
                "- Use visual metaphors and symbolic representations for abstract concepts\n" +
                "- Include specific details: shapes, objects, colors, spatial arrangement, lighting\n" +
                "- Use cohesive color palettes appropriate to the subject\n" +
                "- ✗ NEVER include text, words, letters, numbers, labels, captions, or annotations\n" +
                "- ✗ NEVER describe diagrams with labeled boxes or annotated charts\n" +
                "- ✓ ALWAYS end each prompt with: 'No text, no labels, no letters, no words anywhere in the image.'\n\n" +
                "Respond with ONLY a JSON array of strings. No other text.";

        String userPrompt = "Lecture title: " + lectureTitle + "\n\n" +
                "Lecture content (first 2000 chars):\n" +
                (lectureContent != null && lectureContent.length() > 2000
                        ? lectureContent.substring(0, 2000) + "..."
                        : (lectureContent != null ? lectureContent : ""));

        try {
            String response = foundryAIService.callOpenAI(systemPrompt, userPrompt);
            JsonNode prompts = objectMapper.readTree(response);
            if (prompts.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode p : prompts) {
                    result.add(p.asText());
                }
                return result;
            }
        } catch (Exception e) {
            logger.error("Failed to generate image prompts via GPT: {}", e.getMessage());
        }

        // Fallback
        return Collections.singletonList(
                "An educational illustration for: " + lectureTitle +
                ". No text, no labels, no letters, no words anywhere in the image.");
    }

    // ==========================================
    // Upload
    // ==========================================

    /**
     * Upload generated image bytes to Azure Blob Storage.
     */
    private String uploadImageBytes(byte[] imageBytes) {
        String clientId = TenantContext.getClientId();
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalStateException("Tenant context is not set");
        }

        LocalDateTime now = LocalDateTime.now();
        String datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = UlidGenerator.nextUlid() + ".png";
        String blobPath = String.format("%s/ai-generated/%s/%s", clientId, datePath, fileName);

        return mediaUploadService.uploadBytesToPath(imageBytes, blobPath, "image/png");
    }
}
