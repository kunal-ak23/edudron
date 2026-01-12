import { ApiClient } from './ApiClient'

export type IssueType = 
  | 'CONTENT_ERROR'
  | 'TECHNICAL_ISSUE'
  | 'VIDEO_PLAYBACK'
  | 'AUDIO_QUALITY'
  | 'TRANSCRIPT_ERROR'
  | 'OTHER'

export type IssueStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'

export interface IssueReport {
  id: string
  studentId: string
  lectureId: string
  courseId: string
  issueType: IssueType
  description: string
  status: IssueStatus
  createdAt: string
  updatedAt: string
}

export interface CreateIssueReportRequest {
  lectureId: string
  courseId: string
  issueType: IssueType
  description: string
}

export class IssuesApi {
  constructor(private apiClient: ApiClient) {}

  async reportIssue(lectureId: string, request: CreateIssueReportRequest): Promise<IssueReport> {
    const response = await this.apiClient.post<IssueReport>(
      `/api/lectures/${lectureId}/issues`,
      request
    )
    return response.data
  }

  async getIssuesByLecture(lectureId: string): Promise<IssueReport[]> {
    const response = await this.apiClient.get<IssueReport[]>(`/api/lectures/${lectureId}/issues`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getIssuesByCourse(courseId: string): Promise<IssueReport[]> {
    const response = await this.apiClient.get<IssueReport[]>(`/api/courses/${courseId}/issues`)
    return Array.isArray(response.data) ? response.data : []
  }
}

