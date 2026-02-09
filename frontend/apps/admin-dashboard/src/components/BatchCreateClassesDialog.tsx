'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
import type { CreateClassRequest, BatchCreateClassesRequest } from '@kunal-ak23/edudron-shared-utils'

interface BatchCreateClassesDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  instituteId: string
  onSuccess?: () => void
}

interface ClassFormData extends CreateClassRequest {
  tempId: string
}

export function BatchCreateClassesDialog({
  open,
  onOpenChange,
  instituteId,
  onSuccess,
}: BatchCreateClassesDialogProps) {
  const { toast } = useToast()
  const [submitting, setSubmitting] = useState(false)
  
  const [classes, setClasses] = useState<ClassFormData[]>([
    {
      tempId: '1',
      name: '',
      code: '',
      instituteId,
      academicYear: '',
      grade: '',
      level: '',
      isActive: true,
    }
  ])

  const addClass = () => {
    setClasses([
      ...classes,
      {
        tempId: Date.now().toString(),
        name: '',
        code: '',
        instituteId,
        academicYear: '',
        grade: '',
        level: '',
        isActive: true,
      }
    ])
  }

  const removeClass = (tempId: string) => {
    if (classes.length > 1) {
      setClasses(classes.filter(c => c.tempId !== tempId))
    }
  }

  const updateClass = (tempId: string, field: keyof CreateClassRequest, value: any) => {
    setClasses(classes.map(c => 
      c.tempId === tempId ? { ...c, [field]: value } : c
    ))
  }

  const resetForm = () => {
    setClasses([
      {
        tempId: '1',
        name: '',
        code: '',
        instituteId,
        academicYear: '',
        grade: '',
        level: '',
        isActive: true,
      }
    ])
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    // Validate classes
    const validClasses = classes.filter(c => c.name.trim() && c.code.trim())
    
    if (validClasses.length === 0) {
      toast({
        variant: 'destructive',
        title: 'Validation Error',
        description: 'At least one class with name and code is required',
      })
      return
    }

    if (validClasses.length > 50) {
      toast({
        variant: 'destructive',
        title: 'Validation Error',
        description: 'Maximum 50 classes can be created at once',
      })
      return
    }

    setSubmitting(true)
    try {
      const request: BatchCreateClassesRequest = {
        classes: validClasses.map(({ tempId, ...classData }) => ({
          ...classData,
          instituteId,
        }))
      }

      const result = await classesApi.batchCreateClasses(instituteId, request)
      
      toast({
        title: 'Success',
        description: result.message || `Created ${result.totalCreated} class(es) successfully`,
      })
      
      resetForm()
      onOpenChange(false)
      
      if (onSuccess) {
        onSuccess()
      }
    } catch (err: any) {
      toast({
        variant: 'destructive',
        title: 'Failed to create classes',
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
          <DialogTitle>Batch Create Classes</DialogTitle>
          <DialogDescription>
            Create multiple classes at once (max 50). Only classes with name and code will be created.
          </DialogDescription>
        </DialogHeader>
        
        <form onSubmit={handleSubmit} className="space-y-6 py-4">
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-sm">Classes ({classes.length}/50)</h3>
              <Button 
                type="button" 
                size="sm" 
                variant="outline" 
                onClick={addClass}
                disabled={classes.length >= 50}
              >
                <Plus className="h-4 w-4 mr-1" />
                Add Class
              </Button>
            </div>
            
            <div className="space-y-4">
              {classes.map((classItem, index) => (
                <div key={classItem.tempId} className="border rounded-lg p-4 space-y-3">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-medium">Class {index + 1}</span>
                    {classes.length > 1 && (
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        onClick={() => removeClass(classItem.tempId)}
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    )}
                  </div>
                  
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-2">
                      <Label>Class Name *</Label>
                      <Input
                        value={classItem.name}
                        onChange={(e) => updateClass(classItem.tempId, 'name', e.target.value)}
                        placeholder="e.g., Grade 10 Science"
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>Class Code *</Label>
                      <Input
                        value={classItem.code}
                        onChange={(e) => updateClass(classItem.tempId, 'code', e.target.value.toUpperCase())}
                        placeholder="e.g., G10-SCI"
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>Academic Year</Label>
                      <Input
                        value={classItem.academicYear || ''}
                        onChange={(e) => updateClass(classItem.tempId, 'academicYear', e.target.value)}
                        placeholder="e.g., 2024-2025"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>Grade</Label>
                      <Input
                        value={classItem.grade || ''}
                        onChange={(e) => updateClass(classItem.tempId, 'grade', e.target.value)}
                        placeholder="e.g., 10"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label>Level</Label>
                      <Input
                        value={classItem.level || ''}
                        onChange={(e) => updateClass(classItem.tempId, 'level', e.target.value)}
                        placeholder="e.g., Intermediate"
                      />
                    </div>
                    <div className="flex items-center space-x-2 pt-7">
                      <input
                        type="checkbox"
                        id={`active-${classItem.tempId}`}
                        checked={classItem.isActive}
                        onChange={(e) => updateClass(classItem.tempId, 'isActive', e.target.checked)}
                        className="rounded"
                      />
                      <Label htmlFor={`active-${classItem.tempId}`}>Active</Label>
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
                `Create ${classes.filter(c => c.name && c.code).length} Class(es)`
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
