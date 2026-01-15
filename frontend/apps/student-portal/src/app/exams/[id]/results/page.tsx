'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { Button, Label, ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { StudentLayout } from '@/components/StudentLayout'
import { Loader2, CheckCircle, XCircle, ArrowLeft } from 'lucide-react'
import { apiClient } from '@/lib/api'

interface Submission {
  id: string
  score: number | null
  maxScore: number | null
  percentage: number | null
  isPassed: boolean
  answersJson: Record<string, any>
  aiReviewFeedback?: {
    questionReviews: QuestionReview[]
    totalScore: number
    maxScore: number
    percentage: number
  }
  reviewStatus: string
}

interface QuestionReview {
  questionId: string
  pointsEarned: number
  maxPoints: number
  feedback: string
  isCorrect: boolean
}

interface Exam {
  id: string
  title: string
  questions: Question[]
}

interface Question {
  id: string
  questionText: string
  questionType: string
  points: number
  options?: Option[]
}

interface Option {
  id: string
  optionText: string
  isCorrect: boolean
}

export const dynamic = 'force-dynamic'

export default function ExamResultsPage() {
  const params = useParams()
  const router = useRouter()
  const examId = params.id as string
  const [exam, setExam] = useState<Exam | null>(null)
  const [submission, setSubmission] = useState<Submission | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadResults()
  }, [examId])

  const loadResults = async () => {
    try {
      setLoading(true)
      
      // Load exam
      try {
        const examData = await apiClient.get<Exam>(`/api/student/exams/${examId}`)
        setExam(examData)
      } catch (error) {
        console.error('Failed to load exam:', error)
      }

      // Load submission
      try {
        const submissionData = await apiClient.get<Submission>(`/api/student/exams/${examId}/submission`)
        setSubmission(submissionData)
      } catch (error) {
        console.error('Failed to load submission:', error)
      }
    } catch (error) {
      console.error('Failed to load results:', error)
    } finally {
      setLoading(false)
    }
  }

  const getQuestionReview = (questionId: string): QuestionReview | null => {
    if (!submission?.aiReviewFeedback?.questionReviews) return null
    return submission.aiReviewFeedback.questionReviews.find(
      r => r.questionId === questionId
    ) || null
  }

  if (loading) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="flex items-center justify-center h-64">
            <Loader2 className="h-8 w-8 animate-spin" />
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  if (!submission || !exam) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="text-center py-12">
            <p className="text-gray-500">Results not available</p>
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="max-w-4xl mx-auto space-y-6">
          <div className="flex items-center gap-4">
            <Button variant="ghost" onClick={() => router.push('/exams')}>
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Exams
            </Button>
            <div>
              <h1 className="text-3xl font-bold">{exam.title}</h1>
              <p className="text-gray-600 mt-1">Exam Results</p>
            </div>
          </div>

          {/* Score Summary */}
          <Card className="border-2">
            <CardHeader>
              <CardTitle>Your Score</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-4xl font-bold">
                    {submission.score !== null && submission.score !== undefined 
                      ? submission.score 
                      : 'N/A'} / {submission.maxScore !== null && submission.maxScore !== undefined 
                      ? submission.maxScore 
                      : 'N/A'}
                  </div>
                  <div className="text-2xl font-semibold mt-2">
                    {(() => {
                      // Calculate percentage if not available
                      let percentage = submission.percentage
                      if (percentage === null || percentage === undefined) {
                        if (submission.score !== null && submission.score !== undefined && 
                            submission.maxScore !== null && submission.maxScore !== undefined && 
                            submission.maxScore > 0) {
                          percentage = (submission.score / submission.maxScore) * 100
                        } else {
                          return 'Not Graded'
                        }
                      }
                      return `${percentage.toFixed(1)}%`
                    })()}
                  </div>
                </div>
                <div className="text-right">
                  {submission.isPassed ? (
                    <Badge className="bg-green-500 text-white text-lg px-4 py-2">
                      <CheckCircle className="h-5 w-5 mr-2" />
                      Passed
                    </Badge>
                  ) : (
                    <Badge variant="destructive" className="text-lg px-4 py-2">
                      <XCircle className="h-5 w-5 mr-2" />
                      Not Passed
                    </Badge>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Question Reviews */}
          {exam.questions && exam.questions.length > 0 && (
            <div className="space-y-4">
              <h2 className="text-2xl font-semibold">Question Review</h2>
              {exam.questions.map((question, index) => {
                const review = getQuestionReview(question.id)
                const studentAnswer = submission.answersJson[question.id]
                const isCorrect = review?.isCorrect ?? false

                return (
                  <Card key={question.id} className={isCorrect ? 'border-green-200' : 'border-red-200'}>
                    <CardHeader>
                      <div className="flex items-start justify-between">
                        <CardTitle className="text-lg">
                          Question {index + 1}
                        </CardTitle>
                        <div className="flex items-center gap-2">
                          {isCorrect ? (
                            <CheckCircle className="h-5 w-5 text-green-600" />
                          ) : (
                            <XCircle className="h-5 w-5 text-red-600" />
                          )}
                          <Badge variant={isCorrect ? 'default' : 'destructive'}>
                            {review?.pointsEarned || 0} / {question.points} points
                          </Badge>
                        </div>
                      </div>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <div>
                        <p className="font-medium">{question.questionText}</p>
                      </div>

                      {question.questionType === 'MULTIPLE_CHOICE' && question.options && (
                        <div className="space-y-2">
                          {question.options.map((option, optIndex) => {
                            const isSelected = studentAnswer === option.id
                            const isCorrectOption = option.isCorrect
                            return (
                              <div
                                key={option.id}
                                className={`p-3 rounded border ${
                                  isCorrectOption
                                    ? 'bg-green-50 border-green-200'
                                    : isSelected
                                    ? 'bg-red-50 border-red-200'
                                    : 'bg-gray-50'
                                }`}
                              >
                                <div className="flex items-center gap-2">
                                  <span className="font-medium">{String.fromCharCode(65 + optIndex)}.</span>
                                  <span>{option.optionText}</span>
                                  {isCorrectOption && (
                                    <Badge variant="default" className="ml-auto">Correct</Badge>
                                  )}
                                  {isSelected && !isCorrectOption && (
                                    <Badge variant="destructive" className="ml-auto">Your Answer</Badge>
                                  )}
                                </div>
                              </div>
                            )
                          })}
                        </div>
                      )}

                      {(question.questionType === 'SHORT_ANSWER' || question.questionType === 'ESSAY') && (
                        <div className="space-y-2">
                          <div>
                            <Label className="text-sm font-medium">Your Answer</Label>
                            <div className="mt-1 p-3 bg-gray-50 rounded border">
                              {studentAnswer || 'No answer provided'}
                            </div>
                          </div>
                          {review?.feedback && (
                            <div>
                              <Label className="text-sm font-medium">Feedback</Label>
                              <div className="mt-1 p-3 bg-blue-50 rounded border border-blue-200">
                                {review.feedback}
                              </div>
                            </div>
                          )}
                        </div>
                      )}

                      {question.questionType === 'TRUE_FALSE' && (
                        <div className="space-y-2">
                          <div className={`p-3 rounded border ${
                            studentAnswer === true ? 'bg-blue-50 border-blue-200' : 'bg-gray-50'
                          }`}>
                            Your Answer: {studentAnswer === true ? 'True' : 'False'}
                          </div>
                        </div>
                      )}
                    </CardContent>
                  </Card>
                )
              })}
            </div>
          )}
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}
