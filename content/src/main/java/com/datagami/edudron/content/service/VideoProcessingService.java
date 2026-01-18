package com.datagami.edudron.content.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for processing videos using ffmpeg.
 * Optimizes videos for streaming by moving the moov atom to the beginning (faststart).
 * This enables immediate seeking in long videos without downloading the entire file.
 */
@Service
public class VideoProcessingService {

    @Value("${video.processing.enabled:true}")
    private boolean processingEnabled;

    @Value("${video.processing.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${video.processing.temp-dir:${java.io.tmpdir}}")
    private String tempDir;

    /**
     * Process a video file to optimize it for streaming.
     * Uses ffmpeg with -movflags +faststart to move the moov atom to the beginning.
     * 
     * Memory implications:
     * - ffmpeg processes files in chunks, not loading entire video in memory
     * - Uses temp files for input/output (disk-based, not memory)
     * - Memory usage: ~50-100MB regardless of video size (ffmpeg's internal buffers)
     * - Processing time: ~10-30% of video duration (depends on CPU)
     * 
     * @param inputFile The input video file
     * @return The processed video file (new temp file)
     * @throws IOException If processing fails
     * @throws InterruptedException If process is interrupted
     */
    public File processVideoForStreaming(File inputFile) throws IOException, InterruptedException {
        if (!processingEnabled) {
            // Return original file if processing is disabled
            return inputFile;
        }

        // Check if ffmpeg is available
        if (!isFfmpegAvailable()) {
            System.err.println("WARNING: ffmpeg not found. Video will be uploaded without optimization.");
            return inputFile;
        }

        // Create output temp file
        Path outputPath = Files.createTempFile(Paths.get(tempDir), "video-processed-", ".mp4");
        File outputFile = outputPath.toFile();

        try {
            // Build ffmpeg command
            // -i: input file
            // -c copy: copy codecs (no re-encoding, fast and preserves quality)
            // -movflags +faststart: move moov atom to beginning (enables immediate seeking)
            // -y: overwrite output file if exists
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-i");
            command.add(inputFile.getAbsolutePath());
            command.add("-c");
            command.add("copy"); // Copy codecs (no re-encoding = fast + preserves quality)
            command.add("-movflags");
            command.add("+faststart"); // Move moov atom to beginning
            command.add("-y"); // Overwrite output file
            command.add(outputFile.getAbsolutePath());

            // Execute ffmpeg
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true); // Combine stderr with stdout
            
            Process process = processBuilder.start();
            
            // Wait for process to complete (with timeout for very large files)
            // For a 2-hour video, processing typically takes 5-15 minutes
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.MINUTES);
            
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Video processing timed out after 30 minutes");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("ffmpeg failed with exit code: " + exitCode);
            }

            // Verify output file exists and has content
            if (!outputFile.exists() || outputFile.length() == 0) {
                throw new IOException("ffmpeg did not produce a valid output file");
            }

            return outputFile;

        } catch (Exception e) {
            // Clean up output file on error
            if (outputFile.exists()) {
                outputFile.delete();
            }
            throw new IOException("Failed to process video: " + e.getMessage(), e);
        }
    }

    /**
     * Check if ffmpeg is available in the system PATH.
     */
    private boolean isFfmpegAvailable() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(ffmpegPath, "-version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

}
