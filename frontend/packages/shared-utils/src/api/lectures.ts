import { ApiClient } from './ApiClient'
import type { Lecture, LectureContent } from './courses'

export interface CreateLectureRequest {
  title: string
  description?: string
  contentType?: 'VIDEO' | 'TEXT' | 'AUDIO' | 'DOCUMENT'
  contentUrl?: string
  durationSeconds?: number
}

export interface UpdateLectureRequest {
  title?: string
  description?: string
  contentType?: 'VIDEO' | 'TEXT' | 'AUDIO' | 'DOCUMENT'
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
    return response.data
  }

  async updateSubLecture(courseId: string, lectureId: string, subLectureId: string, request: UpdateLectureRequest): Promise<Lecture> {
    // Use the lecture update endpoint: /api/lectures/{id}
    const response = await this.apiClient.put<Lecture>(
      `/content/api/lectures/${subLectureId}`,
      {
        title: request.title,
        description: request.description,
        durationSeconds: request.durationSeconds,
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
  async uploadVideo(lectureId: string, file: File): Promise<LectureContent> {
    const formData = new FormData()
    formData.append('file', file)
    const response = await this.apiClient.postForm<LectureContent>(
      `/content/api/lectures/${lectureId}/media/video`,
      formData
    )
    return response.data
  }

  async uploadAudio(lectureId: string, file: File): Promise<LectureContent> {
    const formData = new FormData()
    formData.append('file', file)
    const response = await this.apiClient.postForm<LectureContent>(
      `/content/api/lectures/${lectureId}/media/audio`,
      formData
    )
    return response.data
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
}

