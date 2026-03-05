import { ApiClient } from './ApiClient'

export interface UploadResponse {
  url: string
  message: string
  tenantId?: string
  folder?: string
}

export class MediaApi {
  private apiClient: ApiClient

  constructor(apiClient: ApiClient) {
    this.apiClient = apiClient
  }

  /** Extract the URL from an upload response, handling both wrapped and unwrapped shapes. */
  private extractUrl(response: { data?: UploadResponse } & Partial<UploadResponse>): string {
    const url = response.data?.url ?? response.url
    if (!url) {
      throw new Error('Upload succeeded but response did not contain a URL')
    }
    return url
  }

  /**
   * Upload an image file
   * @param file The image file to upload
   * @param folder Optional folder name (default: 'thumbnails')
   * @returns Promise with the uploaded file URL
   */
  async uploadImage(file: File, folder: string = 'thumbnails'): Promise<string> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('folder', folder)

    const response = await this.apiClient.postForm<UploadResponse>(
      '/content/media/upload/image',
      formData
    )
    return this.extractUrl(response)
  }

  /**
   * Upload an image file with progress tracking
   * @param file The image file to upload
   * @param folder Optional folder name (default: 'thumbnails')
   * @param onProgress Optional progress callback (0-100)
   * @returns Promise with the uploaded file URL
   */
  async uploadImageWithProgress(
    file: File,
    folder: string = 'thumbnails',
    onProgress?: (percent: number) => void
  ): Promise<string> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('folder', folder)

    const response = await this.apiClient.postForm<UploadResponse>(
      '/content/media/upload/image',
      formData,
      {
        onUploadProgress: onProgress
          ? (progressEvent: { loaded: number; total?: number }) => {
              const total = (progressEvent.total != null && progressEvent.total > 0)
                ? progressEvent.total
                : progressEvent.loaded
              const percent = total > 0 ? Math.round((progressEvent.loaded * 100) / total) : 0
              onProgress(percent)
            }
          : undefined,
      }
    )
    return this.extractUrl(response)
  }

  /**
   * Upload a video file
   * @param file The video file to upload
   * @param folder Optional folder name (default: 'preview-videos')
   * @returns Promise with the uploaded file URL
   */
  async uploadVideo(file: File, folder: string = 'preview-videos'): Promise<string> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('folder', folder)

    // Timeout: ~6 seconds per MB (assumes worst-case ~170 KB/s upload), clamped to 5-30 minutes
    const fileSizeMB = file.size / (1024 * 1024)
    const timeoutMs = Math.min(Math.max(fileSizeMB * 6000, 300000), 1800000)

    const response = await this.apiClient.postForm<UploadResponse>(
      '/content/media/upload/video',
      formData,
      { timeout: timeoutMs }
    )

    return this.extractUrl(response)
  }

  /**
   * Delete a media file by URL
   * @param url The URL of the media file to delete
   */
  async deleteMedia(url: string): Promise<void> {
    await this.apiClient.delete('/content/media/delete', {
      params: { url }
    })
  }
}
