# Async Video Processing Architecture

## Overview

Videos are now processed **asynchronously** for optimal user experience. The system uploads the video immediately and processes it in the background.

## How It Works

### Flow

1. **User uploads video** → Server receives file
2. **Upload to Azure immediately** → Unprocessed video is uploaded
3. **Return URL immediately** → User gets response in seconds (not minutes)
4. **Process in background** → FFmpeg optimizes video asynchronously
5. **Update blob** → Optimized version replaces unprocessed version (same URL)

### Timeline Example (2GB video)

**Synchronous (Old):**
- Upload: 5-10 minutes
- Processing: 5-15 minutes
- **Total wait: 10-25 minutes** ❌

**Asynchronous (New):**
- Upload: 5-10 minutes
- **Response: Immediate** ✅
- Processing: 5-15 minutes (background)
- **User can use video immediately** ✅

## Benefits

### ✅ Fast Response
- User gets URL in seconds (upload time only)
- No waiting for processing (5-15 minutes saved)

### ✅ Immediate Availability
- Video is playable immediately (even if not optimized)
- Works for all use cases right away

### ✅ Seamless Upgrade
- Blob gets updated with optimized version
- Same URL, better performance
- No frontend changes needed

### ✅ Better UX
- No timeout issues
- Progress tracking works normally
- User can continue working

### ✅ Fault Tolerance
- If processing fails, video still works (unoptimized)
- No impact on user experience

## Technical Details

### Components

1. **MediaUploadService**
   - Uploads unprocessed video immediately
   - Triggers async processing task
   - Returns URL right away

2. **VideoProcessingTask** (`@Async`)
   - Processes video with FFmpeg
   - Updates blob with optimized version
   - Handles errors gracefully

3. **VideoProcessingService**
   - FFmpeg processing logic
   - Moves moov atom to beginning (faststart)

### Thread Safety

- Uses Spring's `@Async` (thread pool managed)
- Tenant context preserved in async task
- Temp files cleaned up properly

### Error Handling

- If processing fails → Video still works (unoptimized)
- Errors logged but don't affect user
- Blob remains available

## Configuration

### Enable/Disable Processing

```yaml
video:
  processing:
    enabled: true  # Set to false to disable
```

### Async Configuration

Spring's default async executor is used. To customize:

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor videoProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("video-processing-");
        executor.initialize();
        return executor;
    }
}
```

## Monitoring

### Logs

Look for these log messages:

```
INFO: Starting async video processing for: video.mp4
INFO: Processing video for streaming optimization: video.mp4
INFO: Uploading optimized video version: video.mp4
INFO: Successfully updated video with optimized version: video.mp4
```

### Errors

If processing fails:
```
ERROR: Failed to process/update video: video.mp4
```

Video still works, just not optimized.

## Frontend Impact

**No changes needed!** 

- Same API response format
- Same URL structure
- Video works immediately
- Gets optimized automatically

The frontend doesn't need to know about processing - it's completely transparent.

## Performance Considerations

### Memory
- Async processing uses same memory as sync (~50-100MB)
- No additional memory overhead

### CPU
- Processing happens in background thread
- Doesn't block request handling
- Can process multiple videos concurrently

### Storage
- Temp files cleaned up after processing
- No additional storage needed

## Future Enhancements

Potential improvements:
- Processing status endpoint (`/video/{id}/processing-status`)
- Webhook notification when processing completes
- Processing queue with priority
- Multiple quality levels (transcoding)

## Summary

**Answer: Use async processing** ✅

- Fast response (seconds, not minutes)
- Better UX (no waiting)
- Video works immediately
- Gets optimized automatically
- No frontend changes needed

The video URL is returned immediately, and the blob gets updated with the optimized version in the background. Same URL, better performance over time.
