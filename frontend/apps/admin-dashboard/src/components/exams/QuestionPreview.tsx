'use client'

import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Loader2, AlertTriangle, HelpCircle, ChevronDown, ChevronUp } from 'lucide-react'
import { apiClient } from '@/lib/api'

interface QuestionBank {
  id: string
  questionType: 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'ESSAY' | 'MATCHING'
  questionText: string
  defaultPoints: number
  difficultyLevel?: 'EASY' | 'MEDIUM' | 'HARD'
}

interface QuestionPreviewProps {
  courseId: string
  moduleIds: string[]
  numberOfQuestions: number
  difficultyLevel?: string
}

interface QuestionStats {
  total: number
  byDifficulty: {
    EASY: number
    MEDIUM: number
    HARD: number
    UNSET: number
  }
  byType: {
    MULTIPLE_CHOICE: number
    TRUE_FALSE: number
    SHORT_ANSWER: number
    ESSAY: number
    MATCHING: number
  }
}

export function QuestionPreview({ courseId, moduleIds, numberOfQuestions, difficultyLevel }: QuestionPreviewProps) {
  const [questions, setQuestions] = useState<QuestionBank[]>([])
  const [stats, setStats] = useState<QuestionStats | null>(null)
  const [loading, setLoading] = useState(false)
  const [showSamples, setShowSamples] = useState(false)

  // Use a stable string representation of moduleIds for dependency tracking
  const moduleIdsKey = moduleIds.join(',')
  
  useEffect(() => {
    const loadQuestions = async () => {
      if (!courseId || moduleIds.length === 0) {
        setQuestions([])
        setStats(null)
        return
      }
      
      setLoading(true)
      try {
        const url = '/api/question-bank/modules?moduleIds=' + moduleIds.join(',')
        const response = await apiClient.get<QuestionBank[]>(url)
        
        // Handle both direct array response and wrapped {data: [...]} response
        let allQuestions: QuestionBank[] = []
        if (Array.isArray(response)) {
          allQuestions = response
        } else if (response && typeof response === 'object') {
          // Check for .data property (ApiClient wraps responses)
          const responseObj = response as any
          if (Array.isArray(responseObj.data)) {
            allQuestions = responseObj.data
          } else if (responseObj.content && Array.isArray(responseObj.content)) {
            // Paginated response
            allQuestions = responseObj.content
          }
        }
        
        
        // Calculate stats from all questions
        const calculatedStats: QuestionStats = {
          total: allQuestions.length,
          byDifficulty: {
            EASY: allQuestions.filter(q => q.difficultyLevel === 'EASY').length,
            MEDIUM: allQuestions.filter(q => q.difficultyLevel === 'MEDIUM').length,
            HARD: allQuestions.filter(q => q.difficultyLevel === 'HARD').length,
            UNSET: allQuestions.filter(q => !q.difficultyLevel).length,
          },
          byType: {
            MULTIPLE_CHOICE: allQuestions.filter(q => q.questionType === 'MULTIPLE_CHOICE').length,
            TRUE_FALSE: allQuestions.filter(q => q.questionType === 'TRUE_FALSE').length,
            SHORT_ANSWER: allQuestions.filter(q => q.questionType === 'SHORT_ANSWER').length,
            ESSAY: allQuestions.filter(q => q.questionType === 'ESSAY').length,
            MATCHING: allQuestions.filter(q => q.questionType === 'MATCHING').length,
          }
        }
        setStats(calculatedStats)
        
        // Filter by difficulty if specified
        if (difficultyLevel) {
          allQuestions = allQuestions.filter(q => q.difficultyLevel === difficultyLevel)
        }
        
        // Take sample questions (first 5)
        setQuestions(allQuestions.slice(0, 5))
      } catch (error) {
        setQuestions([])
        setStats(null)
      } finally {
        setLoading(false)
      }
    }
    
    loadQuestions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId, moduleIdsKey, difficultyLevel])

  if (!courseId) {
    return null
  }

  if (moduleIds.length === 0) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-base flex items-center gap-2">
            <HelpCircle className="h-4 w-4" />
            Question Bank Preview
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-gray-500">
            Select modules to see available questions from the question bank.
          </p>
        </CardContent>
      </Card>
    )
  }

  if (loading) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-base">Question Bank Preview</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center py-4">
            <Loader2 className="h-5 w-5 animate-spin text-gray-400" />
          </div>
        </CardContent>
      </Card>
    )
  }

  const availableCount = stats?.total || 0
  const filteredCount = difficultyLevel 
    ? stats?.byDifficulty[difficultyLevel as keyof typeof stats.byDifficulty] || 0
    : availableCount
  const isInsufficient = filteredCount < numberOfQuestions

  const getDifficultyColor = (difficulty?: string) => {
    switch (difficulty) {
      case 'EASY': return 'bg-green-100 text-green-800'
      case 'MEDIUM': return 'bg-yellow-100 text-yellow-800'
      case 'HARD': return 'bg-red-100 text-red-800'
      default: return 'bg-gray-100 text-gray-600'
    }
  }

  return (
    <Card className={isInsufficient ? 'border-yellow-400' : ''}>
      <CardHeader className="pb-2">
        <CardTitle className="text-base flex items-center justify-between">
          <span className="flex items-center gap-2">
            <HelpCircle className="h-4 w-4" />
            Question Bank Preview
          </span>
          {isInsufficient && (
            <Badge variant="outline" className="text-yellow-600 border-yellow-400">
              <AlertTriangle className="h-3 w-3 mr-1" />
              Insufficient
            </Badge>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Summary Stats */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <div className="text-2xl font-bold text-blue-600">{filteredCount}</div>
            <div className="text-xs text-gray-500">
              Available Questions
              {difficultyLevel && ` (${difficultyLevel})`}
            </div>
          </div>
          <div>
            <div className="text-2xl font-bold">{numberOfQuestions}</div>
            <div className="text-xs text-gray-500">Requested per Exam</div>
          </div>
        </div>

        {isInsufficient && (
          <div className="bg-yellow-50 border border-yellow-200 rounded-md p-3 text-sm text-yellow-800">
            <AlertTriangle className="h-4 w-4 inline mr-2" />
            Not enough questions! You requested {numberOfQuestions} but only {filteredCount} are available
            {difficultyLevel && ` with ${difficultyLevel} difficulty`}.
            Consider reducing the number or adding more questions to the bank.
          </div>
        )}

        {/* Difficulty Breakdown */}
        {stats && (
          <div>
            <div className="text-xs font-medium text-gray-500 mb-2">By Difficulty</div>
            <div className="flex gap-2 flex-wrap">
              <Badge variant="outline" className={getDifficultyColor('EASY')}>
                Easy: {stats.byDifficulty.EASY}
              </Badge>
              <Badge variant="outline" className={getDifficultyColor('MEDIUM')}>
                Medium: {stats.byDifficulty.MEDIUM}
              </Badge>
              <Badge variant="outline" className={getDifficultyColor('HARD')}>
                Hard: {stats.byDifficulty.HARD}
              </Badge>
              {stats.byDifficulty.UNSET > 0 && (
                <Badge variant="outline" className={getDifficultyColor()}>
                  Unset: {stats.byDifficulty.UNSET}
                </Badge>
              )}
            </div>
          </div>
        )}

        {/* Type Breakdown */}
        {stats && (
          <div>
            <div className="text-xs font-medium text-gray-500 mb-2">By Type</div>
            <div className="flex gap-2 flex-wrap text-xs">
              {stats.byType.MULTIPLE_CHOICE > 0 && (
                <Badge variant="secondary">MCQ: {stats.byType.MULTIPLE_CHOICE}</Badge>
              )}
              {stats.byType.TRUE_FALSE > 0 && (
                <Badge variant="secondary">T/F: {stats.byType.TRUE_FALSE}</Badge>
              )}
              {stats.byType.SHORT_ANSWER > 0 && (
                <Badge variant="secondary">Short: {stats.byType.SHORT_ANSWER}</Badge>
              )}
              {stats.byType.ESSAY > 0 && (
                <Badge variant="secondary">Essay: {stats.byType.ESSAY}</Badge>
              )}
              {stats.byType.MATCHING > 0 && (
                <Badge variant="secondary">Match: {stats.byType.MATCHING}</Badge>
              )}
            </div>
          </div>
        )}

        {/* Sample Questions Toggle */}
        {questions.length > 0 && (
          <div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setShowSamples(!showSamples)}
              className="w-full justify-between"
            >
              <span>Sample Questions ({questions.length})</span>
              {showSamples ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
            </Button>
            
            {showSamples && (
              <div className="mt-2 space-y-2 max-h-48 overflow-y-auto">
                {questions.map((q, idx) => (
                  <div key={q.id} className="text-xs p-2 bg-gray-50 rounded">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-medium text-gray-400">{idx + 1}.</span>
                      <Badge variant="outline" className="text-[10px]">
                        {q.questionType.replace('_', ' ')}
                      </Badge>
                      {q.difficultyLevel && (
                        <span className={`px-1.5 py-0.5 rounded text-[10px] ${getDifficultyColor(q.difficultyLevel)}`}>
                          {q.difficultyLevel}
                        </span>
                      )}
                    </div>
                    <p className="text-gray-700 line-clamp-2">{q.questionText}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
