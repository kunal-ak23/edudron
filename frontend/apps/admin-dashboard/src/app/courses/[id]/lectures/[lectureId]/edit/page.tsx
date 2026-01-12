'use client'

import { useEffect, useState, useRef } from 'react'
import { useRouter, useParams, useSearchParams } from 'next/navigation'
import { ProtectedRoute, FileUpload } from '@edudron/ui-components'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { SplitMarkdownEditor } from '@/components/SplitMarkdownEditor'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Loader2, X, ArrowLeft, Save } from 'lucide-react'
import { lecturesApi } from '@/lib/api'
import type { Lecture, LectureContent } from '@edudron/shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

export default function LectureEditPage() {
  const router = useRouter()
  const params = useParams()
  const searchParams = useSearchParams()
  const courseId = params.id as string
  const lectureId = params.lectureId as string
  // Check if we're editing a sub-lecture (passed as query param)
  const subLectureId = searchParams.get('subLectureId') || undefined
  const isNewSubLecture = searchParams.get('newSubLecture') === 'true'
  const { toast } = useToast()
  
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [lecture, setLecture] = useState<Lecture | null>(null)
  const [textContents, setTextContents] = useState<LectureContent[]>([])
  const [currentLectureId, setCurrentLectureId] = useState<string | null>(null)
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    contentType: 'TEXT' as 'VIDEO' | 'TEXT' | 'AUDIO' | 'DOCUMENT',
    durationSeconds: 0,
    isPublished: false,
    contentUrl: ''
  })
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)
  const initialDataRef = useRef<string | null>(null)
  const initialTextContentsRef = useRef<string | null>(null)

  const isMainLecture = !subLectureId && !isNewSubLecture
  const isSubLecture = !!subLectureId || isNewSubLecture

  useEffect(() => {
    loadLectureData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId, lectureId, subLectureId, isNewSubLecture])

  // Separate effect for beforeunload warning
  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (hasUnsavedChanges) {
        e.preventDefault()
        e.returnValue = ''
      }
    }
    
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [hasUnsavedChanges])

  const loadLectureData = async () => {
    setLoading(true)
    try {
      if (isNewSubLecture) {
        // Creating a new sub-lecture - no data to load, just set up empty form
        setFormData({
          title: '',
          description: '',
          contentType: 'TEXT',
          durationSeconds: 0,
          isPublished: false,
          contentUrl: ''
        })
        setTextContents([])
        initialDataRef.current = JSON.stringify(formData)
        initialTextContentsRef.current = JSON.stringify([])
      } else if (isSubLecture && subLectureId) {
        // Load sub-lecture
        const data = await lecturesApi.getLecture(courseId, subLectureId)
        setLecture(data)
        setCurrentLectureId(subLectureId)
        const contentType: 'VIDEO' | 'TEXT' | 'AUDIO' | 'DOCUMENT' = 
          (['VIDEO', 'TEXT', 'AUDIO', 'DOCUMENT'].includes(data.contentType as string))
            ? (data.contentType as 'VIDEO' | 'TEXT' | 'AUDIO' | 'DOCUMENT')
            : 'TEXT'
        const formDataValue: typeof formData = {
          title: data.title || '',
          description: data.description || '',
          contentType,
          durationSeconds: data.duration || 0,
          isPublished: data.isPublished || false,
          contentUrl: data.contentUrl || ''
        }
        setFormData(formDataValue)
        const textItems = await loadTextContents(subLectureId)
        
        // Store initial state after all data is loaded
        initialDataRef.current = JSON.stringify(formDataValue)
        initialTextContentsRef.current = JSON.stringify(textItems)
      } else {
        // Load main lecture
        const data = await lecturesApi.getLecture(courseId, lectureId)
        setLecture(data)
        setCurrentLectureId(lectureId)
        const formDataValue: typeof formData = {
          title: data.title || '',
          description: data.description || '',
          contentType: 'TEXT',
          durationSeconds: 0,
          isPublished: data.isPublished || false,
          contentUrl: data.contentUrl || ''
        }
        setFormData(formDataValue)
        const textItems = await loadTextContents(lectureId)
        
        // Store initial state after all data is loaded
        initialDataRef.current = JSON.stringify(formDataValue)
        initialTextContentsRef.current = JSON.stringify(textItems)
      }
    } catch (error) {
      console.error('Failed to load lecture:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to load lecture',
        description: extractErrorMessage(error),
      })
      router.back()
    } finally {
      setLoading(false)
    }
  }

  const loadTextContents = async (lectureId: string): Promise<LectureContent[]> => {
    try {
      const media = await lecturesApi.getLectureMedia(lectureId)
      const textItems = media.filter((content: LectureContent) => content.contentType === 'TEXT')
      setTextContents(textItems)
      
      // Also check for video content to set contentUrl
      const videoContent = media.find((content: LectureContent) => content.contentType === 'VIDEO')
      if (videoContent && (videoContent.videoUrl || videoContent.fileUrl)) {
        setFormData(prev => ({ ...prev, contentUrl: videoContent.videoUrl || videoContent.fileUrl || '' }))
      }
      
      return textItems
    } catch (error) {
      console.error('Failed to load text contents:', error)
      setTextContents([])
      return []
    }
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      if (isMainLecture) {
        await lecturesApi.updateMainLecture(courseId, lectureId, {
          title: formData.title,
          description: formData.description
        })
      } else if (isNewSubLecture) {
        // Create new sub-lecture
        const savedLecture = await lecturesApi.createSubLecture(courseId, lectureId, {
          title: formData.title,
          description: formData.description,
          contentType: formData.contentType,
          durationSeconds: formData.durationSeconds
        })
        
        setCurrentLectureId(savedLecture.id)
        setLecture(savedLecture)
        
        // Note: Video upload should be done after creation using the FileUpload component
        // The FileUpload will handle the upload once the lecture is created
        
        // Save all text content items
        for (const content of textContents) {
          // For new sub-lectures, all content items are temporary and need to be created
          await lecturesApi.createTextContent(savedLecture.id, content.textContent || '', content.title || '')
        }
        
        toast({
          title: 'Sub-lecture created',
          description: 'The sub-lecture has been created successfully. You can now upload the video file.',
        })
        
        // Update URL to remove newSubLecture flag and set subLectureId so they can upload
        router.replace(`/courses/${courseId}/lectures/${lectureId}/edit?subLectureId=${savedLecture.id}`)
        
        // Update state to reflect that we're now editing (not creating)
        // This allows video upload to work
        setHasUnsavedChanges(false)
        return // Don't navigate back yet - allow user to upload video
      } else if (subLectureId && lecture) {
        await lecturesApi.updateSubLecture(courseId, lectureId, subLectureId, {
          title: formData.title,
          description: formData.description,
          contentType: formData.contentType,
          durationSeconds: formData.durationSeconds,
          isPublished: formData.isPublished
        })
        
        // Note: Video URL is updated via uploadVideo API, not through updateSubLecture
        
        // Save all text content items
        for (const content of textContents) {
          await lecturesApi.updateTextContent(content.id, content.textContent || '', content.title || '')
        }
        
        toast({
          title: 'Lecture saved',
          description: 'The lecture has been saved successfully.',
        })
      }
      
      setHasUnsavedChanges(false)
      // Navigate back to course page
      router.push(`/courses/${courseId}`)
    } catch (error) {
      console.error('Failed to save lecture:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to save lecture',
        description: extractErrorMessage(error),
      })
    } finally {
      setSaving(false)
    }
  }

  const handleBack = () => {
    if (hasUnsavedChanges) {
      if (confirm('You have unsaved changes. Are you sure you want to leave?')) {
        router.push(`/courses/${courseId}`)
      }
    } else {
      router.push(`/courses/${courseId}`)
    }
  }

  // Track unsaved changes
  useEffect(() => {
    // Only check for changes if initial state has been set
    if (!initialDataRef.current || !initialTextContentsRef.current) {
      setHasUnsavedChanges(false)
      return
    }
    
    const currentData = JSON.stringify(formData)
    const currentTextContents = JSON.stringify(textContents)
    
    const hasFormChanges = currentData !== initialDataRef.current
    const hasContentChanges = currentTextContents !== initialTextContentsRef.current
    
    if (hasFormChanges || hasContentChanges) {
      setHasUnsavedChanges(true)
    } else {
      setHasUnsavedChanges(false)
    }
  }, [formData, textContents])

  if (loading) {
    return (
      <ProtectedRoute>
        <div className="min-h-screen bg-gray-50 flex items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </div>
      </ProtectedRoute>
    )
  }

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gray-50">
        {/* Header */}
        <div className="bg-white border-b border-gray-200 sticky top-0 z-10">
          <div className="max-w-7xl mx-auto px-6 py-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-4">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleBack}
                  disabled={saving}
                >
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  Back
                </Button>
                <div>
                  <h1 className="text-2xl font-bold">
                    {isMainLecture ? 'Edit Lecture' : isNewSubLecture ? 'Create Sub-Lecture' : 'Edit Sub-Lecture'}
                  </h1>
                  <p className="text-sm text-gray-500">
                    {courseId && `Course: ${courseId}`}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                {hasUnsavedChanges && (
                  <span className="text-sm text-amber-600">Unsaved changes</span>
                )}
                <Button
                  variant="outline"
                  onClick={handleBack}
                  disabled={saving}
                >
                  Cancel
                </Button>
                <Button
                  onClick={handleSave}
                  disabled={saving || !formData.title.trim()}
                >
                  {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                  <Save className="h-4 w-4 mr-2" />
                  Save Changes
                </Button>
              </div>
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="max-w-7xl mx-auto px-6 py-6">
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 space-y-6">
            {/* Title */}
            <div className="space-y-2">
              <Label>
                Title <span className="text-destructive">*</span>
              </Label>
              <Input
                value={formData.title}
                onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                placeholder="Enter lecture title"
                required
              />
            </div>

            {/* Description */}
            <div className="space-y-2">
              <Label>Description</Label>
              <div className="w-full">
                <SplitMarkdownEditor
                  content={formData.description || ''}
                  onChange={(content) => setFormData({ ...formData, description: content })}
                  placeholder="Enter lecture description (markdown supported)"
                  className="w-full"
                />
              </div>
            </div>

            {/* Text Content Items (only for sub-lectures) */}
            {isSubLecture && (currentLectureId || isNewSubLecture) && (
              <div className="space-y-4 border-t pt-6">
                <div className="flex items-center justify-between">
                  <Label className="text-base font-semibold">Content Sections</Label>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={async () => {
                      if (isNewSubLecture) {
                        // For new sub-lectures, add to local state (will be saved when lecture is created)
                        const newContent: LectureContent = {
                          id: `temp-${Date.now()}`,
                          lectureId: '',
                          contentType: 'TEXT',
                          textContent: '',
                          title: 'New Content Section',
                          sequence: textContents.length
                        } as LectureContent
                        setTextContents([...textContents, newContent])
                        toast({
                          title: 'Content section added',
                          description: 'You can now edit the content. It will be saved when you create the sub-lecture.',
                        })
                      } else if (currentLectureId) {
                        // For existing sub-lectures, create on server
                        try {
                          const newContent = await lecturesApi.createTextContent(currentLectureId, '', 'New Content Section')
                          console.log('[EditPage] Created new content:', newContent)
                          if (newContent && newContent.id) {
                            setTextContents([...textContents, newContent])
                            toast({
                              title: 'Content section created',
                              description: 'You can now edit the content.',
                            })
                          } else {
                            console.error('[EditPage] Invalid response from createTextContent:', newContent)
                            throw new Error('Invalid response from server: content section was not created properly')
                          }
                        } catch (error) {
                          console.error('[EditPage] Failed to create content section:', error)
                          toast({
                            variant: 'destructive',
                            title: 'Failed to create content section',
                            description: extractErrorMessage(error),
                          })
                        }
                      }
                    }}
                    disabled={saving || (!currentLectureId && !isNewSubLecture)}
                  >
                    + Add Content Section
                  </Button>
                </div>
                {textContents.length === 0 ? (
                  <p className="text-sm text-gray-500">No content sections yet. Click &quot;Add Content Section&quot; to create one.</p>
                ) : (
                  <div className="space-y-4">
                    {textContents.filter(content => content != null).map((content, index) => (
                      <div key={content.id} className="border border-gray-200 rounded-lg p-4 space-y-3">
                        <div className="flex items-center justify-between">
                          <Label className="text-sm font-medium">
                            Content Section {index + 1}
                            {content?.title && `: ${content.title}`}
                          </Label>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={async () => {
                              if (confirm('Are you sure you want to delete this content section?')) {
                                try {
                                  await lecturesApi.deleteMedia(content.id)
                                  setTextContents(textContents.filter(c => c.id !== content.id))
                                  toast({
                                    title: 'Content section deleted',
                                  })
                                } catch (error) {
                                  toast({
                                    variant: 'destructive',
                                    title: 'Failed to delete content section',
                                    description: extractErrorMessage(error),
                                  })
                                }
                              }
                            }}
                            disabled={saving}
                          >
                            <X className="h-4 w-4" />
                          </Button>
                        </div>
                        <div className="space-y-2">
                          <Label className="text-xs">Title (optional)</Label>
                          <Input
                            value={content.title || ''}
                            onChange={(e) => {
                              const newTitle = e.target.value
                              const updatedContents = textContents.map(c =>
                                c.id === content.id ? { ...c, title: newTitle } : c
                              )
                              setTextContents(updatedContents)
                            }}
                            placeholder="Content section title"
                            className="text-sm"
                          />
                        </div>
                        <div className="space-y-2">
                          <Label className="text-xs">Content (Markdown/Rich Text)</Label>
                          <div className="w-full">
                            <SplitMarkdownEditor
                              content={content.textContent || ''}
                              onChange={(newContent) => {
                                const updatedContents = textContents.map(c =>
                                  c.id === content.id ? { ...c, textContent: newContent } : c
                                )
                                setTextContents(updatedContents)
                              }}
                              placeholder="Start typing markdown... Use # for headings, ** for bold, * for italic, etc."
                              className="w-full"
                            />
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Content Type and other fields (only for sub-lectures) */}
            {(isSubLecture || isNewSubLecture) && (
              <>
                <div className="space-y-2 border-t pt-6">
                  <Label>Content Type</Label>
                  <Select
                    value={formData.contentType}
                    onValueChange={(value: any) => setFormData({ ...formData, contentType: value })}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="VIDEO">Video</SelectItem>
                      <SelectItem value="TEXT">Text/Reading</SelectItem>
                      <SelectItem value="AUDIO">Audio</SelectItem>
                      <SelectItem value="DOCUMENT">Document</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {/* Video Upload (only for VIDEO content type) */}
                {formData.contentType === 'VIDEO' && (
                  <div className="space-y-2">
                    <Label>Video File</Label>
                    <FileUpload
                      accept="video/*"
                      maxSize={500 * 1024 * 1024} // 500MB
                      value={formData.contentUrl || ''}
                      onChange={(url) => setFormData({ ...formData, contentUrl: url })}
                      onUpload={async (file) => {
                        if (!currentLectureId) {
                          throw new Error('Please save the sub-lecture first, then upload the video')
                        }
                        // Upload video for existing sub-lectures
                        const content = await lecturesApi.uploadVideo(currentLectureId, file)
                        const url = content.videoUrl || content.fileUrl || ''
                        setFormData({ ...formData, contentUrl: url })
                        return url
                      }}
                      helperText={isNewSubLecture ? "Please save the sub-lecture first, then upload the video file (MP4, MOV, etc. up to 500MB)" : "Upload a video file for this lecture (MP4, MOV, etc. up to 500MB)"}
                      disabled={!currentLectureId}
                    />
                  </div>
                )}

                <div className="space-y-2">
                  <Label>Duration (seconds)</Label>
                  <Input
                    type="number"
                    value={formData.durationSeconds}
                    onChange={(e) => setFormData({ ...formData, durationSeconds: parseInt(e.target.value) || 0 })}
                    placeholder="0"
                    min="0"
                  />
                </div>

                <div className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    id="isPublished"
                    checked={formData.isPublished}
                    onChange={(e) => setFormData({ ...formData, isPublished: e.target.checked })}
                    className="w-4 h-4"
                  />
                  <Label htmlFor="isPublished" className="font-normal cursor-pointer">
                    Published
                  </Label>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </ProtectedRoute>
  )
}

