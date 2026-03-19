'use client'

import { useState, useCallback } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
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
    <div className="space-y-2">
      {items.map((item, index) => (
        <Card key={item.id} className="transition-all duration-150">
          <CardContent className="p-3 flex items-center gap-3">
            <span className="flex-shrink-0 w-7 h-7 rounded-full bg-primary-100 text-primary-700 flex items-center justify-center text-sm font-semibold">
              {index + 1}
            </span>
            <span className="flex-1 text-sm">{item.label}</span>
            <div className="flex gap-1">
              <button
                type="button"
                onClick={() => moveUp(index)}
                disabled={index === 0 || disabled}
                className="p-1.5 rounded hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                aria-label={`Move ${item.label} up`}
              >
                <ArrowUp className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={() => moveDown(index)}
                disabled={index === items.length - 1 || disabled}
                className="p-1.5 rounded hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                aria-label={`Move ${item.label} down`}
              >
                <ArrowDown className="h-4 w-4" />
              </button>
            </div>
          </CardContent>
        </Card>
      ))}

      <div className="pt-2">
        <Button
          onClick={() => onSubmit({ input: { ranking: items.map((i) => i.id) } })}
          disabled={disabled}
          className="w-full"
        >
          Confirm Ranking
        </Button>
      </div>
    </div>
  )
}
