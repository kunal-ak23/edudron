import { ApiClient } from './ApiClient'

export interface StudentImportRowResult {
  rowNumber: number
  email: string
  name: string
  success: boolean
  studentId?: string
  errorMessage?: string
}

export interface BulkStudentImportResult {
  totalRows: number
  processedRows: number
  successfulRows: number
  failedRows: number
  skippedRows: number
  rowResults: StudentImportRowResult[]
}

export interface BulkEnrollmentResult {
  totalStudents: number
  enrolledStudents: number
  skippedStudents: number
  failedStudents: number
  enrolledStudentIds?: string[]
  errorMessages?: string[]
}

export class StudentsApi {
  constructor(private apiClient: ApiClient) {}

  async bulkImport(
    file: File,
    options: {
      autoGeneratePassword?: boolean
      upsertExisting?: boolean
      autoEnroll?: boolean
      defaultCourseIds?: string[]
    } = {}
  ): Promise<BulkStudentImportResult> {
    const formData = new FormData()
    formData.append('file', file)
    
    if (options.autoGeneratePassword !== undefined) {
      formData.append('autoGeneratePassword', String(options.autoGeneratePassword))
    }
    if (options.upsertExisting !== undefined) {
      formData.append('upsertExisting', String(options.upsertExisting))
    }
    if (options.autoEnroll !== undefined) {
      formData.append('autoEnroll', String(options.autoEnroll))
    }
    if (options.defaultCourseIds && options.defaultCourseIds.length > 0) {
      formData.append('defaultCourseIds', options.defaultCourseIds.join(','))
    }

    const response = await this.apiClient.postForm<BulkStudentImportResult>(
      '/api/students/bulk-import',
      formData
    )
    return response.data
  }
}

