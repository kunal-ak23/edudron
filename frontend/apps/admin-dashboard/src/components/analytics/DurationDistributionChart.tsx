'use client'

import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import type { LectureEngagementSummary } from '@kunal-ak23/edudron-shared-utils'

interface DurationDistributionChartProps {
  data: LectureEngagementSummary[]
}

export function DurationDistributionChart({ data }: DurationDistributionChartProps) {
  const chartData = data.map(lecture => ({
    name: lecture.lectureTitle.length > 20 
      ? lecture.lectureTitle.substring(0, 20) + '...' 
      : lecture.lectureTitle,
    duration: Math.floor(lecture.averageDurationSeconds / 60) // Convert to minutes
  }))

  return (
    <ResponsiveContainer width="100%" height={300}>
      <BarChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="name" angle={-45} textAnchor="end" height={100} />
        <YAxis label={{ value: 'Minutes', angle: -90, position: 'insideLeft' }} />
        <Tooltip formatter={(value: number) => `${value} minutes`} />
        <Bar dataKey="duration" fill="#82ca9d" name="Avg Duration (min)" />
      </BarChart>
    </ResponsiveContainer>
  )
}
