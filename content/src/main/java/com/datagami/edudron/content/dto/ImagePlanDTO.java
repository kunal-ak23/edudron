package com.datagami.edudron.content.dto;

import java.util.List;

/**
 * DTO for GPT-generated image placement plan.
 * Used during course/lecture generation to determine how many images to generate,
 * where to insert them, and what prompts to use for FLUX.2-pro.
 */
public class ImagePlanDTO {

    private int imageCount;
    private List<ImagePlacement> images;

    public static class ImagePlacement {
        private String insertAfter;  // heading text or paragraph start to insert after
        private String prompt;       // FLUX image generation prompt (no text instructions)
        private String altText;      // alt text for the markdown image

        public String getInsertAfter() { return insertAfter; }
        public void setInsertAfter(String insertAfter) { this.insertAfter = insertAfter; }

        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }

        public String getAltText() { return altText; }
        public void setAltText(String altText) { this.altText = altText; }
    }

    public int getImageCount() { return imageCount; }
    public void setImageCount(int imageCount) { this.imageCount = imageCount; }

    public List<ImagePlacement> getImages() { return images; }
    public void setImages(List<ImagePlacement> images) { this.images = images; }
}
