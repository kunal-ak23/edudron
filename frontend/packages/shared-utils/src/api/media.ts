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
    // #region agent log
    fetch('http://127.0.0.1:7242/ingest/539a6c71-ea02-4a10-be32-4a5d508ee167',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({location:'media.ts:23',message:'Upload image called',data:{folder,fileName:file.name,fileSize:file.size},timestamp:Date.now(),sessionId:'debug-session',runId:'run1',hypothesisId:'B,C,D'})}).catch(()=>{});
    // #endregion
    const formData = new FormData()
    formData.append('file', file)
    formData.append('folder', folder)

    try {
      const response = await this.apiClient.postForm<UploadResponse>(
        '/content/media/upload/image',
        formData
      )
      // #region agent log
      fetch('http://127.0.0.1:7242/ingest/539a6c71-ea02-4a10-be32-4a5d508ee167',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({location:'media.ts:33',message:'Upload image success',data:{hasUrl:!!(response.data?.url || (response as any).url)},timestamp:Date.now(),sessionId:'debug-session',runId:'run1',hypothesisId:'B,C,D'})}).catch(()=>{});
      // #endregion
      return response.data?.url || (response as any).url
    } catch (error: any) {
      // #region agent log
      fetch('http://127.0.0.1:7242/ingest/539a6c71-ea02-4a10-be32-4a5d508ee167',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({location:'media.ts:38',message:'Upload image failed',data:{error:error?.message,status:error?.response?.status},timestamp:Date.now(),sessionId:'debug-session',runId:'run1',hypothesisId:'B,C,D'})}).catch(()=>{});
      // #endregion
      throw error
    }
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


