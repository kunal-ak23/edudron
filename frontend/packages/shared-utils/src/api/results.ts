import { ApiClient } from './ApiClient'

export class ResultsApi {
  constructor(private apiClient: ApiClient) {}

  async exportBySection(sectionId: string): Promise<Blob> {
    return this.apiClient.downloadFile(`/api/results/export?sectionId=${sectionId}`, { timeout: 600000 })
  }

  async exportByClass(classId: string): Promise<Blob> {
    return this.apiClient.downloadFile(`/api/results/export?classId=${classId}`, { timeout: 600000 })
  }

  async exportByCourse(courseId: string): Promise<Blob> {
    return this.apiClient.downloadFile(`/api/results/export?courseId=${courseId}`, { timeout: 600000 })
  }
}
