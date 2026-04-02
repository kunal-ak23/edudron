'use client'

import { Button } from '@/components/ui/button'
import { Undo2, Redo2, Save, ArrowLeft, Download, Upload, Copy } from 'lucide-react'

interface DesignerToolbarProps {
  templateName: string
  onNameChange: (name: string) => void
  canUndo: boolean
  canRedo: boolean
  onUndo: () => void
  onRedo: () => void
  zoom: number
  onZoomChange: (zoom: number) => void
  orientation: 'landscape' | 'portrait'
  onToggleOrientation: () => void
  snapToGrid: boolean
  onSnapToGridChange: (snap: boolean) => void
  backgroundColor: string
  onBackgroundColorChange: (color: string) => void
  saving: boolean
  onSave: () => void
  onSaveAsNew?: () => void
  isEditing: boolean
  onBack: () => void
}

export default function DesignerToolbar({
  templateName,
  onNameChange,
  canUndo,
  canRedo,
  onUndo,
  onRedo,
  zoom,
  onZoomChange,
  orientation,
  onToggleOrientation,
  snapToGrid,
  onSnapToGridChange,
  backgroundColor,
  onBackgroundColorChange,
  saving,
  onSave,
  onSaveAsNew,
  isEditing,
  onBack,
}: DesignerToolbarProps) {
  return (
    <div className="h-14 border-b bg-white flex items-center px-4 gap-3 shrink-0">
      <Button variant="ghost" size="sm" onClick={onBack}>
        <ArrowLeft className="h-4 w-4 mr-1" />
        Back
      </Button>

      <div className="w-px h-6 bg-gray-200" />

      <input
        className="text-sm font-medium border-b border-transparent hover:border-gray-300 focus:border-blue-500 outline-none px-1 min-w-[120px]"
        value={templateName}
        onChange={e => onNameChange(e.target.value)}
        placeholder="Template name"
      />

      <div className="flex-1" />

      {/* Undo / Redo */}
      <Button variant="outline" size="sm" onClick={onUndo} disabled={!canUndo} title="Undo">
        <Undo2 className="h-4 w-4" />
      </Button>
      <Button variant="outline" size="sm" onClick={onRedo} disabled={!canRedo} title="Redo">
        <Redo2 className="h-4 w-4" />
      </Button>

      <div className="w-px h-6 bg-gray-200" />

      {/* Orientation */}
      <Button variant="outline" size="sm" onClick={onToggleOrientation}>
        {orientation === 'landscape' ? 'Landscape' : 'Portrait'}
      </Button>

      {/* Grid */}
      <label className="flex items-center gap-1.5 text-xs cursor-pointer select-none">
        <input
          type="checkbox"
          checked={snapToGrid}
          onChange={e => onSnapToGridChange(e.target.checked)}
          className="rounded"
        />
        Grid
      </label>

      {/* Background color */}
      <div className="flex items-center gap-1">
        <span className="text-xs text-gray-500">BG</span>
        <input
          type="color"
          value={backgroundColor}
          onChange={e => onBackgroundColorChange(e.target.value)}
          className="h-7 w-7 rounded cursor-pointer border"
        />
      </div>

      {/* Zoom */}
      <select
        className="text-xs border rounded px-2 py-1.5 bg-white"
        value={zoom}
        onChange={e => onZoomChange(Number(e.target.value))}
      >
        <option value={0.5}>50%</option>
        <option value={0.75}>75%</option>
        <option value={1}>100%</option>
        <option value={1.5}>150%</option>
      </select>

      <div className="w-px h-6 bg-gray-200" />

      {/* Save */}
      <Button size="sm" onClick={onSave} disabled={saving}>
        <Save className="h-4 w-4 mr-1" />
        {saving ? 'Saving...' : 'Save'}
      </Button>
      {isEditing && onSaveAsNew && (
        <Button variant="outline" size="sm" onClick={onSaveAsNew} disabled={saving}>
          <Copy className="h-4 w-4 mr-1" />
          Save as New
        </Button>
      )}

      <div className="w-px h-6 bg-gray-200" />

      {/* Export / Import — wired in Tasks 7-8 */}
      <Button variant="outline" size="sm" disabled title="Export (coming soon)">
        <Download className="h-4 w-4" />
      </Button>
      <Button variant="outline" size="sm" disabled title="Import (coming soon)">
        <Upload className="h-4 w-4" />
      </Button>
    </div>
  )
}
