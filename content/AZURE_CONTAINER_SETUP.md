# Azure Container Apps Setup for Video Processing

This guide explains how to configure Azure Container Apps for video processing with FFmpeg.

## FFmpeg Installation

✅ **Already configured in Dockerfile** - FFmpeg is installed during the Docker build process.

The Dockerfile has been updated to include:
```dockerfile
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        ffmpeg && \
    rm -rf /var/lib/apt/lists/*
```

## Storage Requirements

### Video Processing Storage Needs

For video processing, you need temporary disk space:
- **Formula**: `2 × video_size` (original file + processed file)
- **Example**: 2GB video → 4GB temp space needed
- **Location**: `/tmp` directory (default Java temp directory)

### Azure Container Apps Storage Options

Azure Container Apps provides two storage options:

#### Option 1: Ephemeral Storage (Recommended - Automatically Allocated)

**What it is:**
- Built-in temporary storage per replica
- **Automatically allocated based on CPU** (no manual configuration needed)
- **Free** (included in Container Apps pricing)
- Cleared when container restarts

**Automatic Allocation:**
- **≤ 0.25 CPU** → **1Gi** ephemeral storage
- **0.5 CPU** → **2Gi** ephemeral storage
- **1.0 CPU** → **4Gi** ephemeral storage
- **2.0 CPU** → **8Gi** ephemeral storage (our configuration)
- **> 2.0 CPU** → **8Gi** ephemeral storage

**Current Configuration:**
- **CPU: 2.0** → **Automatically provides 8Gi ephemeral storage** ✅
- More than sufficient for 2GB video processing (needs ~4GB temp space)
- No manual configuration needed!

**Pros:**
- ✅ Free (no additional cost)
- ✅ Automatic (no configuration needed)
- ✅ Fast (local SSD)
- ✅ Automatic cleanup
- ✅ 8Gi with 2.0 CPU (plenty for video processing)

**Cons:**
- ❌ Lost on container restart
- ❌ Not shared across replicas

#### Option 2: Azure Files Volume (For Larger Videos or High Concurrency)

**What it is:**
- Persistent Azure Files share
- Can be any size (pay per GB)
- Shared across replicas
- Persists across restarts

**When to use:**
- Videos larger than 2GB
- Multiple concurrent uploads
- Need persistent temp storage

**Configuration:**

1. **Create Azure Files Share:**

```bash
# Create storage account (if not exists)
az storage account create \
  --name edudrontempstorage \
  --resource-group your-resource-group \
  --location eastus \
  --sku Standard_LRS

# Create file share
az storage share create \
  --name video-temp \
  --account-name edudrontempstorage \
  --quota 10  # 10GB (adjust as needed)
```

2. **Mount Volume in Container App:**

```bash
# Create storage account secret
az containerapp secret set \
  --name content-dev \
  --resource-group your-resource-group \
  --secrets storage-key="<storage-account-key>"

# Update container app with volume mount
az containerapp update \
  --name content-dev \
  --resource-group your-resource-group \
  --set-env-vars VIDEO_PROCESSING_TEMP_DIR=/mnt/video-temp \
  --volume-mount "volume=video-temp,mount-path=/mnt/video-temp" \
  --volumes "name=video-temp,storage-type=AzureFile,storage-name=edudrontempstorage,share-name=video-temp,access-mode=ReadWrite"
```

3. **Update application.yml:**

```yaml
video:
  processing:
    temp-dir: /mnt/video-temp  # Use mounted volume
```

**Pros:**
- ✅ Unlimited size (pay per GB)
- ✅ Shared across replicas
- ✅ Persistent across restarts
- ✅ Good for high concurrency

**Cons:**
- ❌ Additional cost (~$0.06/GB/month)
- ❌ Slightly slower than ephemeral (network storage)
- ❌ Requires manual cleanup (or lifecycle policy)

## Recommended Configuration

### For Development/Testing (Videos < 2GB)

```bash
az containerapp update \
  --name content-dev \
  --resource-group your-resource-group \
  --cpu 1.0 \
  --memory 2.0Gi
```

**Ephemeral Storage**: Automatically 4Gi (with 1.0 CPU)  
**Cost**: No additional storage cost

### For Production (Videos up to 2GB, Low Concurrency)

```bash
az containerapp update \
  --name content-prod \
  --resource-group your-resource-group \
  --cpu 2.0 \
  --memory 4.0Gi \
  --min-replicas 1 \
  --max-replicas 3
```

**Ephemeral Storage**: Automatically 8Gi (with 2.0 CPU)  
**Cost**: No additional storage cost

### For Production (Large Videos or High Concurrency)

Use Azure Files volume (see Option 2 above)

**Estimated Cost**:
- 10GB Azure Files: ~$0.60/month
- 50GB Azure Files: ~$3.00/month

## Memory Configuration

### Current Default
- **CPU**: 0.5
- **Memory**: 1.0Gi

### Recommended for Video Processing

**Minimum:**
- **CPU**: 1.0 (for FFmpeg processing)
- **Memory**: 2.0Gi (for Java app + FFmpeg buffers)

**Optimal:**
- **CPU**: 2.0 (faster processing)
- **Memory**: 4.0Gi (better performance)

### Update Configuration

Edit `azure/config/services-config.json`:

```json
{
  "services": {
    "content": {
      "port": 8082,
      "cpu": "2.0",
      "memory": "4.0Gi",
      "minReplicas": 1,
      "maxReplicas": 5,
      "ingressExternal": false
    }
  }
}
```

Or update via Azure CLI:

```bash
az containerapp update \
  --name content-dev \
  --resource-group your-resource-group \
  --cpu 2.0 \
  --memory 4.0Gi
```

## Processing Time Considerations

For Azure Container Apps, consider:

1. **Request Timeout**: 
   - Default: 230 seconds (3.8 minutes)
   - For large videos, increase timeout:
   
```bash
az containerapp update \
  --name content-dev \
  --resource-group your-resource-group \
  --set-env-vars SERVER_CONNECTION_TIMEOUT=1800000
```

2. **Processing Time**:
   - Small videos (< 100MB): 10-30 seconds
   - Medium videos (100-500MB): 1-5 minutes
   - Large videos (500MB-2GB): 5-15 minutes

3. **Async Processing** (Future Enhancement):
   - Upload video immediately
   - Process in background
   - Update video URL when processing completes

## Verification

### Check FFmpeg Installation

```bash
# Exec into container
az containerapp exec \
  --name content-dev \
  --resource-group your-resource-group \
  --command "ffmpeg -version"
```

### Check Storage

```bash
# Check ephemeral storage allocation
az containerapp show \
  --name content-dev \
  --resource-group your-resource-group \
  --query "properties.template.containers[0].resources.ephemeralStorage" \
  -o tsv

# Check actual disk usage inside container
az containerapp exec \
  --name content-dev \
  --resource-group your-resource-group \
  --command "df -h /tmp"

# Check mounted volume (if using Azure Files)
az containerapp exec \
  --name content-dev \
  --resource-group your-resource-group \
  --command "df -h /mnt/video-temp"
```

## Cost Estimation

### Ephemeral Storage (4GB)
- **Cost**: $0 (included)
- **Best for**: Videos < 2GB

### Azure Files (10GB)
- **Storage**: ~$0.60/month
- **Transactions**: ~$0.01/month (minimal)
- **Total**: ~$0.61/month
- **Best for**: Larger videos or high concurrency

### Compute (2 CPU, 4GB RAM)
- **Consumption Plan**: Pay per use
- **Estimated**: $0.000012/vCPU-second + $0.0000015/GB-second
- **Example**: 1 hour processing = ~$0.05-0.10

## Troubleshooting

### "No space left on device"

**Solution**: Increase ephemeral storage or use Azure Files volume

### "FFmpeg not found"

**Solution**: Rebuild Docker image (FFmpeg is in Dockerfile)

### "Processing timeout"

**Solution**: Increase connection timeout or implement async processing

### "Out of memory"

**Solution**: Increase container memory to 4Gi or higher

## Summary

**For your use case (2-hour videos, up to 2GB):**

✅ **Ephemeral Storage**: **Automatically 8Gi** (with 2.0 CPU)
- No manual configuration needed
- Cost: $0 additional
- More than sufficient for videos up to 2GB (needs ~4GB temp space)

✅ **Memory**: 4.0Gi (configured)

✅ **CPU**: 2.0 (configured) → Provides 8Gi ephemeral storage automatically

**Current Status:**
- ✅ CPU: 2.0 → Automatically provides 8Gi ephemeral storage
- ✅ Memory: 4.0Gi
- ✅ FFmpeg: Installed in Docker image
- ✅ Video Processing: Configured and ready

**You only need Azure Files if:**
- Videos are larger than 2GB
- You have many concurrent uploads (need shared storage)
- You need persistent temp storage across restarts
