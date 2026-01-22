'use client'

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { StudentLectureEngagement } from '@kunal-ak23/edudron-shared-utils'

interface StudentEngagementTableProps {
  data: StudentLectureEngagement[]
}

export function StudentEngagementTable({ data }: StudentEngagementTableProps) {
  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}m ${secs}s`
  }

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return '—'
    return new Date(dateStr).toLocaleDateString()
  }

  return (
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Student Email</TableHead>
            <TableHead>Total Sessions</TableHead>
            <TableHead>Total Duration</TableHead>
            <TableHead>Avg Session</TableHead>
            <TableHead>First View</TableHead>
            <TableHead>Last View</TableHead>
            <TableHead>Completed</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.length === 0 ? (
            <TableRow>
              <TableCell colSpan={7} className="text-center text-gray-500 py-8">
                No student engagement data available
              </TableCell>
            </TableRow>
          ) : (
            data.map((engagement, index) => (
              <TableRow key={engagement.studentId || index}>
                <TableCell className="font-medium">
                  {engagement.studentEmail || engagement.studentId}
                </TableCell>
                <TableCell>{engagement.totalSessions}</TableCell>
                <TableCell>{formatDuration(engagement.totalDurationSeconds || 0)}</TableCell>
                <TableCell>{formatDuration(engagement.averageSessionDurationSeconds || 0)}</TableCell>
                <TableCell>{formatDate(engagement.firstViewAt)}</TableCell>
                <TableCell>{formatDate(engagement.lastViewAt)}</TableCell>
                <TableCell>
                  {engagement.isCompleted ? (
                    <span className="text-green-600">✓</span>
                  ) : (
                    <span className="text-gray-400">—</span>
                  )}
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  )
}
