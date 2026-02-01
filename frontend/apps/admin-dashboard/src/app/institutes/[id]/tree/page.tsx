'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Loader2, ArrowLeft, Network } from 'lucide-react'
import { institutesApi, classesApi, sectionsApi, apiClient } from '@/lib/api'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import type { Institute, Class, Section } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import Link from 'next/link'
import { D3TreeView } from '@/components/D3TreeView'

interface InstructorAccess {
  allowedClassIds: string[]
  allowedSectionIds: string[]
  allowedCourseIds: string[]
}

export default function InstituteTreePage() {
  const router = useRouter()
  const params = useParams()
  const instituteId = params.id as string
  const { toast } = useToast()
  const { user } = useAuth()
  const [institute, setInstitute] = useState<Institute | null>(null)
  const [classes, setClasses] = useState<Class[]>([])
  const [sectionsByClass, setSectionsByClass] = useState<Record<string, Section[]>>({})
  const [loading, setLoading] = useState(true)
  
  const isInstructor = user?.role === 'INSTRUCTOR'
  const isSupportStaff = user?.role === 'SUPPORT_STAFF'
  const isViewOnly = isInstructor || isSupportStaff

  const loadData = useCallback(async () => {
    try {
      setLoading(true)
      
      // For instructors, fetch their allowed access first
      let allowedClassIds: Set<string> | null = null
      let allowedSectionIds: Set<string> | null = null
      
      if (isViewOnly && user?.id) {
        try {
          const accessResponse = await apiClient.get<InstructorAccess>(`/api/instructor-assignments/instructor/${user.id}/access`)
          allowedClassIds = new Set(accessResponse.data.allowedClassIds || [])
          allowedSectionIds = new Set(accessResponse.data.allowedSectionIds || [])
        } catch (err) {
          console.error('Failed to load instructor access:', err)
          // If we can't load access, show nothing for safety
          allowedClassIds = new Set()
          allowedSectionIds = new Set()
        }
      }
      
      const [instituteData, classesData] = await Promise.all([
        institutesApi.getInstitute(instituteId),
        classesApi.listClassesByInstitute(instituteId)
      ])
      setInstitute(instituteData)
      
      // Filter classes for instructors
      let filteredClasses = classesData || []
      if (allowedClassIds !== null) {
        filteredClasses = filteredClasses.filter(cls => allowedClassIds!.has(cls.id))
      }
      setClasses(filteredClasses)

      // Load sections for all classes
      const sectionsMap: Record<string, Section[]> = {}
      if (filteredClasses.length > 0) {
        const sectionsPromises = filteredClasses.map(async (cls) => {
          try {
            let sections = await sectionsApi.listSectionsByClass(cls.id)
            // Filter sections for instructors
            if (allowedSectionIds !== null) {
              sections = (sections || []).filter(sec => allowedSectionIds!.has(sec.id))
            }
            return { classId: cls.id, sections: sections || [] }
          } catch (err) {
            console.error(`Error loading sections for class ${cls.id}:`, err)
            return { classId: cls.id, sections: [] }
          }
        })
        const sectionsResults = await Promise.all(sectionsPromises)
        sectionsResults.forEach(({ classId, sections }) => {
          sectionsMap[classId] = sections
        })
      }
      setSectionsByClass(sectionsMap)
    } catch (err: any) {
      console.error('Error loading data:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to load data',
        description: errorMessage,
      })
      router.push('/institutes')
    } finally {
      setLoading(false)
    }
  }, [instituteId, toast, isViewOnly, user?.id])

  useEffect(() => {
    if (instituteId) {
      loadData()
    }
  }, [instituteId, loadData])

  const handleAddClass = () => {
    if (isViewOnly) return
    router.push(`/institutes/${instituteId}/classes/new`)
  }

  const handleAddSection = (classId: string) => {
    if (isViewOnly) return
    router.push(`/classes/${classId}/sections/new`)
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (!institute) {
    return null
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-4 flex items-center gap-2 text-sm text-gray-600">
          <Link href="/institutes" className="hover:text-gray-900">Institutes</Link>
          <span>/</span>
          <Link href={`/institutes/${instituteId}`} className="hover:text-gray-900">{institute.name}</Link>
          <span>/</span>
          <span className="text-gray-900">Tree View</span>
        </div>

        <div className="flex items-center gap-4 mb-6">
          <Link href={`/institutes/${instituteId}`}>
            <Button variant="ghost">
              <ArrowLeft className="h-4 w-4 mr-2" />
              Back to Institute
            </Button>
          </Link>
          <div className="flex items-center gap-2 text-gray-600">
            <Network className="h-5 w-5" />
            <span className="text-sm">Tree Visualization</span>
          </div>
        </div>

        <D3TreeView
          institute={institute}
          classes={classes}
          sectionsByClass={sectionsByClass}
          onAddClass={isViewOnly ? undefined : handleAddClass}
          onAddSection={isViewOnly ? undefined : handleAddSection}
        />
      </div>
    </div>
  )
}
