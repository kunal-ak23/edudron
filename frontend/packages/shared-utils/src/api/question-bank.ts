/**
 * Question Bank API types and functions
 */

export type QuestionType = 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'ESSAY' | 'MATCHING'
export type DifficultyLevel = 'EASY' | 'MEDIUM' | 'HARD'
export type TimingMode = 'FIXED_WINDOW' | 'FLEXIBLE_START'

export interface QuestionBankOption {
  id: string
  questionId: string
  optionText: string
  isCorrect: boolean
  sequence: number
}

export interface QuestionBank {
  id: string
  clientId: string
  courseId: string
  moduleIds: string[] // Supports multiple modules
  moduleId?: string // Legacy - kept for backward compatibility
  subModuleIds?: string[] // Supports multiple sub-modules (lectures)
  subModuleId?: string // Legacy - kept for backward compatibility
  questionType: QuestionType
  questionText: string
  defaultPoints: number
  difficultyLevel?: DifficultyLevel
  explanation?: string
  tags?: string[]
  isActive: boolean
  tentativeAnswer?: string
  options?: QuestionBankOption[]
  createdAt: string
  updatedAt: string
}

export interface ExamQuestion {
  id: string
  clientId: string
  examId: string
  questionId: string
  sequence: number
  pointsOverride?: number
  question?: QuestionBank
  createdAt: string
}

export interface CreateQuestionRequest {
  courseId: string
  moduleIds?: string[] // Supports multiple modules
  moduleId?: string // Legacy - kept for backward compatibility
  subModuleIds?: string[] // Supports multiple sub-modules (lectures)
  subModuleId?: string // Legacy - kept for backward compatibility
  questionType: QuestionType
  questionText: string
  points?: number
  difficultyLevel?: DifficultyLevel
  explanation?: string
  tags?: string[]
  tentativeAnswer?: string
  options?: Array<{ text: string; correct: boolean }>
}

export interface UpdateQuestionRequest {
  questionText?: string
  points?: number
  difficultyLevel?: DifficultyLevel
  explanation?: string
  tags?: string[]
  tentativeAnswer?: string
  subModuleIds?: string[] // Supports multiple sub-modules (lectures)
  subModuleId?: string // Legacy - kept for backward compatibility
  moduleIds?: string[] // Supports updating module associations
  options?: Array<{ text: string; correct: boolean }>
}

export interface GenerateExamPaperRequest {
  moduleIds?: string[]
  numberOfQuestions?: number
  difficultyLevel?: DifficultyLevel
  questionTypes?: QuestionType[]
  difficultyDistribution?: Record<DifficultyLevel, number>
  randomize?: boolean
  clearExisting?: boolean
}

export interface ExamStats {
  questionCount: number
  totalPoints: number
}

/**
 * Exam with timing mode information
 */
export interface ExamWithTimingMode {
  id: string
  title: string
  timeLimitSeconds?: number
  startTime?: string
  endTime?: string
  timingMode?: TimingMode
  // ... other exam fields
}
