'use client'

import { useState } from 'react'

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
    <div className="bg-[#222a3d] rounded-xl p-5 space-y-5">
      <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">
        Tradeoff Decision
      </h3>

      <div className="flex items-center justify-between text-sm font-medium text-[#bdc8ce]">
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
        className="w-full h-1 appearance-none cursor-pointer rounded-full bg-[#2d3448] disabled:opacity-50 disabled:cursor-not-allowed"
        style={{ accentColor: '#6cd3f7' }}
      />

      <div className="text-center text-sm text-slate-400">
        Position: <span className="font-mono font-bold text-[#6cd3f7]">{value}%</span>
      </div>

      <button
        type="button"
        onClick={() => onSubmit({ input: { value } })}
        disabled={disabled}
        className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none disabled:active:scale-100 rounded-lg"
      >
        Confirm
      </button>
    </div>
  )
}
