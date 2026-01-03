'use client'

import { useEffect, useState } from 'react'
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
import { ArrowLeft, Loader2, Save } from 'lucide-react'
import { coursesApi, mediaApi, institutesApi, classesApi, sectionsApi } from '@/lib/api'
import type { Course, Institute, Class, Section } from '@edudron/shared-utils'
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

  useEffect(() => {
    loadHierarchyData()
    if (courseId && courseId !== 'new') {
      loadCourse()
    } else {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId])

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

  const handleSave = async () => {
    setSaving(true)
    try {
      const courseToSave = {
        ...course,
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
        <header className="bg-white shadow-sm border-b border-gray-200">
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
                          value={course.title || ''}
                          onChange={(e) => setCourse({ ...course, title: e.target.value })}
                          required
                          placeholder="Enter course title"
                        />
                      </div>
                      <div className="space-y-2">
                        <Label>Description</Label>
                        <RichTextEditor
                          content={course.description || ''}
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
                          checked={course.isFree || false}
                          onCheckedChange={(checked) => setCourse({ ...course, isFree: checked as boolean })}
                        />
                        <Label htmlFor="isFree" className="font-normal cursor-pointer">
                          Free Course
                        </Label>
                      </div>
                      {!course.isFree && (
                        <div className="space-y-2">
                          <Label>Price (₹)</Label>
                          <Input
                            type="number"
                            value={course.pricePaise ? (course.pricePaise / 100).toString() : ''}
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
                          value={course.difficultyLevel || ''}
                          onValueChange={(value) => setCourse({ ...course, difficultyLevel: value as any })}
                        >
                          <SelectTrigger>
                            <SelectValue placeholder="Select difficulty" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="">Select difficulty</SelectItem>
                            <SelectItem value="BEGINNER">Beginner</SelectItem>
                            <SelectItem value="INTERMEDIATE">Intermediate</SelectItem>
                            <SelectItem value="ADVANCED">Advanced</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="space-y-2">
                        <Label>Status</Label>
                        <Select
                          value={course.isPublished ? 'PUBLISHED' : 'DRAFT'}
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
                          checked={course.certificateEligible || false}
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

                  {/* Media */}
                  <div className="border-t pt-6">
                    <h2 className="text-lg font-semibold mb-4">Media</h2>
                    <div className="space-y-4">
                      <FileUpload
                        label="Thumbnail Image"
                        accept="image/*"
                        maxSize={10 * 1024 * 1024} // 10MB
                        value={course.thumbnailUrl}
                        onChange={(url) => setCourse({ ...course, thumbnailUrl: url })}
                        onUpload={async (file) => await mediaApi.uploadImage(file, 'thumbnails')}
                        helperText="Upload a thumbnail image for the course (PNG, JPG, GIF up to 10MB)"
                      />
                      <FileUpload
                        label="Preview Video"
                        accept="video/*"
                        maxSize={500 * 1024 * 1024} // 500MB
                        value={course.previewVideoUrl}
                        onChange={(url) => setCourse({ ...course, previewVideoUrl: url })}
                        onUpload={async (file) => await mediaApi.uploadVideo(file, 'preview-videos')}
                        helperText="Upload a preview video for the course (MP4, MOV, etc. up to 500MB)"
                      />
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="border-t pt-6 flex justify-end space-x-4">
                    <Button
                      variant="outline"
                      onClick={() => router.push('/courses')}
                      disabled={saving}
                    >
                      Cancel
                    </Button>
                    <Button onClick={handleSave} disabled={saving}>
                      {saving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                      {courseId === 'new' ? 'Create Course' : 'Save Changes'}
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </main>
      </div>
    </ProtectedRoute>
  )
}
