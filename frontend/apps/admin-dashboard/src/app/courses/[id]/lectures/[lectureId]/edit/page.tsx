'use client'

import { useEffect, useState, useRef } from 'react'
import { useRouter, useParams, useSearchParams } from 'next/navigation'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { FileUpload } from '@kunal-ak23/edudron-ui-components'
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
import { Loader2, X, ArrowLeft, Save, FileText } from 'lucide-react'
import { lecturesApi } from '@/lib/api'
import type { Lecture, LectureContent } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import { ConfirmationDialog } from '@/components/ConfirmationDialog'

// Force dynamic rendering
export const dynamic = 'force-dynamic'

// Utility function to format file size
function formatFileSize(bytes: number | null | undefined): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
}

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
  const [attachments, setAttachments] = useState<LectureContent[]>([])
  const [currentLectureId, setCurrentLectureId] = useState<string | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    contentType: 'TEXT' as 'VIDEO' | 'TEXT',
    durationSeconds: 0,
    isPublished: false,
    contentUrl: ''
  })
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)
  const [showUnsavedDialog, setShowUnsavedDialog] = useState(false)
  const [showDeleteDialog, setShowDeleteDialog] = useState(false)
  const [showDeleteVideoDialog, setShowDeleteVideoDialog] = useState(false)
  const [contentToDelete, setContentToDelete] = useState<string | null>(null)
  const [selectedFileSize, setSelectedFileSize] = useState<number | null>(null)
  const [uploadingFileSize, setUploadingFileSize] = useState<number | null>(null)
  const [uploadProgress, setUploadProgress] = useState<{ loaded: number; total: number } | null>(null)
  const [isProcessing, setIsProcessing] = useState(false) // Track server-to-Azure processing phase
  const pendingNavigation = useRef<(() => void) | null>(null)
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
      if (isNewSubLecture && !currentLectureId) {
        // Creating a new sub-lecture - no data to load, just set up empty form
        // Only reset if currentLectureId is not already set (to avoid resetting after save)
        setFormData({
          title: '',
          description: '',
          contentType: 'TEXT',
          durationSeconds: 0,
          isPublished: false,
          contentUrl: ''
        })
        setTextContents([])
        setAttachments([])
        setCurrentLectureId(null) // Reset to null for new sub-lectures
        initialDataRef.current = JSON.stringify(formData)
        initialTextContentsRef.current = JSON.stringify([])
      } else if (isSubLecture && subLectureId) {
        // Load sub-lecture
        const data = await lecturesApi.getLecture(courseId, subLectureId)
        setLecture(data)
        setCurrentLectureId(subLectureId)
        const contentType: 'VIDEO' | 'TEXT' = 
          (['VIDEO', 'TEXT'].includes(data.contentType as string))
            ? (data.contentType as 'VIDEO' | 'TEXT')
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
        const { allItems, textItems, updatedFormData } = await loadTextContents(subLectureId, formDataValue)
        await loadAttachments(subLectureId)
        
        // Don't set initial refs here - let useEffect handle it after state stabilizes
        console.log('[EditPage] Loaded sub-lecture data:', {
          formDataValue,
          updatedFormData,
          wasFormDataUpdated: !!updatedFormData,
          allItems,
          textItems
        })
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
        const { allItems, textItems, updatedFormData } = await loadTextContents(lectureId, formDataValue)
        await loadAttachments(lectureId)
        
        // Don't set initial refs here - let useEffect handle it after state stabilizes
        console.log('[EditPage] Loaded main lecture data:', {
          formDataValue,
          updatedFormData,
          wasFormDataUpdated: !!updatedFormData,
          allItems,
          textItems
        })
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

  const loadTextContents = async (lectureId: string, currentFormData?: typeof formData): Promise<{ allItems: LectureContent[], textItems: LectureContent[], updatedFormData?: typeof formData }> => {
    try {
      const media = await lecturesApi.getLectureMedia(lectureId)
      // Load both TEXT and VIDEO content (VIDEO for transcript upload)
      const allItems = media.filter((content: LectureContent) => 
        content.contentType === 'TEXT' || content.contentType === 'VIDEO'
      )
      setTextContents(allItems)
      
      // Also check for video content to set contentUrl
      let updatedFormData = currentFormData
      const videoContent = media.find((content: LectureContent) => content.contentType === 'VIDEO')
      if (videoContent && (videoContent.videoUrl || videoContent.fileUrl)) {
        const videoUrl = videoContent.videoUrl || videoContent.fileUrl || ''
        setFormData(prev => ({ ...prev, contentUrl: videoUrl }))
        // Also update our local copy for immediate use
        updatedFormData = currentFormData ? { ...currentFormData, contentUrl: videoUrl } : undefined
      }
      
      // Return both allItems and textItems, plus updated form data if it was modified
      const textItems = allItems.filter((content: LectureContent) => content.contentType === 'TEXT')
      return { allItems, textItems, updatedFormData }
    } catch (error) {
      console.error('Failed to load text contents:', error)
      setTextContents([])
      return { allItems: [], textItems: [], updatedFormData: undefined }
    }
  }

  const loadAttachments = async (lectureId: string) => {
    try {
      console.log('[EditPage] Loading attachments for lectureId:', lectureId)
      const media = await lecturesApi.getLectureMedia(lectureId)
      console.log('[EditPage] All media items:', media)
      // Filter out TEXT and VIDEO content types - only show attachments (PDF, IMAGE, AUDIO, etc.)
      const attachmentItems = media.filter((content: LectureContent) => 
        content.contentType !== 'TEXT' && content.contentType !== 'VIDEO'
      )
      console.log('[EditPage] Filtered attachment items:', attachmentItems)
      setAttachments(attachmentItems)
      console.log('[EditPage] Attachments state updated, count:', attachmentItems.length)
    } catch (error) {
      console.error('[EditPage] Failed to load attachments:', error)
      setAttachments([])
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
        console.log('[EditPage] Creating new sub-lecture with data:', {
          title: formData.title,
          description: formData.description,
          contentType: formData.contentType,
          durationSeconds: formData.durationSeconds
        })
        
        let savedLecture: Lecture | undefined
        try {
          console.log('[EditPage] Calling lecturesApi.createSubLecture...')
          savedLecture = await lecturesApi.createSubLecture(courseId, lectureId, {
            title: formData.title,
            description: formData.description,
            contentType: formData.contentType,
            durationSeconds: formData.durationSeconds
          })
          console.log('[EditPage] createSubLecture returned:', savedLecture)
          console.log('[EditPage] savedLecture type:', typeof savedLecture)
          console.log('[EditPage] savedLecture is undefined?', savedLecture === undefined)
          console.log('[EditPage] savedLecture is null?', savedLecture === null)
        } catch (error: any) {
          console.error('[EditPage] Failed to create sub-lecture - error caught:', error)
          console.error('[EditPage] Error message:', error?.message)
          console.error('[EditPage] Error stack:', error?.stack)
          throw new Error(`Failed to create sub-lecture: ${error.message || 'Unknown error'}`)
        }
        
        // Ensure savedLecture is defined and has an id
        if (!savedLecture) {
          console.error('[EditPage] savedLecture is undefined or null after creation')
          throw new Error('Failed to create sub-lecture: No lecture object returned from server')
        }
        
        if (!savedLecture.id) {
          console.error('[EditPage] savedLecture missing id property:', savedLecture)
          throw new Error('Failed to create sub-lecture: Invalid response from server - missing id')
        }
        
        console.log('[EditPage] Setting currentLectureId to:', savedLecture.id)
        // Set currentLectureId immediately so FileUpload is enabled
        setCurrentLectureId(savedLecture.id)
        setLecture(savedLecture)
        
        // Save all text content items
        for (const content of textContents) {
          // For new sub-lectures, all content items are temporary and need to be created
          await lecturesApi.createTextContent(savedLecture.id, content.textContent || '', content.title || '')
        }
        
        // Load attachments for the new lecture
        await loadAttachments(savedLecture.id)
        
        toast({
          title: 'Sub-lecture created',
          description: 'The sub-lecture has been created successfully. You can now upload the video file.',
        })
        
        // Update state to reflect that we're now editing (not creating)
        setHasUnsavedChanges(false)
        
        // Update URL to remove newSubLecture flag and set subLectureId
        // Use replace to update URL without adding to history
        // The currentLectureId is already set, so FileUpload will remain enabled
        router.replace(`/courses/${courseId}/lectures/${lectureId}/edit?subLectureId=${savedLecture.id}`, { scroll: false })
        
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
        
        // Save all text content items (only TEXT type, not VIDEO)
        // Double-check: filter out any non-TEXT items and ensure they have IDs
        console.log('[EditPage] Saving text contents. Total items:', textContents.length)
        const textOnlyContents = textContents.filter(content => {
          const isText = content?.contentType === 'TEXT'
          const hasId = !!content?.id
          if (!isText && hasId) {
            console.warn(`[EditPage] Skipping non-TEXT content item ${content.id} with type ${content?.contentType}`)
          }
          return isText && hasId
        })
        console.log('[EditPage] Filtered to TEXT items:', textOnlyContents.length)
        
        for (const content of textOnlyContents) {
          try {
            // Triple-check before making the API call
            if (content.contentType !== 'TEXT') {
              console.error(`[EditPage] Attempted to update non-TEXT content ${content.id} with type ${content.contentType}`)
              continue
            }
            console.log(`[EditPage] Updating text content ${content.id}`)
            await lecturesApi.updateTextContent(content.id, content.textContent || '', content.title || '')
          } catch (error) {
            console.error(`[EditPage] Failed to update text content ${content.id}:`, error)
            // Continue with other content items even if one fails
          }
        }
        
        toast({
          title: 'Lecture saved',
          description: 'The lecture has been saved successfully.',
        })
        
        // Don't navigate away for sub-lectures - stay on the edit page
        setHasUnsavedChanges(false)
        return // Stay on the sub-lecture edit page
      }
      
      setHasUnsavedChanges(false)
      // Navigate back to course page only for main lectures
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

  const handleAttachmentUpload = async (files: File[]) => {
    console.log('[EditPage] handleAttachmentUpload called - files:', files.length, 'currentLectureId:', currentLectureId)
    try {
      if (currentLectureId) {
        console.log('[EditPage] Uploading attachments to lecture:', currentLectureId)
        await lecturesApi.uploadAttachments(currentLectureId, files)
        console.log('[EditPage] Upload successful, refreshing list')
        toast({
          title: 'Attachments uploaded',
          description: `${files.length} file(s) uploaded successfully.`,
        })
        // Refresh attachments list
        await loadAttachments(currentLectureId)
        return []
      } else {
        console.warn('[EditPage] Cannot upload - currentLectureId is null')
        throw new Error('Please save the lecture first, then upload attachments')
      }
    } catch (error: any) {
      console.error('[EditPage] Upload failed:', error)
      throw new Error(error.message || 'Failed to upload attachments')
    }
  }

  const handleDeleteAttachment = async (contentId: string) => {
    console.log('[EditPage] handleDeleteAttachment called - contentId:', contentId)
    try {
      await lecturesApi.deleteMedia(contentId)
      console.log('[EditPage] Delete successful, updating state')
      setAttachments(attachments.filter(a => a.id !== contentId))
      toast({
        title: 'Attachment deleted',
      })
    } catch (error) {
      console.error('[EditPage] Delete failed:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to delete attachment',
        description: extractErrorMessage(error),
      })
    }
  }

  const handleDeleteVideo = async () => {
    const videoContent = textContents.find((c: any) => c.contentType === 'VIDEO')
    if (!videoContent || !videoContent.id) {
      toast({
        variant: 'destructive',
        title: 'No video found',
        description: 'No video content found to delete.',
      })
      return
    }

    try {
      await lecturesApi.deleteMedia(videoContent.id)
      // Remove video from textContents
      setTextContents(textContents.filter(c => c.id !== videoContent.id))
      // Clear contentUrl
      setFormData({ ...formData, contentUrl: '' })
      setShowDeleteVideoDialog(false)
      toast({
        title: 'Video deleted',
        description: 'The video has been removed from Azure storage and the lecture.',
      })
    } catch (error) {
      console.error('[EditPage] Delete video failed:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to delete video',
        description: extractErrorMessage(error),
      })
    }
  }

  const handleBack = () => {
    if (hasUnsavedChanges) {
      pendingNavigation.current = () => router.push(`/courses/${courseId}`)
      setShowUnsavedDialog(true)
    } else {
      router.push(`/courses/${courseId}`)
    }
  }

  const handleConfirmLeave = () => {
    if (pendingNavigation.current) {
      pendingNavigation.current()
      pendingNavigation.current = null
    }
    setShowUnsavedDialog(false)
  }

  // Set initial refs after data is loaded and state has stabilized (only once)
  useEffect(() => {
    // Only set initial refs if they haven't been set yet, loading is complete, and we have data
    if (!initialDataRef.current && !loading && formData.title && textContents.length >= 0) {
      const serializedFormData = JSON.stringify(formData)
      const serializedTextContents = JSON.stringify(textContents)
      initialDataRef.current = serializedFormData
      initialTextContentsRef.current = serializedTextContents
      console.log('[EditPage] Set initial refs (useEffect):', {
        formData,
        textContents,
        serializedFormDataLength: serializedFormData.length,
        serializedTextContentsLength: serializedTextContents.length,
        serializedFormData: serializedFormData.substring(0, 200)
      })
    }
  }, [formData, textContents, loading])

  // Track upload progress changes
  useEffect(() => {
    if (uploadProgress) {
      console.log('[EditPage] uploadProgress state changed:', {
        loaded: uploadProgress.loaded,
        total: uploadProgress.total,
        percentage: ((uploadProgress.loaded / uploadProgress.total) * 100).toFixed(2) + '%',
        loadedFormatted: formatFileSize(uploadProgress.loaded),
        totalFormatted: formatFileSize(uploadProgress.total)
      })
    }
  }, [uploadProgress])

  // Track unsaved changes
  useEffect(() => {
    // Only check for changes if initial state has been set
    if (!initialDataRef.current || !initialTextContentsRef.current) {
      console.log('[EditPage] Unsaved changes check skipped - initial refs not set:', {
        hasInitialData: !!initialDataRef.current,
        hasInitialTextContents: !!initialTextContentsRef.current
      })
      setHasUnsavedChanges(false)
      return
    }
    
    const currentData = JSON.stringify(formData)
    const currentTextContents = JSON.stringify(textContents)
    
    const hasFormChanges = currentData !== initialDataRef.current
    const hasContentChanges = currentTextContents !== initialTextContentsRef.current
    
    console.log('[EditPage] Unsaved changes check:', {
      currentData: currentData,
      initialData: initialDataRef.current,
      currentDataLength: currentData.length,
      initialDataLength: initialDataRef.current.length,
      hasFormChanges,
      currentTextContents: currentTextContents.substring(0, 200),
      initialTextContents: initialTextContentsRef.current.substring(0, 200),
      currentTextContentsLength: currentTextContents.length,
      initialTextContentsLength: initialTextContentsRef.current.length,
      hasContentChanges,
      textContentsCount: textContents.length,
      willSetUnsaved: hasFormChanges || hasContentChanges,
      formDataKeys: Object.keys(formData),
      formDataValues: formData
    })
    
    if (hasFormChanges || hasContentChanges) {
      setHasUnsavedChanges(true)
    } else {
      setHasUnsavedChanges(false)
    }
  }, [formData, textContents])

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  return (
    <>
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
                {textContents.filter(content => content != null && content.contentType === 'TEXT').length === 0 ? (
                  <p className="text-sm text-gray-500">No content sections yet. Click &quot;Add Content Section&quot; to create one.</p>
                ) : (
                  <div className="space-y-4">
                    {textContents.filter(content => content != null && content.contentType === 'TEXT').map((content, index) => (
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
                            onClick={() => {
                              setContentToDelete(content.id)
                              setShowDeleteDialog(true)
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
                              // Only update if this is a TEXT content item
                              if (content.contentType !== 'TEXT') {
                                console.warn(`Attempted to update title for non-TEXT content ${content.id} with type ${content.contentType}`)
                                return
                              }
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
                                // Only update if this is a TEXT content item
                                if (content.contentType !== 'TEXT') {
                                  console.warn(`Attempted to update textContent for non-TEXT content ${content.id} with type ${content.contentType}`)
                                  return
                                }
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
                    </SelectContent>
                  </Select>
                </div>

                {/* Video Upload (only for VIDEO content type) */}
                {formData.contentType === 'VIDEO' && (
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <Label>Video File</Label>
                      {(() => {
                        const videoContent = textContents.find((c: any) => c.contentType === 'VIDEO')
                        if (videoContent && (videoContent.videoUrl || videoContent.fileUrl)) {
                          return (
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => setShowDeleteVideoDialog(true)}
                              disabled={saving}
                              className="text-red-600 hover:text-red-700 hover:bg-red-50"
                            >
                              <X className="h-4 w-4 mr-1" />
                              Remove Video
                            </Button>
                          )
                        }
                        return null
                      })()}
                    </div>
                    {(() => {
                      const videoContent = textContents.find((c: any) => c.contentType === 'VIDEO')
                      const hasVideo = !!videoContent && (videoContent.videoUrl || videoContent.fileUrl)
                      const fileSize = videoContent?.fileSizeBytes
                      
                      return (
                        <>
                          {hasVideo && fileSize && (
                            <div className="text-sm text-gray-600 bg-gray-50 p-2 rounded border border-gray-200">
                              <div className="flex items-center justify-between">
                                <span className="font-medium">Uploaded Video:</span>
                                <span className="text-gray-500">{formatFileSize(fileSize)}</span>
                              </div>
                              {videoContent.videoUrl && (
                                <a
                                  href={videoContent.videoUrl}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="text-primary hover:underline text-xs mt-1 block truncate"
                                >
                                  {videoContent.videoUrl}
                                </a>
                              )}
                            </div>
                          )}
                          {uploadProgress && uploadingFileSize && (
                            <div className={`text-sm text-gray-600 p-2 rounded border ${isProcessing ? 'bg-blue-50 border-blue-200' : 'bg-yellow-50 border-yellow-200'}`}>
                              <div className="flex items-center justify-between mb-2">
                                <div className="flex items-center gap-2">
                                  <Loader2 className="h-4 w-4 animate-spin" />
                                  {isProcessing ? (
                                    <span>Processing: <span className="font-medium">Uploading to Azure Storage...</span></span>
                                  ) : (
                                    <span>Uploading: <span className="font-medium">{formatFileSize(uploadProgress.loaded)}</span> of <span className="font-medium">{formatFileSize(uploadProgress.total)}</span></span>
                                  )}
                                </div>
                                <span className="text-gray-500 font-medium">
                                  {isProcessing ? 'Processing...' : `${Math.round((uploadProgress.loaded / uploadProgress.total) * 100)}%`}
                                </span>
                              </div>
                              <div className="w-full bg-gray-200 rounded-full h-2">
                                <div 
                                  className={`h-2 rounded-full transition-all duration-300 ${isProcessing ? 'bg-blue-500 animate-pulse' : 'bg-primary'}`}
                                  style={{ width: `${Math.min(100, (uploadProgress.loaded / uploadProgress.total) * 100)}%` }}
                                />
                              </div>
                              <p className="text-xs text-gray-500 mt-1">
                                {isProcessing 
                                  ? 'Server is uploading your file to Azure Storage. This may take a moment...'
                                  : 'Upload progress - transferring from browser to server'}
                              </p>
                            </div>
                          )}
                          {selectedFileSize && !hasVideo && !uploadingFileSize && (
                            <div className="text-sm text-gray-600 bg-blue-50 p-2 rounded border border-blue-200">
                              Selected file size: <span className="font-medium">{formatFileSize(selectedFileSize)}</span>
                            </div>
                          )}
                        </>
                      )
                    })()}
                    <FileUpload
                      label="Video File"
                      accept="video/*"
                      maxSize={2 * 1024 * 1024 * 1024} // 2GB
                      value={formData.contentUrl || ''}
                      onChange={(url) => {
                        setFormData({ ...formData, contentUrl: url })
                        setSelectedFileSize(null)
                        setUploadingFileSize(null)
                        setUploadProgress(null)
                        setIsProcessing(false)
                      }}
                      onUpload={async (file) => {
                        if (!currentLectureId) {
                          throw new Error('Please save the sub-lecture first, then upload the video')
                        }
                        // Show file size before upload starts
                        setSelectedFileSize(file.size)
                        setUploadingFileSize(file.size)
                        setUploadProgress({ loaded: 0, total: file.size })
                        console.log('[EditPage] Starting video upload:', {
                          fileSize: file.size,
                          fileName: file.name,
                          currentLectureId,
                          fileSizeMB: (file.size / (1024 * 1024)).toFixed(2) + ' MB'
                        })
                        
                        // NOTE: Spring Boot's multipart resolver buffers the entire request before processing,
                        // so XMLHttpRequest progress events may not fire until buffering completes.
                        // We use a time-based fallback progress that estimates upload speed.
                        let fallbackProgressInterval: NodeJS.Timeout | null = null
                        let realProgressStarted = false
                        let uploadCompleted = false
                        const uploadStartTime = Date.now()
                        
                        // Estimate upload time: ~2-5 seconds per MB depending on connection
                        // This provides realistic progress estimation while Spring Boot buffers
                        const mbSize = file.size / (1024 * 1024)
                        const estimatedSecondsPerMB = 3 // Conservative estimate: 3 seconds per MB
                        const estimatedTotalTime = Math.max(30000, mbSize * estimatedSecondsPerMB * 1000) // Min 30 seconds
                        
                        console.log('[EditPage] Starting upload with time-based progress estimation:', {
                          fileSizeMB: mbSize.toFixed(2),
                          estimatedTimeSeconds: (estimatedTotalTime / 1000).toFixed(0),
                          note: 'Spring Boot buffers request before processing, so using time-based estimation'
                        })
                        
                        // Start fallback progress that gradually increases based on elapsed time
                        // This provides visual feedback during the buffering phase
                        fallbackProgressInterval = setInterval(() => {
                          if (realProgressStarted || uploadCompleted) {
                            if (fallbackProgressInterval) {
                              clearInterval(fallbackProgressInterval)
                              fallbackProgressInterval = null
                            }
                            return
                          }
                          
                          const elapsed = Date.now() - uploadStartTime
                          const progressPercent = Math.min(95, (elapsed / estimatedTotalTime) * 100)
                          const loaded = Math.floor((progressPercent / 100) * file.size)
                          
                          // Update progress every 500ms with smooth progression
                          setUploadProgress({ loaded, total: file.size })
                          
                          if (progressPercent >= 95) {
                            // Stop at 95% to leave room for real progress or completion
                            if (fallbackProgressInterval) {
                              clearInterval(fallbackProgressInterval)
                              fallbackProgressInterval = null
                            }
                          }
                        }, 500) // Update every 500ms
                        
                        try {
                          // Upload video for existing sub-lectures with progress tracking
                          // Using XMLHttpRequest for more reliable progress updates
                          const content = await lecturesApi.uploadVideo(
                            currentLectureId, 
                            file,
                            (progress) => {
                              console.log('[EditPage] Real progress event received:', {
                                loaded: progress.loaded,
                                total: progress.total,
                                percentage: ((progress.loaded / progress.total) * 100).toFixed(2) + '%'
                              })
                              // Mark that real progress has started (XMLHttpRequest events are firing)
                              realProgressStarted = true
                              // Clear fallback interval when real progress starts
                              if (fallbackProgressInterval) {
                                clearInterval(fallbackProgressInterval)
                                fallbackProgressInterval = null
                              }
                              
                              // Check if client upload is complete (100%) - switch to processing phase
                              const isComplete = progress.loaded >= progress.total
                              if (isComplete && !isProcessing) {
                                console.log('[EditPage] Client upload complete, switching to processing phase')
                                setIsProcessing(true)
                              }
                              
                              setUploadProgress(progress)
                            }
                          )
                          console.log('[EditPage] Upload completed, content received:', content)
                          uploadCompleted = true
                          // Clear fallback timeout on completion
                          if (fallbackProgressInterval) {
                            clearTimeout(fallbackProgressInterval)
                            fallbackProgressInterval = null
                          }
                          // Set to 100% on completion - ensure user sees completion
                          console.log('[EditPage] Setting progress to 100% - upload complete')
                          setUploadProgress({ loaded: file.size, total: file.size })
                          setIsProcessing(false) // Processing complete
                          // Show 100% for a moment before clearing
                          await new Promise(resolve => setTimeout(resolve, 1000))
                          const url = content.videoUrl || content.fileUrl || ''
                          setFormData({ ...formData, contentUrl: url })
                          setSelectedFileSize(null)
                          setUploadingFileSize(null)
                          setUploadProgress(null)
                          setIsProcessing(false)
                          // Reload text contents to get updated file size
                          await loadTextContents(currentLectureId, formData)
                          return url
                        } catch (error) {
                          // Clear fallback timeout on error
                          if (fallbackProgressInterval) {
                            clearTimeout(fallbackProgressInterval)
                            fallbackProgressInterval = null
                          }
                          setSelectedFileSize(null)
                          setUploadingFileSize(null)
                          setUploadProgress(null)
                          setIsProcessing(false)
                          throw error
                        }
                      }}
                      helperText={!currentLectureId ? "Please save the sub-lecture first, then upload the video file (MP4, MOV, etc. up to 2GB)" : "Upload a video file for this lecture (MP4, MOV, etc. up to 2GB)"}
                      disabled={!currentLectureId || saving}
                    />
                  </div>
                )}

                {/* Transcript Upload (only for VIDEO content type with existing video) */}
                {formData.contentType === 'VIDEO' && currentLectureId && (() => {
                  const videoContent = textContents.find((c: any) => c.contentType === 'VIDEO') as any
                  const hasVideo = !!videoContent
                  return hasVideo ? (
                    <div className="space-y-2 border-t pt-6">
                      <Label>Transcript File</Label>
                      <FileUpload
                        label="Transcript File"
                        accept=".txt,.vtt,.srt,.doc,.docx"
                        maxSize={10 * 1024 * 1024} // 10MB
                        value={(videoContent as any)?.transcriptUrl || ''}
                        onChange={(url) => {
                          // Update the video content's transcript URL
                          const updatedContents = textContents.map((c: any) =>
                            c.id === videoContent.id ? { ...c, transcriptUrl: url } : c
                          )
                          setTextContents(updatedContents)
                        }}
                        onUpload={async (file) => {
                          if (!currentLectureId) {
                            throw new Error('Please save the sub-lecture first, then upload the transcript')
                          }
                          const content = await (lecturesApi as any).uploadTranscript(currentLectureId, file)
                          // Update the video content with the new transcript URL
                          const updatedContents = textContents.map((c: any) =>
                            c.id === videoContent.id ? { ...c, transcriptUrl: (content as any).transcriptUrl } : c
                          )
                          setTextContents(updatedContents)
                          return (content as any).transcriptUrl || ''
                        }}
                        helperText="Upload a transcript file for this video (TXT, VTT, SRT, DOC, DOCX up to 10MB)"
                        disabled={!currentLectureId || saving}
                      />
                    </div>
                  ) : null
                })()}

                {/* Attachments (Multiple Files) */}
                <div className="space-y-2 border-t pt-6">
                  <Label className="text-base font-semibold">
                    Attachments
                    {!currentLectureId && <span className="text-xs text-gray-500 ml-2 font-normal">(Upload after saving lecture)</span>}
                  </Label>
                  {currentLectureId ? (
                    <>
                      {/* Existing Attachments List */}
                      {attachments.length > 0 && (
                        <div className="space-y-2 mb-4">
                          <Label className="text-sm font-medium">Existing Attachments</Label>
                          <div className="space-y-2">
                            {attachments.map((attachment) => (
                              <div
                                key={attachment.id}
                                className="flex items-center justify-between p-3 border border-gray-200 rounded-md bg-gray-50"
                              >
                                <div className="flex items-center space-x-3 flex-1 min-w-0">
                                  <FileText className="h-5 w-5 text-gray-400 flex-shrink-0" />
                                  <div className="flex-1 min-w-0">
                                    <p className="text-sm font-medium truncate">
                                      {attachment.title || attachment.fileUrl?.split('/').pop() || 'Untitled'}
                                    </p>
                                    {attachment.contentType && (
                                      <p className="text-xs text-gray-500">{attachment.contentType}</p>
                                    )}
                                  </div>
                                </div>
                                <div className="flex items-center space-x-2 flex-shrink-0">
                                  {attachment.fileUrl && (
                                    <a
                                      href={attachment.fileUrl}
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      className="text-primary hover:underline text-sm"
                                    >
                                      View
                                    </a>
                                  )}
                                  <Button
                                    type="button"
                                    variant="ghost"
                                    size="sm"
                                    onClick={() => handleDeleteAttachment(attachment.id)}
                                    disabled={saving}
                                  >
                                    <X className="h-4 w-4" />
                                  </Button>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                      {/* File Upload with Drag and Drop */}
                      <div
                        onDragOver={(e) => {
                          e.preventDefault()
                          e.stopPropagation()
                          if (!saving && currentLectureId) {
                            setIsDragging(true)
                          }
                        }}
                        onDragEnter={(e) => {
                          e.preventDefault()
                          e.stopPropagation()
                          if (!saving && currentLectureId) {
                            setIsDragging(true)
                          }
                        }}
                        onDragLeave={(e) => {
                          e.preventDefault()
                          e.stopPropagation()
                          setIsDragging(false)
                        }}
                        onDrop={async (e) => {
                          e.preventDefault()
                          e.stopPropagation()
                          setIsDragging(false)
                          if (saving || !currentLectureId) return
                          const files = Array.from(e.dataTransfer.files)
                          if (files.length > 0) {
                            try {
                              await handleAttachmentUpload(files)
                            } catch (error) {
                              toast({
                                variant: 'destructive',
                                title: 'Upload failed',
                                description: extractErrorMessage(error),
                              })
                            }
                          }
                        }}
                        onClick={() => {
                          if (!saving && currentLectureId) {
                            const input = document.createElement('input')
                            input.type = 'file'
                            input.multiple = true
                            input.accept = '.pdf,.doc,.docx,.txt,.mp3,.wav,.ogg,.jpg,.jpeg,.png,.gif,.zip,.rar,.csv,.xlsx,.xls'
                            input.onchange = async (e) => {
                              const files = Array.from((e.target as HTMLInputElement).files || [])
                              if (files.length > 0) {
                                try {
                                  await handleAttachmentUpload(files)
                                } catch (error) {
                                  toast({
                                    variant: 'destructive',
                                    title: 'Upload failed',
                                    description: extractErrorMessage(error),
                                  })
                                }
                              }
                            }
                            input.click()
                          }
                        }}
                        className={`border-2 border-dashed rounded-lg p-6 cursor-pointer transition-colors ${
                          saving || !currentLectureId
                            ? 'opacity-50 cursor-not-allowed border-gray-300 bg-gray-50'
                            : isDragging
                            ? 'border-primary-500 bg-primary-100'
                            : 'border-gray-300 hover:border-primary-400 hover:bg-primary-50'
                        }`}
                      >
                        <div className="text-center">
                          <svg
                            className="mx-auto h-12 w-12 text-gray-400"
                            stroke="currentColor"
                            fill="none"
                            viewBox="0 0 48 48"
                          >
                            <path
                              d="M28 8H12a4 4 0 00-4 4v20m32-12v8m0 0v8a4 4 0 01-4 4H12a4 4 0 01-4-4v-4m32-4l-3.172-3.172a4 4 0 00-5.656 0L28 28M8 32l9.172-9.172a4 4 0 015.656 0L28 28m0 0l4 4m4-24h8m-4-4v8m-12 4h.02"
                              strokeWidth={2}
                              strokeLinecap="round"
                              strokeLinejoin="round"
                            />
                          </svg>
                          <div className="mt-2">
                            <p className="text-sm text-gray-600">
                              <span className="font-medium text-primary-600 hover:text-primary-500">
                                Click to upload
                              </span>{' '}
                              or drag and drop
                            </p>
                            <p className="text-xs text-gray-500 mt-1">
                              PDF, DOC, audio, images, etc. up to 100MB each
                            </p>
                          </div>
                        </div>
                      </div>
                      <p className="text-xs text-gray-500">
                        Upload multiple files (PDF, DOC, audio, images, etc. up to 100MB each)
                      </p>
                    </>
                  ) : (
                    <div className="p-4 border border-gray-300 rounded-md bg-gray-50">
                      <p className="text-sm text-gray-600">
                        Save the lecture first, then you can upload attachment files.
                      </p>
                    </div>
                  )}
                </div>

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

      <ConfirmationDialog
        isOpen={showUnsavedDialog}
        onClose={() => {
          setShowUnsavedDialog(false)
          pendingNavigation.current = null
        }}
        onConfirm={handleConfirmLeave}
        title="Unsaved Changes"
        description="You have unsaved changes. Are you sure you want to leave?"
        confirmText="Leave"
        variant="default"
      />

      <ConfirmationDialog
        isOpen={showDeleteDialog}
        onClose={() => {
          setShowDeleteDialog(false)
          setContentToDelete(null)
        }}
        onConfirm={async () => {
          if (contentToDelete) {
            try {
              await lecturesApi.deleteMedia(contentToDelete)
              setTextContents(textContents.filter(c => c.id !== contentToDelete))
              toast({
                title: 'Content section deleted',
              })
              setShowDeleteDialog(false)
              setContentToDelete(null)
            } catch (error) {
              toast({
                variant: 'destructive',
                title: 'Failed to delete content section',
                description: extractErrorMessage(error),
              })
            }
          }
        }}
        title="Delete Content Section"
        description="Are you sure you want to delete this content section?"
        confirmText="Delete"
        variant="destructive"
      />

      <ConfirmationDialog
        isOpen={showDeleteVideoDialog}
        onClose={() => {
          setShowDeleteVideoDialog(false)
        }}
        onConfirm={handleDeleteVideo}
        title="Delete Video"
        description="Are you sure you want to delete this video? This will remove the video from Azure storage and cannot be undone."
        confirmText="Delete Video"
        variant="destructive"
      />

      {/* Unsaved Changes Bubble */}
      {hasUnsavedChanges && (
        <div className="fixed bottom-4 right-4 bg-amber-500 text-white px-4 py-2 rounded-lg shadow-lg flex items-center gap-2 z-50">
          <span className="text-sm font-medium">You have unsaved changes</span>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleSave}
            disabled={saving || !formData.title.trim()}
            className="text-white hover:bg-amber-600 h-auto py-1"
          >
            {saving && <Loader2 className="h-3 w-3 mr-1 animate-spin" />}
            Save Now
          </Button>
        </div>
      )}
    </>
  )
}


