'use client'

import React, { useState } from 'react'
import { ChevronRight, ChevronDown, Building2, GraduationCap, Users, Plus, Eye } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import type { Institute, Class, Section } from '@kunal-ak23/edudron-shared-utils'
import Link from 'next/link'

interface TreeNode {
  id: string
  name: string
  type: 'institute' | 'class' | 'section'
  data: Institute | Class | Section
  children?: TreeNode[]
  expanded?: boolean
}

interface InstituteTreeViewProps {
  institute: Institute
  classes: Class[]
  sectionsByClass: Record<string, Section[]>
  onAddClass?: () => void
  onAddSection?: (classId: string) => void
}

export function InstituteTreeView({
  institute,
  classes,
  sectionsByClass,
  onAddClass,
  onAddSection
}: InstituteTreeViewProps) {
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set([institute.id]))

  const toggleNode = (nodeId: string) => {
    const newExpanded = new Set(expandedNodes)
    if (newExpanded.has(nodeId)) {
      newExpanded.delete(nodeId)
    } else {
      newExpanded.add(nodeId)
    }
    setExpandedNodes(newExpanded)
  }

  const expandAll = () => {
    const allIds = new Set<string>([institute.id])
    classes.forEach(c => {
      allIds.add(c.id)
    })
    Object.values(sectionsByClass).flat().forEach(s => {
      allIds.add(s.id)
    })
    setExpandedNodes(allIds)
  }

  const collapseAll = () => {
    setExpandedNodes(new Set([institute.id]))
  }

  const renderNode = (node: TreeNode, level: number = 0): React.ReactNode => {
    const isExpanded = expandedNodes.has(node.id)
    const hasChildren = node.children && node.children.length > 0
    const indent = level * 24

    const getIcon = () => {
      switch (node.type) {
        case 'institute':
          return <Building2 className="h-5 w-5 text-blue-600" />
        case 'class':
          return <GraduationCap className="h-4 w-4 text-green-600" />
        case 'section':
          return <Users className="h-4 w-4 text-purple-600" />
      }
    }

    const getNodeColor = () => {
      switch (node.type) {
        case 'institute':
          return 'bg-blue-50 border-blue-200 hover:bg-blue-100'
        case 'class':
          return 'bg-green-50 border-green-200 hover:bg-green-100'
        case 'section':
          return 'bg-purple-50 border-purple-200 hover:bg-purple-100'
      }
    }

    const getBadgeInfo = () => {
      if (node.type === 'institute') {
        const inst = node.data as Institute
        return { count: inst.classCount || classes.length, label: 'Classes' }
      }
      if (node.type === 'class') {
        const cls = node.data as Class
        const sections = sectionsByClass[cls.id] || []
        return { count: cls.sectionCount || sections.length, label: 'Sections' }
      }
      if (node.type === 'section') {
        const sec = node.data as Section
        return { count: sec.studentCount || 0, label: 'Students' }
      }
      return null
    }

    const badgeInfo = getBadgeInfo()

    return (
      <div key={node.id} className="mb-1">
        <div
          className={`
            flex items-center gap-2 p-3 rounded-lg border transition-all cursor-pointer
            ${getNodeColor()}
          `}
          style={{ marginLeft: `${indent}px` }}
        >
          {hasChildren ? (
            <button
              onClick={() => toggleNode(node.id)}
              className="flex-shrink-0 p-0.5 hover:bg-white/50 rounded"
            >
              {isExpanded ? (
                <ChevronDown className="h-4 w-4 text-gray-600" />
              ) : (
                <ChevronRight className="h-4 w-4 text-gray-600" />
              )}
            </button>
          ) : (
            <div className="w-5" />
          )}

          <div className="flex-shrink-0">{getIcon()}</div>

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className="font-medium text-gray-900 truncate">{node.name}</span>
              {node.type === 'class' && (node.data as Class).code && (
                <span className="text-xs text-gray-500">({(node.data as Class).code})</span>
              )}
              {node.type === 'section' && (node.data as Section).description && (
                <span className="text-xs text-gray-500 truncate">
                  - {(node.data as Section).description}
                </span>
              )}
            </div>
            {badgeInfo && (
              <div className="mt-1 flex items-center gap-2">
                <Badge variant="secondary" className="text-xs">
                  {badgeInfo.count} {badgeInfo.label}
                </Badge>
                {node.type === 'institute' && (
                  <Badge variant={institute.isActive ? 'default' : 'secondary'} className="text-xs">
                    {institute.isActive ? 'Active' : 'Inactive'}
                  </Badge>
                )}
                {node.type === 'class' && (
                  <Badge variant={(node.data as Class).isActive ? 'default' : 'secondary'} className="text-xs">
                    {(node.data as Class).isActive ? 'Active' : 'Inactive'}
                  </Badge>
                )}
                {node.type === 'section' && (
                  <Badge variant={(node.data as Section).isActive ? 'default' : 'secondary'} className="text-xs">
                    {(node.data as Section).isActive ? 'Active' : 'Inactive'}
                  </Badge>
                )}
              </div>
            )}
          </div>

          <div className="flex items-center gap-1 flex-shrink-0">
            {node.type === 'institute' && onAddClass && (
              <Button
                variant="ghost"
                size="sm"
                onClick={(e) => {
                  e.stopPropagation()
                  onAddClass()
                }}
                className="h-7 px-2"
              >
                <Plus className="h-3 w-3 mr-1" />
                Class
              </Button>
            )}
            {node.type === 'class' && onAddSection && (
              <Button
                variant="ghost"
                size="sm"
                onClick={(e) => {
                  e.stopPropagation()
                  onAddSection(node.id)
                }}
                className="h-7 px-2"
              >
                <Plus className="h-3 w-3 mr-1" />
                Section
              </Button>
            )}
            {node.type === 'class' && (
              <Link href={`/classes/${node.id}`}>
                <Button variant="ghost" size="sm" className="h-7 px-2">
                  <Eye className="h-3 w-3" />
                </Button>
              </Link>
            )}
            {node.type === 'section' && (
              <Link href={`/sections/${node.id}`}>
                <Button variant="ghost" size="sm" className="h-7 px-2">
                  <Eye className="h-3 w-3" />
                </Button>
              </Link>
            )}
          </div>
        </div>

        {hasChildren && isExpanded && (
          <div className="mt-1">
            {node.children!.map((child) => renderNode(child, level + 1))}
          </div>
        )}
      </div>
    )
  }

  // Build tree structure
  const buildTree = (): TreeNode => {
    const classNodes: TreeNode[] = classes.map(cls => {
      const sections = sectionsByClass[cls.id] || []
      return {
        id: cls.id,
        name: cls.name,
        type: 'class',
        data: cls,
        children: sections.map(sec => ({
          id: sec.id,
          name: sec.name,
          type: 'section',
          data: sec
        }))
      }
    })

    return {
      id: institute.id,
      name: institute.name,
      type: 'institute',
      data: institute,
      children: classNodes
    }
  }

  const rootNode = buildTree()

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Organization Tree</h2>
          <p className="text-sm text-gray-600 mt-1">
            Visualize the complete hierarchy of classes and sections
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={expandAll}>
            Expand All
          </Button>
          <Button variant="outline" size="sm" onClick={collapseAll}>
            Collapse All
          </Button>
        </div>
      </div>

      <Card className="p-4">
        <div className="space-y-1">
          {renderNode(rootNode)}
        </div>
      </Card>
    </div>
  )
}
