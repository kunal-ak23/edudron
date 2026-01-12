'use client'

import { useEffect, useState, useRef } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { ProtectedRoute, FileUpload } from '@edudron/ui-components'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { RichTextEditor } from '@/components/RichTextEditor'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ArrowLeft, Loader2, Save, ChevronDown, ChevronRight, BookOpen, Play, Eye, Globe, Archive, Edit, Plus, Trash2, Sparkles } from 'lucide-react'
import { coursesApi, mediaApi, institutesApi, classesApi, sectionsApi, lecturesApi, apiClient } from '@/lib/api'
import type { Course, Institute, Class, Section, CourseSection, Lecture } from '@edudron/shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

// Force dynamic rendering - disable static generation
export const dynamic = 'force-dynamic'

export default function CourseEditPage() {
  const router = useRouter()
  const params = useParams()
  const courseId = params.id as string
  const { toast } = useToast()
  const [course, setCourse] = useState<Partial<Course>>({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [classes, setClasses] = useState<Class[]>([])
  const [sections, setSections] = useState<Section[]>([])
  const [selectedClassIds, setSelectedClassIds] = useState<string[]>([])
  const [selectedSectionIds, setSelectedSectionIds] = useState<string[]>([])
  const [courseSections, setCourseSections] = useState<CourseSection[]>([])
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set())
  const [loadingSections, setLoadingSections] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)
  const initialCourseRef = useRef<string | null>(null)
  const [showCreateLectureDialog, setShowCreateLectureDialog] = useState(false)
  const [showAIGenerateDialog, setShowAIGenerateDialog] = useState(false)
  const [lectureTitle, setLectureTitle] = useState('')
  const [aiPrompt, setAiPrompt] = useState('')
  const [aiDialogType, setAiDialogType] = useState<{ isSubLecture: boolean; sectionId?: string } | null>(null)
  const [showDeleteLectureDialog, setShowDeleteLectureDialog] = useState(false)
  const [showDeleteSubLectureDialog, setShowDeleteSubLectureDialog] = useState(false)
  const [lectureToDelete, setLectureToDelete] = useState<{ sectionId: string; lectureId?: string; title: string } | null>(null)
  const [showUnsavedChangesDialog, setShowUnsavedChangesDialog] = useState(false)
  const [pendingNavigation, setPendingNavigation] = useState<(() => void) | null>(null)

  useEffect(() => {
    loadHierarchyData()
    if (courseId && courseId !== 'new') {
      loadCourse()
    } else {
      setLoading(false)
    }
  }, [courseId])

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

  const loadHierarchyData = async () => {
    try {
      const [institutesData, classesData] = await Promise.all([
        institutesApi.listInstitutes(),
        institutesApi.listInstitutes().then(async (insts) => {
          const allClasses: Class[] = []
          for (const inst of insts) {
            const instClasses = await classesApi.listClassesByInstitute(inst.id)
            allClasses.push(...instClasses)
          }
          return allClasses
        })
      ])
      setInstitutes(institutesData)
      setClasses(classesData)
      
      // Load sections for all classes
      const allSections: Section[] = []
      for (const classItem of classesData) {
        const classSections = await sectionsApi.listSectionsByClass(classItem.id)
        allSections.push(...classSections)
      }
      setSections(allSections)
    } catch (err) {
      console.error('Failed to load hierarchy data:', err)
    }
  }

  const loadCourse = async () => {
    try {
      const data = await coursesApi.getCourse(courseId)
      setCourse(data)
      setSelectedClassIds(data.assignedToClassIds || [])
      setSelectedSectionIds(data.assignedToSectionIds || [])
      await loadCourseSections()
      
      // Store initial state for unsaved changes detection
      initialCourseRef.current = JSON.stringify({
        ...data,
        assignedToClassIds: data.assignedToClassIds || [],
        assignedToSectionIds: data.assignedToSectionIds || []
      })
      setHasUnsavedChanges(false)
    } catch (error) {
      console.error('Failed to load course:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to load course',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoading(false)
    }
  }

  const loadCourseSections = async () => {
    if (!courseId || courseId === 'new') return
    
    try {
      setLoadingSections(true)
      const sections = await coursesApi.getSections(courseId)
      
      // Load sub-lectures for each lecture (section)
      const sectionsWithLectures = await Promise.all(
        sections.map(async (section) => {
          try {
            const lectures = await lecturesApi.getSubLecturesByLecture(courseId, section.id)
            return { ...section, lectures }
          } catch (error) {
            console.error(`Failed to load sub-lectures for lecture ${section.id}:`, error)
            return { ...section, lectures: [] }
          }
        })
      )
      
      setCourseSections(sectionsWithLectures)
      // Expand all sections by default
      setExpandedSections(new Set(sectionsWithLectures.map(s => s.id)))
    } catch (error) {
      console.error('Failed to load course sections:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to load course structure',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoadingSections(false)
    }
  }

  const toggleSection = (sectionId: string) => {
    const newExpanded = new Set(expandedSections)
    if (newExpanded.has(sectionId)) {
      newExpanded.delete(sectionId)
    } else {
      newExpanded.add(sectionId)
    }
    setExpandedSections(newExpanded)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      // Prepare course data, excluding fields that shouldn't be sent in CreateCourseRequest
      const { 
        id, 
        clientId: _clientId, 
        isPublished,
        status,
        totalDurationSeconds, 
        totalLecturesCount, 
        totalStudentsCount, 
        createdAt, 
        updatedAt, 
        publishedAt,
        sections,
        learningObjectives,
        instructors,
        resources,
        ...courseData 
      } = course as any

      const courseToSave = {
        ...courseData,
        assignedToClassIds: selectedClassIds,
        assignedToSectionIds: selectedSectionIds
      }
      
      if (courseId === 'new') {
        await coursesApi.createCourse(courseToSave)
        toast({
          title: 'Course created',
          description: 'The course has been created successfully.',
        })
      } else {
        await coursesApi.updateCourse(courseId, courseToSave)
        toast({
          title: 'Course updated',
          description: 'The course has been updated successfully.',
        })
      }
      setHasUnsavedChanges(false)
      router.push('/courses')
    } catch (error) {
      console.error('Failed to save course:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to save course',
        description: extractErrorMessage(error),
      })
    } finally {
      setSaving(false)
    }
  }

  // Track unsaved changes
  useEffect(() => {
    if (courseId === 'new' || !initialCourseRef.current) return
    
    const currentData = JSON.stringify({
      ...course,
      assignedToClassIds: selectedClassIds,
      assignedToSectionIds: selectedSectionIds
    })
    
    if (currentData !== initialCourseRef.current) {
      setHasUnsavedChanges(true)
    } else {
      setHasUnsavedChanges(false)
    }
  }, [course, selectedClassIds, selectedSectionIds, courseId])

  const handleCreateMainLecture = () => {
    if (!courseId || courseId === 'new') return
    setLectureTitle('')
    setShowCreateLectureDialog(true)
  }

  const handleCreateMainLectureSubmit = async () => {
    if (!lectureTitle.trim()) return
    
    try {
      setLoadingSections(true)
      setShowCreateLectureDialog(false)
      // Create a new section (main lecture) using the API client
      await apiClient.post<any>(
        `/content/courses/${courseId}/lectures`,
        {
          title: lectureTitle.trim(),
          description: ''
        }
      )
      
      await loadCourseSections()
      toast({
        title: 'Lecture created',
        description: 'The lecture has been created successfully.',
      })
      setLectureTitle('')
    } catch (error) {
      console.error('Failed to create lecture:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to create lecture',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoadingSections(false)
    }
  }

  const handleGenerateWithAI = (sectionId?: string, isSubLecture: boolean = false) => {
    if (!courseId || courseId === 'new') return
    setAiPrompt('')
    setAiDialogType({ isSubLecture, sectionId })
    setShowAIGenerateDialog(true)
  }

  const handleAIGenerateSubmit = async () => {
    if (!aiPrompt.trim() || !aiDialogType) return
    
    try {
      setLoadingSections(true)
      setShowAIGenerateDialog(false)
      
      if (aiDialogType.isSubLecture && aiDialogType.sectionId) {
        // Generate sub-lecture with AI - creates a sub-lecture with full AI-generated content
        await apiClient.post<any>(
          `/content/api/sections/${aiDialogType.sectionId}/lectures/generate`,
          {
            prompt: aiPrompt.trim(),
            courseId: courseId
          }
        )
        toast({
          title: 'Sub-lecture generated',
          description: 'The sub-lecture has been generated with AI content successfully.',
        })
      } else {
        // Generate main lecture with AI - this will create a lecture with sub-lectures
        const response = await apiClient.post<any>(
          `/content/courses/${courseId}/lectures/generate`,
          {
            prompt: aiPrompt.trim()
          }
        ) as any
        
        const responseData = (response as any).data || response
        
        toast({
          title: 'Lecture generated',
          description: `Lecture "${responseData?.title || 'Lecture'}" has been generated with ${responseData?.lectures?.length || 0} sub-lectures.`,
        })
      }
      
      await loadCourseSections()
      setAiPrompt('')
      setAiDialogType(null)
    } catch (error) {
      console.error('Failed to generate with AI:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to generate with AI',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoadingSections(false)
    }
  }

  const handleDeleteLecture = (sectionId: string, title: string) => {
    setLectureToDelete({ sectionId, title })
    setShowDeleteLectureDialog(true)
  }

  const handleDeleteLectureConfirm = async () => {
    if (!lectureToDelete || !courseId || courseId === 'new') return
    
    try {
      setLoadingSections(true)
      setShowDeleteLectureDialog(false)
      
      await apiClient.delete(`/content/courses/${courseId}/lectures/${lectureToDelete.sectionId}`)
      
      await loadCourseSections()
      toast({
        title: 'Lecture deleted',
        description: 'The lecture has been deleted successfully.',
      })
      setLectureToDelete(null)
    } catch (error) {
      console.error('Failed to delete lecture:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to delete lecture',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoadingSections(false)
    }
  }

  const handleDeleteSubLecture = (sectionId: string, lectureId: string, title: string) => {
    setLectureToDelete({ sectionId, lectureId, title })
    setShowDeleteSubLectureDialog(true)
  }

  const handleDeleteSubLectureConfirm = async () => {
    if (!lectureToDelete || !lectureToDelete.lectureId || !courseId || courseId === 'new') return
    
    try {
      setLoadingSections(true)
      setShowDeleteSubLectureDialog(false)
      
      await lecturesApi.deleteSubLecture(courseId, lectureToDelete.sectionId, lectureToDelete.lectureId)
      
      await loadCourseSections()
      toast({
        title: 'Sub-lecture deleted',
        description: 'The sub-lecture has been deleted successfully.',
      })
      setLectureToDelete(null)
    } catch (error) {
      console.error('Failed to delete sub-lecture:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to delete sub-lecture',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoadingSections(false)
    }
  }

  const handleEditLecture = (sectionId: string, lecture?: Lecture, isMain?: boolean) => {
    if (hasUnsavedChanges) {
      setPendingNavigation(() => () => {
        // Navigation will happen after dialog confirmation
        if (isMain) {
          router.push(`/courses/${courseId}/lectures/${sectionId}/edit`)
        } else if (lecture) {
          router.push(`/courses/${courseId}/lectures/${sectionId}/sub-lectures/${lecture.id}/edit`)
        }
      })
      setShowUnsavedChangesDialog(true)
      return
    }
    
    if (isMain) {
      router.push(`/courses/${courseId}/lectures/${sectionId}/edit`)
    } else if (lecture) {
      // For sub-lectures, we'll use the same route but pass the sub-lecture ID as a query param
      // Or we can use the lectureId as the sub-lecture ID
      router.push(`/courses/${courseId}/lectures/${sectionId}/edit?subLectureId=${lecture.id}`)
    } else {
      // Create new sub-lecture - navigate to main lecture edit page
      router.push(`/courses/${courseId}/lectures/${sectionId}/edit`)
    }
  }

  const handlePublish = async () => {
    if (!courseId || courseId === 'new') return
    setPublishing(true)
    try {
      const updatedCourse = await coursesApi.publishCourse(courseId)
      if (updatedCourse) {
        setCourse(updatedCourse)
        // Update initial course ref to reflect published state
        initialCourseRef.current = JSON.stringify({
          ...updatedCourse,
          assignedToClassIds: selectedClassIds,
          assignedToSectionIds: selectedSectionIds
        })
        setHasUnsavedChanges(false)
      }
      toast({
        title: 'Course published',
        description: 'The course has been published and is now visible to students.',
      })
    } catch (error) {
      console.error('Failed to publish course:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to publish course',
        description: extractErrorMessage(error),
      })
    } finally {
      setPublishing(false)
    }
  }

  const handleUnpublish = async () => {
    if (!courseId || courseId === 'new') return
    setPublishing(true)
    try {
      const updatedCourse = await coursesApi.unpublishCourse(courseId)
      if (updatedCourse) {
        setCourse(updatedCourse)
        // Update initial course ref to reflect unpublished state
        initialCourseRef.current = JSON.stringify({
          ...updatedCourse,
          assignedToClassIds: selectedClassIds,
          assignedToSectionIds: selectedSectionIds
        })
        setHasUnsavedChanges(false)
      }
      toast({
        title: 'Course unpublished',
        description: 'The course has been unpublished and is no longer visible to students.',
      })
    } catch (error) {
      console.error('Failed to unpublish course:', error)
      toast({
        variant: 'destructive',
        title: 'Failed to unpublish course',
        description: extractErrorMessage(error),
      })
    } finally {
      setPublishing(false)
    }
  }

  const handlePreview = () => {
    if (!courseId || courseId === 'new') return
    // Open student portal course view in a new tab
    // Student portal runs on port 3001 by default
    const studentPortalUrl = typeof window !== 'undefined' 
      ? (window.location.origin.includes('localhost') 
          ? 'http://localhost:3001' 
          : window.location.origin.replace('admin', 'student').replace('dashboard', 'portal'))
      : 'http://localhost:3001'
    window.open(`${studentPortalUrl}/courses/${courseId}`, '_blank')
  }

  const getClassDisplayName = (classId: string) => {
    const classItem = classes.find(c => c.id === classId)
    if (!classItem) return classId
    const institute = institutes.find(i => i.id === classItem.instituteId)
    return institute ? `${institute.name} - ${classItem.name}` : classItem.name
  }

  const getSectionDisplayName = (sectionId: string) => {
    const section = sections.find(s => s.id === sectionId)
    if (!section) return sectionId
    const classItem = classes.find(c => c.id === section.classId)
    if (!classItem) return section.name
    const institute = institutes.find(i => i.id === classItem.instituteId)
    return institute ? `${institute.name} - ${classItem.name} - ${section.name}` : `${classItem.name} - ${section.name}`
  }

  return (
    <ProtectedRoute requiredRoles={['SYSTEM_ADMIN', 'TENANT_ADMIN', 'CONTENT_MANAGER']}>
      <div className="min-h-screen bg-gray-50">
        {/* Header */}
        <header className="bg-white shadow-sm border-b border-gray-200 sticky top-0 z-40">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-16">
              <div className="flex items-center space-x-4">
                <Button
                  variant="ghost"
                  onClick={() => router.push('/courses')}
                >
                  <ArrowLeft className="h-4 w-4 mr-2" />
                  Back to Courses
                </Button>
                <h1 className="text-xl font-bold text-gray-900">
                  {courseId === 'new' ? 'Create Course' : 'Edit Course'}
                </h1>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  onClick={() => router.push('/courses')}
                  disabled={saving || publishing}
                >
                  Cancel
                </Button>
                <Button onClick={handleSave} disabled={saving || publishing}>
                  {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                  {courseId === 'new' ? 'Create Course' : 'Save Changes'}
                </Button>
              </div>
            </div>
          </div>
        </header>

        <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <Card>
            <CardHeader>
              <CardTitle>{courseId === 'new' ? 'Create Course' : 'Edit Course'}</CardTitle>
            </CardHeader>
            <CardContent>
              {loading ? (
                <div className="text-center py-12">
                  <Loader2 className="h-8 w-8 animate-spin text-primary mx-auto" />
                </div>
              ) : (
                <div className="space-y-6">
                  {/* Basic Information */}
                  <div>
                    <h2 className="text-lg font-semibold mb-4">Basic Information</h2>
                    <div className="space-y-4">
                      <div className="space-y-2">
                        <Label>
                          Course Title <span className="text-destructive">*</span>
                        </Label>
                        <Input
                          value={course?.title || ''}
                          onChange={(e) => setCourse({ ...course, title: e.target.value })}
                          required
                          placeholder="Enter course title"
                        />
                      </div>
                      <div className="space-y-2">
                        <Label>Description</Label>
                        <RichTextEditor
                          content={course?.description || ''}
                          onChange={(content) => setCourse({ ...course, description: content })}
                          placeholder="Enter course description (supports rich text formatting)"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Pricing */}
                  <div className="border-t pt-6">
                    <h2 className="text-lg font-semibold mb-4">Pricing</h2>
                    <div className="space-y-4">
                      <div className="flex items-center space-x-2">
                        <Checkbox
                          id="isFree"
                          checked={course?.isFree || false}
                          onCheckedChange={(checked) => setCourse({ ...course, isFree: checked as boolean })}
                        />
                        <Label htmlFor="isFree" className="font-normal cursor-pointer">
                          Free Course
                        </Label>
                      </div>
                      {!course?.isFree && (
                        <div className="space-y-2">
                          <Label>Price (₹)</Label>
                          <Input
                            type="number"
                            value={course?.pricePaise ? (course.pricePaise / 100).toString() : ''}
                            onChange={(e) =>
                              setCourse({
                                ...course,
                                pricePaise: parseFloat(e.target.value) * 100 || 0
                              })
                            }
                            placeholder="0.00"
                          />
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Course Settings */}
                  <div className="border-t pt-6">
                    <h2 className="text-lg font-semibold mb-4">Course Settings</h2>
                    <div className="space-y-4">
                      <div className="space-y-2">
                        <Label>Difficulty Level</Label>
                        <Select
                          value={course?.difficultyLevel || undefined}
                          onValueChange={(value) => setCourse({ ...course, difficultyLevel: value === 'none' ? undefined : value as any })}
                        >
                          <SelectTrigger>
                            <SelectValue placeholder="Select difficulty" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="none">None</SelectItem>
                            <SelectItem value="BEGINNER">Beginner</SelectItem>
                            <SelectItem value="INTERMEDIATE">Intermediate</SelectItem>
                            <SelectItem value="ADVANCED">Advanced</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="space-y-2">
                        <Label>Status</Label>
                        <Select
                          value={course?.isPublished ? 'PUBLISHED' : 'DRAFT'}
                          onValueChange={(value) =>
                            setCourse({
                              ...course,
                              isPublished: value === 'PUBLISHED'
                            })
                          }
                        >
                          <SelectTrigger>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="DRAFT">Draft</SelectItem>
                            <SelectItem value="PUBLISHED">Published</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="flex items-center space-x-2">
                        <Checkbox
                          id="certificateEligible"
                          checked={course?.certificateEligible || false}
                          onCheckedChange={(checked) =>
                            setCourse({ ...course, certificateEligible: checked as boolean })
                          }
                        />
                        <Label htmlFor="certificateEligible" className="font-normal cursor-pointer">
                          Certificate Eligible
                        </Label>
                      </div>
                    </div>
                  </div>

                  {/* Class/Section Assignments */}
                  <div className="border-t pt-6">
                    <h2 className="text-lg font-semibold mb-4">Assign to Classes/Sections</h2>
                    <div className="space-y-4">
                      <div className="space-y-2">
                        <Label>Assign to Classes</Label>
                        <div className="space-y-2 max-h-40 overflow-y-auto border rounded-md p-2">
                          {classes.length === 0 ? (
                            <p className="text-sm text-gray-500">No classes available. Create classes first.</p>
                          ) : (
                            classes.map((classItem) => {
                              const institute = institutes.find(i => i.id === classItem.instituteId)
                              return (
                                <div key={classItem.id} className="flex items-center space-x-2">
                                  <Checkbox
                                    id={`class-${classItem.id}`}
                                    checked={selectedClassIds.includes(classItem.id)}
                                    onCheckedChange={(checked) => {
                                      if (checked) {
                                        setSelectedClassIds([...selectedClassIds, classItem.id])
                                      } else {
                                        setSelectedClassIds(selectedClassIds.filter(id => id !== classItem.id))
                                        // Remove sections from this class when class is deselected
                                        const classSections = sections.filter(s => s.classId === classItem.id)
                                        setSelectedSectionIds(selectedSectionIds.filter(id => 
                                          !classSections.some(cs => cs.id === id)
                                        ))
                                      }
                                    }}
                                  />
                                  <Label 
                                    htmlFor={`class-${classItem.id}`} 
                                    className="font-normal cursor-pointer flex-1"
                                  >
                                    {institute ? `${institute.name} - ${classItem.name}` : classItem.name}
                                  </Label>
                                </div>
                              )
                            })
                          )}
                        </div>
                      </div>
                      <div className="space-y-2">
                        <Label>Assign to Sections</Label>
                        <div className="space-y-2 max-h-40 overflow-y-auto border rounded-md p-2">
                          {sections.length === 0 ? (
                            <p className="text-sm text-gray-500">No sections available. Create sections first.</p>
                          ) : (
                            sections
                              .filter(s => selectedClassIds.length === 0 || selectedClassIds.includes(s.classId))
                              .map((section) => {
                                const classItem = classes.find(c => c.id === section.classId)
                                const institute = classItem ? institutes.find(i => i.id === classItem.instituteId) : null
                                return (
                                  <div key={section.id} className="flex items-center space-x-2">
                                    <Checkbox
                                      id={`section-${section.id}`}
                                      checked={selectedSectionIds.includes(section.id)}
                                      disabled={selectedClassIds.length > 0 && !selectedClassIds.includes(section.classId)}
                                      onCheckedChange={(checked) => {
                                        if (checked) {
                                          setSelectedSectionIds([...selectedSectionIds, section.id])
                                        } else {
                                          setSelectedSectionIds(selectedSectionIds.filter(id => id !== section.id))
                                        }
                                      }}
                                    />
                                    <Label 
                                      htmlFor={`section-${section.id}`} 
                                      className="font-normal cursor-pointer flex-1"
                                    >
                                      {institute && classItem 
                                        ? `${institute.name} - ${classItem.name} - ${section.name}`
                                        : section.name}
                                    </Label>
                                  </div>
                                )
                              })
                          )}
                        </div>
                        <p className="text-xs text-gray-500">
                          Sections are filtered by selected classes. Select classes first to see their sections.
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Course Structure - Lectures */}
                  {courseId !== 'new' && (
                    <div className="border-t pt-6">
                      <div className="flex items-center justify-between mb-4">
                        <h2 className="text-lg font-semibold">Course Structure</h2>
                        <div className="flex gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={handleCreateMainLecture}
                            disabled={loadingSections}
                          >
                            <Plus className="h-4 w-4 mr-2" />
                            Add Lecture
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleGenerateWithAI(undefined, false)}
                            disabled={loadingSections}
                          >
                            <Sparkles className="h-4 w-4 mr-2" />
                            Generate with AI
                          </Button>
                        </div>
                      </div>
                      {loadingSections ? (
                        <div className="flex items-center justify-center py-8">
                          <Loader2 className="h-6 w-6 animate-spin text-primary" />
                          <span className="ml-2 text-sm text-gray-600">Loading course structure...</span>
                        </div>
                      ) : courseSections.length === 0 ? (
                        <div className="text-center py-8 text-gray-500">
                          <BookOpen className="h-12 w-12 mx-auto mb-2 text-gray-400" />
                          <p className="text-sm mb-4">No lectures found. Create a new lecture or generate with AI.</p>
                          <div className="flex gap-2 justify-center">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={handleCreateMainLecture}
                            >
                              <Plus className="h-4 w-4 mr-2" />
                              Add Lecture
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleGenerateWithAI(undefined, false)}
                            >
                              <Sparkles className="h-4 w-4 mr-2" />
                              Generate with AI
                            </Button>
                          </div>
                        </div>
                      ) : (
                        <div className="space-y-2">
                          {courseSections.map((section) => (
                            <div key={section.id} className="border rounded-lg">
                              <button
                                onClick={() => toggleSection(section.id)}
                                className="w-full flex items-center justify-between p-4 hover:bg-gray-50 transition-colors"
                              >
                                <div className="flex items-center flex-1 text-left">
                                  {expandedSections.has(section.id) ? (
                                    <ChevronDown className="h-4 w-4 mr-2 text-gray-500" />
                                  ) : (
                                    <ChevronRight className="h-4 w-4 mr-2 text-gray-500" />
                                  )}
                                  <BookOpen className="h-4 w-4 mr-2 text-blue-600" />
                                  <div>
                                    <div className="font-medium text-gray-900">
                                      Lecture {section.sequence}: {section.title}
                                    </div>
                                    {section.description && (
                                      <div className="text-sm text-gray-500 mt-1 line-clamp-1">
                                        {section.description}
                                      </div>
                                    )}
                                  </div>
                                </div>
                                <div className="flex items-center gap-4 text-sm text-gray-500">
                                  <span>{section.lectures?.length || 0} sub-lectures</span>
                                  <Badge variant={section.isPublished ? 'default' : 'secondary'}>
                                    {section.isPublished ? 'Published' : 'Draft'}
                                  </Badge>
                                  <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={(e) => {
                                      e.stopPropagation()
                                      handleEditLecture(section.id, undefined, true)
                                    }}
                                    className="h-8 w-8 p-0"
                                  >
                                    <Edit className="h-4 w-4" />
                                  </Button>
                                  <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={(e) => {
                                      e.stopPropagation()
                                      handleDeleteLecture(section.id, section.title)
                                    }}
                                    className="h-8 w-8 p-0 text-destructive hover:text-destructive hover:bg-destructive/10"
                                  >
                                    <Trash2 className="h-4 w-4" />
                                  </Button>
                                </div>
                              </button>
                              {expandedSections.has(section.id) && section.lectures && section.lectures.length > 0 && (
                                <div className="border-t bg-gray-50">
                                  <div className="p-4 space-y-2">
                                    {section.lectures.map((lecture, idx) => (
                                      <div
                                        key={lecture.id}
                                        className="flex items-center justify-between p-3 bg-white rounded border border-gray-200 hover:border-gray-300 transition-colors"
                                      >
                                        <div className="flex items-center flex-1">
                                          <Play className="h-4 w-4 mr-3 text-gray-400" />
                                          <div>
                                            <div className="font-medium text-sm text-gray-900">
                                              {idx + 1}. {lecture.title}
                                            </div>
                                            {lecture.description && (
                                              <div className="text-xs text-gray-500 mt-1 line-clamp-1">
                                                {lecture.description}
                                              </div>
                                            )}
                                          </div>
                                        </div>
                                        <div className="flex items-center gap-3">
                                          <div className="flex items-center gap-2 text-xs text-gray-500">
                                            {lecture.duration && (
                                              <span>{Math.floor(lecture.duration / 60)}m</span>
                                            )}
                                            <Badge variant="outline" className="text-xs">
                                              {lecture.contentType}
                                            </Badge>
                                          </div>
                                          <Button
                                            variant="ghost"
                                            size="sm"
                                            onClick={() => handleEditLecture(section.id, lecture)}
                                            className="h-8 w-8 p-0"
                                          >
                                            <Edit className="h-4 w-4" />
                                          </Button>
                                          <Button
                                            variant="ghost"
                                            size="sm"
                                            onClick={() => handleDeleteSubLecture(section.id, lecture.id, lecture.title)}
                                            className="h-8 w-8 p-0 text-destructive hover:text-destructive hover:bg-destructive/10"
                                          >
                                            <Trash2 className="h-4 w-4" />
                                          </Button>
                                        </div>
                                      </div>
                                    ))}
                                    {/* Add Sub-Lecture Buttons */}
                                    <div className="flex gap-2 mt-2">
                                      <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => handleEditLecture(section.id)}
                                        className="flex-1"
                                      >
                                        <Plus className="h-4 w-4 mr-2" />
                                        Add Sub-Lecture
                                      </Button>
                                      <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => handleGenerateWithAI(section.id, true)}
                                        className="flex-1"
                                      >
                                        <Sparkles className="h-4 w-4 mr-2" />
                                        Generate with AI
                                      </Button>
                                    </div>
                                  </div>
                                </div>
                              )}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}

                  {/* Media */}
                  <div className="border-t pt-6">
                    <h2 className="text-lg font-semibold mb-4">Media</h2>
                    <div className="space-y-4">
                      <FileUpload
                        label="Thumbnail Image"
                        accept="image/*"
                        maxSize={10 * 1024 * 1024} // 10MB
                        value={course?.thumbnailUrl || ''}
                        onChange={(url) => setCourse({ ...course, thumbnailUrl: url })}
                        onUpload={async (file) => await mediaApi.uploadImage(file, 'thumbnails')}
                        helperText="Upload a thumbnail image for the course (PNG, JPG, GIF up to 10MB)"
                      />
                      <FileUpload
                        label="Preview Video"
                        accept="video/*"
                        maxSize={500 * 1024 * 1024} // 500MB
                        value={course?.previewVideoUrl || ''}
                        onChange={(url) => setCourse({ ...course, previewVideoUrl: url })}
                        onUpload={async (file) => await mediaApi.uploadVideo(file, 'preview-videos')}
                        helperText="Upload a preview video for the course (MP4, MOV, etc. up to 500MB)"
                      />
                    </div>
                  </div>

                  {/* Course Actions */}
                  {courseId !== 'new' && (
                    <div className="border-t pt-6">
                      <h2 className="text-lg font-semibold mb-4">Course Actions</h2>
                      <div className="flex items-center gap-4">
                        <Badge variant={course?.isPublished ? 'default' : 'secondary'} className="text-sm">
                          {course?.isPublished ? 'Published' : 'Draft'}
                        </Badge>
                        <div className="flex gap-2">
                          <Button
                            variant="outline"
                            onClick={handlePreview}
                          >
                            <Eye className="h-4 w-4 mr-2" />
                            Preview as Student
                          </Button>
                          {course?.isPublished ? (
                            <Button
                              variant="outline"
                              onClick={handleUnpublish}
                              disabled={publishing}
                            >
                              {publishing && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                              <Archive className="h-4 w-4 mr-2" />
                              Unpublish
                            </Button>
                          ) : (
                            <Button
                              onClick={handlePublish}
                              disabled={publishing}
                            >
                              {publishing && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                              <Globe className="h-4 w-4 mr-2" />
                              Publish Course
                            </Button>
                          )}
                        </div>
                      </div>
                      {!course?.isPublished && (
                        <p className="text-sm text-gray-500 mt-2">
                          Publish the course to make it visible to students. You can preview it once published.
                        </p>
                      )}
                    </div>
                  )}

                  {/* Actions */}
                  <div className="border-t pt-6 flex justify-end space-x-4">
                    <Button
                      variant="outline"
                      onClick={() => router.push('/courses')}
                      disabled={saving || publishing}
                    >
                      Cancel
                    </Button>
                    <Button onClick={handleSave} disabled={saving || publishing}>
                      {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                      {courseId === 'new' ? 'Create Course' : 'Save Changes'}
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </main>

        {/* Unsaved Changes Indicator */}
        {hasUnsavedChanges && (
          <div className="fixed bottom-4 right-4 bg-amber-500 text-white px-4 py-2 rounded-lg shadow-lg flex items-center gap-2 z-50">
            <span className="text-sm font-medium">You have unsaved changes</span>
            <Button
              variant="ghost"
              size="sm"
              onClick={handleSave}
              className="text-white hover:bg-amber-600 h-auto py-1"
            >
              Save Now
            </Button>
          </div>
        )}
      </div>

      {/* Create Lecture Dialog */}
      <Dialog open={showCreateLectureDialog} onOpenChange={setShowCreateLectureDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create New Lecture</DialogTitle>
            <DialogDescription>
              Enter a title for the new lecture (module).
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="lecture-title">Lecture Title</Label>
              <Input
                id="lecture-title"
                value={lectureTitle}
                onChange={(e) => setLectureTitle(e.target.value)}
                placeholder="e.g., Introduction to Python"
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && lectureTitle.trim()) {
                    handleCreateMainLectureSubmit()
                  }
                }}
                autoFocus
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowCreateLectureDialog(false)
                setLectureTitle('')
              }}
            >
              Cancel
            </Button>
            <Button
              onClick={handleCreateMainLectureSubmit}
              disabled={!lectureTitle.trim() || loadingSections}
            >
              {loadingSections && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              Create Lecture
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* AI Generate Dialog */}
      <Dialog open={showAIGenerateDialog} onOpenChange={setShowAIGenerateDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              <Sparkles className="h-5 w-5 inline mr-2" />
              Generate with AI
            </DialogTitle>
            <DialogDescription>
              {aiDialogType?.isSubLecture
                ? 'Enter a prompt to generate sub-lecture content (e.g., "Introduction to Python variables")'
                : 'Enter a prompt to generate lecture content (e.g., "Python Basics Module with 5 lessons")'}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="ai-prompt">Prompt</Label>
              <Textarea
                id="ai-prompt"
                value={aiPrompt}
                onChange={(e) => setAiPrompt(e.target.value)}
                placeholder={
                  aiDialogType?.isSubLecture
                    ? 'e.g., Introduction to Python variables'
                    : 'e.g., Python Basics Module with 5 lessons'
                }
                rows={4}
                autoFocus
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowAIGenerateDialog(false)
                setAiPrompt('')
                setAiDialogType(null)
              }}
            >
              Cancel
            </Button>
            <Button
              onClick={handleAIGenerateSubmit}
              disabled={!aiPrompt.trim() || loadingSections}
            >
              {loadingSections && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              <Sparkles className="h-4 w-4 mr-2" />
              Generate
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Lecture Dialog */}
      <Dialog open={showDeleteLectureDialog} onOpenChange={setShowDeleteLectureDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Lecture</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete the lecture &quot;{lectureToDelete?.title}&quot;? This will also delete all sub-lectures within it. This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowDeleteLectureDialog(false)
                setLectureToDelete(null)
              }}
              disabled={loadingSections}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteLectureConfirm}
              disabled={loadingSections}
            >
              {loadingSections && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              Delete Lecture
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Sub-Lecture Dialog */}
      <Dialog open={showDeleteSubLectureDialog} onOpenChange={setShowDeleteSubLectureDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Sub-Lecture</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete the sub-lecture &quot;{lectureToDelete?.title}&quot;? This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowDeleteSubLectureDialog(false)
                setLectureToDelete(null)
              }}
              disabled={loadingSections}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteSubLectureConfirm}
              disabled={loadingSections}
            >
              {loadingSections && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              Delete Sub-Lecture
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Unsaved Changes Dialog */}
      <Dialog open={showUnsavedChangesDialog} onOpenChange={setShowUnsavedChangesDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Unsaved Changes</DialogTitle>
            <DialogDescription>
              You have unsaved changes on this page. Are you sure you want to leave? Your changes will be lost.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowUnsavedChangesDialog(false)
                setPendingNavigation(null)
              }}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                setHasUnsavedChanges(false)
                setShowUnsavedChangesDialog(false)
                if (pendingNavigation) {
                  pendingNavigation()
                  setPendingNavigation(null)
                }
              }}
            >
              Leave Without Saving
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </ProtectedRoute>
  )
}
