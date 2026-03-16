'use client'

import { useState, useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { ChevronDown, ChevronRight, Circle, CheckCircle2, XCircle } from 'lucide-react'
import { cn } from '@/lib/utils'

interface TreeNode {
  id: string
  type: 'SCENARIO' | 'TERMINAL'
  narrative?: string
  decisionType?: string
  choices?: { id: string; text: string; nextNodeId?: string; quality?: number }[]
  score?: number
  debrief?: any
}

interface SimulationTreeViewProps {
  treeData: { rootNodeId: string; nodes: Record<string, TreeNode> } | null
  selectedNodeId: string | null
  onSelectNode: (nodeId: string) => void
}

// Determine path quality for a node by tracing from root via highest-quality choices
function computePathQualities(
  rootNodeId: string,
  nodes: Record<string, TreeNode>
): Record<string, 'golden' | 'recovery' | 'failure'> {
  const qualities: Record<string, 'golden' | 'recovery' | 'failure'> = {}

  // BFS marking golden path first (follow highest quality choices)
  const goldenPath = new Set<string>()
  let currentId: string | undefined = rootNodeId
  while (currentId && nodes[currentId]) {
    goldenPath.add(currentId)
    const node = nodes[currentId]
    if (node.type === 'TERMINAL' || !node.choices?.length) break
    // Find highest quality choice
    const bestChoice = node.choices.reduce((best, c) =>
      (c.quality || 0) > (best.quality || 0) ? c : best
    , node.choices[0])
    currentId = bestChoice.nextNodeId
  }

  // Assign qualities
  for (const nodeId of Object.keys(nodes)) {
    const node = nodes[nodeId]
    if (goldenPath.has(nodeId)) {
      qualities[nodeId] = 'golden'
    } else if (node.type === 'TERMINAL' && (node.score ?? 100) < 40) {
      qualities[nodeId] = 'failure'
    } else {
      qualities[nodeId] = 'recovery'
    }
  }

  return qualities
}

function TreeNodeItem({
  nodeId,
  nodes,
  selectedNodeId,
  onSelectNode,
  pathQualities,
  depth,
  visited,
}: {
  nodeId: string
  nodes: Record<string, TreeNode>
  selectedNodeId: string | null
  onSelectNode: (nodeId: string) => void
  pathQualities: Record<string, 'golden' | 'recovery' | 'failure'>
  depth: number
  visited: Set<string>
}) {
  const [expanded, setExpanded] = useState(depth < 2)
  const node = nodes[nodeId]

  if (!node || visited.has(nodeId)) return null

  // Track visited to avoid infinite loops in circular references
  const newVisited = new Set(visited)
  newVisited.add(nodeId)

  const childNodeIds = (node.choices || [])
    .map((c) => c.nextNodeId)
    .filter((id): id is string => !!id && !!nodes[id] && !visited.has(id))

  const hasChildren = childNodeIds.length > 0
  const isSelected = selectedNodeId === nodeId
  const quality = pathQualities[nodeId] || 'recovery'

  const label = node.narrative
    ? node.narrative.slice(0, 40) + (node.narrative.length > 40 ? '...' : '')
    : `Node: ${nodeId}`

  const qualityColors = {
    golden: 'border-l-green-500',
    recovery: 'border-l-yellow-500',
    failure: 'border-l-red-500',
  }

  const terminalIcon = node.type === 'TERMINAL' ? (
    (node.score ?? 0) >= 50 ? (
      <CheckCircle2 className="h-3.5 w-3.5 text-green-600 shrink-0" />
    ) : (
      <XCircle className="h-3.5 w-3.5 text-red-600 shrink-0" />
    )
  ) : (
    <Circle className="h-3.5 w-3.5 text-blue-500 shrink-0" />
  )

  return (
    <div className="select-none">
      <div
        className={cn(
          'flex items-center gap-1.5 py-1 px-2 rounded-md cursor-pointer border-l-2 transition-colors',
          qualityColors[quality],
          isSelected
            ? 'bg-primary/10 ring-1 ring-primary/40'
            : 'hover:bg-muted/60'
        )}
        style={{ paddingLeft: `${depth * 16 + 8}px` }}
        onClick={() => onSelectNode(nodeId)}
      >
        {/* Expand/collapse */}
        <button
          className="p-0.5 shrink-0"
          onClick={(e) => {
            e.stopPropagation()
            if (hasChildren) setExpanded(!expanded)
          }}
        >
          {hasChildren ? (
            expanded ? (
              <ChevronDown className="h-3.5 w-3.5 text-muted-foreground" />
            ) : (
              <ChevronRight className="h-3.5 w-3.5 text-muted-foreground" />
            )
          ) : (
            <span className="w-3.5" />
          )}
        </button>

        {terminalIcon}

        <span className="text-sm truncate flex-1 min-w-0">{label}</span>

        <div className="flex items-center gap-1 shrink-0">
          <Badge
            variant="outline"
            className={cn(
              'text-[10px] px-1.5 py-0',
              node.type === 'TERMINAL'
                ? (node.score ?? 0) >= 50
                  ? 'bg-green-50 text-green-700 border-green-300'
                  : 'bg-red-50 text-red-700 border-red-300'
                : 'bg-blue-50 text-blue-700 border-blue-300'
            )}
          >
            {node.type === 'TERMINAL'
              ? `END (${node.score ?? 0})`
              : 'SCENARIO'}
          </Badge>
          {node.decisionType && node.type !== 'TERMINAL' && (
            <Badge
              variant="outline"
              className="text-[10px] px-1.5 py-0 bg-gray-50 text-gray-500 border-gray-200"
            >
              {node.decisionType.replace(/_/g, ' ')}
            </Badge>
          )}
        </div>
      </div>

      {/* Children */}
      {expanded && hasChildren && (
        <div>
          {childNodeIds.map((childId) => (
            <TreeNodeItem
              key={childId}
              nodeId={childId}
              nodes={nodes}
              selectedNodeId={selectedNodeId}
              onSelectNode={onSelectNode}
              pathQualities={pathQualities}
              depth={depth + 1}
              visited={newVisited}
            />
          ))}
        </div>
      )}
    </div>
  )
}

export default function SimulationTreeView({
  treeData,
  selectedNodeId,
  onSelectNode,
}: SimulationTreeViewProps) {
  const pathQualities = useMemo(() => {
    if (!treeData?.rootNodeId || !treeData?.nodes) return {}
    return computePathQualities(treeData.rootNodeId, treeData.nodes)
  }, [treeData])

  if (!treeData?.rootNodeId || !treeData?.nodes) {
    return (
      <div className="p-4 text-center text-sm text-muted-foreground">
        No tree data available. The simulation may still be generating.
      </div>
    )
  }

  const nodeCount = Object.keys(treeData.nodes).length
  const terminalCount = Object.values(treeData.nodes).filter(
    (n) => n.type === 'TERMINAL'
  ).length

  return (
    <div className="flex flex-col h-full">
      <div className="px-3 py-2 border-b flex items-center justify-between">
        <h3 className="text-sm font-medium">Decision Tree</h3>
        <span className="text-xs text-muted-foreground">
          {nodeCount} nodes, {terminalCount} endpoints
        </span>
      </div>
      <div className="flex-1 overflow-y-auto p-2 space-y-0.5">
        <TreeNodeItem
          nodeId={treeData.rootNodeId}
          nodes={treeData.nodes}
          selectedNodeId={selectedNodeId}
          onSelectNode={onSelectNode}
          pathQualities={pathQualities}
          depth={0}
          visited={new Set()}
        />
      </div>
    </div>
  )
}
