import { ApiClient } from './ApiClient'

export interface CertificateTemplate {
  id: string
  name: string
  description?: string
  config: Record<string, unknown>
  backgroundImageUrl?: string
  isDefault: boolean
  createdAt: string
}

export interface Certificate {
  id: string
  studentId: string
  studentName?: string
  studentEmail?: string
  courseId: string
  courseName?: string
  sectionId?: string
  classId?: string
  templateId: string
  credentialId: string
  qrCodeUrl: string
  pdfUrl: string
  issuedAt: string
  issuedBy: string
  revoked: boolean
  revokedAt?: string
  revokedReason?: string
  metadata?: Record<string, unknown>
  visibility?: CertificateVisibility
}

export interface CertificateVisibility {
  showScores: boolean
  showProjectDetails: boolean
  showOverallPercentage: boolean
  showCourseName: boolean
}

export interface CertificateGenerateRequest {
  courseId: string
  sectionId?: string
  classId?: string
  templateId: string
  students: { name: string; email: string }[]
}

export interface CertificateVerification {
  credentialId: string
  studentName: string
  courseName?: string
  institutionName?: string
  institutionLogoUrl?: string
  issuedAt: string
  valid: boolean
  revoked: boolean
  revokedAt?: string
  revokedReason?: string
  pdfUrl: string
  scores?: Record<string, unknown>
  visibility?: CertificateVisibility
}

export class CertificatesApi {
  constructor(private apiClient: ApiClient) {}

  // Templates
  async listTemplates(): Promise<CertificateTemplate[]> {
    const response = await this.apiClient.get<CertificateTemplate[]>('/api/certificates/templates')
    return Array.isArray(response.data) ? response.data : []
  }

  async createTemplate(template: Partial<CertificateTemplate>): Promise<CertificateTemplate> {
    const response = await this.apiClient.post<CertificateTemplate>('/api/certificates/templates', template)
    return response.data
  }

  async updateTemplate(id: string, template: Partial<CertificateTemplate>): Promise<CertificateTemplate> {
    const response = await this.apiClient.put<CertificateTemplate>(`/api/certificates/templates/${id}`, template)
    return response.data
  }

  async deleteTemplate(id: string): Promise<void> {
    await this.apiClient.delete(`/api/certificates/templates/${id}`)
  }

  async exportTemplate(id: string): Promise<Blob> {
    return this.apiClient.downloadFile(`/api/certificates/templates/${id}/export`)
  }

  async importTemplate(file: File): Promise<CertificateTemplate> {
    const formData = new FormData()
    formData.append('file', file)
    const response = await this.apiClient.postForm<CertificateTemplate>('/api/certificates/templates/import', formData)
    return response.data
  }

  // Generation
  async generate(request: CertificateGenerateRequest): Promise<Certificate[]> {
    const response = await this.apiClient.post<Certificate[]>('/api/certificates/generate', request)
    return Array.isArray(response.data) ? response.data : []
  }

  // Listing
  async list(params?: {
    courseId?: string
    sectionId?: string
    classId?: string
    page?: number
    size?: number
  }): Promise<{ content: Certificate[]; totalElements: number; totalPages: number }> {
    const response = await this.apiClient.get<any>('/api/certificates', { params })

    if (response.data && response.data.content && Array.isArray(response.data.content)) {
      return {
        content: response.data.content,
        totalElements: response.data.totalElements || 0,
        totalPages: response.data.totalPages || 1,
      }
    }
    if (Array.isArray(response.data)) {
      return { content: response.data, totalElements: response.data.length, totalPages: 1 }
    }
    return { content: [], totalElements: 0, totalPages: 1 }
  }

  // Downloads
  async downloadPdf(id: string): Promise<Blob> {
    return this.apiClient.downloadFile(`/api/certificates/${id}/download`)
  }

  async downloadAllAsZip(sectionId: string, courseId: string): Promise<Blob> {
    return this.apiClient.downloadFile(`/api/certificates/download-all?sectionId=${sectionId}&courseId=${courseId}`)
  }

  // Management
  async revoke(id: string, reason: string): Promise<void> {
    await this.apiClient.post(`/api/certificates/${id}/revoke`, { reason })
  }

  // Student
  async myCertificates(): Promise<Certificate[]> {
    const response = await this.apiClient.get<Certificate[]>('/api/certificates/my')
    return Array.isArray(response.data) ? response.data : []
  }

  async updateVisibility(id: string, visibility: CertificateVisibility): Promise<Certificate> {
    const response = await this.apiClient.put<Certificate>(`/api/certificates/${id}/visibility`, visibility)
    return response.data
  }

  // Public verification
  async verify(credentialId: string): Promise<CertificateVerification> {
    const response = await this.apiClient.get<CertificateVerification>(`/api/verify/${credentialId}`)
    return response.data
  }
}
