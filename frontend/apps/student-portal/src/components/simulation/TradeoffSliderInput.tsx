'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'

interface TradeoffConfig {
  leftLabel: string
  rightLabel: string
  defaultValue?: number
}

interface TradeoffSliderInputProps {
  config: TradeoffConfig
  onSubmit: (data: { input: { value: number } }) => void
  disabled?: boolean
}

export function TradeoffSliderInput({ config, onSubmit, disabled }: TradeoffSliderInputProps) {
  const [value, setValue] = useState(config.defaultValue ?? 50)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between text-sm font-medium text-gray-600 mb-1">
        <span>{config.leftLabel}</span>
        <span>{config.rightLabel}</span>
      </div>

      <input
        type="range"
        min={0}
        max={100}
        value={value}
        onChange={(e) => setValue(parseInt(e.target.value, 10))}
        disabled={disabled}
        className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-primary-600 disabled:opacity-50"
      />

      <div className="text-center text-sm text-gray-500">
        Position: <span className="font-mono font-medium">{value}%</span>
      </div>

      <Button
        onClick={() => onSubmit({ input: { value } })}
        disabled={disabled}
        className="w-full"
      >
        Confirm
      </Button>
    </div>
  )
}
