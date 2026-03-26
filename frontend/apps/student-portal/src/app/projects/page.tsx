'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute } from '@kunal-ak23/edudron-ui-components'
import { StudentLayout } from '@/components/StudentLayout'
import { projectsApi } from '@/lib/api'
import { useProjectsFeature } from '@/hooks/useProjectsFeature'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Loader2, FolderKanban, Calendar, CheckCircle2, Clock } from 'lucide-react'
import type { ProjectDTO } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

export default function ProjectsPage() {
  const router = useRouter()
  const { enabled, loading: featureLoading } = useProjectsFeature()
  const [projects, setProjects] = useState<ProjectDTO[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!enabled) return
    loadProjects()
  }, [enabled])

  async function loadProjects() {
    try {
      const data = await projectsApi.getMyProjects()
      setProjects(data)
    } catch (err) {
      console.error('Failed to load projects', err)
    } finally {
      setLoading(false)
    }
  }

  function formatDate(dateStr?: string): string {
    if (!dateStr) return 'No deadline'
    try {
      return new Date(dateStr).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      })
    } catch {
      return dateStr
    }
  }

  function isCutoffPassed(dateStr?: string): boolean {
    if (!dateStr) return false
    return new Date(dateStr) < new Date()
  }

  if (featureLoading) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="flex justify-center items-center p-12">
            <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  if (!enabled) {
    return (
      <ProtectedRoute>
        <StudentLayout>
          <div className="p-12 text-center text-gray-500">
            Projects are not available for your institution.
          </div>
        </StudentLayout>
      </ProtectedRoute>
    )
  }

  return (
    <ProtectedRoute>
      <StudentLayout>
        <div className="container mx-auto px-4 py-8">
          <div className="flex items-center gap-3 mb-6">
            <FolderKanban className="h-8 w-8 text-primary-600" />
            <div>
              <h1 className="text-2xl font-bold text-gray-900">Projects</h1>
              <p className="text-gray-500">Your assigned group projects</p>
            </div>
          </div>

          {loading ? (
            <div className="flex justify-center p-12">
              <Loader2 className="h-8 w-8 animate-spin text-primary-600" />
            </div>
          ) : projects.length === 0 ? (
            <div className="text-center p-12 text-gray-500">
              No projects assigned yet.
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {projects.map((project) => (
                <Card
                  key={project.id}
                  className="hover:shadow-md transition-shadow cursor-pointer"
                  onClick={() => router.push(`/projects/${project.id}`)}
                >
                  <CardHeader>
                    <div className="flex items-start justify-between gap-2">
                      <CardTitle className="text-lg">{project.title}</CardTitle>
                      <Badge
                        variant="outline"
                        className={`shrink-0 ${
                          project.status === 'COMPLETED'
                            ? 'border-green-500 text-green-700'
                            : project.status === 'ACTIVE'
                            ? 'border-blue-500 text-blue-700'
                            : 'border-gray-400 text-gray-600'
                        }`}
                      >
                        {project.status}
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent>
                    {project.description && (
                      <p className="text-sm text-gray-500 mb-4 line-clamp-2">
                        {project.description}
                      </p>
                    )}
                    <div className="flex items-center justify-between text-sm">
                      <div className="flex items-center gap-1.5 text-gray-500">
                        <Calendar className="h-4 w-4" />
                        <span>{formatDate(project.submissionCutoff)}</span>
                      </div>
                      {project.submissionCutoff && (
                        isCutoffPassed(project.submissionCutoff) ? (
                          <Badge variant="outline" className="border-red-300 text-red-600 text-xs">
                            <Clock className="h-3 w-3 mr-1" />
                            Past due
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="border-green-300 text-green-600 text-xs">
                            <CheckCircle2 className="h-3 w-3 mr-1" />
                            Open
                          </Badge>
                        )
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </div>
      </StudentLayout>
    </ProtectedRoute>
  )
}
