package com.datagami.edudron.content.constants;

public class MediaFolderConstants {
    
    // Content type folders
    public static final String COURSES = "courses";
    public static final String THUMBNAILS = "thumbnails";
    public static final String VIDEOS = "videos";
    public static final String PREVIEW_VIDEOS = "preview-videos";
    public static final String LECTURES = "lectures";
    public static final String ASSESSMENTS = "assessments";
    public static final String RESOURCES = "resources";
    public static final String INSTRUCTORS = "instructors";
    public static final String TEMP = "temp";
    public static final String LOGOS = "logos";
    public static final String FAVICONS = "favicons";
    
    // Helper method to validate folder name
    // Also accepts nested folders like "lectures/videos", "lectures/audio", "lectures/attachments"
    public static boolean isValidFolder(String folder) {
        if (folder == null || folder.isEmpty()) {
            return false;
        }
        // Extract base folder (first part before /)
        String baseFolder = folder.contains("/") ? folder.split("/")[0] : folder;
        return baseFolder.equals(COURSES) || 
               baseFolder.equals(THUMBNAILS) || 
               baseFolder.equals(VIDEOS) || 
               baseFolder.equals(PREVIEW_VIDEOS) ||
               baseFolder.equals(LECTURES) ||
               baseFolder.equals(ASSESSMENTS) ||
               baseFolder.equals(RESOURCES) ||
               baseFolder.equals(INSTRUCTORS) ||
               baseFolder.equals(TEMP) ||
               baseFolder.equals(LOGOS) ||
               baseFolder.equals(FAVICONS);
    }
}

