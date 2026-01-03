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
    public static boolean isValidFolder(String folder) {
        return folder != null && !folder.isEmpty() && 
               (folder.equals(COURSES) || 
                folder.equals(THUMBNAILS) || 
                folder.equals(VIDEOS) || 
                folder.equals(PREVIEW_VIDEOS) ||
                folder.equals(LECTURES) ||
                folder.equals(ASSESSMENTS) ||
                folder.equals(RESOURCES) ||
                folder.equals(INSTRUCTORS) ||
                folder.equals(TEMP) ||
                folder.equals(LOGOS) ||
                folder.equals(FAVICONS));
    }
}

