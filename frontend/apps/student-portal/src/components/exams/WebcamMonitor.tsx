'use client'

import { useState, useRef, useEffect } from 'react'
import { Camera, Video, VideoOff } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { proctoringApi } from '@/lib/proctoring-api'

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
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [photoCount, setPhotoCount] = useState(0)
  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const intervalRef = useRef<NodeJS.Timeout | null>(null)

  // Initialize webcam
  useEffect(() => {
    const initWebcam = async () => {
      try {
        const mediaStream = await navigator.mediaDevices.getUserMedia({
          video: { width: 640, height: 480 },
          audio: false
        })
        
        setStream(mediaStream)
        
        if (videoRef.current) {
          videoRef.current.srcObject = mediaStream
          // Explicitly play the video
          try {
            await videoRef.current.play()
          } catch (playErr) {
            console.error('Video play error:', playErr)
          }
        }
        
        setIsActive(true)
      } catch (err) {
        console.error('Webcam initialization error:', err)
        onError?.('Failed to access webcam. Proctoring may be affected.')
      }
    }

    initWebcam()

    return () => {
      // Cleanup
      if (stream) {
        stream.getTracks().forEach(track => track.stop())
      }
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
      }
    }
  }, [])

  // Start periodic photo capture
  useEffect(() => {
    if (!isActive || !stream) return

    const captureInterval = photoIntervalSeconds * 1000

    intervalRef.current = setInterval(() => {
      captureAndUploadPhoto()
    }, captureInterval)

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
      }
    }
  }, [isActive, stream, photoIntervalSeconds])

  // Capture and upload photo
  const captureAndUploadPhoto = async () => {
    if (!canvasRef.current || !videoRef.current) return

    try {
      const canvas = canvasRef.current
      const video = videoRef.current
      
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
        console.log(`[PREVIEW] Proctoring photo ${photoCount + 1} simulated at ${timestamp}`)
      } else {
        // Upload to backend
        const response = await proctoringApi.capturePhoto(examId, submissionId, base64Photo)
        
        setPhotoCount(prev => prev + 1)
        onPhotoCapture?.(response.capturedAt)
        
        console.log(`Proctoring photo ${photoCount + 1} captured at ${response.capturedAt}`)
      }
    } catch (err) {
      console.error('Photo capture error:', err)
      onError?.('Failed to capture proctoring photo')
    }
  }

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
              <span className="text-xs">Proctoring Active</span>
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
