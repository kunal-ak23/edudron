'use client'

import { useEffect, useState, useCallback } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Search, Loader2, Users, CheckSquare, Square } from 'lucide-react'
import { apiClient } from '@/lib/api'

interface Section {
  id: string
  name: string
  studentCount?: number
  className?: string
  isActive?: boolean
}

interface SectionMultiSelectProps {
  courseId: string
  selectedIds: string[]
  onChange: (ids: string[]) => void
  disabled?: boolean
}

export function SectionMultiSelect({ courseId, selectedIds, onChange, disabled }: SectionMultiSelectProps) {
  const [sections, setSections] = useState<Section[]>([])
  const [loading, setLoading] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')

  const loadSections = useCallback(async () => {
    if (!courseId) {
      setSections([])
      return
    }
    
    setLoading(true)
    try {
      const response = await apiClient.get(`/api/exams/courses/${courseId}/sections`)
      const data = Array.isArray(response) ? response : (response as any)?.data || []
      setSections(data)
    } catch (error) {
      console.error('Failed to load sections:', error)
      setSections([])
    } finally {
      setLoading(false)
    }
  }, [courseId])

  useEffect(() => {
    loadSections()
  }, [loadSections])

  const handleToggle = (sectionId: string) => {
    if (disabled) return
    
    const newSelection = selectedIds.includes(sectionId)
      ? selectedIds.filter(id => id !== sectionId)
      : [...selectedIds, sectionId]
    onChange(newSelection)
  }

  const handleSelectAll = () => {
    if (disabled) return
    const filteredIds = filteredSections.map(s => s.id)
    onChange(filteredIds)
  }

  const handleDeselectAll = () => {
    if (disabled) return
    onChange([])
  }

  const filteredSections = sections.filter(section => {
    if (!searchQuery) return true
    const query = searchQuery.toLowerCase()
    return (
      section.name.toLowerCase().includes(query) ||
      (section.className && section.className.toLowerCase().includes(query))
    )
  })

  if (!courseId) {
    return (
      <div className="text-sm text-gray-500 py-4 text-center">
        Please select a course first
      </div>
    )
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-8">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    )
  }

  if (sections.length === 0) {
    return (
      <div className="text-sm text-gray-500 py-4 text-center">
        No sections found for this course. Students must be enrolled in sections to generate papers.
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {/* Search and Actions */}
      <div className="flex items-center gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-gray-400" />
          <Input
            placeholder="Search sections..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-9"
            disabled={disabled}
          />
        </div>
        <Button 
          variant="outline" 
          size="sm" 
          onClick={handleSelectAll}
          disabled={disabled || filteredSections.length === 0}
        >
          <CheckSquare className="h-4 w-4 mr-1" />
          All
        </Button>
        <Button 
          variant="outline" 
          size="sm" 
          onClick={handleDeselectAll}
          disabled={disabled || selectedIds.length === 0}
        >
          <Square className="h-4 w-4 mr-1" />
          None
        </Button>
      </div>

      {/* Selection Summary */}
      <div className="text-sm text-gray-600">
        {selectedIds.length} of {sections.length} section(s) selected
      </div>

      {/* Section List */}
      <div className="max-h-64 overflow-y-auto space-y-2 border rounded-md p-2">
        {filteredSections.map(section => (
          <Card 
            key={section.id}
            className={`cursor-pointer transition-colors ${
              selectedIds.includes(section.id) 
                ? 'border-blue-500 bg-blue-50' 
                : 'hover:bg-gray-50'
            } ${disabled ? 'opacity-50 cursor-not-allowed' : ''}`}
            onClick={() => handleToggle(section.id)}
          >
            <CardContent className="py-2 px-3">
              <div className="flex items-center gap-3">
                <Checkbox
                  checked={selectedIds.includes(section.id)}
                  onCheckedChange={() => handleToggle(section.id)}
                  disabled={disabled}
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-sm truncate">{section.name}</span>
                    {section.isActive === false && (
                      <Badge variant="outline" className="text-xs text-gray-500">
                        Inactive
                      </Badge>
                    )}
                  </div>
                  {section.className && (
                    <div className="text-xs text-gray-500">{section.className}</div>
                  )}
                </div>
                {section.studentCount !== undefined && (
                  <div className="flex items-center gap-1 text-xs text-gray-500">
                    <Users className="h-3 w-3" />
                    {section.studentCount}
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        ))}
        
        {filteredSections.length === 0 && searchQuery && (
          <div className="text-center py-4 text-sm text-gray-500">
            No sections match "{searchQuery}"
          </div>
        )}
      </div>
    </div>
  )
}
