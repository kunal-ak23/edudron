'use client'

import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import type { LectureEngagementSummary } from '@kunal-ak23/edudron-shared-utils'

interface DurationDistributionChartProps {
  data: LectureEngagementSummary[]
}

export function DurationDistributionChart({ data }: DurationDistributionChartProps) {
  const chartData = data.map(lecture => {
    // Use lecture title, or fallback to a formatted version of the ID if title is missing/equals ID
    const displayName = lecture.lectureTitle && lecture.lectureTitle !== lecture.lectureId
      ? lecture.lectureTitle
      : `Lecture ${lecture.lectureId.substring(0, 8)}...`
    
    return {
      name: displayName.length > 30 
        ? displayName.substring(0, 30) + '...' 
        : displayName,
      fullName: displayName, // Keep full name for tooltip
      duration: Math.floor((lecture.averageDurationSeconds || 0) / 60) // Convert to minutes
    }
  })

  const CustomTooltip = ({ active, payload }: any) => {
    if (active && payload && payload.length) {
      const data = payload[0].payload
      return (
        <div className="bg-white p-3 border border-gray-200 rounded shadow-lg">
          <p className="font-semibold mb-2">{data.fullName}</p>
          <p style={{ color: payload[0].color }}>
            Avg Duration: {payload[0].value} minutes
          </p>
        </div>
      )
    }
    return null
  }

  return (
    <ResponsiveContainer width="100%" height={300}>
      <BarChart data={chartData} margin={{ bottom: 80 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis 
          dataKey="name" 
          angle={-45} 
          textAnchor="end" 
          height={120}
          interval={0}
          tick={{ fontSize: 11 }}
        />
        <YAxis label={{ value: 'Minutes', angle: -90, position: 'insideLeft' }} />
        <Tooltip content={<CustomTooltip />} />
        <Bar dataKey="duration" fill="#82ca9d" name="Avg Duration (min)" />
      </BarChart>
    </ResponsiveContainer>
  )
}
