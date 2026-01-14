'use client'

import React, { useEffect, useRef, useState } from 'react'
import * as d3 from 'd3'
import { Copy, Check, Building2, GraduationCap, Users, Plus, Eye, Download, List, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { useToast } from '@/hooks/use-toast'
import type { Institute, Class, Section } from '@kunal-ak23/edudron-shared-utils'
import Link from 'next/link'

interface TreeNode {
  id: string
  name: string
  type: 'institute' | 'class' | 'section'
  data: Institute | Class | Section
  children?: TreeNode[]
}

interface D3TreeViewProps {
  institute: Institute
  classes: Class[]
  sectionsByClass: Record<string, Section[]>
  onAddClass?: () => void
  onAddSection?: (classId: string) => void
}

export function D3TreeView({
  institute,
  classes,
  sectionsByClass,
  onAddClass,
  onAddSection
}: D3TreeViewProps) {
  const svgRef = useRef<SVGSVGElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const [zoom, setZoom] = useState(1)
  const [showIdList, setShowIdList] = useState(false)
  const { toast } = useToast()

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

  const copyToClipboard = async (id: string, name: string) => {
    try {
      await navigator.clipboard.writeText(id)
      setCopiedId(id)
      toast({
        title: 'ID Copied',
        description: `${name} ID copied to clipboard`,
      })
      setTimeout(() => setCopiedId(null), 2000)
    } catch (err) {
      console.error('Failed to copy:', err)
      toast({
        variant: 'destructive',
        title: 'Failed to copy',
        description: 'Could not copy ID to clipboard',
      })
    }
  }

  const exportIds = () => {
    const allIds: { type: string; name: string; id: string }[] = [
      { type: 'Institute', name: institute.name, id: institute.id }
    ]
    
    classes.forEach(cls => {
      allIds.push({ type: 'Class', name: cls.name, id: cls.id })
      const sections = sectionsByClass[cls.id] || []
      sections.forEach(sec => {
        allIds.push({ type: 'Section', name: sec.name, id: sec.id })
      })
    })

    const csv = [
      'Type,Name,ID',
      ...allIds.map(item => `"${item.type}","${item.name}","${item.id}"`)
    ].join('\n')

    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${institute.name.replace(/\s+/g, '_')}_ids.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    toast({
      title: 'IDs Exported',
      description: 'All IDs exported to CSV file',
    })
  }

  useEffect(() => {
    if (!svgRef.current || !containerRef.current) return

    const svg = d3.select(svgRef.current)
    const container = containerRef.current

    // Clear previous render
    svg.selectAll('*').remove()

    const width = container.clientWidth
    const height = Math.max(800, classes.length * 250)

    svg.attr('width', width)
      .attr('height', height)
      .attr('viewBox', `0 0 ${width} ${height}`)
      .style('overflow', 'visible')

    // Create zoom behavior
    const zoomBehavior = d3.zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.1, 3])
      .on('zoom', (event) => {
        g.attr('transform', event.transform.toString())
        setZoom(event.transform.k)
      })

    svg.call(zoomBehavior as any)

    const g = svg.append('g')

    const treeData = buildTree()
    const root = d3.hierarchy(treeData)
    const treeLayout = d3.tree().size([height - 200, width - 400])
    treeLayout(root)

    // Color scheme
    const colors = {
      institute: '#3b82f6', // blue
      class: '#10b981', // green
      section: '#8b5cf6' // purple
    }

    // Draw links
    const links = g
      .selectAll('.link')
      .data(root.links())
      .enter()
      .append('path')
      .attr('class', 'link')
      .attr('d', d3.linkHorizontal()
        .x((d: any) => d.y + 200)
        .y((d: any) => d.x + 100)
      )
      .attr('fill', 'none')
      .attr('stroke', '#94a3b8')
      .attr('stroke-width', 2)

    // Draw nodes
    const nodes = g
      .selectAll('.node')
      .data(root.descendants())
      .enter()
      .append('g')
      .attr('class', 'node')
      .attr('transform', (d) => `translate(${d.y + 200},${d.x + 100})`)

    // Node circles
    nodes
      .append('circle')
      .attr('r', 8)
      .attr('fill', (d: any) => {
        const node = d.data as TreeNode
        return colors[node.type]
      })
      .attr('stroke', '#fff')
      .attr('stroke-width', 2)

    // Node labels container
    const labels = nodes
      .append('foreignObject')
      .attr('x', 15)
      .attr('y', -50)
      .attr('width', 320)
      .attr('height', 140)
      .style('overflow', 'visible')
      .style('pointer-events', 'auto')

    labels.each(function(d) {
      const node = (d as any).data as TreeNode
      const foreignObject = d3.select(this)
      
      const labelDiv = foreignObject.append('xhtml:div')
        .style('display', 'flex')
        .style('flex-direction', 'column')
        .style('gap', '8px')
        .style('padding', '14px')
        .style('background', 'white')
        .style('border', `2px solid ${colors[node.type]}`)
        .style('border-radius', '8px')
        .style('box-shadow', '0 2px 8px rgba(0,0,0,0.1)')
        .style('min-width', '290px')
        .style('width', '100%')
        .style('max-width', '100%')
        .style('box-sizing', 'border-box')
        .style('overflow', 'visible')
        .style('min-height', 'auto')

      // Name
      labelDiv.append('xhtml:div')
        .style('font-weight', '600')
        .style('font-size', '14px')
        .style('color', '#1f2937')
        .text(node.name)

      // ID with copy button
      const idRow = labelDiv.append('xhtml:div')
        .style('display', 'flex')
        .style('align-items', 'center')
        .style('gap', '8px')
        .style('font-size', '11px')
        .style('color', '#6b7280')
        .style('font-family', 'monospace')
        .style('flex-wrap', 'wrap')
        .style('word-break', 'break-all')

      const idSpan = idRow.append('xhtml:span')
        .text(`ID: ${node.id}`)
        .style('flex', '1')
        .style('min-width', '150px')
        .style('word-break', 'break-all')
        .style('overflow-wrap', 'break-word')
        .style('line-height', '1.4')

      const copyBtn = idRow.append('xhtml:button')
        .style('background', copiedId === node.id ? '#10b98120' : 'transparent')
        .style('border', '1px solid #e5e7eb')
        .style('border-radius', '4px')
        .style('cursor', 'pointer')
        .style('padding', '4px 8px')
        .style('display', 'flex')
        .style('align-items', 'center')
        .style('color', copiedId === node.id ? '#10b981' : '#6b7280')
        .style('font-size', '10px')
        .style('transition', 'all 0.2s')
        .style('flex-shrink', '0')
        .style('white-space', 'nowrap')
        .on('click', function(e) {
          e.stopPropagation()
          copyToClipboard(node.id, node.name)
        })
        .on('mouseenter', function() {
          d3.select(this).style('background', '#f3f4f6')
        })
        .on('mouseleave', function() {
          d3.select(this).style('background', copiedId === node.id ? '#10b98120' : 'transparent')
        })

      if (copiedId === node.id) {
        copyBtn.append('xhtml:span').html('✓ Copied')
      } else {
        copyBtn.append('xhtml:span').html('📋 Copy ID')
      }

      // Badge for type
      labelDiv.append('xhtml:div')
        .style('display', 'inline-block')
        .style('padding', '2px 8px')
        .style('background', `${colors[node.type]}20`)
        .style('color', colors[node.type])
        .style('border-radius', '4px')
        .style('font-size', '10px')
        .style('font-weight', '500')
        .style('width', 'fit-content')
        .style('margin-top', '2px')
        .text(node.type.charAt(0).toUpperCase() + node.type.slice(1))

      // Additional info
      if (node.type === 'class') {
        const cls = node.data as Class
        if (cls.code) {
          labelDiv.append('xhtml:div')
            .style('font-size', '11px')
            .style('color', '#6b7280')
            .style('margin-top', '2px')
            .text(`Code: ${cls.code}`)
        }
      }
      if (node.type === 'section') {
        const sec = node.data as Section
        if (sec.studentCount !== undefined) {
          labelDiv.append('xhtml:div')
            .style('font-size', '11px')
            .style('color', '#6b7280')
            .style('margin-top', '2px')
            .text(`Students: ${sec.studentCount}`)
        }
      }
    })

    // Reset zoom to fit
    const bounds = (g.node() as any)?.getBBox()
    if (bounds) {
      const fullWidth = bounds.width
      const fullHeight = bounds.height
      const scale = Math.min(width / fullWidth, height / fullHeight) * 0.8
      const translateX = (width - fullWidth * scale) / 2 - bounds.x * scale
      const translateY = 20 - bounds.y * scale

      svg.transition().duration(750).call(
        zoomBehavior.transform as any,
        d3.zoomIdentity.translate(translateX, translateY).scale(scale)
      )
    } else {
      svg.transition().duration(750).call(
        zoomBehavior.transform as any,
        d3.zoomIdentity.translate(width / 2 - 100, 20).scale(0.8)
      )
    }

  }, [institute, classes, sectionsByClass, copiedId])

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Organization Tree</h2>
          <p className="text-sm text-gray-600 mt-1">
            Interactive visualization of classes and sections. Click 📋 to copy IDs.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => setShowIdList(!showIdList)}>
            <List className="h-4 w-4 mr-2" />
            {showIdList ? 'Hide' : 'Show'} ID List
          </Button>
          <Button variant="outline" size="sm" onClick={exportIds}>
            <Download className="h-4 w-4 mr-2" />
            Export IDs
          </Button>
          {onAddClass && (
            <Button variant="outline" size="sm" onClick={onAddClass}>
              <Plus className="h-4 w-4 mr-2" />
              Add Class
            </Button>
          )}
        </div>
      </div>

      <Card className="p-4 overflow-hidden">
        <div className="mb-4 flex items-center justify-between text-sm text-gray-600">
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-blue-500"></div>
              <span>Institute</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-green-500"></div>
              <span>Class</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-purple-500"></div>
              <span>Section</span>
            </div>
          </div>
          <div className="text-xs">
            Zoom: {Math.round(zoom * 100)}% | Scroll to zoom, drag to pan
          </div>
        </div>
        {showIdList && (
          <Card className="mb-4 p-4 bg-gray-50">
            <div className="flex justify-between items-center mb-4">
              <h3 className="font-semibold text-gray-900">All IDs (Click to Copy)</h3>
              <Button variant="ghost" size="sm" onClick={() => {
                const allIds = [
                  { type: 'Institute', name: institute.name, id: institute.id },
                  ...classes.flatMap(cls => [
                    { type: 'Class', name: cls.name, id: cls.id },
                    ...(sectionsByClass[cls.id] || []).map(sec => ({
                      type: 'Section',
                      name: sec.name,
                      id: sec.id
                    }))
                  ])
                ]
                const allIdsText = allIds.map(item => item.id).join('\n')
                navigator.clipboard.writeText(allIdsText)
                toast({
                  title: 'All IDs Copied',
                  description: 'All IDs copied to clipboard (one per line)',
                })
              }}>
                <Copy className="h-4 w-4 mr-2" />
                Copy All IDs
              </Button>
            </div>
            <div className="space-y-2 max-h-64 overflow-y-auto">
              <div className="flex items-center gap-2 p-2 bg-white rounded border">
                <Building2 className="h-4 w-4 text-blue-600" />
                <span className="flex-1 font-medium">{institute.name}</span>
                <code className="text-xs text-gray-600 font-mono">{institute.id}</code>
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-6 px-2"
                  onClick={() => copyToClipboard(institute.id, institute.name)}
                >
                  {copiedId === institute.id ? (
                    <Check className="h-3 w-3 text-green-600" />
                  ) : (
                    <Copy className="h-3 w-3" />
                  )}
                </Button>
              </div>
              {classes.map(cls => (
                <div key={cls.id} className="space-y-1">
                  <div className="flex items-center gap-2 p-2 bg-white rounded border ml-4">
                    <GraduationCap className="h-4 w-4 text-green-600" />
                    <span className="flex-1 font-medium">{cls.name}</span>
                    <code className="text-xs text-gray-600 font-mono">{cls.id}</code>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-6 px-2"
                      onClick={() => copyToClipboard(cls.id, cls.name)}
                    >
                      {copiedId === cls.id ? (
                        <Check className="h-3 w-3 text-green-600" />
                      ) : (
                        <Copy className="h-3 w-3" />
                      )}
                    </Button>
                  </div>
                  {(sectionsByClass[cls.id] || []).map(sec => (
                    <div key={sec.id} className="flex items-center gap-2 p-2 bg-white rounded border ml-8">
                      <Users className="h-4 w-4 text-purple-600" />
                      <span className="flex-1 text-sm">{sec.name}</span>
                      <code className="text-xs text-gray-600 font-mono">{sec.id}</code>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-6 px-2"
                        onClick={() => copyToClipboard(sec.id, sec.name)}
                      >
                        {copiedId === sec.id ? (
                          <Check className="h-3 w-3 text-green-600" />
                        ) : (
                          <Copy className="h-3 w-3" />
                        )}
                      </Button>
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </Card>
        )}
        <div 
          ref={containerRef}
          className="w-full overflow-auto border rounded-lg bg-gray-50"
          style={{ minHeight: '600px', overflow: 'visible' }}
        >
          <svg ref={svgRef} className="w-full" style={{ overflow: 'visible' }}></svg>
        </div>
      </Card>
    </div>
  )
}
