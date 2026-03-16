'use client'

import { useState } from 'react'
import { ChevronDown, ChevronRight, BookOpen, MessageSquare, Star } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

export interface SelectedItem {
  type: 'decision' | 'review' | 'opening'
  year: number
  index?: number // decision index within the year
}

interface SimulationYearViewProps {
  simulationData: any
  selectedItem: SelectedItem | null
  onSelectItem: (item: SelectedItem) => void
}

export default function SimulationYearView({
  simulationData,
  selectedItem,
  onSelectItem,
}: SimulationYearViewProps) {
  const [expandedYears, setExpandedYears] = useState<Set<number>>(() => new Set([1]))

  if (!simulationData?.years) {
    return (
      <div className="p-4 text-center text-sm text-muted-foreground">
        No simulation data available. The simulation may still be generating.
      </div>
    )
  }

  const years: any[] = simulationData.years
  const totalDecisions = years.reduce(
    (sum: number, y: any) => sum + (y.decisions?.length || 0),
    0
  )

  const toggleYear = (yearNum: number) => {
    setExpandedYears((prev) => {
      const next = new Set(prev)
      if (next.has(yearNum)) {
        next.delete(yearNum)
      } else {
        next.add(yearNum)
      }
      return next
    })
  }

  const isSelected = (item: SelectedItem) =>
    selectedItem?.type === item.type &&
    selectedItem?.year === item.year &&
    selectedItem?.index === item.index

  return (
    <div className="flex flex-col h-full">
      <div className="px-3 py-2 border-b flex items-center justify-between">
        <h3 className="text-sm font-medium">Year Navigator</h3>
        <span className="text-xs text-muted-foreground">
          {years.length} years, {totalDecisions} decisions
        </span>
      </div>
      <div className="flex-1 overflow-y-auto p-2 space-y-1">
        {years.map((year: any, yearIdx: number) => {
          const yearNum = year.year ?? yearIdx + 1
          const isExpanded = expandedYears.has(yearNum)
          const decisions: any[] = year.decisions || []

          return (
            <div key={yearNum}>
              {/* Year header */}
              <button
                className="flex items-center gap-2 w-full py-1.5 px-2 rounded-md hover:bg-muted/60 text-left"
                onClick={() => toggleYear(yearNum)}
              >
                {isExpanded ? (
                  <ChevronDown className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                ) : (
                  <ChevronRight className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                )}
                <span className="text-sm font-medium">Year {yearNum}</span>
                <Badge variant="outline" className="text-[10px] px-1.5 py-0 ml-auto">
                  {decisions.length} decisions
                </Badge>
              </button>

              {/* Year items */}
              {isExpanded && (
                <div className="ml-4 space-y-0.5">
                  {/* Opening narrative */}
                  <button
                    className={cn(
                      'flex items-center gap-2 w-full py-1 px-2 rounded-md text-left text-sm transition-colors',
                      isSelected({ type: 'opening', year: yearNum })
                        ? 'bg-primary/10 ring-1 ring-primary/40'
                        : 'hover:bg-muted/60'
                    )}
                    onClick={() => onSelectItem({ type: 'opening', year: yearNum })}
                  >
                    <BookOpen className="h-3.5 w-3.5 text-blue-500 shrink-0" />
                    <span className="truncate">Opening Narrative</span>
                  </button>

                  {/* Decisions */}
                  {decisions.map((decision: any, dIdx: number) => {
                    const label = decision.narrative
                      ? decision.narrative.slice(0, 35) + (decision.narrative.length > 35 ? '...' : '')
                      : `Decision ${dIdx + 1}`

                    return (
                      <button
                        key={decision.decisionId || dIdx}
                        className={cn(
                          'flex items-center gap-2 w-full py-1 px-2 rounded-md text-left text-sm transition-colors',
                          isSelected({ type: 'decision', year: yearNum, index: dIdx })
                            ? 'bg-primary/10 ring-1 ring-primary/40'
                            : 'hover:bg-muted/60'
                        )}
                        onClick={() =>
                          onSelectItem({ type: 'decision', year: yearNum, index: dIdx })
                        }
                      >
                        <Star className="h-3.5 w-3.5 text-amber-500 shrink-0" />
                        <span className="truncate flex-1">{label}</span>
                        {decision.decisionType && decision.decisionType !== 'NARRATIVE_CHOICE' && (
                          <Badge
                            variant="outline"
                            className="text-[10px] px-1.5 py-0 bg-gray-50 text-gray-500 border-gray-200"
                          >
                            {decision.decisionType.replace(/_/g, ' ')}
                          </Badge>
                        )}
                      </button>
                    )
                  })}

                  {/* Year-end review */}
                  <button
                    className={cn(
                      'flex items-center gap-2 w-full py-1 px-2 rounded-md text-left text-sm transition-colors',
                      isSelected({ type: 'review', year: yearNum })
                        ? 'bg-primary/10 ring-1 ring-primary/40'
                        : 'hover:bg-muted/60'
                    )}
                    onClick={() => onSelectItem({ type: 'review', year: yearNum })}
                  >
                    <MessageSquare className="h-3.5 w-3.5 text-green-500 shrink-0" />
                    <span className="truncate">Year-End Review</span>
                  </button>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
