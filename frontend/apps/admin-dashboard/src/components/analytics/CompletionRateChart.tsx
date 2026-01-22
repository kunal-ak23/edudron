'use client'

import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import type { LectureEngagementSummary } from '@kunal-ak23/edudron-shared-utils'

interface CompletionRateChartProps {
  data: LectureEngagementSummary[]
}

export function CompletionRateChart({ data }: CompletionRateChartProps) {
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
      completionRate: Number((lecture.completionRate ?? 0).toFixed(1)),
      skipRate: Number((lecture.skipRate ?? 0).toFixed(1))
    }
  })

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      const data = payload[0].payload
      return (
        <div className="bg-white p-3 border border-gray-200 rounded shadow-lg">
          <p className="font-semibold mb-2">{data.fullName}</p>
          {payload.map((entry: any, index: number) => (
            <p key={index} style={{ color: entry.color }}>
              {entry.name}: {entry.value}%
            </p>
          ))}
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
        <YAxis />
        <Tooltip content={<CustomTooltip />} />
        <Legend />
        <Bar dataKey="completionRate" fill="#8884d8" name="Completion %" />
        <Bar dataKey="skipRate" fill="#ff7300" name="Skip %" />
      </BarChart>
    </ResponsiveContainer>
  )
}
