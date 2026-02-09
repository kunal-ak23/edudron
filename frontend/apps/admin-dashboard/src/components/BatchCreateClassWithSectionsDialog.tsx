'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Loader2, Plus, X } from 'lucide-react'
import { classesApi } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import type { CreateClassWithSectionsRequest, CreateSectionForClassRequest } from '@kunal-ak23/edudron-shared-utils'

interface BatchCreateClassWithSectionsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  instituteId: string
  onSuccess?: () => void
}

interface SectionFormData {
  id: string
  name: string
  description: string
  startDate: string
  endDate: string
  maxStudents: string
}

export function BatchCreateClassWithSectionsDialog({
  open,
  onOpenChange,
  instituteId,
  onSuccess,
}: BatchCreateClassWithSectionsDialogProps) {
  const { toast } = useToast()
  const [submitting, setSubmitting] = useState(false)
  
  const [classData, setClassData] = useState({
    name: '',
    code: '',
    academicYear: '',
    grade: '',
    level: '',
    isActive: true,
  })

  const [sections, setSections] = useState<SectionFormData[]>([
    { id: '1', name: '', description: '', startDate: '', endDate: '', maxStudents: '' }
  ])

  const addSection = () => {
    setSections([
      ...sections,
      { id: Date.now().toString(), name: '', description: '', startDate: '', endDate: '', maxStudents: '' }
    ])
  }

  const removeSection = (id: string) => {
    if (sections.length > 1) {
      setSections(sections.filter(s => s.id !== id))
    }
  }

  const updateSection = (id: string, field: keyof SectionFormData, value: string) => {
    setSections(sections.map(s => 
      s.id === id ? { ...s, [field]: value } : s
    ))
  }

  const resetForm = () => {
    setClassData({
      name: '',
      code: '',
      academicYear: '',
      grade: '',
      level: '',
      isActive: true,
    })
    setSections([
      { id: '1', name: '', description: '', startDate: '', endDate: '', maxStudents: '' }
    ])
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    // Validate class data
    if (!classData.name.trim() || !classData.code.trim()) {
      toast({
        variant: 'destructive',
        title: 'Validation Error',
        description: 'Class name and code are required',
      })
      return
    }

    // Validate sections
    const validSections = sections.filter(s => s.name.trim())
    if (validSections.length === 0) {
      toast({
        variant: 'destructive',
        title: 'Validation Error',
        description: 'At least one section with a name is required',
      })
      return
    }

    setSubmitting(true)
    try {
      const request: CreateClassWithSectionsRequest = {
        ...classData,
        instituteId,
        sections: validSections.map(s => ({
          name: s.name,
          description: s.description || undefined,
          startDate: s.startDate || undefined,
          endDate: s.endDate || undefined,
          maxStudents: s.maxStudents ? parseInt(s.maxStudents) : undefined,
        }))
      }

      const result = await classesApi.createClassWithSections(instituteId, request)
      
      toast({
        title: 'Success',
        description: `Created class "${result.classInfo.name}" with ${result.sections.length} section(s)`,
      })
      
      resetForm()
      onOpenChange(false)
      
      if (onSuccess) {
        onSuccess()
      }
    } catch (err: any) {
      toast({
        variant: 'destructive',
        title: 'Failed to create class',
        description: extractErrorMessage(err),
      })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Create Class with Sections</DialogTitle>
          <DialogDescription>
            Create a new class and add multiple sections in one go.
          </DialogDescription>
        </DialogHeader>
        
        <form onSubmit={handleSubmit} className="space-y-6 py-4">
          {/* Class Details */}
          <div className="space-y-4">
            <h3 className="font-semibold text-sm">Class Details</h3>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="className">Class Name *</Label>
                <Input
                  id="className"
                  value={classData.name}
                  onChange={(e) => setClassData({ ...classData, name: e.target.value })}
                  placeholder="e.g., Grade 10 Mathematics"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="classCode">Class Code *</Label>
                <Input
                  id="classCode"
                  value={classData.code}
                  onChange={(e) => setClassData({ ...classData, code: e.target.value.toUpperCase() })}
                  placeholder="e.g., G10-MATH"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="academicYear">Academic Year</Label>
                <Input
                  id="academicYear"
                  value={classData.academicYear}
                  onChange={(e) => setClassData({ ...classData, academicYear: e.target.value })}
                  placeholder="e.g., 2024-2025"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="grade">Grade</Label>
                <Input
                  id="grade"
                  value={classData.grade}
                  onChange={(e) => setClassData({ ...classData, grade: e.target.value })}
                  placeholder="e.g., 10"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="level">Level</Label>
                <Input
                  id="level"
                  value={classData.level}
                  onChange={(e) => setClassData({ ...classData, level: e.target.value })}
                  placeholder="e.g., Intermediate"
                />
              </div>
            </div>
          </div>

          {/* Sections */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-sm">Sections</h3>
              <Button type="button" size="sm" variant="outline" onClick={addSection}>
                <Plus className="h-4 w-4 mr-1" />
                Add Section
              </Button>
            </div>
            
            <div className="space-y-4">
              {sections.map((section, index) => (
                <div key={section.id} className="border rounded-lg p-4 space-y-3">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-medium">Section {index + 1}</span>
                    {sections.length > 1 && (
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        onClick={() => removeSection(section.id)}
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    )}
                  </div>
                  
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-2">
                      <Label>Section Name *</Label>
                      <Input
                        value={section.name}
                        onChange={(e) => updateSection(section.id, 'name', e.target.value)}
                        placeholder="e.g., Section A"
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>Max Students</Label>
                      <Input
                        type="number"
                        min="1"
                        value={section.maxStudents}
                        onChange={(e) => updateSection(section.id, 'maxStudents', e.target.value)}
                        placeholder="Optional"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>Start Date</Label>
                      <Input
                        type="date"
                        value={section.startDate}
                        onChange={(e) => updateSection(section.id, 'startDate', e.target.value)}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>End Date</Label>
                      <Input
                        type="date"
                        value={section.endDate}
                        onChange={(e) => updateSection(section.id, 'endDate', e.target.value)}
                      />
                    </div>
                    <div className="col-span-2 space-y-2">
                      <Label>Description</Label>
                      <Textarea
                        value={section.description}
                        onChange={(e) => updateSection(section.id, 'description', e.target.value)}
                        placeholder="Optional description"
                        rows={2}
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                resetForm()
                onOpenChange(false)
              }}
              disabled={submitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Creating...
                </>
              ) : (
                'Create Class & Sections'
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
