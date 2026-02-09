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
import { sectionsApi } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import type { CreateSectionRequest, BatchCreateSectionsRequest } from '@kunal-ak23/edudron-shared-utils'

interface BatchCreateSectionsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  classId: string
  onSuccess?: () => void
}

interface SectionFormData extends Omit<CreateSectionRequest, 'maxStudents'> {
  tempId: string
  maxStudents: string
}

export function BatchCreateSectionsDialog({
  open,
  onOpenChange,
  classId,
  onSuccess,
}: BatchCreateSectionsDialogProps) {
  const { toast } = useToast()
  const [submitting, setSubmitting] = useState(false)
  
  const [sections, setSections] = useState<SectionFormData[]>([
    {
      tempId: '1',
      name: '',
      description: '',
      classId,
      startDate: '',
      endDate: '',
      maxStudents: '',
    }
  ])

  const addSection = () => {
    setSections([
      ...sections,
      {
        tempId: Date.now().toString(),
        name: '',
        description: '',
        classId,
        startDate: '',
        endDate: '',
        maxStudents: '',
      }
    ])
  }

  const removeSection = (tempId: string) => {
    if (sections.length > 1) {
      setSections(sections.filter(s => s.tempId !== tempId))
    }
  }

  const updateSection = (tempId: string, field: keyof Omit<SectionFormData, 'tempId'>, value: string) => {
    setSections(sections.map(s => 
      s.tempId === tempId ? { ...s, [field]: value } : s
    ))
  }

  const resetForm = () => {
    setSections([
      {
        tempId: '1',
        name: '',
        description: '',
        classId,
        startDate: '',
        endDate: '',
        maxStudents: '',
      }
    ])
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
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

    if (validSections.length > 50) {
      toast({
        variant: 'destructive',
        title: 'Validation Error',
        description: 'Maximum 50 sections can be created at once',
      })
      return
    }

    setSubmitting(true)
    try {
      const request: BatchCreateSectionsRequest = {
        sections: validSections.map(({ tempId, maxStudents, ...sectionData }) => ({
          ...sectionData,
          classId,
          description: sectionData.description || undefined,
          startDate: sectionData.startDate || undefined,
          endDate: sectionData.endDate || undefined,
          maxStudents: maxStudents ? parseInt(maxStudents) : undefined,
        }))
      }

      const result = await sectionsApi.batchCreateSections(classId, request)
      
      toast({
        title: 'Success',
        description: result.message || `Created ${result.totalCreated} section(s) successfully`,
      })
      
      resetForm()
      onOpenChange(false)
      
      if (onSuccess) {
        onSuccess()
      }
    } catch (err: any) {
      toast({
        variant: 'destructive',
        title: 'Failed to create sections',
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
          <DialogTitle>Batch Create Sections</DialogTitle>
          <DialogDescription>
            Create multiple sections at once for this class (max 50). Only sections with a name will be created.
          </DialogDescription>
        </DialogHeader>
        
        <form onSubmit={handleSubmit} className="space-y-6 py-4">
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-sm">Sections ({sections.length}/50)</h3>
              <Button 
                type="button" 
                size="sm" 
                variant="outline" 
                onClick={addSection}
                disabled={sections.length >= 50}
              >
                <Plus className="h-4 w-4 mr-1" />
                Add Section
              </Button>
            </div>
            
            <div className="space-y-4">
              {sections.map((section, index) => (
                <div key={section.tempId} className="border rounded-lg p-4 space-y-3">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-medium">Section {index + 1}</span>
                    {sections.length > 1 && (
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        onClick={() => removeSection(section.tempId)}
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
                        onChange={(e) => updateSection(section.tempId, 'name', e.target.value)}
                        placeholder="e.g., Section A, Morning Batch"
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>Max Students</Label>
                      <Input
                        type="number"
                        min="1"
                        value={section.maxStudents}
                        onChange={(e) => updateSection(section.tempId, 'maxStudents', e.target.value)}
                        placeholder="Optional"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>Start Date</Label>
                      <Input
                        type="date"
                        value={section.startDate || ''}
                        onChange={(e) => updateSection(section.tempId, 'startDate', e.target.value)}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>End Date</Label>
                      <Input
                        type="date"
                        value={section.endDate || ''}
                        onChange={(e) => updateSection(section.tempId, 'endDate', e.target.value)}
                      />
                    </div>
                    <div className="col-span-2 space-y-2">
                      <Label>Description</Label>
                      <Textarea
                        value={section.description || ''}
                        onChange={(e) => updateSection(section.tempId, 'description', e.target.value)}
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
                `Create ${sections.filter(s => s.name).length} Section(s)`
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
