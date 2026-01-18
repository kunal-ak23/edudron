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
    // Use the section-based endpoint: /api/sections/{sectionId}/lectures
    const response = await this.apiClient.post<Lecture>(
      `/content/api/sections/${lectureId}/lectures`,
      {
        title: request.title,
        description: request.description,
        contentType: request.contentType || 'TEXT'
      }
    )

    // The backend returns the lecture DTO directly: {"id":"...","title":"...",...}
    // ApiClient.post returns axios response.data, which is the lecture object directly,
    // but TS types it as ApiResponse<Lecture> = { data: Lecture }.
    let lecture: Lecture | null = null

    // Check if response itself is the lecture (has 'id' property)
    if (response && typeof response === 'object' && 'id' in response && !Array.isArray(response)) {
      lecture = response as unknown as Lecture
    }
    // Check if response has a 'data' property (wrapped in ApiResponse format)
    else if (response && typeof response === 'object' && 'data' in response) {
      const data = (response as any).data
      if (data && typeof data === 'object' && 'id' in data) {
        lecture = data as Lecture
      }
    }

    if (!lecture) {
      throw new Error(`Invalid response from server: lecture object not found. Response: ${JSON.stringify(response)}`)
    }

    if (!lecture.id) {
      throw new Error(`Invalid response from server: lecture object missing 'id' property. Response: ${JSON.stringify(response)}`)
    }

    return lecture
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

      // Set up progress tracking - XMLHttpRequest provides more reliable progress events
      xhr.upload.addEventListener('progress', (e) => {
        if (onProgress) {
          if (e.lengthComputable && e.total > 0) {
            // Use actual progress if available
            onProgress({ loaded: e.loaded, total: e.total })
          } else if (e.loaded > 0) {
            // Fallback: use loaded bytes with file size as total
            onProgress({ loaded: e.loaded, total: file.size })
          }
        }
      }, false)
      
      // Handle completion
      xhr.addEventListener('load', () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          try {
            const response = JSON.parse(xhr.responseText)
            // Handle both ApiResponse format and direct response
            const data = response.data || response
            // Call progress callback one last time with 100% if provided
            if (onProgress) {
              onProgress({
                loaded: file.size,
                total: file.size
              })
            }
            resolve(data)
          } catch (error) {
            reject(new Error('Failed to parse response'))
          }
        } else {
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
        reject(new Error('Upload failed - network error'))
      }, false)
      
      xhr.addEventListener('abort', () => {
        reject(new Error('Upload aborted'))
      }, false)
      
      // Set timeout
      xhr.timeout = timeoutMs
      xhr.addEventListener('timeout', () => {
        reject(new Error('Upload timeout'))
      }, false)
      
      // Set up request
      xhr.open('POST', url, true)
      
      // Get auth token and set headers
      const token = this.apiClient.getToken()
      if (token) {
        xhr.setRequestHeader('Authorization', `Bearer ${token}`)
      }
      
      // Get tenant ID from localStorage (matching ApiClient behavior)
      if (typeof window !== 'undefined') {
        const tenantId = localStorage.getItem('clientId') || 
                        localStorage.getItem('selectedTenantId') || 
                        localStorage.getItem('tenant_id')
        if (tenantId && tenantId !== 'PENDING_TENANT_SELECTION' && 
            tenantId !== 'SYSTEM' && tenantId !== 'null' && tenantId !== '') {
          xhr.setRequestHeader('X-Client-Id', tenantId)
        }
      }
      
      // Send request
      xhr.send(formData)
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

