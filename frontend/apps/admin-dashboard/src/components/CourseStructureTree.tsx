'use client'

import { useState, useEffect, useCallback } from 'react'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { ChevronDown, ChevronRight, Copy, Check, FolderOpen, FileText, Loader2, ClipboardList } from 'lucide-react'
import { coursesApi, lecturesApi } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { Badge } from '@/components/ui/badge'

interface Section {
  id: string
  title: string
  sequence: number
}

interface Lecture {
  id: string
  title: string
  order: number
}

interface CourseStructureTreeProps {
  courseId: string
}

interface SectionWithLectures extends Section {
  lectures?: Lecture[]
  lecturesLoaded?: boolean
  lecturesLoading?: boolean
}

export function CourseStructureTree({ courseId }: CourseStructureTreeProps) {
  const { toast } = useToast()
  const [sections, setSections] = useState<SectionWithLectures[]>([])
  const [loading, setLoading] = useState(false)
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set())
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const [loadingAllLectures, setLoadingAllLectures] = useState(false)
  const [copiedAll, setCopiedAll] = useState(false)

  // Fetch sections when courseId changes
  useEffect(() => {
    if (!courseId) {
      setSections([])
      return
    }

    const fetchSections = async () => {
      setLoading(true)
      try {
        const data = await coursesApi.getSections(courseId)
        setSections(data.map(s => ({ ...s, lecturesLoaded: false, lecturesLoading: false })))
      } catch (error) {
        toast({
          title: 'Error',
          description: 'Failed to load course structure',
          variant: 'destructive',
        })
      } finally {
        setLoading(false)
      }
    }

    fetchSections()
  }, [courseId, toast])

  // Load lectures for a section
  const loadLectures = useCallback(async (sectionId: string) => {
    setSections(prev => prev.map(s => 
      s.id === sectionId ? { ...s, lecturesLoading: true } : s
    ))

    try {
      const lectures = await lecturesApi.getLecturesBySection(sectionId)
      setSections(prev => prev.map(s => 
        s.id === sectionId ? { 
          ...s, 
          lectures: lectures.map(l => ({ id: l.id, title: l.title, order: l.order })),
          lecturesLoaded: true,
          lecturesLoading: false 
        } : s
      ))
    } catch (error) {
      setSections(prev => prev.map(s => 
        s.id === sectionId ? { ...s, lecturesLoading: false } : s
      ))
    }
  }, [])

  // Handle section expand/collapse
  const toggleSection = (sectionId: string) => {
    const section = sections.find(s => s.id === sectionId)
    const isExpanding = !expandedSections.has(sectionId)

    setExpandedSections(prev => {
      const next = new Set(prev)
      if (isExpanding) {
        next.add(sectionId)
      } else {
        next.delete(sectionId)
      }
      return next
    })

    // Load lectures if expanding and not yet loaded
    if (isExpanding && section && !section.lecturesLoaded && !section.lecturesLoading) {
      loadLectures(sectionId)
    }
  }

  // Load all lectures for all sections
  const loadAllLectures = useCallback(async (): Promise<SectionWithLectures[]> => {
    const updatedSections = [...sections]
    
    for (let i = 0; i < updatedSections.length; i++) {
      const section = updatedSections[i]
      if (!section.lecturesLoaded && !section.lecturesLoading) {
        try {
          const lectures = await lecturesApi.getLecturesBySection(section.id)
          updatedSections[i] = {
            ...section,
            lectures: lectures.map(l => ({ id: l.id, title: l.title, order: l.order })),
            lecturesLoaded: true,
            lecturesLoading: false
          }
        } catch (error) {
        }
      }
    }
    
    setSections(updatedSections)
    return updatedSections
  }, [sections])

  // Copy all IDs in tree format
  const copyAllIds = async () => {
    setLoadingAllLectures(true)
    try {
      // Load all lectures first
      const allSections = await loadAllLectures()
      
      // Generate tree text
      const lines: string[] = []
      lines.push('COURSE STRUCTURE - IDs for Import')
      lines.push('='.repeat(50))
      lines.push('')
      
      for (const section of allSections) {
        lines.push(`Module: ${section.title}`)
        lines.push(`  moduleId: ${section.id}`)
        
        if (section.lectures && section.lectures.length > 0) {
          lines.push('  Lectures:')
          for (const lecture of section.lectures) {
            lines.push(`    - ${lecture.title}`)
            lines.push(`      lectureId: ${lecture.id}`)
          }
        }
        lines.push('')
      }
      
      const text = lines.join('\n')
      
      // Copy to clipboard
      try {
        await navigator.clipboard.writeText(text)
      } catch {
        // Fallback
        const textArea = document.createElement('textarea')
        textArea.value = text
        document.body.appendChild(textArea)
        textArea.select()
        document.execCommand('copy')
        document.body.removeChild(textArea)
      }
      
      setCopiedAll(true)
      toast({
        title: 'All IDs Copied!',
        description: 'Course structure with all IDs copied to clipboard',
      })
      setTimeout(() => setCopiedAll(false), 2000)
    } catch (error) {
      toast({
        title: 'Error',
        description: 'Failed to copy IDs',
        variant: 'destructive',
      })
    } finally {
      setLoadingAllLectures(false)
    }
  }

  // Copy ID to clipboard
  const copyToClipboard = async (id: string, type: 'module' | 'lecture') => {
    try {
      await navigator.clipboard.writeText(id)
      setCopiedId(id)
      toast({
        title: 'Copied!',
        description: `${type === 'module' ? 'Module' : 'Lecture'} ID copied to clipboard`,
      })
      setTimeout(() => setCopiedId(null), 2000)
    } catch (error) {
      // Fallback for older browsers
      const textArea = document.createElement('textarea')
      textArea.value = id
      document.body.appendChild(textArea)
      textArea.select()
      document.execCommand('copy')
      document.body.removeChild(textArea)
      setCopiedId(id)
      toast({
        title: 'Copied!',
        description: `${type === 'module' ? 'Module' : 'Lecture'} ID copied to clipboard`,
      })
      setTimeout(() => setCopiedId(null), 2000)
    }
  }

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Course Structure</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center py-8">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-muted-foreground">Loading structure...</span>
          </div>
        </CardContent>
      </Card>
    )
  }

  if (sections.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Course Structure</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">No modules found for this course.</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div className="flex-1">
            <CardTitle className="text-base">Course Structure - Copy IDs for Import</CardTitle>
            <CardDescription className="text-sm mt-1">
              Use these IDs in your import file:
              <ul className="mt-2 list-disc list-inside space-y-1">
                <li><strong>moduleIds</strong> column: Module IDs (required, can be comma-separated for multiple)</li>
                <li><strong>lectureId</strong> column: Lecture IDs (optional, for sub-module association)</li>
              </ul>
            </CardDescription>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={copyAllIds}
            disabled={loadingAllLectures}
            className="shrink-0"
          >
            {loadingAllLectures ? (
              <Loader2 className="h-4 w-4 mr-2 animate-spin" />
            ) : copiedAll ? (
              <Check className="h-4 w-4 mr-2 text-green-500" />
            ) : (
              <ClipboardList className="h-4 w-4 mr-2" />
            )}
            {copiedAll ? 'Copied!' : 'Copy All IDs'}
          </Button>
        </div>
        <div className="flex items-center gap-2 mt-3">
          <Badge variant="secondary" className="text-xs">
            {sections.length} Module{sections.length !== 1 ? 's' : ''}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="pt-0">
        <div className="space-y-1 max-h-[400px] overflow-y-auto pr-2">
          {sections.map((section) => (
            <Collapsible
              key={section.id}
              open={expandedSections.has(section.id)}
              onOpenChange={() => toggleSection(section.id)}
            >
              <div className="rounded-md border bg-card">
                <CollapsibleTrigger asChild>
                  <Button
                    variant="ghost"
                    className="w-full justify-start px-3 py-2 h-auto font-normal hover:bg-muted/50"
                  >
                    <span className="flex items-center gap-2 flex-1">
                      {expandedSections.has(section.id) ? (
                        <ChevronDown className="h-4 w-4 shrink-0 text-muted-foreground" />
                      ) : (
                        <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" />
                      )}
                      <FolderOpen className="h-4 w-4 shrink-0 text-blue-500" />
                      <span className="truncate text-left">{section.title}</span>
                    </span>
                  </Button>
                </CollapsibleTrigger>
                
                {/* Module ID row */}
                <div className="flex items-center gap-2 px-3 py-1.5 border-t bg-muted/30">
                  <span className="text-xs text-muted-foreground ml-10">ID:</span>
                  <code className="text-xs font-mono bg-muted px-1.5 py-0.5 rounded flex-1 truncate">
                    {section.id}
                  </code>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-6 px-2 text-xs"
                    onClick={(e) => {
                      e.stopPropagation()
                      copyToClipboard(section.id, 'module')
                    }}
                  >
                    {copiedId === section.id ? (
                      <Check className="h-3 w-3 text-green-500" />
                    ) : (
                      <Copy className="h-3 w-3" />
                    )}
                    <span className="ml-1">Copy</span>
                  </Button>
                </div>

                <CollapsibleContent>
                  <div className="border-t">
                    {section.lecturesLoading ? (
                      <div className="flex items-center gap-2 px-3 py-3 ml-10">
                        <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                        <span className="text-sm text-muted-foreground">Loading lectures...</span>
                      </div>
                    ) : section.lectures && section.lectures.length > 0 ? (
                      <div className="py-1">
                        {section.lectures.map((lecture) => (
                          <div key={lecture.id} className="ml-6 border-l-2 border-muted pl-4">
                            <div className="flex items-center gap-2 px-2 py-1.5">
                              <FileText className="h-4 w-4 shrink-0 text-orange-500" />
                              <span className="text-sm truncate flex-1">{lecture.title}</span>
                            </div>
                            <div className="flex items-center gap-2 px-2 py-1 bg-muted/20 rounded mx-2 mb-1">
                              <span className="text-xs text-muted-foreground ml-6">ID:</span>
                              <code className="text-xs font-mono bg-muted px-1.5 py-0.5 rounded flex-1 truncate">
                                {lecture.id}
                              </code>
                              <Button
                                variant="ghost"
                                size="sm"
                                className="h-6 px-2 text-xs"
                                onClick={() => copyToClipboard(lecture.id, 'lecture')}
                              >
                                {copiedId === lecture.id ? (
                                  <Check className="h-3 w-3 text-green-500" />
                                ) : (
                                  <Copy className="h-3 w-3" />
                                )}
                                <span className="ml-1">Copy</span>
                              </Button>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : section.lecturesLoaded ? (
                      <div className="px-3 py-3 ml-10">
                        <span className="text-sm text-muted-foreground">No lectures in this module</span>
                      </div>
                    ) : null}
                  </div>
                </CollapsibleContent>
              </div>
            </Collapsible>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
