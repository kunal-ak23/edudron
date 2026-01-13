'use client'

import React from 'react'
import { Button } from '@kunal-ak23/edudron-ui-components'

interface EnrollmentSuccessModalProps {
  userName: string
  isOpen: boolean
  onClose: () => void
  onGetStarted: () => void
}

export function EnrollmentSuccessModal({ userName, isOpen, onClose, onGetStarted }: EnrollmentSuccessModalProps) {
  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-white">
      <div className="max-w-3xl w-full mx-4 text-center px-4">
        {/* Illustration */}
        <div className="mb-10 flex justify-center">
          <div className="relative">
            {/* Screen/Document */}
            <div className="w-80 h-56 border-4 border-gray-300 rounded-lg bg-white relative shadow-lg">
              {/* Video placeholder */}
              <div className="absolute top-6 left-6 w-40 h-24 bg-teal-500 rounded flex items-center justify-center shadow-md">
                <div className="w-0 h-0 border-l-[24px] border-l-white border-y-[14px] border-y-transparent ml-3"></div>
              </div>
              {/* Text lines */}
              <div className="absolute bottom-6 left-6 right-6 space-y-2.5">
                <div className="h-2.5 bg-gray-200 rounded w-full"></div>
                <div className="h-2.5 bg-gray-200 rounded w-3/4"></div>
                <div className="h-2.5 bg-gray-200 rounded w-5/6"></div>
              </div>
              {/* Pencil */}
              <div className="absolute bottom-2 right-6 transform rotate-12">
                <div className="w-20 h-2.5 bg-yellow-400 rounded-full shadow-md"></div>
                <div className="w-2.5 h-8 bg-yellow-400 rounded-full ml-2.5"></div>
              </div>
            </div>
            
            {/* Confetti */}
            <div className="absolute -top-6 -left-6 w-5 h-5 bg-green-500 rounded-full shadow-md"></div>
            <div className="absolute top-12 -right-6 w-8 h-4 bg-yellow-400 rounded shadow-md"></div>
            <div className="absolute -bottom-6 left-12 w-4 h-8 bg-red-500 rounded-full shadow-md"></div>
            <div className="absolute bottom-12 -right-12 w-5 h-5 bg-teal-400 transform rotate-45 shadow-md"></div>
            <div className="absolute top-20 left-0 w-6 h-3 bg-purple-400 rounded shadow-md"></div>
            <div className="absolute bottom-20 -left-8 w-4 h-4 bg-pink-400 rounded-full shadow-md"></div>
          </div>
        </div>

        {/* Message */}
        <h1 className="text-5xl font-bold text-gray-900 mb-5 leading-tight">
          You&apos;re all set, {userName}
        </h1>
        <p className="text-xl text-gray-600 mb-10 max-w-2xl mx-auto leading-relaxed">
          Congratulations—you&apos;ve taken the first step toward your goals. Your subscription is now active, and you&apos;re ready to start learning.
        </p>

        {/* Action Button */}
        <Button
          onClick={onGetStarted}
          className="px-10 py-4 text-lg font-bold rounded-xl"
        >
          Get Started
        </Button>
      </div>
    </div>
  )
}

