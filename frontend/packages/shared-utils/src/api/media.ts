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

    return response.data?.url || (response as any).url
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

    const response = await this.apiClient.postForm<UploadResponse>(
      '/content/media/upload/video',
      formData
    )

    return response.data?.url || (response as any).url
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

