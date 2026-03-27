'use client'

import { useState, useCallback } from 'react'
import { ArrowUp, ArrowDown } from 'lucide-react'

interface RankingConfig {
  items: Array<{ id: string; label: string }>
}

interface PriorityRankingInputProps {
  config: RankingConfig
  onSubmit: (data: { input: { ranking: string[] } }) => void
  disabled?: boolean
}

export function PriorityRankingInput({ config, onSubmit, disabled }: PriorityRankingInputProps) {
  const [items, setItems] = useState(() => [...(config.items ?? [])])

  const moveUp = useCallback((index: number) => {
    if (index <= 0) return
    setItems((prev) => {
      const next = [...prev]
      ;[next[index - 1], next[index]] = [next[index], next[index - 1]]
      return next
    })
  }, [])

  const moveDown = useCallback((index: number) => {
    setItems((prev) => {
      if (index >= prev.length - 1) return prev
      const next = [...prev]
      ;[next[index], next[index + 1]] = [next[index + 1], next[index]]
      return next
    })
  }, [])

  return (
    <div className="bg-[#222a3d] rounded-xl p-5 space-y-3">
      <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">
        Priority Ranking
      </h3>

      {items.map((item, index) => (
        <div
          key={item.id}
          className="bg-[#1A2744] border border-white/5 rounded-xl p-3 flex items-center gap-3 transition-all duration-150"
        >
          <span className="flex-shrink-0 w-7 h-7 rounded-full bg-[#6cd3f7]/10 text-[#6cd3f7] flex items-center justify-center text-sm font-bold">
            {index + 1}
          </span>
          <span className="flex-1 text-sm text-[#dbe2fb]">{item.label}</span>
          <div className="flex gap-1">
            <button
              type="button"
              onClick={() => moveUp(index)}
              disabled={index === 0 || disabled}
              className="p-1.5 rounded hover:bg-white/5 disabled:opacity-30 disabled:cursor-not-allowed transition-colors text-slate-400 hover:text-[#dbe2fb]"
              aria-label={`Move ${item.label} up`}
            >
              <ArrowUp className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={() => moveDown(index)}
              disabled={index === items.length - 1 || disabled}
              className="p-1.5 rounded hover:bg-white/5 disabled:opacity-30 disabled:cursor-not-allowed transition-colors text-slate-400 hover:text-[#dbe2fb]"
              aria-label={`Move ${item.label} down`}
            >
              <ArrowDown className="h-4 w-4" />
            </button>
          </div>
        </div>
      ))}

      <div className="pt-1">
        <button
          type="button"
          onClick={() => onSubmit({ input: { ranking: items.map((i) => i.id) } })}
          disabled={disabled}
          className="w-full px-10 py-4 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-sm hover:brightness-110 active:scale-95 transition-all shadow-[0_0_20px_rgba(108,211,247,0.3)] disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none disabled:active:scale-100 rounded-lg"
        >
          Confirm Ranking
        </button>
      </div>
    </div>
  )
}
