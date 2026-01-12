'use client'

import React, { useState } from 'react'
import { Button } from '@edudron/ui-components'
import type { IssueType } from '@edudron/shared-utils'

interface IssueReportDialogProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (issueType: IssueType, description: string) => Promise<void>
}

const ISSUE_TYPES: { value: IssueType; label: string }[] = [
  { value: 'CONTENT_ERROR', label: 'Content Error' },
  { value: 'TECHNICAL_ISSUE', label: 'Technical Issue' },
  { value: 'VIDEO_PLAYBACK', label: 'Video Playback Problem' },
  { value: 'AUDIO_QUALITY', label: 'Audio Quality Issue' },
  { value: 'TRANSCRIPT_ERROR', label: 'Transcript Error' },
  { value: 'OTHER', label: 'Other' },
]

export function IssueReportDialog({ isOpen, onClose, onSubmit }: IssueReportDialogProps) {
  const [issueType, setIssueType] = useState<IssueType>('OTHER')
  const [description, setDescription] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (!isOpen) return null

  const handleSubmit = async () => {
    if (!description.trim()) return
    
    setIsSubmitting(true)
    try {
      await onSubmit(issueType, description.trim())
      setDescription('')
      setIssueType('OTHER')
      onClose()
    } catch (error) {
      console.error('Failed to report issue:', error)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-30 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full mx-4">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-900">Report an Issue</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 font-semibold text-xl leading-none"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div className="px-6 py-6">
          <p className="text-sm text-gray-600 mb-4">
            Please describe the issue you encountered with this lesson.
          </p>

          {/* Issue Type Selection */}
          <div className="mb-6">
            <label htmlFor="issueType" className="block text-sm font-medium text-gray-700 mb-2">
              Issue Type
            </label>
            <select
              id="issueType"
              value={issueType}
              onChange={(e) => setIssueType(e.target.value as IssueType)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            >
              {ISSUE_TYPES.map((type) => (
                <option key={type.value} value={type.value}>
                  {type.label}
                </option>
              ))}
            </select>
          </div>

          {/* Description Textarea */}
          <div className="mb-6">
            <label htmlFor="description" className="block text-sm font-medium text-gray-700 mb-2">
              Description <span className="text-red-500">*</span>
            </label>
            <textarea
              id="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Please provide details about the issue..."
              rows={5}
              required
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
            />
          </div>

          {/* Action Buttons */}
          <div className="flex items-center space-x-3">
            <Button
              onClick={onClose}
              variant="outline"
              className="flex-1"
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              onClick={handleSubmit}
              disabled={!description.trim() || isSubmitting}
              className="flex-1"
            >
              {isSubmitting ? 'Submitting...' : 'Submit Report'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

