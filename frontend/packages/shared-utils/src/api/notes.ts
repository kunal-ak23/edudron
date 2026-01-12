import { ApiClient } from './ApiClient'

export interface Note {
  id: string
  studentId: string
  lectureId: string
  courseId: string
  highlightedText?: string
  highlightColor?: string
  noteText?: string
  context?: string
  createdAt: string
  updatedAt: string
}

export interface CreateNoteRequest {
  lectureId: string
  courseId: string
  highlightedText?: string
  highlightColor?: string
  noteText?: string
  context?: string
}

export class NotesApi {
  constructor(private apiClient: ApiClient) {}

  async createNote(lectureId: string, request: CreateNoteRequest): Promise<Note> {
    const response = await this.apiClient.post<Note>(
      `/api/lectures/${lectureId}/notes`,
      request
    )
    return response.data
  }

  async updateNote(noteId: string, request: CreateNoteRequest): Promise<Note> {
    const response = await this.apiClient.put<Note>(
      `/api/notes/${noteId}`,
      request
    )
    return response.data
  }

  async getNotesByLecture(lectureId: string): Promise<Note[]> {
    const response = await this.apiClient.get<Note[]>(`/api/lectures/${lectureId}/notes`)
    return Array.isArray(response.data) ? response.data : []
  }

  async getNotesByCourse(courseId: string): Promise<Note[]> {
    const response = await this.apiClient.get<Note[]>(`/api/courses/${courseId}/notes`)
    return Array.isArray(response.data) ? response.data : []
  }

  async deleteNote(noteId: string): Promise<void> {
    await this.apiClient.delete(`/api/notes/${noteId}`)
  }
}

