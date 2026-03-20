'use client'

import { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Plus, Trash2, Save } from 'lucide-react'
import type { SelectedItem } from './SimulationYearView'

interface Choice {
  id: string
  text: string
  quality?: number
}

interface Debrief {
  yourPath: string
  conceptAtWork: string
  theGap: string
  playAgain: string
}

interface DecisionData {
  decisionId: string
  narrative: string
  decisionType?: string
  decisionConfig?: any
  choices?: Choice[]
}

interface ReviewVariant {
  metrics?: Record<string, any>
  feedback?: Record<string, string>
}

interface ReviewData {
  strong?: ReviewVariant
  mid?: ReviewVariant
  poor?: ReviewVariant
}

interface OpeningData {
  strong?: string
  mid?: string
  poor?: string
}

// Props differ by selected item type
interface SimulationNodeEditorProps {
  selectedItem: SelectedItem
  simulationData: any
  onSaveDecision: (year: number, index: number, decision: DecisionData) => void
  onSaveReview: (year: number, review: ReviewData) => void
  onSaveOpening: (year: number, opening: OpeningData) => void
}

const DECISION_TYPES = [
  'NARRATIVE_CHOICE',
  'STAKEHOLDER_MEETING',
  'INVESTMENT_PORTFOLIO',
  'DASHBOARD_ANALYSIS',
  'HIRE_FIRE',
  'NEGOTIATION',
  'CRISIS_RESPONSE',
  'BUDGET_ALLOCATION',
  'PRIORITY_RANKING',
  'TRADEOFF_SLIDER',
  'RESOURCE_ASSIGNMENT',
  'TIMELINE_CHOICE',
  'COMPOUND',
]

function DecisionEditor({
  decision,
  onSave,
}: {
  decision: DecisionData
  onSave: (d: DecisionData) => void
}) {
  const [edited, setEdited] = useState<DecisionData>({ ...decision })
  const [configJson, setConfigJson] = useState('')
  const [configError, setConfigError] = useState<string | null>(null)

  useEffect(() => {
    setEdited({ ...decision })
    setConfigJson(
      decision.decisionConfig ? JSON.stringify(decision.decisionConfig, null, 2) : ''
    )
    setConfigError(null)
  }, [decision])

  const updateField = <K extends keyof DecisionData>(field: K, value: DecisionData[K]) => {
    setEdited((prev) => ({ ...prev, [field]: value }))
  }

  const updateChoice = (index: number, field: keyof Choice, value: any) => {
    const choices = [...(edited.choices || [])]
    choices[index] = { ...choices[index], [field]: value }
    updateField('choices', choices)
  }

  const addChoice = () => {
    const choices = [...(edited.choices || [])]
    const newId = `choice_${edited.decisionId}_${String.fromCharCode(97 + choices.length)}`
    choices.push({ id: newId, text: '', quality: 2 })
    updateField('choices', choices)
  }

  const removeChoice = (index: number) => {
    const choices = [...(edited.choices || [])]
    choices.splice(index, 1)
    updateField('choices', choices)
  }

  const handleSave = () => {
    let parsedConfig = edited.decisionConfig
    if (configJson.trim()) {
      try {
        parsedConfig = JSON.parse(configJson)
        setConfigError(null)
      } catch {
        setConfigError('Invalid JSON')
        return
      }
    } else {
      parsedConfig = undefined
    }
    onSave({ ...edited, decisionConfig: parsedConfig })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-medium text-muted-foreground">{edited.decisionId}</h3>
          <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-300">
            DECISION
          </Badge>
        </div>
        <Button size="sm" onClick={handleSave}>
          <Save className="h-4 w-4 mr-1" />
          Save Decision
        </Button>
      </div>

      {/* Narrative */}
      <div className="space-y-1.5">
        <Label htmlFor="narrative">Narrative</Label>
        <Textarea
          id="narrative"
          value={edited.narrative || ''}
          onChange={(e) => updateField('narrative', e.target.value)}
          rows={8}
          placeholder="Enter the narrative text..."
          className="resize-y"
        />
      </div>

      {/* Decision Type */}
      <div className="space-y-1.5">
        <Label>Decision Type</Label>
        <Select
          value={edited.decisionType || 'NARRATIVE_CHOICE'}
          onValueChange={(val) => updateField('decisionType', val)}
        >
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {DECISION_TYPES.map((dt) => (
              <SelectItem key={dt} value={dt}>
                {dt.replace(/_/g, ' ')}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Decision Config JSON */}
      <div className="space-y-1.5">
        <Label htmlFor="decisionConfig">Decision Config (JSON)</Label>
        <Textarea
          id="decisionConfig"
          value={configJson}
          onChange={(e) => {
            setConfigJson(e.target.value)
            setConfigError(null)
          }}
          rows={4}
          placeholder='{ "mappings": [...] }'
          className="font-mono text-sm resize-y"
        />
        {configError && <p className="text-xs text-red-500">{configError}</p>}
      </div>

      {/* Choices */}
      <Card>
        <CardHeader className="py-3 px-4">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm">Choices</CardTitle>
            <Button variant="outline" size="sm" onClick={addChoice}>
              <Plus className="h-3.5 w-3.5 mr-1" />
              Add Choice
            </Button>
          </div>
        </CardHeader>
        <CardContent className="px-4 pb-4 space-y-3">
          {(edited.choices || []).length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-2">
              No choices defined.
            </p>
          ) : (
            (edited.choices || []).map((choice, index) => (
              <div
                key={choice.id || index}
                className="flex gap-2 items-start p-3 rounded-md border bg-muted/30"
              >
                <div className="flex-1 space-y-2">
                  <div className="flex gap-2">
                    <div className="flex-1">
                      <Input
                        value={choice.text}
                        onChange={(e) => updateChoice(index, 'text', e.target.value)}
                        placeholder="Choice text..."
                        className="text-sm"
                      />
                    </div>
                    <Select
                      value={String(choice.quality || 2)}
                      onValueChange={(val) => updateChoice(index, 'quality', parseInt(val))}
                    >
                      <SelectTrigger className="w-28">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="1">Quality 1</SelectItem>
                        <SelectItem value="2">Quality 2</SelectItem>
                        <SelectItem value="3">Quality 3</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => removeChoice(index)}
                  className="text-red-500 hover:text-red-700 hover:bg-red-50 shrink-0"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function ReviewEditor({
  review,
  onSave,
}: {
  review: ReviewData
  onSave: (r: ReviewData) => void
}) {
  const [edited, setEdited] = useState<ReviewData>({ ...review })

  useEffect(() => {
    setEdited({ ...review })
  }, [review])

  const updateVariant = (variant: 'strong' | 'mid' | 'poor', field: string, value: any) => {
    setEdited((prev) => ({
      ...prev,
      [variant]: {
        ...prev[variant],
        [field]: value,
      },
    }))
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <Badge variant="outline" className="bg-green-50 text-green-700 border-green-300">
          YEAR-END REVIEW
        </Badge>
        <Button size="sm" onClick={() => onSave(edited)}>
          <Save className="h-4 w-4 mr-1" />
          Save Review
        </Button>
      </div>

      <Tabs defaultValue="strong">
        <TabsList>
          <TabsTrigger value="strong">Strong Performance</TabsTrigger>
          <TabsTrigger value="mid">Mid Performance</TabsTrigger>
          <TabsTrigger value="poor">Poor Performance</TabsTrigger>
        </TabsList>

        {(['strong', 'mid', 'poor'] as const).map((variant) => (
          <TabsContent key={variant} value={variant} className="space-y-4">
            <div className="space-y-1.5">
              <Label>Metrics (JSON)</Label>
              <Textarea
                value={
                  edited[variant]?.metrics
                    ? JSON.stringify(edited[variant]!.metrics, null, 2)
                    : ''
                }
                onChange={(e) => {
                  try {
                    const val = e.target.value.trim() ? JSON.parse(e.target.value) : undefined
                    updateVariant(variant, 'metrics', val)
                  } catch {
                    // Allow typing invalid JSON temporarily
                  }
                }}
                rows={6}
                className="font-mono text-sm resize-y"
                placeholder='{ "revenue": { "value": 5000000, "trend": "up" } }'
              />
            </div>
            <div className="space-y-1.5">
              <Label>Stakeholder Feedback (JSON)</Label>
              <Textarea
                value={
                  edited[variant]?.feedback
                    ? JSON.stringify(edited[variant]!.feedback, null, 2)
                    : ''
                }
                onChange={(e) => {
                  try {
                    const val = e.target.value.trim() ? JSON.parse(e.target.value) : undefined
                    updateVariant(variant, 'feedback', val)
                  } catch {
                    // Allow typing invalid JSON temporarily
                  }
                }}
                rows={4}
                className="font-mono text-sm resize-y"
                placeholder='{ "board": "Great progress this year.", "customers": "..." }'
              />
            </div>
          </TabsContent>
        ))}
      </Tabs>
    </div>
  )
}

function OpeningEditor({
  opening,
  onSave,
}: {
  opening: OpeningData
  onSave: (o: OpeningData) => void
}) {
  const [edited, setEdited] = useState<OpeningData>({ ...opening })

  useEffect(() => {
    setEdited({ ...opening })
  }, [opening])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-300">
          OPENING NARRATIVE
        </Badge>
        <Button size="sm" onClick={() => onSave(edited)}>
          <Save className="h-4 w-4 mr-1" />
          Save Opening
        </Button>
      </div>

      <Tabs defaultValue="strong">
        <TabsList>
          <TabsTrigger value="strong">Strong Performance</TabsTrigger>
          <TabsTrigger value="mid">Mid Performance</TabsTrigger>
          <TabsTrigger value="poor">Poor Performance</TabsTrigger>
        </TabsList>

        {(['strong', 'mid', 'poor'] as const).map((variant) => (
          <TabsContent key={variant} value={variant} className="space-y-4">
            <div className="space-y-1.5">
              <Label>Narrative Text</Label>
              <Textarea
                value={edited[variant] || ''}
                onChange={(e) =>
                  setEdited((prev) => ({ ...prev, [variant]: e.target.value }))
                }
                rows={8}
                placeholder={`Opening narrative for ${variant} performance variant...`}
                className="resize-y"
              />
            </div>
          </TabsContent>
        ))}
      </Tabs>
    </div>
  )
}

export default function SimulationNodeEditor({
  selectedItem,
  simulationData,
  onSaveDecision,
  onSaveReview,
  onSaveOpening,
}: SimulationNodeEditorProps) {
  if (!simulationData?.years) {
    return (
      <div className="flex items-center justify-center h-full min-h-[300px] text-muted-foreground">
        <p className="text-sm">No simulation data available.</p>
      </div>
    )
  }

  const years: any[] = simulationData.years
  const yearData = years.find((y: any) => (y.year ?? 0) === selectedItem.year) ||
    years[selectedItem.year - 1]

  if (!yearData) {
    return (
      <div className="flex items-center justify-center h-full min-h-[300px] text-muted-foreground">
        <p className="text-sm">Year {selectedItem.year} not found in data.</p>
      </div>
    )
  }

  if (selectedItem.type === 'decision' && selectedItem.index != null) {
    const decisions: any[] = yearData.decisions || []
    const decision = decisions[selectedItem.index]
    if (!decision) {
      return (
        <div className="flex items-center justify-center h-full min-h-[300px] text-muted-foreground">
          <p className="text-sm">Decision not found.</p>
        </div>
      )
    }
    return (
      <DecisionEditor
        decision={decision}
        onSave={(d) => onSaveDecision(selectedItem.year, selectedItem.index!, d)}
      />
    )
  }

  if (selectedItem.type === 'review') {
    const review: ReviewData = yearData.review || {}
    return (
      <ReviewEditor
        review={review}
        onSave={(r) => onSaveReview(selectedItem.year, r)}
      />
    )
  }

  if (selectedItem.type === 'opening') {
    const opening: OpeningData = yearData.opening || {}
    return (
      <OpeningEditor
        opening={opening}
        onSave={(o) => onSaveOpening(selectedItem.year, o)}
      />
    )
  }

  return (
    <div className="flex items-center justify-center h-full min-h-[300px] text-muted-foreground">
      <p className="text-sm">Select an item from the year navigator to edit it.</p>
    </div>
  )
}
