'use client'

import React from 'react'

interface PreviewVideoModalProps {
  videoUrl: string
  courseTitle: string
  isOpen: boolean
  onClose: () => void
}

export function PreviewVideoModal({ videoUrl, courseTitle, isOpen, onClose }: PreviewVideoModalProps) {
  if (!isOpen) return null

  return (
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-75 backdrop-blur-sm"
      onClick={onClose}
    >
      <div 
        className="bg-black rounded-2xl shadow-2xl max-w-5xl w-full mx-4 relative"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute -top-10 right-0 text-white hover:text-gray-300 font-semibold text-xl leading-none z-10"
          aria-label="Close"
        >
          ✕
        </button>

        {/* Video Player */}
        <div className="relative w-full" style={{ paddingBottom: '56.25%' }}> {/* 16:9 aspect ratio */}
          <video
            controls
            autoPlay
            className="absolute top-0 left-0 w-full h-full rounded-t-2xl"
            src={videoUrl}
          >
            Your browser does not support the video tag.
          </video>
        </div>

        {/* Course Title */}
        <div className="px-6 py-4 bg-gray-900 rounded-b-2xl">
          <h3 className="text-white font-semibold text-lg">{courseTitle}</h3>
        </div>
      </div>
    </div>
  )
}
