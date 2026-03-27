'use client'

import { useState } from 'react'
import { TrendingUp, TrendingDown, Minus } from 'lucide-react'
import { LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import type { ChoiceDTO } from '@kunal-ak23/edudron-shared-utils'

interface DashboardConfig {
  metrics: Array<{ label: string; value: string; trend: string; change: string }>
  chartData?: {
    type: 'line' | 'bar'
    title: string
    labels: string[]
    datasets: Array<{ label: string; data: number[]; color: string }>
  }
  question: string
}

interface DashboardAnalysisInputProps {
  config: DashboardConfig
  choices?: ChoiceDTO[]
  onSubmit: (data: { choiceId: string }) => void
  disabled?: boolean
}

export function DashboardAnalysisInput({ config, choices = [], onSubmit, disabled }: DashboardAnalysisInputProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const TrendIcon = ({ trend }: { trend: string }) => {
    if (trend === 'up') return <TrendingUp className="w-4 h-4 text-green-400" />
    if (trend === 'down') return <TrendingDown className="w-4 h-4 text-red-400" />
    return <Minus className="w-4 h-4 text-[#94A3B8]" />
  }

  const chartData = config.chartData ? config.chartData.labels.map((label, i) => {
    const point: Record<string, any> = { name: label }
    config.chartData!.datasets.forEach(ds => { point[ds.label] = ds.data[i] })
    return point
  }) : null

  return (
    <div className="bg-[#222a3d] rounded-xl p-5 space-y-5">
      <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">
        Dashboard Analysis
      </h3>

      {/* Metrics Cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        {config.metrics.map((m, i) => (
          <div key={i} className="bg-[#1A2744] border border-white/5 rounded-lg p-3">
            <p className="text-[10px] uppercase tracking-widest text-slate-400 font-bold mb-1">{m.label}</p>
            <div className="flex items-center gap-2">
              <span className="text-lg font-mono text-[#dbe2fb]">{m.value}</span>
              <TrendIcon trend={m.trend} />
            </div>
            <p className={`text-xs mt-1 ${m.trend === 'up' ? 'text-green-400' : m.trend === 'down' ? 'text-red-400' : 'text-[#94A3B8]'}`}>
              {m.change}
            </p>
          </div>
        ))}
      </div>

      {/* Chart */}
      {chartData && config.chartData && (
        <div className="bg-[#1A2744] border border-white/5 rounded-lg p-4">
          <p className="text-sm text-[#94A3B8] mb-3">{config.chartData.title}</p>
          <ResponsiveContainer width="100%" height={200}>
            {config.chartData.type === 'bar' ? (
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1E3A5F" />
                <XAxis dataKey="name" tick={{ fill: '#94A3B8', fontSize: 12 }} />
                <YAxis tick={{ fill: '#94A3B8', fontSize: 12 }} />
                <Tooltip contentStyle={{ backgroundColor: '#1A2744', border: '1px solid #1E3A5F', color: '#E2E8F0' }} />
                <Legend wrapperStyle={{ color: '#94A3B8' }} />
                {config.chartData.datasets.map(ds => (
                  <Bar key={ds.label} dataKey={ds.label} fill={ds.color} />
                ))}
              </BarChart>
            ) : (
              <LineChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1E3A5F" />
                <XAxis dataKey="name" tick={{ fill: '#94A3B8', fontSize: 12 }} />
                <YAxis tick={{ fill: '#94A3B8', fontSize: 12 }} />
                <Tooltip contentStyle={{ backgroundColor: '#1A2744', border: '1px solid #1E3A5F', color: '#E2E8F0' }} />
                <Legend wrapperStyle={{ color: '#94A3B8' }} />
                {config.chartData.datasets.map(ds => (
                  <Line key={ds.label} type="monotone" dataKey={ds.label} stroke={ds.color} strokeWidth={2} />
                ))}
              </LineChart>
            )}
          </ResponsiveContainer>
        </div>
      )}

      {/* Question + Choices */}
      <p className="text-sm text-[#dbe2fb] font-medium">{config.question}</p>
      <div className="space-y-3">
        {choices.map((choice) => (
          <button
            key={choice.id}
            type="button"
            className={`w-full text-left rounded-xl p-4 border transition-all duration-200 ${
              selectedId === choice.id
                ? 'bg-[#1A2744] border-[#6cd3f7]/50 ring-1 ring-[#6cd3f7]/30'
                : 'bg-[#1A2744] border-white/5 hover:border-[#6cd3f7]/30'
            } ${disabled ? 'opacity-60 pointer-events-none' : 'cursor-pointer'}`}
            onClick={() => !disabled && setSelectedId(choice.id)}
          >
            <p className="text-sm text-[#dbe2fb]">{choice.text}</p>
          </button>
        ))}
      </div>

      <button
        type="button"
        onClick={() => selectedId && onSubmit({ choiceId: selectedId })}
        disabled={!selectedId || disabled}
        className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none disabled:active:scale-100 rounded-lg"
      >
        Confirm Decision
      </button>
    </div>
  )
}
