'use client'

import { useEffect, useState, useCallback } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Clock, AlertTriangle, Info } from 'lucide-react'

type TimingMode = 'FIXED_WINDOW' | 'FLEXIBLE_START'

interface ExamTimerProps {
  timeRemainingSeconds: number
  onTimeUp: () => void
  onUpdate?: (remaining: number) => void
  autoSubmit?: boolean
  // Optional timing mode information
  timingMode?: TimingMode
  examEndTime?: string // ISO timestamp for when the exam window closes
  examStartedAt?: string // ISO timestamp when student started
}

export function ExamTimer({ 
  timeRemainingSeconds, 
  onTimeUp, 
  onUpdate, 
  autoSubmit = true,
  timingMode = 'FIXED_WINDOW',
  examEndTime,
  examStartedAt
}: ExamTimerProps) {
  const [remaining, setRemaining] = useState(timeRemainingSeconds)
  const [warningShown, setWarningShown] = useState(false)
  const [fiveMinWarningShown, setFiveMinWarningShown] = useState(false)
  const [oneMinWarningShown, setOneMinWarningShown] = useState(false)

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
    if (remaining <= 300 && remaining > 60 && !fiveMinWarningShown) {
      setFiveMinWarningShown(true)
    }
    // Show warning when 1 minute remaining
    if (remaining <= 60 && remaining > 0 && !oneMinWarningShown) {
      setOneMinWarningShown(true)
    }
  }, [remaining, fiveMinWarningShown, oneMinWarningShown])

  const formatTime = (seconds: number) => {
    const hours = Math.floor(seconds / 3600)
    const minutes = Math.floor((seconds % 3600) / 60)
    const secs = seconds % 60

    if (hours > 0) {
      return `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
    }
    return `${minutes}:${String(secs).padStart(2, '0')}`
  }

  const formatDateTime = (isoString?: string) => {
    if (!isoString) return ''
    try {
      return new Date(isoString).toLocaleTimeString([], { 
        hour: '2-digit', 
        minute: '2-digit',
        hour12: true
      })
    } catch {
      return ''
    }
  }

  const getColorClass = () => {
    if (remaining <= 60) return 'text-red-600'
    if (remaining <= 300) return 'text-orange-600'
    return 'text-gray-900'
  }

  const getBackgroundClass = () => {
    if (remaining <= 60) return 'bg-red-50 border-red-200'
    if (remaining <= 300) return 'bg-orange-50 border-orange-200'
    return 'bg-blue-50 border-blue-200'
  }

  const getTimingModeDescription = () => {
    if (timingMode === 'FLEXIBLE_START') {
      return 'You have a fixed duration from when you started.'
    } else if (examEndTime) {
      const endTimeStr = formatDateTime(examEndTime)
      return `Exam window closes at ${endTimeStr}. Your time is limited by this window.`
    }
    return null
  }

  const progressPercentage = Math.max(0, Math.min(100, (remaining / timeRemainingSeconds) * 100))

  return (
    <div className="space-y-2">
      {/* Main Timer Display */}
      <div className={`p-3 rounded-lg border ${getBackgroundClass()}`}>
        <div className={`flex items-center gap-3 ${getColorClass()}`}>
          <Clock className="h-6 w-6 flex-shrink-0" />
          <div className="flex-1">
            <div className="text-2xl font-bold tabular-nums">
              {formatTime(remaining)}
            </div>
            <div className="text-xs text-gray-600 mt-0.5">
              Time Remaining
            </div>
          </div>
        </div>
        
        {/* Progress Bar */}
        <div className="mt-2 h-1.5 bg-gray-200 rounded-full overflow-hidden">
          <div 
            className={`h-full transition-all duration-1000 ${
              remaining <= 60 ? 'bg-red-500' : 
              remaining <= 300 ? 'bg-orange-500' : 
              'bg-blue-500'
            }`}
            style={{ width: `${progressPercentage}%` }}
          />
        </div>
      </div>
      
      {/* Timing Mode Info */}
      {getTimingModeDescription() && (
        <div className="flex items-start gap-2 text-xs text-gray-500 px-1">
          <Info className="h-3 w-3 mt-0.5 flex-shrink-0" />
          <span>{getTimingModeDescription()}</span>
        </div>
      )}
      
      {/* Warning Alerts */}
      {remaining <= 300 && remaining > 60 && fiveMinWarningShown && (
        <Alert variant="destructive" className="bg-orange-50 border-orange-300 text-orange-800">
          <AlertTriangle className="h-4 w-4" />
          <AlertDescription>
            Less than 5 minutes remaining! Please review and save your answers.
          </AlertDescription>
        </Alert>
      )}

      {remaining <= 60 && remaining > 0 && oneMinWarningShown && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertDescription className="font-semibold">
            Less than 1 minute remaining! Complete your answers now.
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
