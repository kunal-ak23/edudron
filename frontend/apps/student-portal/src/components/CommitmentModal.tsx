'use client'

import React, { useState } from 'react'
import { Button } from '@edudron/ui-components'

interface CommitmentModalProps {
  courseTitle: string
  isOpen: boolean
  onClose: () => void
  onCommit: () => void
}

export function CommitmentModal({ courseTitle, isOpen, onClose, onCommit }: CommitmentModalProps) {
  const [isCommitted, setIsCommitted] = useState(false)

  if (!isOpen) return null

  const solutions = [
    'Take a breath',
    'Break it down',
    'Ask for help',
    'Research',
    'Stay positive',
    'Talk through it'
  ]

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-30 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl max-w-3xl w-full mx-4 max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between px-8 py-6 border-b border-gray-200">
          <div className="text-primary-600 font-bold text-2xl tracking-tight">coursera</div>
          <button
            onClick={onClose}
            className="text-primary-600 hover:text-primary-700 font-semibold text-xl leading-none"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div className="px-8 py-8">
          <h1 className="text-4xl font-bold text-gray-900 mb-6">My commitment</h1>
          
          <div className="space-y-5 text-gray-900 mb-8 text-lg leading-relaxed">
            <p>
              I&apos;m beginning my learning in <strong className="font-semibold">{courseTitle}</strong>
            </p>
            <p>
              I know learning can be hard, but I have the patience, determination, and discipline to reach my goals.
            </p>
          </div>

          <p className="text-lg font-semibold text-gray-900 mb-5">
            When I&apos;m stuck, I&apos;ll find a solution, like...
          </p>

          {/* Solutions Grid */}
          <div className="grid grid-cols-2 gap-4 mb-8">
            {solutions.map((solution, index) => (
              <div key={index} className="flex items-start">
                <span className="text-primary-600 mr-3 text-xl font-bold leading-none">•</span>
                <span className="text-gray-900 text-base">{solution}</span>
              </div>
            ))}
          </div>

          {/* Commitment Checkbox */}
          <div className="flex items-start mb-8">
            <input
              type="checkbox"
              id="commitment"
              checked={isCommitted}
              onChange={(e) => setIsCommitted(e.target.checked)}
              className="w-6 h-6 text-primary-600 border-2 border-gray-300 rounded mt-0.5 focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 cursor-pointer"
            />
            <label htmlFor="commitment" className="ml-3 text-gray-900 font-medium text-base cursor-pointer leading-relaxed">
              I commit to completing this course
            </label>
          </div>

          {/* Action Button */}
          <Button
            onClick={() => {
              if (isCommitted) {
                onCommit()
              }
            }}
            disabled={!isCommitted}
            className="w-full py-4 text-lg font-bold rounded-xl disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Start the course
          </Button>
        </div>
      </div>
    </div>
  )
}

