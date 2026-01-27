// ADD THIS TO: frontend/apps/student-portal/src/app/exams/[id]/take/page.tsx
// This file shows the complete integration of proctoring into the exam taking page

// 1. ADD THESE IMPORTS at the top of the file:
import { ProctoringSetupDialog } from '@/components/exams/ProctoringSetupDialog'
import { WebcamMonitor } from '@/components/exams/WebcamMonitor'
import { proctoringApi } from '@/lib/proctoring-api'

// 2. UPDATE THE Exam INTERFACE to include proctoring fields:
interface Exam {
  id: string
  title: string
  instructions?: string
  timeLimitSeconds?: number
  courseId?: string
  questions: Question[]
  // Add proctoring fields
  enableProctoring?: boolean
  proctoringMode?: 'DISABLED' | 'BASIC_MONITORING' | 'WEBCAM_RECORDING' | 'LIVE_PROCTORING'
  photoIntervalSeconds?: number
  requireIdentityVerification?: boolean
  blockCopyPaste?: boolean
  blockTabSwitch?: boolean
  maxTabSwitchesAllowed?: number
}

// 3. ADD THESE STATE VARIABLES after the existing state declarations:
const [showProctoringSetup, setShowProctoringSetup] = useState(false)
const [proctoringComplete, setProctoringComplete] = useState(false)
const [tabSwitchCount, setTabSwitchCount] = useState(0)

// 4. ADD THIS HELPER FUNCTION to log proctoring events:
const logProctoringEvent = useCallback(async (
  eventType: string,
  severity: 'INFO' | 'WARNING' | 'VIOLATION',
  metadata: Record<string, any> = {}
) => {
  if (!exam?.enableProctoring || !submissionId) return
  
  try {
    await proctoringApi.logEvent(examId, submissionId, {
      eventType,
      severity,
      metadata: {
        ...metadata,
        timestamp: new Date().toISOString(),
        userAgent: navigator.userAgent
      }
    })
  } catch (err) {
    console.error('Failed to log proctoring event:', err)
  }
}, [exam, examId, submissionId])

// 5. UPDATE THE loadExam FUNCTION to check for proctoring:
// After you set the exam state, add this:
/*
setExam(examWithQuestions)

// Check if proctoring is enabled and we haven't completed setup
if (examWithQuestions.enableProctoring && !proctoringComplete) {
  setShowProctoringSetup(true)
}
*/

// 6. REPLACE THE EXISTING handleVisibilityChange with this enhanced version:
const handleVisibilityChange = useCallback(() => {
  if (document.hidden && hasUnsavedChangesRef.current && submissionId) {
    // Save when user switches tabs or minimizes
    saveProgress()
    
    // Log proctoring event for tab switch
    if (exam?.enableProctoring) {
      setTabSwitchCount(prev => {
        const newCount = prev + 1
        
        logProctoringEvent('TAB_SWITCH', 'WARNING', {
          count: newCount,
          maxAllowed: exam.maxTabSwitchesAllowed || 3
        })
        
        // Check if max switches exceeded
        if (exam.blockTabSwitch || (exam.maxTabSwitchesAllowed && newCount > exam.maxTabSwitchesAllowed)) {
          logProctoringEvent('PROCTORING_VIOLATION', 'VIOLATION', {
            reason: 'Maximum tab switches exceeded',
            count: newCount,
            maxAllowed: exam.maxTabSwitchesAllowed
          })
          
          // Auto-submit if configured
          if (exam.blockTabSwitch) {
            alert('Tab switching is not allowed. Your exam will be auto-submitted.')
            setTimeout(() => submitExam(), 1000)
          }
        }
        
        return newCount
      })
    }
  }
}, [exam, submissionId, logProctoringEvent])

// 7. ADD COPY/PASTE DETECTION useEffect:
useEffect(() => {
  if (!exam?.enableProctoring || !exam?.blockCopyPaste) return
  
  const handleCopy = (e: ClipboardEvent) => {
    e.preventDefault()
    logProctoringEvent('COPY_ATTEMPT', 'WARNING')
    // Show a toast or alert
    console.warn('Copy action blocked by proctoring')
  }
  
  const handlePaste = (e: ClipboardEvent) => {
    e.preventDefault()
    logProctoringEvent('PASTE_ATTEMPT', 'WARNING')
    // Show a toast or alert
    console.warn('Paste action blocked by proctoring')
  }
  
  document.addEventListener('copy', handleCopy)
  document.addEventListener('paste', handlePaste)
  
  return () => {
    document.removeEventListener('copy', handleCopy)
    document.removeEventListener('paste', handlePaste)
  }
}, [exam, logProctoringEvent])

// 8. ADD WINDOW BLUR/FOCUS DETECTION useEffect:
useEffect(() => {
  if (!exam?.enableProctoring) return
  
  const handleBlur = () => {
    logProctoringEvent('WINDOW_BLUR', 'WARNING')
  }
  
  const handleFocus = () => {
    logProctoringEvent('WINDOW_FOCUS', 'INFO')
  }
  
  window.addEventListener('blur', handleBlur)
  window.addEventListener('focus', handleFocus)
  
  return () => {
    window.removeEventListener('blur', handleBlur)
    window.removeEventListener('focus', handleFocus)
  }
}, [exam, logProctoringEvent])

// 9. ADD THIS JSX AT THE TOP OF THE RETURN STATEMENT (before the main content):
/*
{exam?.enableProctoring && (
  <>
    {/* Proctoring Setup Dialog */}
    <ProctoringSetupDialog
      open={showProctoringSetup}
      examId={examId}
      submissionId={submissionId || ''}
      examTitle={exam.title}
      proctoringMode={exam.proctoringMode || 'BASIC_MONITORING'}
      requireIdentityVerification={exam.requireIdentityVerification || false}
      onComplete={() => {
        setShowProctoringSetup(false)
        setProctoringComplete(true)
      }}
      onCancel={() => {
        router.push(`/exams/${examId}`)
      }}
    />
    
    {/* Webcam Monitor (during exam) */}
    {proctoringComplete && 
     exam.proctoringMode === 'WEBCAM_RECORDING' && 
     submissionId && (
      <WebcamMonitor
        examId={examId}
        submissionId={submissionId}
        photoIntervalSeconds={exam.photoIntervalSeconds || 120}
        onPhotoCapture={(timestamp) => {
          console.log('Photo captured at:', timestamp)
        }}
        onError={(error) => {
          console.error('Webcam error:', error)
        }}
      />
    )}
  </>
)}
*/

// 10. ADD PROCTORING INDICATOR to the header (optional):
// Add this near the timer in the exam header:
/*
{exam?.enableProctoring && (
  <div className="flex items-center gap-2 text-sm">
    <div className="h-2 w-2 bg-red-500 rounded-full animate-pulse" />
    <span className="text-gray-600">Proctored</span>
    {tabSwitchCount > 0 && (
      <span className="text-orange-600 text-xs">
        ({tabSwitchCount} tab switches)
      </span>
    )}
  </div>
)}
*/

// COMPLETE EXAMPLE OF THE RETURN STATEMENT WITH PROCTORING:
/*
return (
  <ProtectedRoute>
    <StudentLayout>
      {exam?.enableProctoring && (
        <>
          <ProctoringSetupDialog
            open={showProctoringSetup}
            examId={examId}
            submissionId={submissionId || ''}
            examTitle={exam.title}
            proctoringMode={exam.proctoringMode || 'BASIC_MONITORING'}
            requireIdentityVerification={exam.requireIdentityVerification || false}
            onComplete={() => {
              setShowProctoringSetup(false)
              setProctoringComplete(true)
            }}
            onCancel={() => router.push(`/exams/${examId}`)}
          />
          
          {proctoringComplete && 
           exam.proctoringMode === 'WEBCAM_RECORDING' && 
           submissionId && (
            <WebcamMonitor
              examId={examId}
              submissionId={submissionId}
              photoIntervalSeconds={exam.photoIntervalSeconds || 120}
              onPhotoCapture={(timestamp) => console.log('Photo captured at:', timestamp)}
              onError={(error) => console.error('Webcam error:', error)}
            />
          )}
        </>
      )}
      
      {loading && (
        <div className="flex items-center justify-center min-h-screen">
          <Loader2 className="h-8 w-8 animate-spin" />
        </div>
      )}
      
      {!loading && exam && (
        <div className="max-w-7xl mx-auto p-6">
          {/* Existing exam content */}
        </div>
      )}
    </StudentLayout>
  </ProtectedRoute>
)
*/
