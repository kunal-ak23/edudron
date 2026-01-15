'use client'

import { useEffect, useState, useCallback } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Clock, AlertTriangle } from 'lucide-react'

interface ExamTimerProps {
  timeRemainingSeconds: number
  onTimeUp: () => void
  onUpdate?: (remaining: number) => void
  autoSubmit?: boolean
}

export function ExamTimer({ timeRemainingSeconds, onTimeUp, onUpdate, autoSubmit = true }: ExamTimerProps) {
  const [remaining, setRemaining] = useState(timeRemainingSeconds)
  const [warningShown, setWarningShown] = useState(false)

  useEffect(() => {
    setRemaining(timeRemainingSeconds)
  }, [timeRemainingSeconds])

  useEffect(() => {
    if (remaining <= 0) {
      if (autoSubmit) {
        onTimeUp()
      }
      return
    }

    const interval = setInterval(() => {
      setRemaining(prev => {
        const newRemaining = Math.max(0, prev - 1)
        
        if (onUpdate) {
          onUpdate(newRemaining)
        }

        if (newRemaining === 0 && autoSubmit) {
          onTimeUp()
        }

        return newRemaining
      })
    }, 1000)

    return () => clearInterval(interval)
  }, [remaining, onTimeUp, onUpdate, autoSubmit])

  useEffect(() => {
    // Show warning when 5 minutes remaining
    if (remaining <= 300 && remaining > 0 && !warningShown) {
      setWarningShown(true)
    }
  }, [remaining, warningShown])

  const formatTime = (seconds: number) => {
    const hours = Math.floor(seconds / 3600)
    const minutes = Math.floor((seconds % 3600) / 60)
    const secs = seconds % 60

    if (hours > 0) {
      return `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
    }
    return `${minutes}:${String(secs).padStart(2, '0')}`
  }

  const getColorClass = () => {
    if (remaining <= 60) return 'text-red-600'
    if (remaining <= 300) return 'text-orange-600'
    return 'text-gray-900'
  }

  return (
    <div className="space-y-2">
      <div className={`flex items-center gap-2 text-lg font-semibold ${getColorClass()}`}>
        <Clock className="h-5 w-5" />
        <span>Time Remaining: {formatTime(remaining)}</span>
      </div>
      
      {remaining <= 300 && remaining > 0 && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertDescription>
            Less than 5 minutes remaining! Please save your work.
          </AlertDescription>
        </Alert>
      )}

      {remaining === 0 && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertDescription>
            Time is up! Your exam will be submitted automatically.
          </AlertDescription>
        </Alert>
      )}
    </div>
  )
}
