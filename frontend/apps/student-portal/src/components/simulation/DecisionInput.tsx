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
import { MentorGuidanceBanner } from './MentorGuidanceBanner'
import type { ChoiceDTO, MentorGuidance } from '@kunal-ak23/edudron-shared-utils'

interface DecisionInputProps {
  decisionType?: string
  decisionConfig?: any
  choices?: ChoiceDTO[]
  onSubmit: (data: any) => void
  disabled?: boolean
  mentorGuidance?: MentorGuidance
}

export function DecisionInput({ decisionType, decisionConfig, choices, onSubmit, disabled, mentorGuidance }: DecisionInputProps) {
  const config = decisionConfig || {}
  const choiceHints = mentorGuidance?.choiceHints
  const fallback = <NarrativeChoiceInput choices={choices ?? []} onSubmit={onSubmit} disabled={disabled} choiceHints={choiceHints} />

  // Validate config has required fields for interactive types — fall back to narrative if missing
  let inputComponent: React.ReactNode
  switch (decisionType) {
    case 'BUDGET_ALLOCATION':
      inputComponent = !config.buckets?.length ? fallback
        : <BudgetAllocationInput config={config} onSubmit={onSubmit} disabled={disabled} />
      break
    case 'PRIORITY_RANKING':
      inputComponent = !config.items?.length ? fallback
        : <PriorityRankingInput config={config} onSubmit={onSubmit} disabled={disabled} />
      break
    case 'TRADEOFF_SLIDER':
      inputComponent = (!config.leftLabel && !config.label) ? fallback
        : <TradeoffSliderInput config={config} onSubmit={onSubmit} disabled={disabled} />
      break
    case 'RESOURCE_ASSIGNMENT':
      inputComponent = !config.buckets?.length ? fallback
        : <ResourceAssignmentInput config={config} onSubmit={onSubmit} disabled={disabled} />
      break
    case 'TIMELINE_CHOICE':
      inputComponent = !config.milestones?.length ? fallback
        : <TimelineChoiceInput config={config} onSubmit={onSubmit} disabled={disabled} />
      break
    case 'COMPOUND':
      inputComponent = !config.steps?.length ? fallback
        : <CompoundInput config={config} onSubmit={onSubmit} disabled={disabled} />
      break
    case 'NEGOTIATION':
      inputComponent = !config.npcResponses?.length ? fallback
        : <NegotiationInput config={config} onSubmit={onSubmit} disabled={disabled} />
      break
    case 'DASHBOARD_ANALYSIS':
      inputComponent = !config.metrics?.length ? fallback
        : <DashboardAnalysisInput config={config} choices={choices} onSubmit={onSubmit} disabled={disabled} choiceHints={choiceHints} />
      break
    case 'HIRE_FIRE':
      inputComponent = !config.candidates?.length ? fallback
        : <HireFireInput config={config} onSubmit={onSubmit} disabled={disabled} />
      break
    case 'CRISIS_RESPONSE':
      inputComponent = <CrisisResponseInput config={config} choices={choices} onSubmit={onSubmit} disabled={disabled} choiceHints={choiceHints} />
      break
    case 'INVESTMENT_PORTFOLIO':
      inputComponent = !config.departments?.length ? fallback
        : <InvestmentPortfolioInput config={config} onSubmit={onSubmit} disabled={disabled} />
      break
    case 'STAKEHOLDER_MEETING':
      inputComponent = !config.stakeholders?.length ? fallback
        : <StakeholderMeetingInput config={config} onSubmit={onSubmit} disabled={disabled} />
      break
    case 'NARRATIVE_CHOICE':
    default:
      inputComponent = fallback
  }

  return (
    <div className="space-y-3">
      {mentorGuidance && <MentorGuidanceBanner guidance={mentorGuidance} />}
      {inputComponent}
    </div>
  )
}
