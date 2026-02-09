'use client'

import { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { X, Plus, Trash2 } from 'lucide-react'
import type { QuestionData, QuestionOption } from '@/lib/api'

interface QuestionEditorProps {
  question?: {
    id: string
    questionType: string
    questionText: string
    points: number
    options?: Array<{ id: string; optionText: string; isCorrect: boolean; sequence: number }>
    tentativeAnswer?: string
  }
  onSave: (data: QuestionData) => Promise<void>
  onCancel: () => void
}

export function QuestionEditor({ question, onSave, onCancel }: QuestionEditorProps) {
  const [questionType, setQuestionType] = useState<QuestionData['questionType']>(
    (question?.questionType as QuestionData['questionType']) || 'MULTIPLE_CHOICE'
  )
  const [questionText, setQuestionText] = useState(question?.questionText || '')
  const [points, setPoints] = useState(question?.points || 1)
  const [options, setOptions] = useState<QuestionOption[]>([])
  const [tentativeAnswer, setTentativeAnswer] = useState(question?.tentativeAnswer || '')
  const [saving, setSaving] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})

  useEffect(() => {
    if (question?.options) {
      setOptions(
        question.options.map(opt => ({
          text: opt.optionText,
          isCorrect: opt.isCorrect
        }))
      )
    } else if (questionType === 'MULTIPLE_CHOICE' && options.length === 0) {
      // Initialize with 2 empty options for new multiple choice questions
      setOptions([
        { text: '', isCorrect: false },
        { text: '', isCorrect: false }
      ])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [question, questionType])

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {}

    if (!questionText.trim()) {
      newErrors.questionText = 'Question text is required'
    }

    if (points < 1) {
      newErrors.points = 'Points must be at least 1'
    }

    if (questionType === 'MULTIPLE_CHOICE') {
      if (options.length < 2) {
        newErrors.options = 'At least 2 options are required'
      } else {
        const hasEmptyOption = options.some(opt => !opt.text.trim())
        if (hasEmptyOption) {
          newErrors.options = 'All options must have text'
        }
        const hasCorrect = options.some(opt => opt.isCorrect)
        if (!hasCorrect) {
          newErrors.options = 'At least one option must be marked as correct'
        }
      }
    }

    if (questionType === 'TRUE_FALSE' && !tentativeAnswer) {
      newErrors.tentativeAnswer = 'Please select the correct answer'
    }
    
    if ((questionType === 'SHORT_ANSWER' || questionType === 'ESSAY') && !tentativeAnswer.trim()) {
      newErrors.tentativeAnswer = 'Tentative answer is recommended for grading reference'
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSave = async () => {
    if (!validate()) {
      return
    }

    setSaving(true)
    try {
      const questionData: QuestionData = {
        questionType,
        questionText: questionText.trim(),
        points,
        ...(questionType === 'MULTIPLE_CHOICE' && { options }),
        ...((questionType === 'TRUE_FALSE' || questionType === 'SHORT_ANSWER' || questionType === 'ESSAY') && 
            tentativeAnswer && tentativeAnswer.trim() && { tentativeAnswer: tentativeAnswer.trim() })
      }
      await onSave(questionData)
    } catch (error) {
    } finally {
      setSaving(false)
    }
  }

  const addOption = () => {
    setOptions([...options, { text: '', isCorrect: false }])
  }

  const removeOption = (index: number) => {
    if (options.length > 2) {
      setOptions(options.filter((_, i) => i !== index))
    }
  }

  const updateOption = (index: number, field: 'text' | 'isCorrect', value: string | boolean) => {
    const newOptions = [...options]
    newOptions[index] = { ...newOptions[index], [field]: value }
    setOptions(newOptions)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{question ? 'Edit Question' : 'Add Question'}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <Label htmlFor="questionType">Question Type *</Label>
          <Select
            value={questionType}
            onValueChange={(value: QuestionData['questionType']) => {
              setQuestionType(value)
              setErrors({})
              if (value === 'MULTIPLE_CHOICE' && options.length === 0) {
                setOptions([
                  { text: '', isCorrect: false },
                  { text: '', isCorrect: false }
                ])
              } else if (value !== 'MULTIPLE_CHOICE') {
                setOptions([])
              }
            }}
            disabled={!!question}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="MULTIPLE_CHOICE">Multiple Choice</SelectItem>
              <SelectItem value="TRUE_FALSE">True/False</SelectItem>
              <SelectItem value="SHORT_ANSWER">Short Answer</SelectItem>
              <SelectItem value="ESSAY">Essay</SelectItem>
              <SelectItem value="MATCHING">Matching</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div>
          <Label htmlFor="questionText">Question Text *</Label>
          <Textarea
            id="questionText"
            value={questionText}
            onChange={(e) => setQuestionText(e.target.value)}
            placeholder="Enter the question"
            rows={3}
            className={errors.questionText ? 'border-red-500' : ''}
          />
          {errors.questionText && (
            <p className="text-sm text-red-500 mt-1">{errors.questionText}</p>
          )}
        </div>

        <div>
          <Label htmlFor="points">Points *</Label>
          <Input
            id="points"
            type="number"
            value={points}
            onChange={(e) => setPoints(parseInt(e.target.value) || 1)}
            min={1}
            className={errors.points ? 'border-red-500' : ''}
          />
          {errors.points && (
            <p className="text-sm text-red-500 mt-1">{errors.points}</p>
          )}
        </div>

        {questionType === 'MULTIPLE_CHOICE' && (
          <div>
            <Label>Options *</Label>
            <div className="space-y-2 mt-2">
              {options.map((option, index) => (
                <div key={index} className="flex items-center gap-2">
                  <Input
                    value={option.text}
                    onChange={(e) => updateOption(index, 'text', e.target.value)}
                    placeholder={`Option ${index + 1}`}
                    className="flex-1"
                  />
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={option.isCorrect}
                      onChange={(e) => updateOption(index, 'isCorrect', e.target.checked)}
                      className="w-4 h-4"
                    />
                    <span className="text-sm">Correct</span>
                  </label>
                  {options.length > 2 && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => removeOption(index)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
              ))}
              {errors.options && (
                <p className="text-sm text-red-500">{errors.options}</p>
              )}
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={addOption}
                disabled={options.length >= 10}
              >
                <Plus className="h-4 w-4 mr-2" />
                Add Option
              </Button>
            </div>
          </div>
        )}

        {questionType === 'TRUE_FALSE' && (
          <div>
            <Label htmlFor="trueFalseAnswer">Correct Answer *</Label>
            <Select
              value={tentativeAnswer || ''}
              onValueChange={(value) => setTentativeAnswer(value)}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select correct answer" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="true">True</SelectItem>
                <SelectItem value="false">False</SelectItem>
              </SelectContent>
            </Select>
            <p className="text-sm text-gray-500 mt-1">
              Select the correct answer for this True/False question.
            </p>
          </div>
        )}

        {(questionType === 'SHORT_ANSWER' || questionType === 'ESSAY') && (
          <div>
            <Label htmlFor="tentativeAnswer">
              Tentative Answer {questionType === 'ESSAY' ? '(Recommended)' : ''}
            </Label>
            <Textarea
              id="tentativeAnswer"
              value={tentativeAnswer}
              onChange={(e) => setTentativeAnswer(e.target.value)}
              placeholder="Enter the expected answer or grading reference"
              rows={questionType === 'ESSAY' ? 6 : 3}
              className={errors.tentativeAnswer ? 'border-red-500' : ''}
            />
            {errors.tentativeAnswer && (
              <p className="text-sm text-red-500 mt-1">{errors.tentativeAnswer}</p>
            )}
            <p className="text-sm text-gray-500 mt-1">
              This will be used as a reference for grading student responses.
            </p>
          </div>
        )}

        {questionType === 'MATCHING' && (
          <div className="p-4 bg-yellow-50 rounded border border-yellow-200">
            <p className="text-sm text-yellow-800">
              Matching questions are not yet fully supported in the manual editor.
              Please use the tentative answer field to describe the matching pairs.
            </p>
          </div>
        )}

        <div className="flex gap-2 justify-end pt-4">
          <Button variant="outline" onClick={onCancel} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving ? 'Saving...' : question ? 'Update Question' : 'Add Question'}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
