'use client'

import { NarrativeChoiceInput } from './NarrativeChoiceInput'
import { BudgetAllocationInput } from './BudgetAllocationInput'
import { PriorityRankingInput } from './PriorityRankingInput'
import { TradeoffSliderInput } from './TradeoffSliderInput'
import { ResourceAssignmentInput } from './ResourceAssignmentInput'
import { TimelineChoiceInput } from './TimelineChoiceInput'
import { CompoundInput } from './CompoundInput'
import type { ChoiceDTO } from '@kunal-ak23/edudron-shared-utils'

interface DecisionInputProps {
  decisionType?: string
  decisionConfig?: any
  choices?: ChoiceDTO[]
  onSubmit: (data: any) => void
  disabled?: boolean
}

export function DecisionInput({ decisionType, decisionConfig, choices, onSubmit, disabled }: DecisionInputProps) {
  switch (decisionType) {
    case 'BUDGET_ALLOCATION':
      return <BudgetAllocationInput config={decisionConfig} onSubmit={onSubmit} disabled={disabled} />
    case 'PRIORITY_RANKING':
      return <PriorityRankingInput config={decisionConfig} onSubmit={onSubmit} disabled={disabled} />
    case 'TRADEOFF_SLIDER':
      return <TradeoffSliderInput config={decisionConfig} onSubmit={onSubmit} disabled={disabled} />
    case 'RESOURCE_ASSIGNMENT':
      return <ResourceAssignmentInput config={decisionConfig} onSubmit={onSubmit} disabled={disabled} />
    case 'TIMELINE_CHOICE':
      return <TimelineChoiceInput config={decisionConfig} onSubmit={onSubmit} disabled={disabled} />
    case 'COMPOUND':
      return <CompoundInput config={decisionConfig} onSubmit={onSubmit} disabled={disabled} />
    case 'NARRATIVE_CHOICE':
    default:
      return <NarrativeChoiceInput choices={choices ?? []} onSubmit={onSubmit} disabled={disabled} />
  }
}
