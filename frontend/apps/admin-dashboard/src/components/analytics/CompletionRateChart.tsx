'use client'

import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import type { LectureEngagementSummary } from '@kunal-ak23/edudron-shared-utils'

interface CompletionRateChartProps {
  data: LectureEngagementSummary[]
}

export function CompletionRateChart({ data }: CompletionRateChartProps) {
  const chartData = data.map(lecture => ({
    name: lecture.lectureTitle.length > 20 
      ? lecture.lectureTitle.substring(0, 20) + '...' 
      : lecture.lectureTitle,
    completionRate: Number(lecture.completionRate.toFixed(1)),
    skipRate: Number(lecture.skipRate.toFixed(1))
  }))

  return (
    <ResponsiveContainer width="100%" height={300}>
      <BarChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="name" angle={-45} textAnchor="end" height={100} />
        <YAxis />
        <Tooltip />
        <Legend />
        <Bar dataKey="completionRate" fill="#8884d8" name="Completion %" />
        <Bar dataKey="skipRate" fill="#ff7300" name="Skip %" />
      </BarChart>
    </ResponsiveContainer>
  )
}
