'use client'

import { NarrativeChoiceInput } from './NarrativeChoiceInput'
import { BudgetAllocationInput } from './BudgetAllocationInput'
import { PriorityRankingInput } from './PriorityRankingInput'
import { TradeoffSliderInput } from './TradeoffSliderInput'
import { ResourceAssignmentInput } from './ResourceAssignmentInput'
import { TimelineChoiceInput } from './TimelineChoiceInput'
import { CompoundInput } from './CompoundInput'
import { NegotiationInput } from './NegotiationInput'
import { DashboardAnalysisInput } from './DashboardAnalysisInput'
import { HireFireInput } from './HireFireInput'
import { CrisisResponseInput } from './CrisisResponseInput'
import { InvestmentPortfolioInput } from './InvestmentPortfolioInput'
import { StakeholderMeetingInput } from './StakeholderMeetingInput'
import type { ChoiceDTO } from '@kunal-ak23/edudron-shared-utils'

interface DecisionInputProps {
  decisionType?: string
  decisionConfig?: any
  choices?: ChoiceDTO[]
  onSubmit: (data: any) => void
  disabled?: boolean
}

export function DecisionInput({ decisionType, decisionConfig, choices, onSubmit, disabled }: DecisionInputProps) {
  const config = decisionConfig || {}
  const fallback = <NarrativeChoiceInput choices={choices ?? []} onSubmit={onSubmit} disabled={disabled} />

  // Validate config has required fields for interactive types — fall back to narrative if missing
  switch (decisionType) {
    case 'BUDGET_ALLOCATION':
      if (!config.buckets?.length) return fallback
      return <BudgetAllocationInput config={config} onSubmit={onSubmit} disabled={disabled} />
    case 'PRIORITY_RANKING':
      if (!config.items?.length) return fallback
      return <PriorityRankingInput config={config} onSubmit={onSubmit} disabled={disabled} />
    case 'TRADEOFF_SLIDER':
      if (!config.leftLabel && !config.label) return fallback
      return <TradeoffSliderInput config={config} onSubmit={onSubmit} disabled={disabled} />
    case 'RESOURCE_ASSIGNMENT':
      if (!config.buckets?.length) return fallback
      return <ResourceAssignmentInput config={config} onSubmit={onSubmit} disabled={disabled} />
    case 'TIMELINE_CHOICE':
      if (!config.milestones?.length) return fallback
      return <TimelineChoiceInput config={config} onSubmit={onSubmit} disabled={disabled} />
    case 'COMPOUND':
      if (!config.steps?.length) return fallback
      return <CompoundInput config={config} onSubmit={onSubmit} disabled={disabled} />
    case 'NEGOTIATION':
      if (!config.npcResponses?.length) return fallback
      return <NegotiationInput config={config} onSubmit={onSubmit} disabled={disabled} />
    case 'DASHBOARD_ANALYSIS':
      if (!config.metrics?.length) return fallback
      return <DashboardAnalysisInput config={config} choices={choices} onSubmit={onSubmit} disabled={disabled} />
    case 'HIRE_FIRE':
      if (!config.candidates?.length) return fallback
      return <HireFireInput config={config} onSubmit={onSubmit} disabled={disabled} />
    case 'CRISIS_RESPONSE':
      return <CrisisResponseInput config={config} choices={choices} onSubmit={onSubmit} disabled={disabled} />
    case 'INVESTMENT_PORTFOLIO':
      if (!config.departments?.length) return fallback
      return <InvestmentPortfolioInput config={config} onSubmit={onSubmit} disabled={disabled} />
    case 'STAKEHOLDER_MEETING':
      if (!config.stakeholders?.length) return fallback
      return <StakeholderMeetingInput config={config} onSubmit={onSubmit} disabled={disabled} />
    case 'NARRATIVE_CHOICE':
    default:
      return fallback
  }
}
