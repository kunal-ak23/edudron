import { ApiClient } from './ApiClient'

export class ResultsApi {
  constructor(private apiClient: ApiClient) {}

  async exportBySection(sectionId: string): Promise<Blob> {
    return this.apiClient.downloadFile(`/api/content/results/export/section/${sectionId}`)
  }

  async exportByClass(classId: string): Promise<Blob> {
    return this.apiClient.downloadFile(`/api/content/results/export/class/${classId}`)
  }

  async exportByCourse(courseId: string): Promise<Blob> {
    return this.apiClient.downloadFile(`/api/content/results/export/course/${courseId}`)
  }
}
