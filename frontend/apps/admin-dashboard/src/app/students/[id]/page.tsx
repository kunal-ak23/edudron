'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import Link from 'next/link'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Loader2, ArrowLeft, Mail, Phone, User, KeyRound, Copy, Check, Pencil, BookOpen, ClipboardList } from 'lucide-react'
import { apiClient, enrollmentsApi, coursesApi, institutesApi } from '@/lib/api'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import type { Enrollment } from '@kunal-ak23/edudron-shared-utils'

export const dynamic = 'force-dynamic'

interface StudentDetail {
  id: string
  email: string
  name: string
  phone?: string
  role: string
  active: boolean
  passwordResetRequired?: boolean
  instituteIds?: string[]
  createdAt: string
  lastLoginAt?: string
  classId?: string
  className?: string
  sectionId?: string
  sectionName?: string
}

interface CourseMap {
  [courseId: string]: { title: string; progress?: number }
}

interface StudentSubmission {
  id: string
  assessmentId: string
  courseId?: string
  score: number | null
  maxScore: number | null
  percentage: number | null
  isPassed: boolean | null
  submittedAt: string | null
  completedAt?: string | null
}

export default function StudentDetailPage() {
  const router = useRouter()
  const params = useParams()
  const studentId = params?.id as string
  const { toast } = useToast()
  const { user, isAuthenticated } = useAuth()

  const [student, setStudent] = useState<StudentDetail | null>(null)
  const [enrollments, setEnrollments] = useState<Enrollment[]>([])
  const [submissions, setSubmissions] = useState<StudentSubmission[]>([])
  const [courseNames, setCourseNames] = useState<CourseMap>({})
  const [examTitles, setExamTitles] = useState<Record<string, string>>({})
  const [instituteNames, setInstituteNames] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)

  const [showResetConfirmDialog, setShowResetConfirmDialog] = useState(false)
  const [showResetSuccessDialog, setShowResetSuccessDialog] = useState(false)
  const [temporaryPassword, setTemporaryPassword] = useState('')
  const [resettingPassword, setResettingPassword] = useState(false)
  const [passwordCopied, setPasswordCopied] = useState(false)

  const isSystemAdmin = user?.role === 'SYSTEM_ADMIN'
  const isTenantAdmin = user?.role === 'TENANT_ADMIN'
  const canManageUsers = isSystemAdmin || isTenantAdmin

  const canResetPassword = (target: StudentDetail): boolean => {
    if (!canManageUsers) return false
    if (isTenantAdmin) return ['INSTRUCTOR', 'SUPPORT_STAFF', 'STUDENT'].includes(target.role)
    return true
  }

  const loadStudent = useCallback(async () => {
    if (!studentId) return
    try {
      setLoading(true)
      setNotFound(false)
      const response = await apiClient.get<StudentDetail>(`/idp/users/${studentId}`)
      const userData = response.data
      if (userData.role !== 'STUDENT') {
        setNotFound(true)
        setStudent(null)
        return
      }
      setStudent(userData)
      return userData
    } catch (err: any) {
      if (err?.response?.status === 404 || err?.response?.status === 403) {
        setNotFound(true)
        setStudent(null)
      } else {
        const errorMessage = extractErrorMessage(err)
        toast({
          variant: 'destructive',
          title: 'Failed to load student',
          description: errorMessage,
        })
        router.push('/students')
      }
    } finally {
      setLoading(false)
    }
  }, [studentId, toast, router])

  const loadEnrollments = useCallback(async (email: string, sid: string) => {
    try {
      const result = await enrollmentsApi.listAllEnrollmentsPaginated(0, 50, { email })
      setEnrollments(result.content || [])
      const courseIds = [...new Set((result.content || []).map((e) => e.courseId).filter(Boolean))]
      const map: CourseMap = {}
      await Promise.allSettled(
        courseIds.map(async (courseId) => {
          let title = courseId
          let progress = undefined
          try {
            const [courseRes, progressRes] = await Promise.allSettled([
              coursesApi.getCourse(courseId),
              apiClient.get(`/api/students/${sid}/courses/${courseId}/progress`)
            ])
            if (courseRes.status === 'fulfilled') {
              title = courseRes.value.title || courseId
            }
            if (progressRes.status === 'fulfilled') {
              progress = progressRes.value.data?.completionPercentage
            }
          } catch {
            // Failed requests are caught and ignored
          }
          map[courseId] = { title, progress }
        })
      )
      setCourseNames(map)
    } catch {
      setEnrollments([])
    }
  }, [])

  const loadInstituteNames = useCallback(async (instituteIds: string[] | undefined) => {
    if (!instituteIds?.length) return
    try {
      const list = await institutesApi.listInstitutes()
      const map: Record<string, string> = {}
      instituteIds.forEach((id) => {
        const inst = list.find((i) => i.id === id)
        map[id] = inst?.name || id
      })
      setInstituteNames(map)
    } catch {
      setInstituteNames({})
    }
  }, [])

  const loadSubmissions = useCallback(async (sid: string) => {
    try {
      const response = await apiClient.get<StudentSubmission[] | { content?: StudentSubmission[] }>(
        `/api/students/${sid}/submissions`
      )
      const raw = response.data
      const list = Array.isArray(raw) ? raw : (raw as { content?: StudentSubmission[] })?.content ?? []
      setSubmissions(list)
      const assessmentIds = [...new Set(list.map((s) => s.assessmentId).filter(Boolean))]
      const titles: Record<string, string> = {}
      await Promise.allSettled(
        assessmentIds.map(async (assessmentId) => {
          try {
            const examRes = await apiClient.get<{ title?: string }>(`/api/exams/${assessmentId}`)
            const title = (examRes.data as any)?.title ?? examRes.data?.title ?? assessmentId
            titles[assessmentId] = title
          } catch {
            titles[assessmentId] = assessmentId
          }
        })
      )
      setExamTitles(titles)
    } catch {
      setSubmissions([])
      setExamTitles({})
    }
  }, [])

  useEffect(() => {
    if (!isAuthenticated() || !user) {
      router.push('/login')
      return
    }
    const allowedRoles = ['SYSTEM_ADMIN', 'TENANT_ADMIN', 'INSTRUCTOR']
    if (!allowedRoles.includes(user.role)) {
      router.push('/unauthorized')
      return
    }
  }, [user, isAuthenticated, router])

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      const userData = await loadStudent()
      if (cancelled) return
      if (userData?.email) {
        await loadEnrollments(userData.email, studentId)
        await loadInstituteNames(userData.instituteIds)
      }
      if (studentId) await loadSubmissions(studentId)
    }
    run()
    return () => {
      cancelled = true
    }
  }, [studentId, loadStudent, loadEnrollments, loadInstituteNames, loadSubmissions])

  const handleResetPasswordClick = () => {
    setShowResetConfirmDialog(true)
  }

  const handleResetPassword = async () => {
    if (!student) return
    setResettingPassword(true)
    try {
      const response = await apiClient.post<{ temporaryPassword: string; message?: string }>(
        `/idp/users/${student.id}/reset-password`
      )
      const tempPw = response.data?.temporaryPassword ?? (response as any).data?.data?.temporaryPassword
      setTemporaryPassword(tempPw || '')
      setShowResetConfirmDialog(false)
      setShowResetSuccessDialog(true)
      setPasswordCopied(false)
      toast({
        title: 'Password reset successful',
        description: `Password has been reset for ${student.name}`,
      })
    } catch (err: any) {
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Failed to reset password',
        description: errorMessage,
      })
      setShowResetConfirmDialog(false)
    } finally {
      setResettingPassword(false)
    }
  }

  const handleCopyPassword = async () => {
    try {
      await navigator.clipboard.writeText(temporaryPassword)
      setPasswordCopied(true)
      toast({
        title: 'Copied to clipboard',
        description: 'Temporary password has been copied to your clipboard',
      })
      setTimeout(() => setPasswordCopied(false), 2000)
    } catch {
      toast({
        variant: 'destructive',
        title: 'Failed to copy',
        description: 'Please manually select and copy the password',
      })
    }
  }

  const handleCloseSuccessDialog = () => {
    setShowResetSuccessDialog(false)
    setTemporaryPassword('')
    setPasswordCopied(false)
  }

  if (!user || !isAuthenticated()) {
    return null
  }

  const allowedRoles = ['SYSTEM_ADMIN', 'TENANT_ADMIN', 'INSTRUCTOR']
  if (!allowedRoles.includes(user.role)) {
    return null
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (notFound || !student) {
    return (
      <div className="space-y-4">
        <Link href="/students">
          <Button variant="ghost">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Students
          </Button>
        </Link>
        <Card>
          <CardContent className="py-12 text-center">
            <User className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
            <p className="text-muted-foreground">Student not found.</p>
            <Button variant="outline" className="mt-4" onClick={() => router.push('/students')}>
              Back to Students
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div>
      <div className="mb-4">
        <Link href="/students">
          <Button variant="ghost">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Students
          </Button>
        </Link>
      </div>

      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold">{student.name}</h1>
          <Badge variant={student.active ? 'default' : 'secondary'}>
            {student.active ? 'Active' : 'Inactive'}
          </Badge>
        </div>
        <div className="flex items-center gap-2">
          {canManageUsers && (
            <Button variant="outline" size="sm" onClick={() => router.push(`/users/${student.id}/edit`)}>
              <Pencil className="h-4 w-4 mr-2" />
              Edit
            </Button>
          )}
          {canResetPassword(student) && (
            <Button variant="outline" size="sm" onClick={handleResetPasswordClick}>
              <KeyRound className="h-4 w-4 mr-2" />
              Reset Password
            </Button>
          )}
        </div>
      </div>

      <div className="grid gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Profile</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="flex items-center gap-2">
                <Mail className="h-4 w-4 text-muted-foreground" />
                <span>{student.email}</span>
              </div>
              {student.phone && (
                <div className="flex items-center gap-2">
                  <Phone className="h-4 w-4 text-muted-foreground" />
                  <span>{student.phone}</span>
                </div>
              )}
              <div>
                <span className="text-muted-foreground">Role: </span>
                <Badge variant="outline">{student.role}</Badge>
              </div>
              <div>
                <span className="text-muted-foreground">Created: </span>
                {student.createdAt ? new Date(student.createdAt).toLocaleDateString() : '—'}
              </div>
              <div>
                <span className="text-muted-foreground">Last login: </span>
                {student.lastLoginAt ? new Date(student.lastLoginAt).toLocaleDateString() : 'Never'}
              </div>
              {student.passwordResetRequired && (
                <div className="sm:col-span-2">
                  <Badge variant="secondary">Password reset required</Badge>
                </div>
              )}
              {student.instituteIds?.length ? (
                <div className="sm:col-span-2">
                  <span className="text-muted-foreground">Institutes: </span>
                  {student.instituteIds.map((id) => (
                    <span key={id} className="mr-2">
                      {instituteNames[id] ?? id}
                    </span>
                  ))}
                </div>
              ) : null}
              {(student.className || student.sectionName) && (
                <div className="sm:col-span-2">
                  {student.className && (
                    <>
                      <span className="text-muted-foreground">Class: </span>
                      <span className="mr-4">{student.className}</span>
                    </>
                  )}
                  {student.sectionName && (
                    <>
                      <span className="text-muted-foreground">Section: </span>
                      <span>{student.sectionName}</span>
                    </>
                  )}
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BookOpen className="h-5 w-5" />
              Enrollments
            </CardTitle>
          </CardHeader>
          <CardContent>
            {enrollments.length === 0 ? (
              <p className="text-muted-foreground">No enrollments.</p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Course</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Progress</TableHead>
                    <TableHead>Enrolled</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {enrollments.map((enrollment) => {
                    const progressVal = courseNames[enrollment.courseId]?.progress ?? 0
                    return (
                      <TableRow key={enrollment.id}>
                        <TableCell className="font-medium">
                          <Link
                            href={`/students/${studentId}/courses/${enrollment.courseId}`}
                            className="text-primary hover:underline"
                          >
                            {courseNames[enrollment.courseId]?.title ?? enrollment.courseId}
                          </Link>
                        </TableCell>
                        <TableCell>
                          {enrollment.status ? (
                            <Badge variant={enrollment.status === 'ACTIVE' ? 'default' : 'secondary'}>
                              {enrollment.status}
                            </Badge>
                          ) : (
                            <span className="text-muted-foreground">—</span>
                          )}
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <Progress value={progressVal} className="w-[60px] h-2" />
                            <span className="text-xs text-muted-foreground">{Math.round(progressVal)}%</span>
                          </div>
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {enrollment.enrolledAt
                            ? new Date(enrollment.enrolledAt).toLocaleDateString()
                            : '—'}
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            )}
            {canManageUsers && (
              <div className="mt-4">
                <Link href="/enrollments">
                  <Button variant="outline" size="sm">
                    Manage enrollments
                  </Button>
                </Link>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <ClipboardList className="h-5 w-5" />
              Assessment results
            </CardTitle>
          </CardHeader>
          <CardContent>
            {submissions.length === 0 ? (
              <p className="text-muted-foreground">No assessment submissions yet.</p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Exam</TableHead>
                    <TableHead>Score</TableHead>
                    <TableHead>Result</TableHead>
                    <TableHead>Submitted</TableHead>
                    {canManageUsers && <TableHead className="w-24">View</TableHead>}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {submissions.map((sub) => (
                    <TableRow key={sub.id}>
                      <TableCell className="font-medium">
                        <Link
                          href={`/exams/${sub.assessmentId}`}
                          className="text-primary hover:underline"
                        >
                          {examTitles[sub.assessmentId] ?? sub.assessmentId}
                        </Link>
                      </TableCell>
                      <TableCell>
                        {sub.score != null && sub.maxScore != null
                          ? `${sub.score} / ${sub.maxScore}`
                          : sub.percentage != null
                            ? `${Math.round(Number(sub.percentage))}%`
                            : '—'}
                      </TableCell>
                      <TableCell>
                        {sub.isPassed != null ? (
                          <Badge variant={sub.isPassed ? 'default' : 'destructive'}>
                            {sub.isPassed ? 'Passed' : 'Failed'}
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {sub.submittedAt || sub.completedAt
                          ? new Date(sub.submittedAt || sub.completedAt!).toLocaleString()
                          : '—'}
                      </TableCell>
                      {canManageUsers && (
                        <TableCell>
                          <Link href={`/exams/${sub.assessmentId}/submissions`}>
                            <Button variant="ghost" size="sm">
                              Submissions
                            </Button>
                          </Link>
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </div>

      <AlertDialog open={showResetConfirmDialog} onOpenChange={setShowResetConfirmDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Reset Password</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to reset the password for <strong>{student.name}</strong> ({student.email})?
              <br />
              <br />
              A new temporary password will be generated. The user will be required to change their password on next login.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={resettingPassword}>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleResetPassword} disabled={resettingPassword}>
              {resettingPassword ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  Resetting...
                </>
              ) : (
                'Reset Password'
              )}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={showResetSuccessDialog} onOpenChange={handleCloseSuccessDialog}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Password Reset Successful</DialogTitle>
            <DialogDescription>
              The password for <strong>{student.name}</strong> has been reset.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 dark:bg-amber-950/30 dark:border-amber-800">
              <p className="text-sm text-amber-800 dark:text-amber-200 font-medium mb-2">
                Important: This password will only be shown once
              </p>
              <p className="text-sm text-amber-700 dark:text-amber-300">
                Please copy and share this temporary password with the user securely. They will be required to change it on their next login.
              </p>
            </div>
            <div className="space-y-2">
              <Label className="text-sm font-medium">Temporary Password</Label>
              <div className="flex items-center gap-2">
                <Input value={temporaryPassword} readOnly className="font-mono text-lg" />
                <Button variant="outline" size="icon" onClick={handleCopyPassword} className="shrink-0">
                  {passwordCopied ? (
                    <Check className="h-4 w-4 text-green-600" />
                  ) : (
                    <Copy className="h-4 w-4" />
                  )}
                </Button>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button onClick={handleCloseSuccessDialog}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
