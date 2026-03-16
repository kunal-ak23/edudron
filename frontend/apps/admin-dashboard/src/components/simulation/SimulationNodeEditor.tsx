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
import { Plus, Trash2, Save, Link } from 'lucide-react'

interface Choice {
  id: string
  text: string
  nextNodeId?: string
  quality?: number
}

interface Debrief {
  yourPath: string
  conceptAtWork: string
  theGap: string
  playAgain: string
}

interface NodeData {
  id: string
  type: 'SCENARIO' | 'TERMINAL'
  narrative: string
  decisionType?: string
  decisionConfig?: any
  choices?: Choice[]
  score?: number
  debrief?: Debrief
}

interface SimulationNodeEditorProps {
  node: NodeData
  onSave: (updatedNode: NodeData) => void
}

const DECISION_TYPES = [
  'NARRATIVE_CHOICE',
  'BUDGET_ALLOCATION',
  'PRIORITY_RANKING',
  'TRADEOFF_SLIDER',
  'RESOURCE_ASSIGNMENT',
  'TIMELINE_CHOICE',
]

export default function SimulationNodeEditor({
  node,
  onSave,
}: SimulationNodeEditorProps) {
  const [editedNode, setEditedNode] = useState<NodeData>({ ...node })
  const [configJson, setConfigJson] = useState('')
  const [configError, setConfigError] = useState<string | null>(null)

  // Reset when node changes
  useEffect(() => {
    setEditedNode({ ...node })
    setConfigJson(
      node.decisionConfig ? JSON.stringify(node.decisionConfig, null, 2) : ''
    )
    setConfigError(null)
  }, [node])

  const updateField = <K extends keyof NodeData>(
    field: K,
    value: NodeData[K]
  ) => {
    setEditedNode((prev) => ({ ...prev, [field]: value }))
  }

  const updateChoice = (index: number, field: keyof Choice, value: any) => {
    const choices = [...(editedNode.choices || [])]
    choices[index] = { ...choices[index], [field]: value }
    updateField('choices', choices)
  }

  const addChoice = () => {
    const choices = [...(editedNode.choices || [])]
    const newId = `choice_${editedNode.id}_${String.fromCharCode(
      97 + choices.length
    )}`
    choices.push({ id: newId, text: '', quality: 2 })
    updateField('choices', choices)
  }

  const removeChoice = (index: number) => {
    const choices = [...(editedNode.choices || [])]
    choices.splice(index, 1)
    updateField('choices', choices)
  }

  const updateDebrief = (field: keyof Debrief, value: string) => {
    const debrief = { ...(editedNode.debrief || { yourPath: '', conceptAtWork: '', theGap: '', playAgain: '' }) }
    debrief[field] = value
    updateField('debrief', debrief)
  }

  const handleSave = () => {
    // Validate and parse decision config JSON
    let parsedConfig = editedNode.decisionConfig
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

    onSave({ ...editedNode, decisionConfig: parsedConfig })
  }

  const isScenario = editedNode.type === 'SCENARIO'

  return (
    <div className="space-y-4">
      {/* Node header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-medium text-muted-foreground">
            {editedNode.id}
          </h3>
          <Badge
            variant="outline"
            className={
              isScenario
                ? 'bg-blue-50 text-blue-700 border-blue-300'
                : (editedNode.score ?? 0) >= 50
                ? 'bg-green-50 text-green-700 border-green-300'
                : 'bg-red-50 text-red-700 border-red-300'
            }
          >
            {editedNode.type}
          </Badge>
        </div>
        <Button size="sm" onClick={handleSave}>
          <Save className="h-4 w-4 mr-1" />
          Save Node
        </Button>
      </div>

      {/* Narrative */}
      <div className="space-y-1.5">
        <Label htmlFor="narrative">Narrative</Label>
        <Textarea
          id="narrative"
          value={editedNode.narrative || ''}
          onChange={(e) => updateField('narrative', e.target.value)}
          rows={8}
          placeholder="Enter the narrative text for this node..."
          className="resize-y"
        />
      </div>

      {/* SCENARIO-specific fields */}
      {isScenario && (
        <>
          {/* Decision Type */}
          <div className="space-y-1.5">
            <Label>Decision Type</Label>
            <Select
              value={editedNode.decisionType || 'NARRATIVE_CHOICE'}
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
            <Label htmlFor="decisionConfig">
              Decision Config (JSON)
            </Label>
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
            {configError && (
              <p className="text-xs text-red-500">{configError}</p>
            )}
          </div>

          {/* Choices */}
          <Card>
            <CardHeader className="py-3 px-4">
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm">Choices</CardTitle>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={addChoice}
                >
                  <Plus className="h-3.5 w-3.5 mr-1" />
                  Add Choice
                </Button>
              </div>
            </CardHeader>
            <CardContent className="px-4 pb-4 space-y-3">
              {(editedNode.choices || []).length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-2">
                  No choices defined. Add a choice to create a branch.
                </p>
              ) : (
                (editedNode.choices || []).map((choice, index) => (
                  <div
                    key={choice.id || index}
                    className="flex gap-2 items-start p-3 rounded-md border bg-muted/30"
                  >
                    <div className="flex-1 space-y-2">
                      <div className="flex gap-2">
                        <div className="flex-1">
                          <Input
                            value={choice.text}
                            onChange={(e) =>
                              updateChoice(index, 'text', e.target.value)
                            }
                            placeholder="Choice text..."
                            className="text-sm"
                          />
                        </div>
                        <Select
                          value={String(choice.quality || 2)}
                          onValueChange={(val) =>
                            updateChoice(index, 'quality', parseInt(val))
                          }
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
                      {choice.nextNodeId && (
                        <div className="flex items-center gap-1 text-xs text-muted-foreground">
                          <Link className="h-3 w-3" />
                          <span>{choice.nextNodeId}</span>
                        </div>
                      )}
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
        </>
      )}

      {/* TERMINAL-specific fields */}
      {!isScenario && (
        <>
          {/* Score */}
          <div className="space-y-1.5">
            <Label htmlFor="score">Score (0-100)</Label>
            <Input
              id="score"
              type="number"
              min={0}
              max={100}
              value={editedNode.score ?? 0}
              onChange={(e) =>
                updateField('score', parseInt(e.target.value) || 0)
              }
              className="w-32"
            />
          </div>

          {/* Debrief */}
          <Card>
            <CardHeader className="py-3 px-4">
              <CardTitle className="text-sm">Debrief</CardTitle>
            </CardHeader>
            <CardContent className="px-4 pb-4 space-y-3">
              <div className="space-y-1.5">
                <Label htmlFor="debrief-path">Your Path</Label>
                <Textarea
                  id="debrief-path"
                  value={editedNode.debrief?.yourPath || ''}
                  onChange={(e) => updateDebrief('yourPath', e.target.value)}
                  rows={3}
                  placeholder="Describe the path the student took..."
                  className="resize-y"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="debrief-concept">The Concept at Work</Label>
                <Textarea
                  id="debrief-concept"
                  value={editedNode.debrief?.conceptAtWork || ''}
                  onChange={(e) =>
                    updateDebrief('conceptAtWork', e.target.value)
                  }
                  rows={3}
                  placeholder="Explain the underlying concept..."
                  className="resize-y"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="debrief-gap">The Gap</Label>
                <Textarea
                  id="debrief-gap"
                  value={editedNode.debrief?.theGap || ''}
                  onChange={(e) => updateDebrief('theGap', e.target.value)}
                  rows={3}
                  placeholder="What did the student miss or could improve..."
                  className="resize-y"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="debrief-again">Play Again</Label>
                <Input
                  id="debrief-again"
                  value={editedNode.debrief?.playAgain || ''}
                  onChange={(e) =>
                    updateDebrief('playAgain', e.target.value)
                  }
                  placeholder="Encouragement to try again..."
                />
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
