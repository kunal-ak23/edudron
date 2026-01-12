import { ApiClient } from './ApiClient'

export type FeedbackType = 'LIKE' | 'DISLIKE'

export interface Feedback {
  id: string
  studentId: string
  lectureId: string
  courseId: string
  type: FeedbackType
  comment?: string
  createdAt: string
  updatedAt: string
}

export interface CreateFeedbackRequest {
  lectureId: string
  courseId: string
  type: FeedbackType
  comment?: string
}

export class FeedbackApi {
  constructor(private apiClient: ApiClient) {}

  async createOrUpdateFeedback(lectureId: string, request: CreateFeedbackRequest): Promise<Feedback> {
    const response = await this.apiClient.post<Feedback>(
      `/api/lectures/${lectureId}/feedback`,
      request
    )
    return response.data
  }

  async getFeedback(lectureId: string): Promise<Feedback | null> {
    try {
      const response = await this.apiClient.get<Feedback>(`/api/lectures/${lectureId}/feedback`)
      return response.data
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null
      }
      throw error
    }
  }

  async getAllFeedbackForLecture(lectureId: string): Promise<Feedback[]> {
    const response = await this.apiClient.get<Feedback[]>(`/api/lectures/${lectureId}/feedback/all`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getFeedbackByCourse(courseId: string): Promise<Feedback[]> {
    const response = await this.apiClient.get<Feedback[]>(`/api/courses/${courseId}/feedback`)
    return Array.isArray(response.data) ? response.data : []
  }

  async deleteFeedback(lectureId: string): Promise<void> {
    await this.apiClient.delete(`/api/lectures/${lectureId}/feedback`)
  }
}

