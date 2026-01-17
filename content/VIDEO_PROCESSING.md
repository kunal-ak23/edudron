# Video Processing with FFmpeg

This service automatically optimizes uploaded videos for streaming by using FFmpeg to move the "moov atom" (metadata) to the beginning of the MP4 file. This enables immediate seeking in long videos without requiring the browser to download the entire file first.

## How It Works

When a video is uploaded:
1. Video is saved to a temporary file on disk
2. FFmpeg processes the video with `-movflags +faststart` flag
3. This moves the moov atom to the beginning (no re-encoding, just metadata reorganization)
4. The optimized video is uploaded to Azure Blob Storage
5. Temporary files are cleaned up

## Memory Implications

**✅ Memory Efficient:**
- FFmpeg processes videos in chunks, **NOT loading the entire video in memory**
- Memory usage: **~50-100MB** regardless of video size (FFmpeg's internal buffers)
- Uses temporary files on disk (not memory)
- Processing time: ~10-30% of video duration (depends on CPU)

**Example for a 2-hour video (500MB):**
- Memory usage: ~50-100MB (constant)
- Disk usage: ~1GB temporarily (original + processed file)
- Processing time: ~5-15 minutes (depends on CPU)
- No re-encoding: Original quality preserved

## Configuration

### Enable/Disable Processing

In `application.yml` or environment variables:

```yaml
video:
  processing:
    enabled: true  # Set to false to disable processing
```

Or via environment variable:
```bash
VIDEO_PROCESSING_ENABLED=false
```

### FFmpeg Path

Default: `ffmpeg` (must be in system PATH)

To specify custom path:
```yaml
video:
  processing:
    ffmpeg-path: /usr/bin/ffmpeg
```

Or via environment variable:
```bash
VIDEO_PROCESSING_FFMPEG_PATH=/usr/local/bin/ffmpeg
```

## Installation

### Local Development (macOS)

```bash
brew install ffmpeg
```

### Local Development (Linux)

```bash
sudo apt-get update
sudo apt-get install ffmpeg
```

### Docker

Add to your Dockerfile:

```dockerfile
# Install ffmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Or for Alpine:
# RUN apk add --no-cache ffmpeg
```

### Verify Installation

```bash
ffmpeg -version
```

Should output FFmpeg version information.

## What Happens If FFmpeg Is Not Available?

If FFmpeg is not installed or not found:
- A warning is logged
- The original video is uploaded without processing
- Upload continues normally (no failure)
- Videos will still work, but seeking may require downloading more data

## Performance Considerations

### Processing Time
- **Small videos (< 100MB)**: 10-30 seconds
- **Medium videos (100-500MB)**: 1-5 minutes
- **Large videos (500MB-2GB)**: 5-15 minutes

### Disk Space
- Requires temporary disk space = **2x video size** (original + processed)
- Temporary files are automatically cleaned up after upload
- Ensure `/tmp` or configured temp directory has sufficient space

### CPU Usage
- FFmpeg uses CPU for processing
- For high-traffic scenarios, consider:
  - Async processing (future enhancement)
  - Dedicated processing server
  - Queue-based processing

## Benefits

1. **Immediate Seeking**: Users can seek to any position in long videos instantly
2. **Reduced Bandwidth**: Browser only downloads the segment needed
3. **Better UX**: No waiting for entire video to buffer before seeking
4. **No Quality Loss**: Uses `-c copy` (no re-encoding)
5. **Fast Processing**: Only reorganizes metadata, doesn't re-encode

## Troubleshooting

### "ffmpeg not found" Warning

**Solution**: Install FFmpeg or set correct path in configuration.

### Processing Timeout

Default timeout: 30 minutes. For very large videos, you may need to increase timeout in `VideoProcessingService.java`.

### Out of Disk Space

Ensure temp directory has sufficient space (2x largest video size).

### Processing Fails

If processing fails, the original video is uploaded as fallback. Check logs for FFmpeg error messages.

## Future Enhancements

Potential improvements:
- Async processing (upload first, process in background)
- Queue-based processing for high traffic
- Progress tracking for processing
- Multiple quality levels (transcoding)
- Thumbnail generation
