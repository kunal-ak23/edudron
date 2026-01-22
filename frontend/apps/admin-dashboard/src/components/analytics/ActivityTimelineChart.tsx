'use client'

import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import type { ActivityTimelinePoint } from '@kunal-ak23/edudron-shared-utils'

interface ActivityTimelineChartProps {
  data: ActivityTimelinePoint[]
}

export function ActivityTimelineChart({ data }: ActivityTimelineChartProps) {
  const chartData = data.map(point => ({
    date: new Date(point.timestamp).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
    sessions: point.sessionCount,
    students: point.uniqueStudents
  }))

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="date" />
        <YAxis />
        <Tooltip />
        <Legend />
        <Line type="monotone" dataKey="sessions" stroke="#8884d8" name="Sessions" />
        <Line type="monotone" dataKey="students" stroke="#82ca9d" name="Unique Students" />
      </LineChart>
    </ResponsiveContainer>
  )
}
