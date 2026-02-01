'use client'

import { useState, useRef, useEffect } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@kunal-ak23/edudron-ui-components'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Camera, CheckCircle, AlertCircle, Loader2, Clock, Monitor, Copy, Eye, Maximize, ChevronRight } from 'lucide-react'
import { proctoringApi } from '@/lib/proctoring-api'

interface ExamSettings {
  blockCopyPaste?: boolean
  blockTabSwitch?: boolean
  maxTabSwitchesAllowed?: number
  timeLimitSeconds?: number
  instructions?: string
}

interface ProctoringSetupDialogProps {
  open: boolean
  examId: string
  submissionId: string
  examTitle: string
  proctoringMode: 'BASIC_MONITORING' | 'WEBCAM_RECORDING' | 'LIVE_PROCTORING'
  requireIdentityVerification: boolean
  examSettings?: ExamSettings
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
  examSettings,
  onComplete,
  onCancel,
  isPreview = false,
  requestFullscreen
}: ProctoringSetupDialogProps) {
  const [step, setStep] = useState<'instructions' | 'permission' | 'preview' | 'capture' | 'complete'>('instructions')
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
    console.log('⏭️ skipIdentityVerification called')
    // Request fullscreen before completing (allow in preview too)
    if (requestFullscreen) {
      console.log('Requesting fullscreen before completing...')
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
    console.log('🚀 startExam() called in ProctoringSetupDialog')
    console.log('isPreview:', isPreview)
    console.log('requestFullscreen function exists:', !!requestFullscreen)
    console.log('capturedPhoto exists:', !!capturedPhoto)
    
    // Request fullscreen before completing (user gesture required)
    // Allow fullscreen in both preview and real mode so preview can test it
    if (requestFullscreen) {
      console.log('✅ Calling requestFullscreen()...')
      try {
        await requestFullscreen()
        console.log('✅ requestFullscreen() completed successfully')
      } catch (err) {
        console.error('❌ Fullscreen request failed in startExam:', err)
        // Continue anyway - don't block exam start
      }
    } else {
      console.log('⚠️ Skipping fullscreen request - function not provided')
    }
    
    console.log('Calling onComplete with capturedPhoto')
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
            {examTitle}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Step 0: Instructions */}
          {step === 'instructions' && (
            <div className="space-y-5">
              <div className="bg-primary-50 border border-primary-200 rounded-lg p-4">
                <h4 className="font-semibold text-primary-900 mb-2 flex items-center gap-2">
                  <Eye className="h-5 w-5" />
                  Important Exam Information
                </h4>
                <p className="text-primary-800 text-sm">
                  Please read the following instructions carefully before starting your exam.
                </p>
              </div>

              {/* Exam Details */}
              <div className="space-y-3">
                {/* Time Limit */}
                {examSettings?.timeLimitSeconds && examSettings.timeLimitSeconds > 0 && (
                  <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg">
                    <div className="bg-primary-100 p-2 rounded-lg">
                      <Clock className="h-5 w-5 text-primary-600" />
                    </div>
                    <div>
                      <h5 className="font-medium text-gray-900">Time Limit</h5>
                      <p className="text-sm text-gray-600">
                        You have <span className="font-semibold">{Math.floor(examSettings.timeLimitSeconds / 60)} minutes</span> to complete this exam. 
                        The timer will start as soon as you begin.
                      </p>
                    </div>
                  </div>
                )}

                {/* Fullscreen Mode */}
                <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg">
                  <div className="bg-primary-100 p-2 rounded-lg">
                    <Maximize className="h-5 w-5 text-primary-600" />
                  </div>
                  <div>
                    <h5 className="font-medium text-gray-900">Fullscreen Mode</h5>
                    <p className="text-sm text-gray-600">
                      The exam will open in fullscreen mode. Exiting fullscreen will be logged.
                    </p>
                  </div>
                </div>

                {/* Webcam Monitoring */}
                {needsWebcam && (
                  <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg">
                    <div className="bg-green-100 p-2 rounded-lg">
                      <Camera className="h-5 w-5 text-green-600" />
                    </div>
                    <div>
                      <h5 className="font-medium text-gray-900">Webcam Monitoring</h5>
                      <p className="text-sm text-gray-600">
                        Your webcam will be active during the exam. Please ensure you are clearly visible and alone in the frame.
                        {requireIdentityVerification && ' You will need to take an identity verification photo before starting.'}
                      </p>
                    </div>
                  </div>
                )}

                {/* Tab Switching */}
                <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg">
                  <div className={`${examSettings?.blockTabSwitch ? 'bg-red-100' : 'bg-orange-100'} p-2 rounded-lg`}>
                    <Monitor className={`h-5 w-5 ${examSettings?.blockTabSwitch ? 'text-red-600' : 'text-orange-600'}`} />
                  </div>
                  <div>
                    <h5 className="font-medium text-gray-900">Tab/Window Switching</h5>
                    <p className="text-sm text-gray-600">
                      {examSettings?.blockTabSwitch ? (
                        <span className="text-red-700 font-medium">
                          Switching tabs or windows is NOT allowed. Your exam will be automatically submitted if you leave this page.
                        </span>
                      ) : examSettings?.maxTabSwitchesAllowed ? (
                        <span className="text-amber-700">
                          <span className="font-medium">Warning:</span> Switching tabs more than <span className="font-semibold">{examSettings.maxTabSwitchesAllowed} time{examSettings.maxTabSwitchesAllowed !== 1 ? 's' : ''}</span> will automatically submit your exam.
                        </span>
                      ) : (
                        'Tab and window switches will be monitored and logged. Stay on this page to avoid issues.'
                      )}
                    </p>
                  </div>
                </div>

                {/* Copy/Paste */}
                {examSettings?.blockCopyPaste && (
                  <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg">
                    <div className="bg-red-100 p-2 rounded-lg">
                      <Copy className="h-5 w-5 text-red-600" />
                    </div>
                    <div>
                      <h5 className="font-medium text-gray-900">Copy/Paste Disabled</h5>
                      <p className="text-sm text-gray-600">
                        Copy and paste actions are blocked during this exam. Type your answers directly.
                      </p>
                    </div>
                  </div>
                )}
              </div>

              {/* Custom Instructions */}
              {examSettings?.instructions && (
                <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
                  <h4 className="font-semibold text-amber-900 mb-2">Additional Instructions</h4>
                  <p className="text-sm text-amber-800 whitespace-pre-wrap">{examSettings.instructions}</p>
                </div>
              )}

              <div className="flex justify-end gap-2 pt-2">
                <Button variant="outline" onClick={onCancel}>
                  Cancel
                </Button>
                <Button onClick={() => setStep('permission')}>
                  I Understand, Continue
                  <ChevronRight className="ml-2 h-4 w-4" />
                </Button>
              </div>
            </div>
          )}

          {/* Step 1: Permission Request */}
          {step === 'permission' && (
            <div className="space-y-4">
              <Alert>
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>
                  {needsWebcam ? (
                    <>
                      Before you can start, we need to set up proctoring:
                      <ul className="list-disc list-inside mt-2 space-y-1">
                        <li>Allow camera access</li>
                        {requireIdentityVerification && <li>Capture identity verification photo</li>}
                      </ul>
                    </>
                  ) : (
                    'Click Continue to start your proctored exam.'
                  )}
                </AlertDescription>
              </Alert>

              <div className="bg-gray-50 p-4 rounded-lg space-y-2">
                <h4 className="font-semibold">Quick Reminders:</h4>
                <ul className="text-sm space-y-1 text-gray-700">
                  {needsWebcam && <li>• Stay visible on camera throughout the exam</li>}
                  <li>• Avoid switching tabs or windows</li>
                  {examSettings?.blockCopyPaste && <li>• Copy/paste is disabled</li>}
                  <li>• Complete the exam in one sitting</li>
                </ul>
              </div>

              {error && (
                <Alert variant="destructive">
                  <AlertCircle className="h-4 w-4" />
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              )}

              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={() => setStep('instructions')}>
                  Back
                </Button>
                {needsWebcam ? (
                  <Button onClick={requestCameraPermission} disabled={loading}>
                    {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    <Camera className="mr-2 h-4 w-4" />
                    Allow Camera Access
                  </Button>
                ) : (
                  <Button onClick={skipIdentityVerification}>
                    Start Exam
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
            <div className="space-y-6 py-4">
              <div className="flex items-center justify-center gap-3">
                <CheckCircle className="h-8 w-8 text-green-500" />
                <h3 className="text-lg font-semibold">Setup Complete</h3>
              </div>
              
              {/* Show captured identity photo */}
              {capturedPhoto && (
                <div className="space-y-3">
                  <p className="text-sm text-gray-600 text-center">Your identity verification photo:</p>
                  <div className="flex justify-center">
                    <div className="relative rounded-lg overflow-hidden border-2 border-green-200 shadow-md">
                      <img 
                        src={capturedPhoto} 
                        alt="Identity verification photo" 
                        className="w-64 h-48 object-cover"
                      />
                      <div className="absolute top-2 right-2 bg-green-500 text-white text-xs px-2 py-1 rounded-full flex items-center gap-1">
                        <CheckCircle className="h-3 w-3" />
                        Verified
                      </div>
                    </div>
                  </div>
                  <div className="flex justify-center">
                    <Button 
                      variant="ghost" 
                      size="sm" 
                      onClick={async () => {
                        // Reset and go back to get new photo
                        setCaptured(false)
                        setCapturedPhoto(undefined)
                        setError(null)
                        // Re-request camera
                        await requestCameraPermission()
                      }}
                      className="text-gray-500 hover:text-gray-700"
                    >
                      <Camera className="h-4 w-4 mr-1" />
                      Retake Photo
                    </Button>
                  </div>
                </div>
              )}
              
              <div className="text-center">
                <p className="text-gray-600">
                  You&apos;re ready to start your proctored exam
                </p>
                {!isPreview && (
                  <p className="text-sm text-gray-500 mt-2">
                    The exam will open in fullscreen mode
                  </p>
                )}
              </div>
              
              <div className="flex justify-center gap-3 pt-2">
                <Button variant="outline" onClick={onCancel}>
                  Cancel
                </Button>
                <Button 
                  onClick={() => {
                    console.log('🔘 Start Exam button clicked!')
                    startExam()
                  }} 
                  size="lg" 
                  className="px-8"
                >
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
