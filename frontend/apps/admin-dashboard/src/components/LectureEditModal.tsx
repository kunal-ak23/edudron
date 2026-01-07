'use client'

import React, { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { RichTextEditor } from '@/components/RichTextEditor'
import { FileUpload } from '@edudron/ui-components'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Loader2, X, Video, FileText, Upload } from 'lucide-react'
import { lecturesApi, mediaApi } from '@/lib/api'
import type { Lecture, CreateLectureRequest, UpdateLectureRequest } from '@edudron/shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

interface LectureEditModalProps {
  isOpen: boolean
  onClose: () => void
  courseId: string
  lectureId: string // Main lecture (section) ID
  lecture?: Lecture // Sub-lecture to edit (if editing sub-lecture)
  isMainLecture?: boolean // If true, editing main lecture (section), else sub-lecture
  onSave: () => void
}

export function LectureEditModal({
  isOpen,
  onClose,
  courseId,
  lectureId,
  lecture,
  isMainLecture = false,
  onSave
}: LectureEditModalProps) {
  const { toast } = useToast()
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    contentType: 'TEXT' as 'VIDEO' | 'TEXT' | 'AUDIO' | 'DOCUMENT',
    contentUrl: '',
    durationSeconds: 0,
    isPublished: false
  })

  useEffect(() => {
    if (isOpen) {
      if (lecture) {
        setFormData({
          title: lecture.title || '',
          description: lecture.description || '',
          contentType: (lecture.contentType as any) || 'TEXT',
          contentUrl: lecture.contentUrl || '',
          durationSeconds: lecture.duration || 0,
          isPublished: lecture.isPublished || false
        })
      } else if (isMainLecture) {
        // Load main lecture data
        loadMainLecture()
      } else {
        // New sub-lecture
        setFormData({
          title: '',
          description: '',
          contentType: 'TEXT',
          contentUrl: '',
          durationSeconds: 0,
          isPublished: false
        })
      }
    }
  }, [isOpen, lecture, isMainLecture, courseId, lectureId])

  const loadMainLecture = async () => {
    setLoading(true)
    try {
      const data = await lecturesApi.getLecture(courseId, lectureId)
      setFormData({
        title: data.title || '',
        description: data.description || '',
        contentType: 'TEXT', // Main lectures don't have content type
        contentUrl: '',
        durationSeconds: 0,
        isPublished: data.isPublished || false
      })
    } catch (error) {
      console.error('Failed to load lecture:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to load lecture',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoading(false)
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
      } else if (lecture) {
        // Update existing sub-lecture
        await lecturesApi.updateSubLecture(courseId, lectureId, lecture.id, {
          title: formData.title,
          description: formData.description,
          contentType: formData.contentType,
          durationSeconds: formData.durationSeconds,
          isPublished: formData.isPublished
        })
      } else {
        // Create new sub-lecture
        const savedLecture = await lecturesApi.createSubLecture(courseId, lectureId, {
          title: formData.title,
          description: formData.description,
          contentType: formData.contentType,
          durationSeconds: formData.durationSeconds
        })
        
        // Update local state with saved lecture so media can be uploaded
        // Note: We'll need to pass this back to parent or reload
        console.log('Created lecture:', savedLecture)
      }
      
      toast({
        title: 'Lecture saved',
        description: lecture 
          ? 'The lecture has been saved successfully.' 
          : 'The lecture has been created. You can now upload media files.',
      })
      onSave() // This will reload the course sections
      // Don't close immediately for new lectures - allow media upload
      if (lecture) {
        onClose()
      }
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

  const handleVideoUpload = async (file: File) => {
    try {
      // For new lectures, we need to create the lecture first, then upload
      // For existing lectures, upload directly
      if (lecture?.id) {
        const content = await lecturesApi.uploadVideo(lecture.id, file)
        const url = content.videoUrl || content.fileUrl || ''
        setFormData({ ...formData, contentUrl: url })
        return url
      } else {
        // For new lectures, we'll upload after creation
        // Store the file temporarily or show a message
        throw new Error('Please save the lecture first, then upload the video')
      }
    } catch (error: any) {
      throw new Error(error.message || 'Failed to upload video')
    }
  }

  const handleAudioUpload = async (file: File) => {
    try {
      if (lecture?.id) {
        const content = await lecturesApi.uploadAudio(lecture.id, file)
        const url = content.fileUrl || ''
        setFormData({ ...formData, contentUrl: url })
        return url
      } else {
        throw new Error('Please save the lecture first, then upload the audio')
      }
    } catch (error: any) {
      throw new Error(error.message || 'Failed to upload audio')
    }
  }

  const handleAttachmentUpload = async (files: File[]) => {
    try {
      if (lecture?.id) {
        await lecturesApi.uploadAttachments(lecture.id, files)
        toast({
          title: 'Attachments uploaded',
          description: `${files.length} file(s) uploaded successfully.`,
        })
        return []
      } else {
        throw new Error('Please save the lecture first, then upload attachments')
      }
    } catch (error: any) {
      throw new Error(error.message || 'Failed to upload attachments')
    }
  }

  if (!isOpen) return null

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {isMainLecture ? 'Edit Lecture' : lecture ? 'Edit Sub-Lecture' : 'Create Sub-Lecture'}
          </DialogTitle>
        </DialogHeader>

        {loading ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="h-6 w-6 animate-spin text-primary" />
          </div>
        ) : (
          <div className="space-y-6 py-4">
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
              <RichTextEditor
                content={formData.description}
                onChange={(content) => setFormData({ ...formData, description: content })}
                placeholder="Enter lecture description (supports rich text)"
              />
            </div>

            {/* Content Type and Media (only for sub-lectures) */}
            {!isMainLecture && (
              <>
                <div className="space-y-2">
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

                {/* Video Upload */}
                {formData.contentType === 'VIDEO' && (
                  <div className="space-y-2">
                    <Label>Video File {!lecture && <span className="text-xs text-gray-500">(Upload after saving lecture)</span>}</Label>
                    {lecture ? (
                      <FileUpload
                        accept="video/*"
                        maxSize={2 * 1024 * 1024 * 1024} // 2GB
                        value={formData.contentUrl}
                        onChange={(url) => setFormData({ ...formData, contentUrl: url })}
                        onUpload={handleVideoUpload}
                        helperText="Upload a video file (MP4, MOV, etc. up to 2GB). Replaces existing video."
                      />
                    ) : (
                      <div className="p-4 border border-gray-300 rounded-md bg-gray-50">
                        <p className="text-sm text-gray-600">
                          Save the lecture first, then you can upload a video file.
                        </p>
                      </div>
                    )}
                    {formData.contentUrl && (
                      <div className="mt-2">
                        <video src={formData.contentUrl} controls className="w-full max-h-64 rounded" />
                      </div>
                    )}
                  </div>
                )}

                {/* Document Upload */}
                {formData.contentType === 'DOCUMENT' && (
                  <div className="space-y-2">
                    <Label>Document File</Label>
                    <FileUpload
                      accept=".pdf,.doc,.docx,.txt"
                      maxSize={50 * 1024 * 1024} // 50MB
                      value={formData.contentUrl}
                      onChange={(url) => setFormData({ ...formData, contentUrl: url })}
                      onUpload={async (file) => {
                        // For documents, we can use image upload endpoint or create a document-specific one
                        // For now, let's use a generic upload
                        const formData = new FormData()
                        formData.append('file', file)
                        formData.append('folder', 'lectures')
                        // This would need a document upload endpoint
                        return formData.contentUrl
                      }}
                      helperText="Upload a document file (PDF, DOC, DOCX, TXT up to 50MB)"
                    />
                  </div>
                )}

                {/* Audio Upload */}
                {formData.contentType === 'AUDIO' && (
                  <div className="space-y-2">
                    <Label>Audio File {!lecture && <span className="text-xs text-gray-500">(Upload after saving lecture)</span>}</Label>
                    {lecture ? (
                      <FileUpload
                        accept="audio/*"
                        maxSize={100 * 1024 * 1024} // 100MB
                        value={formData.contentUrl}
                        onChange={(url) => setFormData({ ...formData, contentUrl: url })}
                        onUpload={handleAudioUpload}
                        helperText="Upload an audio file (MP3, WAV, etc. up to 100MB). Replaces existing audio."
                      />
                    ) : (
                      <div className="p-4 border border-gray-300 rounded-md bg-gray-50">
                        <p className="text-sm text-gray-600">
                          Save the lecture first, then you can upload an audio file.
                        </p>
                      </div>
                    )}
                  </div>
                )}

                {/* Attachments (Multiple Files) */}
                <div className="space-y-2">
                  <Label>
                    Attachments (Multiple Files)
                    {!lecture && <span className="text-xs text-gray-500 ml-2">(Upload after saving lecture)</span>}
                  </Label>
                  {lecture ? (
                    <>
                      <input
                        type="file"
                        multiple
                        accept=".pdf,.doc,.docx,.txt,.jpg,.jpeg,.png,.gif,.zip,.rar"
                        onChange={async (e) => {
                          const files = Array.from(e.target.files || [])
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
                          // Reset input
                          e.target.value = ''
                        }}
                        className="w-full p-2 border border-gray-300 rounded-md"
                        disabled={saving}
                      />
                      <p className="text-xs text-gray-500">
                        Upload multiple files (PDF, DOC, images, etc. up to 100MB each)
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

                {/* Duration */}
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

                {/* Published Status */}
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
        )}

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={saving || !formData.title.trim()}>
            {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
            {lecture ? 'Save Changes' : 'Create Lecture'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

