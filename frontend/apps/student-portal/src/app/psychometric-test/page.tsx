'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute, Button } from '@kunal-ak23/edudron-ui-components'
import { StudentLayout } from '@/components/StudentLayout'
import { PsychometricTestChat, ChatMessage } from '@/components/PsychometricTestChat'
import { usePsychometricTestFeature } from '@/hooks/usePsychometricTestFeature'
import { getApiClient } from '@/lib/api'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

interface TestSession {
  id: string
  clientId?: string
  studentId?: string
  status: string
  currentPhase: string
  conversationHistory: any[]
  identifiedFields?: any
  paymentId?: string | null
  paymentRequired?: boolean
  paymentStatus?: string
  startedAt?: string
  completedAt?: string | null
  createdAt?: string
  updatedAt?: string
}

export default function PsychometricTestPage() {
  const router = useRouter()
  const { enabled, loading: featureLoading } = usePsychometricTestFeature()
  const [session, setSession] = useState<TestSession | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isCompleting, setIsCompleting] = useState(false)

  const apiClient = getApiClient()

  // Load existing session or start new one
  useEffect(() => {
    if (!featureLoading && enabled) {
      loadOrStartSession()
    }
  }, [featureLoading, enabled])

  // Convert conversation history to messages
  useEffect(() => {
    if (session?.conversationHistory) {
      const chatMessages: ChatMessage[] = session.conversationHistory.map((msg: any) => ({
        role: msg.role === 'user' ? 'user' : 'assistant',
        content: msg.content || '',
        timestamp: msg.timestamp
      }))
      setMessages(chatMessages)
    }
  }, [session])

  const loadOrStartSession = async () => {
    try {
      setIsLoading(true)
      setError(null)

      // Try to get existing in-progress session
      // For now, we'll always start a new session. In future, we can check for existing sessions.
      const response = await apiClient.post('/api/psychometric-test/start', {})
      
      console.log('Start test response:', response)
      console.log('Response type:', typeof response)
      console.log('Response keys:', Object.keys(response || {}))
      
      // Handle different response structures - ApiClient might return data directly or wrapped
      let sessionData: any = null
      let status: number | undefined = undefined
      
      // Check if response has a 'data' property (wrapped response)
      if (response && typeof response === 'object' && 'data' in response) {
        sessionData = (response as any).data
        status = (response as any).status
      } 
      // Check if response has status property (axios-like response)
      else if (response && typeof response === 'object' && 'status' in response) {
        sessionData = (response as any).data || response
        status = (response as any).status
      }
      // Otherwise, assume response is the data directly
      else {
        sessionData = response
        status = 200 // Assume success if no status provided
      }
      
      console.log('Extracted session data:', sessionData)
      console.log('Extracted status:', status)
      
      // Check if we have valid session data (has id property)
      if (sessionData && sessionData.id) {
        console.log('Setting session with data:', sessionData)
        setSession(sessionData)
      } else if (status === 403) {
        setError('Psychometric test is not available for your account.')
      } else {
        console.warn('Invalid response structure. Status:', status, 'Data:', sessionData)
        setError('Failed to start test. Please try again.')
      }
    } catch (err: any) {
      console.error('Failed to start test:', err)
      console.error('Error response:', err.response)
      console.error('Error data:', err.response?.data)
      if (err.response?.status === 403) {
        setError('Psychometric test is not available for your account.')
      } else {
        setError('Failed to start test. Please try again.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  const handleSendMessage = async (message: string) => {
    if (!session) return

    try {
      setIsLoading(true)
      setError(null)

      const response = await apiClient.post(
        `/api/psychometric-test/${session.id}/answer`,
        { answer: message }
      )

      console.log('Submit answer response:', response)
      console.log('Response type:', typeof response)
      console.log('Response keys:', Object.keys(response || {}))

      // Handle different response structures - ApiClient might return data directly or wrapped
      let sessionData: any = null
      let status: number | undefined = undefined

      // Check if response has a 'data' property (wrapped response)
      if (response && typeof response === 'object' && 'data' in response) {
        sessionData = (response as any).data
        status = (response as any).status
      } 
      // Check if response has status property (axios-like response)
      else if (response && typeof response === 'object' && 'status' in response) {
        sessionData = (response as any).data || response
        status = (response as any).status
      }
      // Otherwise, assume response is the data directly
      else {
        sessionData = response
        status = 200 // Assume success if no status provided
      }

      console.log('Extracted session data:', sessionData)
      console.log('Extracted status:', status)

      // Check if we have valid session data (has id property)
      if (sessionData && sessionData.id) {
        console.log('Setting session with data:', sessionData)
        setSession(sessionData)
      } else {
        console.warn('Invalid response structure. Status:', status, 'Data:', sessionData)
        setError('Failed to submit answer. Please try again.')
      }
    } catch (err: any) {
      console.error('Failed to submit answer:', err)
      console.error('Error response:', err.response)
      console.error('Error data:', err.response?.data)
      setError('Failed to submit answer. Please try again.')
    } finally {
      setIsLoading(false)
    }
  }

  const handleCompleteTest = async () => {
    if (!session) return

    if (!confirm('Are you sure you want to complete the test? You won\'t be able to continue after this.')) {
      return
    }

    try {
      setIsCompleting(true)
      setError(null)

      const response = await apiClient.post(`/api/psychometric-test/${session.id}/complete`)

      console.log('Complete test response:', response)

      // Handle different response structures
      let status: number | undefined = undefined
      
      if (response && typeof response === 'object' && 'status' in response) {
        status = (response as any).status
      } else {
        status = 200 // Assume success if no status provided
      }

      console.log('Extracted status:', status)

      if (status === 200) {
        // Redirect to results page
        router.push(`/psychometric-test/results/${session.id}`)
      } else {
        console.warn('Unexpected status:', status)
        setError('Failed to complete test. Please try again.')
      }
    } catch (err: any) {
      console.error('Failed to complete test:', err)
      console.error('Error response:', err.response)
      console.error('Error data:', err.response?.data)
      setError('Failed to complete test. Please try again.')
    } finally {
      setIsCompleting(false)
    }
  }

  if (featureLoading) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="flex items-center justify-center min-h-screen">
            <p className="text-gray-600">Loading...</p>
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  if (!enabled) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="container mx-auto px-4 py-8">
            <div className="max-w-2xl mx-auto bg-white rounded-lg shadow-md p-8 text-center">
              <h1 className="text-2xl font-bold text-gray-900 mb-4">Psychometric Test</h1>
              <p className="text-gray-600 mb-6">
                The psychometric test feature is not available for your account.
              </p>
              <Button onClick={() => router.push('/')}>Go Home</Button>
            </div>
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="container mx-auto px-4 py-8">
          <div className="max-w-4xl mx-auto">
            {/* Header */}
            <div className="mb-6">
              <h1 className="text-3xl font-bold text-gray-900 mb-2">Psychometric Test</h1>
              <p className="text-gray-600">
                Answer the questions to discover your career and field inclinations.
              </p>
            </div>

            {/* Error Message */}
            {error && (
              <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700">
                {error}
              </div>
            )}

            {/* Chat Interface */}
            <div className="bg-white rounded-lg shadow-md overflow-hidden" style={{ height: '600px' }}>
              {isLoading && !session ? (
                <div className="flex items-center justify-center h-full">
                  <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
                    <p className="text-gray-600">Starting your test...</p>
                  </div>
                </div>
              ) : session ? (
                <>
                  <div className="h-[520px]">
                    <PsychometricTestChat
                      messages={messages}
                      onSendMessage={handleSendMessage}
                      isLoading={isLoading}
                      disabled={session.status !== 'IN_PROGRESS' || isCompleting}
                    />
                  </div>
                  
                  {/* Complete Button */}
                  {session.status === 'IN_PROGRESS' && (
                    <div className="p-4 border-t border-gray-200 bg-gray-50 flex justify-end">
                      <Button
                        onClick={handleCompleteTest}
                        disabled={isCompleting || isLoading}
                        variant="outline"
                      >
                        {isCompleting ? 'Completing...' : 'Complete Test'}
                      </Button>
                    </div>
                  )}
                </>
              ) : (
                <div className="flex items-center justify-center h-full">
                  <div className="text-center">
                    <p className="text-gray-600 mb-4">Failed to load test session.</p>
                    <Button onClick={loadOrStartSession}>Retry</Button>
                  </div>
                </div>
              )}
            </div>

            {/* Progress Indicator */}
            {session && (
              <div className="mt-4 text-sm text-gray-600 text-center">
                Phase: {session.currentPhase === 'INITIAL_EXPLORATION' ? 'Exploration' : 
                        session.currentPhase === 'FIELD_DEEP_DIVE' ? 'Deep Dive' : 'Completed'}
              </div>
            )}
          </div>
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}
