'use client'

import { useState, useEffect, useCallback } from 'react'
import { Button } from '@/components/ui/button'
import { ChevronLeft, ChevronRight, Camera } from 'lucide-react'

export interface ProctoringImageItem {
  url: string
  label?: string
  capturedAt?: string
}

interface ProctoringImagesViewerProps {
  images: ProctoringImageItem[]
}

function formatTimestamp(capturedAt?: string): string {
  if (!capturedAt) return ''
  try {
    const d = new Date(capturedAt)
    return d.toLocaleString(undefined, {
      dateStyle: 'medium',
      timeStyle: 'medium'
    })
  } catch {
    return capturedAt
  }
}

export function ProctoringImagesViewer({ images }: ProctoringImagesViewerProps) {
  const [currentIndex, setCurrentIndex] = useState(0)

  const goPrev = useCallback(() => {
    setCurrentIndex((i) => Math.max(0, i - 1))
  }, [])

  const goNext = useCallback(() => {
    setCurrentIndex((i) => Math.min(images.length - 1, i + 1))
  }, [images.length])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') goPrev()
      if (e.key === 'ArrowRight') goNext()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [goPrev, goNext])

  if (images.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-gray-500">
        <Camera className="h-12 w-12 mb-2 opacity-50" />
        <p>No images captured</p>
      </div>
    )
  }

  const current = images[currentIndex]
  const hasPrev = currentIndex > 0
  const hasNext = currentIndex < images.length - 1

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-center gap-4">
        <Button
          variant="outline"
          size="icon"
          onClick={goPrev}
          disabled={!hasPrev}
          aria-label="Previous image"
        >
          <ChevronLeft className="h-5 w-5" />
        </Button>
        <div className="flex-1 flex justify-center min-h-[300px] max-h-[70vh] bg-muted/30 rounded-lg overflow-hidden">
          <img
            src={current.url}
            alt={current.label || `Image ${currentIndex + 1}`}
            className="max-w-full max-h-[70vh] object-contain"
          />
        </div>
        <Button
          variant="outline"
          size="icon"
          onClick={goNext}
          disabled={!hasNext}
          aria-label="Next image"
        >
          <ChevronRight className="h-5 w-5" />
        </Button>
      </div>
      <div className="text-center space-y-1">
        <p className="text-sm font-medium">
          {current.label || (current.capturedAt ? 'Captured' : `Image ${currentIndex + 1}`)}
        </p>
        {current.capturedAt && (
          <p className="text-xs text-muted-foreground">
            {formatTimestamp(current.capturedAt)}
          </p>
        )}
        <p className="text-xs text-muted-foreground">
          Image {currentIndex + 1} of {images.length}
        </p>
      </div>
    </div>
  )
}
