'use client'

import { useState, useRef, useEffect } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Camera, CheckCircle, AlertCircle, Loader2 } from 'lucide-react'
import { proctoringApi } from '@/lib/proctoring-api'

interface ProctoringSetupDialogProps {
  open: boolean
  examId: string
  submissionId: string
  examTitle: string
  proctoringMode: 'BASIC_MONITORING' | 'WEBCAM_RECORDING' | 'LIVE_PROCTORING'
  requireIdentityVerification: boolean
  onComplete: (identityPhotoUrl?: string) => void
  onCancel: () => void
  isPreview?: boolean
  requestFullscreen?: () => Promise<void>
}

export function ProctoringSetupDialog({
  open,
  examId,
  submissionId,
  examTitle,
  proctoringMode,
  requireIdentityVerification,
  onComplete,
  onCancel,
  isPreview = false,
  requestFullscreen
}: ProctoringSetupDialogProps) {
  const [step, setStep] = useState<'permission' | 'preview' | 'capture' | 'complete'>('permission')
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [captured, setCaptured] = useState(false)
  const [capturedPhoto, setCapturedPhoto] = useState<string | undefined>()
  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)

  const needsWebcam = proctoringMode === 'WEBCAM_RECORDING' || proctoringMode === 'LIVE_PROCTORING'

  // Request camera permission
  const requestCameraPermission = async () => {
    try {
      setLoading(true)
      setError(null)
      
      console.log('Requesting camera permission...')
      const mediaStream = await navigator.mediaDevices.getUserMedia({ 
        video: { 
          width: { ideal: 1280 },
          height: { ideal: 720 },
          facingMode: 'user'
        },
        audio: false 
      })
      
      console.log('Camera permission granted, stream obtained:', mediaStream)
      console.log('Video tracks:', mediaStream.getVideoTracks())
      console.log('Active tracks:', mediaStream.getVideoTracks()[0]?.enabled, mediaStream.getVideoTracks()[0]?.readyState)
      
      // Set stream first, then change step
      setStream(mediaStream)
      setStep('preview')
      setLoading(false)
    } catch (err) {
      console.error('Camera permission error:', err)
      setError('Camera access denied. Please allow camera access to proceed with this proctored exam.')
      setLoading(false)
    }
  }

  // Capture identity verification photo
  const capturePhoto = async () => {
    if (!canvasRef.current || !videoRef.current) return

    try {
      setLoading(true)
      const canvas = canvasRef.current
      const video = videoRef.current
      
      // Set canvas size to match video
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      
      // Draw video frame to canvas
      const ctx = canvas.getContext('2d')
      if (!ctx) throw new Error('Could not get canvas context')
      
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
      
      // Convert to base64
      const base64Photo = canvas.toDataURL('image/jpeg', 0.8)
      
      if (!isPreview) {
        // Upload to backend in real mode
        await proctoringApi.verifyIdentity(examId, submissionId, base64Photo)
      } else {
        // Simulate upload in preview mode
        console.log('[PREVIEW] Identity verification simulated')
        await new Promise(resolve => setTimeout(resolve, 500)) // Simulate network delay
      }
      
      setCaptured(true)
      setCapturedPhoto(base64Photo)
      setStep('complete')
      
      // Stop camera stream
      if (stream) {
        stream.getTracks().forEach(track => track.stop())
      }
      
    } catch (err) {
      console.error('Photo capture error:', err)
      setError('Failed to capture photo. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  // Skip identity verification (for basic monitoring mode)
  const skipIdentityVerification = async () => {
    // Request fullscreen before completing
    if (requestFullscreen && !isPreview) {
      try {
        await requestFullscreen()
      } catch (err) {
        console.error('Fullscreen request failed:', err)
      }
    }
    onComplete()
  }
  
  // Start exam (with fullscreen)
  const startExam = async () => {
    // Request fullscreen before completing (user gesture required)
    if (requestFullscreen && !isPreview) {
      try {
        await requestFullscreen()
      } catch (err) {
        console.error('Fullscreen request failed:', err)
        // Continue anyway - don't block exam start
      }
    }
    onComplete(capturedPhoto)
  }

  // Attach stream to video element when stream is ready
  useEffect(() => {
    if (stream && videoRef.current && step === 'preview') {
      console.log('Attaching stream to video element in useEffect')
      videoRef.current.srcObject = stream
      
      // Play the video
      const playVideo = async () => {
        try {
          if (videoRef.current) {
            console.log('Waiting for video metadata...')
            await new Promise<void>((resolve) => {
              if (videoRef.current) {
                videoRef.current.onloadedmetadata = () => {
                  console.log('Video metadata loaded')
                  resolve()
                }
              }
            })
            
            console.log('Attempting to play video...')
            await videoRef.current.play()
            console.log('Video playing successfully!')
          }
        } catch (err) {
          console.error('Error playing video:', err)
          setError('Failed to display camera feed. Please try again.')
        }
      }
      
      playVideo()
    }
  }, [stream, step])
  
  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (stream) {
        console.log('Cleaning up camera stream')
        stream.getTracks().forEach(track => track.stop())
      }
    }
  }, [stream])

  return (
    <Dialog open={open} onOpenChange={(open) => !open && onCancel()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Proctored Exam Setup</DialogTitle>
          <DialogDescription>
            {examTitle} - {proctoringMode.replace(/_/g, ' ')} Mode
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Step 1: Permission Request */}
          {step === 'permission' && (
            <div className="space-y-4">
              <Alert>
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>
                  This exam requires proctoring. You must complete the following before starting:
                  <ul className="list-disc list-inside mt-2 space-y-1">
                    {needsWebcam && <li>Allow camera access</li>}
                    {requireIdentityVerification && <li>Capture identity verification photo</li>}
                    <li>Acknowledge proctoring rules</li>
                  </ul>
                </AlertDescription>
              </Alert>

              <div className="bg-gray-50 p-4 rounded-lg space-y-2">
                <h4 className="font-semibold">Proctoring Rules:</h4>
                <ul className="text-sm space-y-1 text-gray-700">
                  <li>• You must remain visible on camera throughout the exam</li>
                  <li>• Switching tabs or windows may be flagged as suspicious</li>
                  <li>• Copy/paste actions are monitored</li>
                  <li>• Only one person should be visible on camera</li>
                  <li>• You must complete the exam in one sitting</li>
                </ul>
              </div>

              {error && (
                <Alert variant="destructive">
                  <AlertCircle className="h-4 w-4" />
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              )}

              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={onCancel}>
                  Cancel
                </Button>
                {needsWebcam ? (
                  <Button onClick={requestCameraPermission} disabled={loading}>
                    {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    <Camera className="mr-2 h-4 w-4" />
                    Allow Camera Access
                  </Button>
                ) : (
                  <Button onClick={skipIdentityVerification}>
                    Continue
                  </Button>
                )}
              </div>
            </div>
          )}

          {/* Step 2: Camera Preview */}
          {step === 'preview' && (
            <div className="space-y-4">
              <Alert>
                <Camera className="h-4 w-4" />
                <AlertDescription>
                  Position yourself in the center of the frame. Make sure your face is clearly visible.
                </AlertDescription>
              </Alert>

              <div className="relative bg-black rounded-lg overflow-hidden aspect-video">
                <video
                  ref={videoRef}
                  autoPlay
                  playsInline
                  muted
                  className="w-full h-full object-cover"
                  onLoadedMetadata={(e) => {
                    // Ensure video is playing
                    const video = e.target as HTMLVideoElement
                    if (video.paused) {
                      video.play().catch(err => console.error('Failed to play video:', err))
                    }
                  }}
                />
                <canvas ref={canvasRef} className="hidden" />
              </div>

              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={onCancel}>
                  Cancel
                </Button>
                <Button onClick={capturePhoto} disabled={loading}>
                  {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Capture Photo
                </Button>
              </div>
            </div>
          )}

          {/* Step 3: Complete */}
          {step === 'complete' && (
            <div className="space-y-6 text-center py-8">
              <CheckCircle className="h-16 w-16 text-green-500 mx-auto" />
              <div>
                <h3 className="text-lg font-semibold">Setup Complete</h3>
                <p className="text-gray-600 mt-2">
                  You&apos;re ready to start your proctored exam
                </p>
                {!isPreview && (
                  <p className="text-sm text-gray-500 mt-2">
                    The exam will open in fullscreen mode
                  </p>
                )}
              </div>
              <div className="flex justify-center gap-3">
                <Button variant="outline" onClick={onCancel}>
                  Cancel
                </Button>
                <Button onClick={startExam} size="lg" className="px-8">
                  Start Exam
                </Button>
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
