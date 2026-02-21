'use client'

import { useState, useRef, useEffect, useCallback } from 'react'
import { Camera, Video, VideoOff } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { proctoringApi } from '@/lib/proctoring-api'
import { logJourneyEvent } from '@/lib/journey-api'

interface WebcamMonitorProps {
  submissionId: string
  examId: string
  photoIntervalSeconds: number
  onPhotoCapture?: (photoUrl: string) => void
  onError?: (error: string) => void
  isPreview?: boolean
}

export function WebcamMonitor({
  submissionId,
  examId,
  photoIntervalSeconds,
  onPhotoCapture,
  onError,
  isPreview = false
}: WebcamMonitorProps) {
  const [isActive, setIsActive] = useState(false)
  const [photoCount, setPhotoCount] = useState(0)
  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const intervalRef = useRef<NodeJS.Timeout | null>(null)

  // Use a ref to hold the live stream so cleanup functions always access the
  // current stream, not a stale closure over the initial (null) state value.
  const streamRef = useRef<MediaStream | null>(null)

  // Capture and upload photo — defined with useCallback so it picks up fresh
  // prop values (examId, submissionId) on every render.
  const captureAndUploadPhoto = useCallback(async () => {
    if (!canvasRef.current || !videoRef.current) return

    try {
      const canvas = canvasRef.current
      const video = videoRef.current

      // Guard: if video hasn't loaded yet, skip this capture
      if (video.readyState < 2 || video.videoWidth === 0) return

      // Set canvas size
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight

      // Draw video frame
      const ctx = canvas.getContext('2d')
      if (!ctx) return

      ctx.drawImage(video, 0, 0, canvas.width, canvas.height)

      // Convert to base64
      const base64Photo = canvas.toDataURL('image/jpeg', 0.7)

      if (isPreview) {
        // In preview mode, just simulate capture without uploading
        setPhotoCount(prev => prev + 1)
        const timestamp = new Date().toISOString()
        onPhotoCapture?.(timestamp)
      } else {
        // Upload to backend
        const response = await proctoringApi.capturePhoto(examId, submissionId, base64Photo)

        setPhotoCount(prev => prev + 1)
        onPhotoCapture?.(response.capturedAt)
      }
    } catch (err) {
      onError?.('Failed to capture proctoring photo')
    }
  }, [examId, submissionId, isPreview, onPhotoCapture, onError])

  // Keep a ref to the latest captureAndUploadPhoto so the interval callback
  // always calls the current version without needing to be re-created.
  const captureRef = useRef(captureAndUploadPhoto)
  useEffect(() => {
    captureRef.current = captureAndUploadPhoto
  }, [captureAndUploadPhoto])

  // Initialize webcam
  useEffect(() => {
    let cancelled = false

    const initWebcam = async () => {
      try {
        const mediaStream = await navigator.mediaDevices.getUserMedia({
          video: { width: 640, height: 480 },
          audio: false
        })

        if (cancelled) {
          // Component unmounted before we got the stream — release immediately
          mediaStream.getTracks().forEach(track => track.stop())
          return
        }

        // Store in ref so the cleanup closure always sees the real stream
        streamRef.current = mediaStream

        if (videoRef.current) {
          videoRef.current.srcObject = mediaStream
          try {
            await videoRef.current.play()
          } catch (playErr) {
            // Autoplay may be blocked; the video will play once the user
            // interacts with the page.
          }
        }

        setIsActive(true)
      } catch (err) {
        if (cancelled) return
        const message = err instanceof Error ? err.message : String(err)
        if (!isPreview && submissionId) {
          logJourneyEvent(examId, submissionId, {
            eventType: 'PERMISSION_DENIED_VIDEO',
            severity: 'WARNING',
            metadata: { reason: 'webcam_access_failed', error: message }
          })
        }
        onError?.('Failed to access webcam. Proctoring may be affected.')
      }
    }

    initWebcam()

    return () => {
      cancelled = true
      // streamRef.current always has the latest stream — no stale closure issue
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop())
        streamRef.current = null
      }
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
        intervalRef.current = null
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []) // Only run once on mount; cleanup handles teardown

  // Start periodic photo capture once the webcam is active
  useEffect(() => {
    if (!isActive) return

    const captureInterval = photoIntervalSeconds * 1000

    // Clear any existing interval before starting a new one
    if (intervalRef.current) {
      clearInterval(intervalRef.current)
    }

    intervalRef.current = setInterval(() => {
      // Always call through captureRef so we get the latest examId/submissionId
      captureRef.current()
    }, captureInterval)

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
        intervalRef.current = null
      }
    }
  }, [isActive, photoIntervalSeconds])

  return (
    <>
      {/* Hidden video element for capturing */}
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        className="hidden"
      />
      <canvas ref={canvasRef} className="hidden" />

      {/* Proctoring indicator */}
      <div className="fixed top-4 right-4 z-50">
        <Badge
          variant={isActive ? "default" : "secondary"}
          className="flex items-center gap-2 px-3 py-1"
        >
          {isActive ? (
            <>
              <Video className="h-3 w-3 animate-pulse" />
              <span className="text-xs">Proctoring Active ({photoCount} captures)</span>
            </>
          ) : (
            <>
              <VideoOff className="h-3 w-3" />
              <span className="text-xs">Camera Off</span>
            </>
          )}
        </Badge>
      </div>
    </>
  )
}
