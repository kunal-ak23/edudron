'use client'

import { useEffect, useState, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  ArrowLeft,
  Loader2,
  Save,
  Globe,
  Archive,
  Download,
} from 'lucide-react'
import { simulationsApi, enrollmentsApi } from '@/lib/api'
import type { SimulationDTO } from '@kunal-ak23/edudron-shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'
import SimulationTreeView from '@/components/simulation/SimulationTreeView'
import SimulationNodeEditor from '@/components/simulation/SimulationNodeEditor'

export const dynamic = 'force-dynamic'

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-700 border-gray-300',
  GENERATING: 'bg-yellow-100 text-yellow-700 border-yellow-300',
  REVIEW: 'bg-blue-100 text-blue-700 border-blue-300',
  PUBLISHED: 'bg-green-100 text-green-700 border-green-300',
  ARCHIVED: 'bg-gray-100 text-gray-500 border-gray-300',
}

export default function SimulationEditorPage() {
  const router = useRouter()
  const params = useParams()
  const simulationId = params.id as string
  const { toast } = useToast()

  const [simulation, setSimulation] = useState<SimulationDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [treeData, setTreeData] = useState<any>(null)

  // Metadata form
  const [title, setTitle] = useState('')
  const [concept, setConcept] = useState('')
  const [subject, setSubject] = useState('')
  const [audience, setAudience] = useState('')
  const [description, setDescription] = useState('')
  const [visibility, setVisibility] = useState<'ALL' | 'ASSIGNED_ONLY'>('ALL')
  const [assignedSectionIds, setAssignedSectionIds] = useState<string[]>([])

  // Section data for assignment
  const [sections, setSections] = useState<{ id: string; name: string }[]>([])
  const [sectionsLoading, setSectionsLoading] = useState(false)

  // Dialogs
  const [publishDialogOpen, setPublishDialogOpen] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [archiving, setArchiving] = useState(false)
  const [exporting, setExporting] = useState(false)

  const loadSimulation = useCallback(async () => {
    try {
      setLoading(true)
      const data = await simulationsApi.getSimulation(simulationId)
      setSimulation(data)
      setTitle(data.title || '')
      setConcept(data.concept || '')
      setSubject(data.subject || '')
      setAudience(data.audience || '')
      setDescription(data.description || '')
      setVisibility(data.visibility || 'ALL')
      setAssignedSectionIds(data.assignedToSectionIds || [])
      setTreeData(data.treeData || null)

      // Auto-select root node
      if (data.treeData?.rootNodeId && !selectedNodeId) {
        setSelectedNodeId(data.treeData.rootNodeId)
      }
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Failed to load simulation',
        description: extractErrorMessage(error),
      })
    } finally {
      setLoading(false)
    }
  }, [simulationId, toast]) // eslint-disable-line react-hooks/exhaustive-deps

  const loadSections = useCallback(async () => {
    try {
      setSectionsLoading(true)
      const response = await enrollmentsApi.listSections()
      setSections(Array.isArray(response) ? response : [])
    } catch {
      // Sections might not load if there are none - that's ok
      setSections([])
    } finally {
      setSectionsLoading(false)
    }
  }, [])

  useEffect(() => {
    loadSimulation()
  }, [loadSimulation])

  useEffect(() => {
    if (visibility === 'ASSIGNED_ONLY') {
      loadSections()
    }
  }, [visibility, loadSections])

  // Save metadata
  const handleSaveMetadata = async () => {
    setSaving(true)
    try {
      const updated = await simulationsApi.updateSimulation(simulationId, {
        title,
        concept,
        subject,
        audience,
        description,
        visibility,
        assignedToSectionIds: visibility === 'ASSIGNED_ONLY' ? assignedSectionIds : undefined,
      })
      setSimulation(updated)
      toast({ title: 'Saved', description: 'Simulation metadata updated.' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Save failed',
        description: extractErrorMessage(error),
      })
    } finally {
      setSaving(false)
    }
  }

  // Save node within tree
  const handleSaveNode = async (updatedNode: any) => {
    if (!treeData?.nodes) return
    setSaving(true)
    try {
      const newTreeData = {
        ...treeData,
        nodes: {
          ...treeData.nodes,
          [updatedNode.id]: updatedNode,
        },
      }
      const updated = await simulationsApi.updateTree(simulationId, newTreeData)
      setTreeData(updated.treeData || newTreeData)
      setSimulation(updated)
      toast({ title: 'Node saved', description: `Node ${updatedNode.id} updated.` })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Save failed',
        description: extractErrorMessage(error),
      })
    } finally {
      setSaving(false)
    }
  }

  // Publish
  const handlePublish = async () => {
    setPublishing(true)
    try {
      const updated = await simulationsApi.publishSimulation(simulationId)
      setSimulation(updated)
      setPublishDialogOpen(false)
      toast({ title: 'Published', description: 'Simulation is now live for students.' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Publish failed',
        description: extractErrorMessage(error),
      })
    } finally {
      setPublishing(false)
    }
  }

  // Archive
  const handleArchive = async () => {
    setArchiving(true)
    try {
      const updated = await simulationsApi.archiveSimulation(simulationId)
      setSimulation(updated)
      toast({ title: 'Archived', description: 'Simulation has been archived.' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Archive failed',
        description: extractErrorMessage(error),
      })
    } finally {
      setArchiving(false)
    }
  }

  // Export
  const handleExport = async () => {
    setExporting(true)
    try {
      const data = await simulationsApi.exportSimulation(simulationId)
      const blob = new Blob([JSON.stringify(data, null, 2)], {
        type: 'application/json',
      })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `simulation-${(title || 'untitled').toLowerCase().replace(/\s+/g, '-')}.json`
      a.click()
      URL.revokeObjectURL(url)
      toast({ title: 'Exported', description: 'Simulation JSON downloaded.' })
    } catch (error) {
      toast({
        variant: 'destructive',
        title: 'Export failed',
        description: extractErrorMessage(error),
      })
    } finally {
      setExporting(false)
    }
  }

  const toggleSectionAssignment = (sectionId: string) => {
    setAssignedSectionIds((prev) =>
      prev.includes(sectionId)
        ? prev.filter((id) => id !== sectionId)
        : [...prev, sectionId]
    )
  }

  // Loading state
  if (loading) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    )
  }

  if (!simulation) {
    return (
      <div className="text-center py-12">
        <p className="text-muted-foreground">Simulation not found.</p>
        <Button
          variant="outline"
          className="mt-4"
          onClick={() => router.push('/simulations')}
        >
          Back to Simulations
        </Button>
      </div>
    )
  }

  const selectedNode =
    selectedNodeId && treeData?.nodes
      ? treeData.nodes[selectedNodeId]
      : null

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => router.push('/simulations')}
          >
            <ArrowLeft className="h-4 w-4 mr-1" />
            Back
          </Button>
          <Badge
            variant="outline"
            className={STATUS_COLORS[simulation.status] || ''}
          >
            {simulation.status}
          </Badge>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={handleExport}
            disabled={exporting}
          >
            {exporting ? (
              <Loader2 className="h-4 w-4 mr-1 animate-spin" />
            ) : (
              <Download className="h-4 w-4 mr-1" />
            )}
            Export
          </Button>
          {simulation.status !== 'ARCHIVED' && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleArchive}
              disabled={archiving}
            >
              {archiving ? (
                <Loader2 className="h-4 w-4 mr-1 animate-spin" />
              ) : (
                <Archive className="h-4 w-4 mr-1" />
              )}
              Archive
            </Button>
          )}
          {(simulation.status === 'DRAFT' ||
            simulation.status === 'REVIEW') && (
            <Button
              size="sm"
              onClick={() => setPublishDialogOpen(true)}
            >
              <Globe className="h-4 w-4 mr-1" />
              Publish
            </Button>
          )}
        </div>
      </div>

      {/* Metadata Section */}
      <Card>
        <CardContent className="pt-4 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="md:col-span-2 space-y-1.5">
              <Label htmlFor="sim-title">Title</Label>
              <Input
                id="sim-title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Simulation title"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="sim-concept">Concept</Label>
              <Input
                id="sim-concept"
                value={concept}
                onChange={(e) => setConcept(e.target.value)}
                placeholder="e.g., Supply Chain Management"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="sim-subject">Subject</Label>
              <Input
                id="sim-subject"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                placeholder="e.g., Operations Management"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="sim-audience">Audience</Label>
              <Select value={audience} onValueChange={setAudience}>
                <SelectTrigger id="sim-audience">
                  <SelectValue placeholder="Select audience" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="UNDERGRADUATE">Undergraduate</SelectItem>
                  <SelectItem value="MBA">MBA</SelectItem>
                  <SelectItem value="GRADUATE">Graduate</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Visibility</Label>
              <Select
                value={visibility}
                onValueChange={(val) =>
                  setVisibility(val as 'ALL' | 'ASSIGNED_ONLY')
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Students</SelectItem>
                  <SelectItem value="ASSIGNED_ONLY">Assigned Sections Only</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="md:col-span-2 space-y-1.5">
              <Label htmlFor="sim-desc">Description</Label>
              <Textarea
                id="sim-desc"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Brief description of the simulation..."
                rows={3}
                className="resize-y"
              />
            </div>
          </div>

          {/* Section assignment */}
          {visibility === 'ASSIGNED_ONLY' && (
            <div className="space-y-2">
              <Label>Assigned Sections</Label>
              {sectionsLoading ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading sections...
                </div>
              ) : sections.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  No sections available. Create sections first.
                </p>
              ) : (
                <div className="grid grid-cols-2 md:grid-cols-3 gap-2 max-h-48 overflow-y-auto border rounded-md p-3">
                  {sections.map((section) => (
                    <label
                      key={section.id}
                      className="flex items-center gap-2 text-sm cursor-pointer"
                    >
                      <Checkbox
                        checked={assignedSectionIds.includes(section.id)}
                        onCheckedChange={() =>
                          toggleSectionAssignment(section.id)
                        }
                      />
                      <span className="truncate">{section.name}</span>
                    </label>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="flex justify-end">
            <Button
              size="sm"
              onClick={handleSaveMetadata}
              disabled={saving}
            >
              {saving ? (
                <Loader2 className="h-4 w-4 mr-1 animate-spin" />
              ) : (
                <Save className="h-4 w-4 mr-1" />
              )}
              Save Metadata
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Tree + Editor split */}
      <div className="grid grid-cols-1 lg:grid-cols-[35%_65%] gap-4 min-h-[500px]">
        {/* Left: Tree View */}
        <Card className="overflow-hidden">
          <SimulationTreeView
            treeData={treeData}
            selectedNodeId={selectedNodeId}
            onSelectNode={setSelectedNodeId}
          />
        </Card>

        {/* Right: Node Editor */}
        <Card>
          <CardContent className="pt-4">
            {selectedNode ? (
              <SimulationNodeEditor
                node={selectedNode}
                onSave={handleSaveNode}
              />
            ) : (
              <div className="flex items-center justify-center h-full min-h-[300px] text-muted-foreground">
                <p className="text-sm">
                  {treeData?.nodes
                    ? 'Select a node from the tree to edit it.'
                    : 'No tree data available.'}
                </p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Publish confirmation dialog */}
      <Dialog open={publishDialogOpen} onOpenChange={setPublishDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Publish Simulation</DialogTitle>
            <DialogDescription>
              This will make the simulation available to students. Are you sure
              you want to publish &quot;{title}&quot;?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setPublishDialogOpen(false)}
            >
              Cancel
            </Button>
            <Button
              onClick={handlePublish}
              disabled={publishing}
            >
              {publishing ? (
                <Loader2 className="h-4 w-4 mr-1 animate-spin" />
              ) : (
                <Globe className="h-4 w-4 mr-1" />
              )}
              Publish
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
