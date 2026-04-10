'use client'

import { useEffect, useState, useCallback } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { FileSpreadsheet, Download, Loader2 } from 'lucide-react'
import { useToast } from '@/hooks/use-toast'
import { resultsApi, sectionsApi, classesApi, coursesApi } from '@/lib/api'

export const dynamic = 'force-dynamic'

interface SelectOption {
  id: string
  name: string
}

export default function ResultsExportPage() {
  const { toast } = useToast()

  const [scope, setScope] = useState<'section' | 'class' | 'course'>('section')
  const [selectedId, setSelectedId] = useState<string>('')
  const [exporting, setExporting] = useState(false)

  const [sections, setSections] = useState<SelectOption[]>([])
  const [classes, setClasses] = useState<SelectOption[]>([])
  const [courses, setCourses] = useState<SelectOption[]>([])
  const [loadingOptions, setLoadingOptions] = useState(true)

  const loadOptions = useCallback(async () => {
    setLoadingOptions(true)
    try {
      const [coursesRes, sectionsRes, classesRes] = await Promise.all([
        coursesApi.listCourses().catch(() => []),
        sectionsApi.listAllSections().catch(() => []),
        classesApi.listAllClasses().catch(() => []),
      ])

      const normalize = (data: any): SelectOption[] => {
        const arr = Array.isArray(data)
          ? data
          : data?.content && Array.isArray(data.content)
            ? data.content
            : data?.data && Array.isArray(data.data)
              ? data.data
              : []
        return arr.map((item: any) => ({
          id: item.id,
          name: item.name || item.title || item.code || item.id,
        }))
      }

      setSections(normalize(sectionsRes))
      setClasses(normalize(classesRes))
      setCourses(normalize(coursesRes))
    } catch {
      toast({
        title: 'Error',
        description: 'Failed to load filter options',
        variant: 'destructive',
      })
    } finally {
      setLoadingOptions(false)
    }
  }, [toast])

  useEffect(() => {
    loadOptions()
  }, [loadOptions])

  // Reset selection when scope changes
  useEffect(() => {
    setSelectedId('')
  }, [scope])

  const currentOptions: SelectOption[] =
    scope === 'section' ? sections : scope === 'class' ? classes : courses

  const handleExport = useCallback(async () => {
    if (!selectedId) return
    setExporting(true)
    try {
      let blob: Blob
      if (scope === 'section') {
        blob = await resultsApi.exportBySection(selectedId)
      } else if (scope === 'class') {
        blob = await resultsApi.exportByClass(selectedId)
      } else {
        blob = await resultsApi.exportByCourse(selectedId)
      }

      const selectedName =
        currentOptions.find((o) => o.id === selectedId)?.name ?? scope
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `results-${scope}-${selectedName.replace(/\s+/g, '_')}-${new Date().toISOString().split('T')[0]}.xlsx`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      
      // Delay revocation to prevent NS_BINDING_ABORTED in Firefox
      setTimeout(() => {
        window.URL.revokeObjectURL(url)
      }, 1000)

      toast({ title: 'Success', description: 'Results exported successfully' })
    } catch {
      toast({
        title: 'Export failed',
        description: 'Could not generate the export file. Please try again.',
        variant: 'destructive',
      })
    } finally {
      setExporting(false)
    }
  }, [selectedId, scope, currentOptions, toast])

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2">
          <FileSpreadsheet className="h-7 w-7 text-primary" />
          <h1 className="text-3xl font-bold">Export Results</h1>
        </div>
        <p className="text-gray-600 mt-1">
          Download student results as a spreadsheet filtered by section, class,
          or course.
        </p>
      </div>

      {/* Export Card */}
      <Card>
        <CardHeader>
          <CardTitle>Select Scope</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Scope tabs */}
          <Tabs
            value={scope}
            onValueChange={(v) => setScope(v as typeof scope)}
          >
            <TabsList>
              <TabsTrigger value="section" className="cursor-pointer">
                Section
              </TabsTrigger>
              <TabsTrigger value="class" className="cursor-pointer">
                Class
              </TabsTrigger>
              <TabsTrigger value="course" className="cursor-pointer">
                Course
              </TabsTrigger>
            </TabsList>

            {/* Each tab shares the same content layout */}
            {(['section', 'class', 'course'] as const).map((tab) => (
              <TabsContent key={tab} value={tab}>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-end">
                  <div>
                    <Label htmlFor={`select-${tab}`}>
                      Choose a {tab}
                    </Label>
                    {loadingOptions ? (
                      <div className="flex items-center gap-2 mt-2 text-sm text-muted-foreground">
                        <Loader2 className="h-4 w-4 animate-spin" />
                        Loading {tab}s...
                      </div>
                    ) : currentOptions.length === 0 ? (
                      <p className="text-sm text-muted-foreground mt-2">
                        No {tab}s available
                      </p>
                    ) : (
                      <Select
                        value={selectedId}
                        onValueChange={setSelectedId}
                      >
                        <SelectTrigger
                          id={`select-${tab}`}
                          className="mt-1 cursor-pointer"
                        >
                          <SelectValue
                            placeholder={`Select a ${tab}...`}
                          />
                        </SelectTrigger>
                        <SelectContent>
                          {currentOptions.map((opt) => (
                            <SelectItem
                              key={opt.id}
                              value={opt.id}
                              className="cursor-pointer"
                            >
                              {opt.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  </div>

                  <div>
                    <Button
                      type="button"
                      onClick={handleExport}
                      disabled={!selectedId || exporting}
                      className="cursor-pointer transition-colors duration-200"
                    >
                      {exporting ? (
                        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                      ) : (
                        <Download className="h-4 w-4 mr-2" />
                      )}
                      {exporting ? 'Exporting...' : 'Export'}
                    </Button>
                  </div>
                </div>
              </TabsContent>
            ))}
          </Tabs>
        </CardContent>
      </Card>
    </div>
  )
}
