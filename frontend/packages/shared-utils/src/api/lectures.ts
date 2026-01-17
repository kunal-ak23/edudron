import { ApiClient } from './ApiClient'
import type { Lecture, LectureContent } from './courses'

export interface CreateLectureRequest {
  title: string
  description?: string
  contentType?: 'VIDEO' | 'TEXT'
  contentUrl?: string
  durationSeconds?: number
}

export interface UpdateLectureRequest {
  title?: string
  description?: string
  contentType?: 'VIDEO' | 'TEXT'
  contentUrl?: string
  durationSeconds?: number
  isPublished?: boolean
}

export class LecturesApi {
  constructor(private apiClient: ApiClient) {}

  async getLecturesBySection(sectionId: string): Promise<Lecture[]> {
    const response = await this.apiClient.get<Lecture[]>(`/content/api/sections/${sectionId}/lectures`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getSubLecturesByLecture(courseId: string, lectureId: string): Promise<Lecture[]> {
    const response = await this.apiClient.get<Lecture[]>(`/content/courses/${courseId}/lectures/${lectureId}/sub-lectures`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getLecture(courseId: string, id: string): Promise<Lecture> {
    const response = await this.apiClient.get<Lecture>(`/content/courses/${courseId}/lectures/${id}`)
    return response.data
  }

  async createSubLecture(courseId: string, lectureId: string, request: CreateLectureRequest): Promise<Lecture> {
    try {
      console.log('[lecturesApi.createSubLecture] Starting...')
      // Use the section-based endpoint: /api/sections/{sectionId}/lectures
      const response = await this.apiClient.post<Lecture>(
        `/content/api/sections/${lectureId}/lectures`,
        {
          title: request.title,
          description: request.description,
          contentType: request.contentType || 'TEXT'
        }
      )
      
      console.log('[lecturesApi.createSubLecture] Response received:', response)
      console.log('[lecturesApi.createSubLecture] Response type:', typeof response)
      console.log('[lecturesApi.createSubLecture] Response keys:', response && typeof response === 'object' ? Object.keys(response) : 'N/A')
      console.log('[lecturesApi.createSubLecture] Has id?', response && typeof response === 'object' && 'id' in response)
      console.log('[lecturesApi.createSubLecture] Has data?', response && typeof response === 'object' && 'data' in response)
      
      // The backend returns the lecture DTO directly: {"id":"...","title":"...",...}
      // ApiClient.post returns axios response.data, which is the lecture object directly
      // But TypeScript types it as ApiResponse<Lecture> = { data: Lecture }
      // At runtime, response IS the lecture object (not wrapped)
      
      let lecture: Lecture | null = null
      
      // Check if response itself is the lecture (has 'id' property)
      if (response && typeof response === 'object' && 'id' in response && !Array.isArray(response)) {
        console.log('[lecturesApi.createSubLecture] Response is lecture directly')
        lecture = response as unknown as Lecture
      }
      // Check if response has a 'data' property (wrapped in ApiResponse format)
      else if (response && typeof response === 'object' && 'data' in response) {
        const data = (response as any).data
        console.log('[lecturesApi.createSubLecture] Response has data property:', data)
        if (data && typeof data === 'object' && 'id' in data) {
          console.log('[lecturesApi.createSubLecture] Found lecture in response.data')
          lecture = data as Lecture
        }
      }
      
      console.log('[lecturesApi.createSubLecture] Extracted lecture:', lecture)
      
      if (!lecture) {
        console.error('[lecturesApi.createSubLecture] Failed to extract lecture. Response:', JSON.stringify(response, null, 2))
        throw new Error(`Invalid response from server: lecture object not found. Response: ${JSON.stringify(response)}`)
      }
      
      if (!lecture.id) {
        console.error('[lecturesApi.createSubLecture] Lecture missing id. Lecture:', JSON.stringify(lecture, null, 2))
        throw new Error(`Invalid response from server: lecture object missing 'id' property. Response: ${JSON.stringify(response)}`)
      }
      
      console.log('[lecturesApi.createSubLecture] Returning lecture with id:', lecture.id)
      return lecture
    } catch (error: any) {
      console.error('[lecturesApi.createSubLecture] Error creating sub-lecture:', error)
      console.error('[lecturesApi.createSubLecture] Error message:', error?.message)
      console.error('[lecturesApi.createSubLecture] Error stack:', error?.stack)
      throw error
    }
  }

  async updateSubLecture(courseId: string, lectureId: string, subLectureId: string, request: UpdateLectureRequest): Promise<Lecture> {
    // Use the lecture update endpoint: /api/lectures/{id}
    // Gateway routes /content/api/** to content service, which has controller at /api/lectures/{id}
    const response = await this.apiClient.put<Lecture>(
      `/content/api/lectures/${subLectureId}`,
      {
        title: request.title,
        description: request.description,
        contentType: request.contentType,
        durationSeconds: request.durationSeconds,
        isPublished: request.isPublished,
        isPreview: false
      }
    )
    return response.data
  }

  async deleteSubLecture(courseId: string, lectureId: string, subLectureId: string): Promise<void> {
    await this.apiClient.delete(`/content/api/lectures/${subLectureId}`)
  }

  async updateMainLecture(courseId: string, lectureId: string, request: { title?: string; description?: string }): Promise<any> {
    const response = await this.apiClient.put<any>(
      `/content/courses/${courseId}/lectures/${lectureId}`,
      request
    )
    return response.data
  }

  // Media upload methods
  async uploadVideo(lectureId: string, file: File, onProgress?: (progress: { loaded: number; total: number }) => void): Promise<LectureContent> {
    // Use XMLHttpRequest for better progress tracking on large files
    return new Promise((resolve, reject) => {
      const formData = new FormData()
      formData.append('file', file)
      
      // Calculate timeout based on file size: 1 minute per 50MB, minimum 5 minutes, maximum 30 minutes
      const fileSizeMB = file.size / (1024 * 1024)
      const timeoutMs = Math.min(Math.max(fileSizeMB * 60000, 300000), 1800000) // 5-30 minutes
      
      const xhr = new XMLHttpRequest()
      const baseURL = this.apiClient.getBaseURL()
      const url = `${baseURL}/content/api/lectures/${lectureId}/media/video`
      
      console.log('[uploadVideo] Starting upload:', {
        url,
        fileSize: file.size,
        fileName: file.name,
        hasProgressCallback: !!onProgress
      })
      
      // Set up progress tracking - XMLHttpRequest provides more reliable progress events
      // Track if we've received any progress events
      let progressEventCount = 0
      let lastProgressUpdate = 0
      
      xhr.upload.addEventListener('progress', (e) => {
        progressEventCount++
        const now = Date.now()
        const timeSinceLastUpdate = now - lastProgressUpdate
        
        console.log(`[uploadVideo] Progress event #${progressEventCount} fired:`, {
          loaded: e.loaded,
          total: e.total,
          lengthComputable: e.lengthComputable,
          percentage: e.lengthComputable ? ((e.loaded / e.total) * 100).toFixed(2) + '%' : 'N/A',
          timeSinceLastUpdate: timeSinceLastUpdate + 'ms',
          hasProgressCallback: !!onProgress
        })
        
        lastProgressUpdate = now
        
        if (onProgress) {
          if (e.lengthComputable && e.total > 0) {
            // Use actual progress if available
            const progressData = {
              loaded: e.loaded,
              total: e.total
            }
            console.log('[uploadVideo] Calling onProgress (lengthComputable):', progressData)
            onProgress(progressData)
          } else if (e.loaded > 0) {
            // Fallback: use loaded bytes with file size as total
            const progressData = {
              loaded: e.loaded,
              total: file.size
            }
            console.log('[uploadVideo] Calling onProgress (fallback):', progressData)
            onProgress(progressData)
          } else {
            console.log('[uploadVideo] Progress event but loaded is 0, skipping callback')
          }
        } else {
          console.log('[uploadVideo] Progress event but no callback provided')
        }
      }, false)
      
      // Log a warning if no progress events fire within 3 seconds
      const progressCheckTimeout = setTimeout(() => {
        if (progressEventCount === 0) {
          console.warn('[uploadVideo] WARNING: No progress events received after 3 seconds. This may indicate:')
          console.warn('[uploadVideo] 1. Browser not firing progress events')
          console.warn('[uploadVideo] 2. Server buffering entire request before processing')
          console.warn('[uploadVideo] 3. Network issues preventing progress tracking')
        } else {
          console.log(`[uploadVideo] Progress events are working - received ${progressEventCount} events so far`)
        }
      }, 3000)
      
      // Handle completion
      xhr.addEventListener('load', () => {
        clearTimeout(progressCheckTimeout)
        console.log('[uploadVideo] XHR load event fired, status:', xhr.status)
        console.log(`[uploadVideo] Total progress events received: ${progressEventCount}`)
        if (xhr.status >= 200 && xhr.status < 300) {
          console.log('[uploadVideo] Upload completed successfully')
          try {
            const response = JSON.parse(xhr.responseText)
            // Handle both ApiResponse format and direct response
            const data = response.data || response
            // Call progress callback one last time with 100% if provided
            if (onProgress) {
              console.log('[uploadVideo] Calling onProgress with 100% completion')
              onProgress({
                loaded: file.size,
                total: file.size
              })
            }
            resolve(data)
          } catch (error) {
            console.error('[uploadVideo] Failed to parse response:', error)
            reject(new Error('Failed to parse response'))
          }
        } else {
          console.error('[uploadVideo] Upload failed with status:', xhr.status)
          try {
            const errorResponse = JSON.parse(xhr.responseText)
            reject(new Error(errorResponse.message || `Upload failed with status ${xhr.status}`))
          } catch {
            reject(new Error(`Upload failed with status ${xhr.status}`))
          }
        }
      }, false)
      
      // Handle errors
      xhr.addEventListener('error', () => {
        clearTimeout(progressCheckTimeout)
        console.error('[uploadVideo] Upload error occurred. Total progress events received:', progressEventCount)
        reject(new Error('Upload failed - network error'))
      }, false)
      
      xhr.addEventListener('abort', () => {
        clearTimeout(progressCheckTimeout)
        console.warn('[uploadVideo] Upload aborted. Total progress events received:', progressEventCount)
        reject(new Error('Upload aborted'))
      }, false)
      
      // Set timeout
      xhr.timeout = timeoutMs
      xhr.addEventListener('timeout', () => {
        clearTimeout(progressCheckTimeout)
        console.error('[uploadVideo] Upload timeout. Total progress events received:', progressEventCount)
        reject(new Error('Upload timeout'))
      }, false)
      
      // Set up request
      xhr.open('POST', url, true)
      console.log('[uploadVideo] XHR opened, setting headers')
      
      // Get auth token and set headers
      const token = this.apiClient.getToken()
      if (token) {
        xhr.setRequestHeader('Authorization', `Bearer ${token}`)
        console.log('[uploadVideo] Authorization header set')
      } else {
        console.warn('[uploadVideo] No auth token available')
      }
      
      // Get tenant ID from localStorage (matching ApiClient behavior)
      if (typeof window !== 'undefined') {
        const tenantId = localStorage.getItem('clientId') || 
                        localStorage.getItem('selectedTenantId') || 
                        localStorage.getItem('tenant_id')
        if (tenantId && tenantId !== 'PENDING_TENANT_SELECTION' && 
            tenantId !== 'SYSTEM' && tenantId !== 'null' && tenantId !== '') {
          xhr.setRequestHeader('X-Client-Id', tenantId)
          console.log('[uploadVideo] X-Client-Id header set:', tenantId)
        }
      }
      
      // Send request
      console.log('[uploadVideo] Sending request...', {
        fileSize: file.size,
        fileName: file.name,
        url: url,
        fileSizeMB: (file.size / (1024 * 1024)).toFixed(2)
      })
      xhr.send(formData)
      console.log('[uploadVideo] Request sent, waiting for progress events...')
      console.log('[uploadVideo] XMLHttpRequest upload progress listener attached:', !!xhr.upload.onprogress)
    })
  }

  async uploadAudio(lectureId: string, file: File): Promise<LectureContent> {
    const formData = new FormData()
    formData.append('file', file)
    
    // Calculate timeout based on file size: 1 minute per 50MB, minimum 2 minutes, maximum 20 minutes
    const fileSizeMB = file.size / (1024 * 1024)
    const timeoutMs = Math.min(Math.max(fileSizeMB * 60000, 120000), 1200000) // 2-20 minutes
    
    const response = await this.apiClient.postForm<LectureContent>(
      `/content/api/lectures/${lectureId}/media/audio`,
      formData,
      { timeout: timeoutMs } // Override default 30s timeout for large audio uploads
    )
    return response.data
  }

  async uploadDocument(lectureId: string, file: File): Promise<LectureContent> {
    const formData = new FormData()
    formData.append('file', file)
    
    // Calculate timeout based on file size: 1 minute per 50MB, minimum 2 minutes, maximum 15 minutes
    const fileSizeMB = file.size / (1024 * 1024)
    const timeoutMs = Math.min(Math.max(fileSizeMB * 60000, 120000), 900000) // 2-15 minutes
    
    const response = await this.apiClient.postForm<LectureContent>(
      `/content/api/lectures/${lectureId}/media/document`,
      formData,
      { timeout: timeoutMs } // Override default 30s timeout for large document uploads
    )
    return response.data
  }

  async uploadTranscript(lectureId: string, file: File): Promise<LectureContent> {
    const formData = new FormData()
    formData.append('file', file)
    const response = await this.apiClient.postForm<LectureContent>(
      `/content/api/lectures/${lectureId}/media/transcript`,
      formData
    )
    // Handle both ApiResponse format and direct lecture content
    if (response && typeof response === 'object' && 'id' in response) {
      return response as unknown as LectureContent
    }
    if (response && typeof response === 'object' && 'data' in response) {
      return (response as any).data as LectureContent
    }
    return response as unknown as LectureContent
  }

  async uploadAttachments(lectureId: string, files: File[]): Promise<LectureContent[]> {
    const formData = new FormData()
    files.forEach(file => {
      formData.append('files', file)
    })
    const response = await this.apiClient.postForm<LectureContent[]>(
      `/content/api/lectures/${lectureId}/media/attachments`,
      formData
    )
    return Array.isArray(response.data) ? response.data : []
  }

  async getLectureMedia(lectureId: string): Promise<LectureContent[]> {
    const response = await this.apiClient.get<LectureContent[]>(
      `/content/api/lectures/${lectureId}/media`
    )
    return Array.isArray(response.data) ? response.data : []
  }

  async deleteMedia(contentId: string): Promise<void> {
    await this.apiClient.delete(`/content/api/lectures/media/${contentId}`)
  }

  async createTextContent(lectureId: string, textContent: string, title?: string, sequence?: number): Promise<LectureContent> {
    const response = await this.apiClient.post<LectureContent>(
      `/content/api/lectures/${lectureId}/media/text`,
      {
        textContent,
        title,
        sequence
      }
    )
    return response.data
  }

  async updateTextContent(contentId: string, textContent: string, title?: string): Promise<LectureContent> {
    const response = await this.apiClient.put<LectureContent>(
      `/content/api/lectures/media/${contentId}/text`,
      {
        textContent,
        title
      }
    )
    return response.data
  }
}

