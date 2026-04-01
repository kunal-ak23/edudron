'use client'

import { useEffect, useState, useCallback } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'
import { useRequireAuth } from '@kunal-ak23/edudron-shared-utils'
import { Button } from '@/components/ui/button'
import { certificatesApi } from '@/lib/api'
import type { TemplateConfig, DesignerField, FieldType } from '@/components/certificate-designer/types'
import { PAGE_SIZES, FIELD_DEFAULTS } from '@/components/certificate-designer/types'

export default function CertificateDesignerPage() {
  const { user } = useRequireAuth({ allowedRoles: ['TENANT_ADMIN', 'SYSTEM_ADMIN', 'CONTENT_MANAGER', 'INSTRUCTOR'] })
  const searchParams = useSearchParams()
  const router = useRouter()
  const templateId = searchParams.get('id')

  const [templateName, setTemplateName] = useState('Untitled Template')
  const [fields, setFields] = useState<DesignerField[]>([])
  const [selectedFieldId, setSelectedFieldId] = useState<string | null>(null)
  const [pageSize, setPageSize] = useState(PAGE_SIZES['a4-landscape'])
  const [orientation, setOrientation] = useState<'landscape' | 'portrait'>('landscape')
  const [backgroundColor, setBackgroundColor] = useState('#FFFFFF')
  const [zoom, setZoom] = useState(0.75)
  const [snapToGrid, setSnapToGrid] = useState(false)
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(!!templateId)

  // History for undo/redo
  const [history, setHistory] = useState<DesignerField[][]>([[]])
  const [historyIndex, setHistoryIndex] = useState(0)

  // Load existing template
  useEffect(() => {
    if (templateId) {
      loadTemplate(templateId)
    }
  }, [templateId])

  const loadTemplate = async (id: string) => {
    setLoading(true)
    try {
      const templates = await certificatesApi.listTemplates()
      const template = templates.find((t: any) => t.id === id)
      if (template) {
        setTemplateName(template.name || 'Untitled Template')
        const config = template.config as TemplateConfig
        if (config) {
          setFields(config.fields || [])
          if (config.pageSize) setPageSize(config.pageSize)
          if (config.orientation) setOrientation(config.orientation)
          if (config.backgroundColor) setBackgroundColor(config.backgroundColor)
          setHistory([config.fields || []])
          setHistoryIndex(0)
        }
      }
    } catch (err) {
      console.error('Failed to load template:', err)
    } finally {
      setLoading(false)
    }
  }

  const pushHistory = useCallback((newFields: DesignerField[]) => {
    setHistory(prev => {
      const newHistory = prev.slice(0, historyIndex + 1)
      newHistory.push(newFields)
      if (newHistory.length > 50) newHistory.shift()
      return newHistory
    })
    setHistoryIndex(prev => Math.min(prev + 1, 49))
  }, [historyIndex])

  const addField = useCallback((type: FieldType) => {
    const defaults = FIELD_DEFAULTS[type]
    const newField: DesignerField = {
      id: crypto.randomUUID(),
      type,
      x: pageSize.width / 2 - (defaults.width || 100) / 2,
      y: pageSize.height / 2 - (defaults.height || 50) / 2,
      ...defaults,
    }
    const newFields = [...fields, newField]
    setFields(newFields)
    setSelectedFieldId(newField.id)
    pushHistory(newFields)
  }, [fields, pageSize, pushHistory])

  const updateField = useCallback((id: string, updates: Partial<DesignerField>) => {
    const newFields = fields.map(f => f.id === id ? { ...f, ...updates } : f)
    setFields(newFields)
    pushHistory(newFields)
  }, [fields, pushHistory])

  const deleteField = useCallback((id: string) => {
    const newFields = fields.filter(f => f.id !== id)
    setFields(newFields)
    setSelectedFieldId(null)
    pushHistory(newFields)
  }, [fields, pushHistory])

  const undo = useCallback(() => {
    if (historyIndex > 0) {
      const newIndex = historyIndex - 1
      setHistoryIndex(newIndex)
      setFields(history[newIndex])
      setSelectedFieldId(null)
    }
  }, [history, historyIndex])

  const redo = useCallback(() => {
    if (historyIndex < history.length - 1) {
      const newIndex = historyIndex + 1
      setHistoryIndex(newIndex)
      setFields(history[newIndex])
      setSelectedFieldId(null)
    }
  }, [history, historyIndex])

  const toggleOrientation = useCallback(() => {
    const newOrientation = orientation === 'landscape' ? 'portrait' : 'landscape'
    setOrientation(newOrientation)
    setPageSize(newOrientation === 'landscape' ? PAGE_SIZES['a4-landscape'] : PAGE_SIZES['a4-portrait'])
  }, [orientation])

  const toConfig = useCallback((): TemplateConfig => ({
    fields,
    pageSize,
    orientation,
    backgroundColor,
  }), [fields, pageSize, orientation, backgroundColor])

  const handleSave = async () => {
    setSaving(true)
    try {
      const config = toConfig()
      if (templateId) {
        await certificatesApi.updateTemplate(templateId, { name: templateName, config })
      } else {
        const created = await certificatesApi.createTemplate({ name: templateName, description: '', config })
        router.replace(`/certificates/designer?id=${created.id}`)
      }
    } catch (err) {
      console.error('Failed to save template:', err)
    } finally {
      setSaving(false)
    }
  }

  const selectedField = fields.find(f => f.id === selectedFieldId) || null

  if (loading) {
    return <div className="flex items-center justify-center h-screen">Loading template...</div>
  }

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* Toolbar */}
      <div className="h-14 border-b bg-white flex items-center px-4 gap-3 shrink-0">
        <Button variant="ghost" size="sm" onClick={() => router.push('/certificates')}>
          Back
        </Button>
        <input
          className="text-sm font-medium border-b border-transparent hover:border-gray-300 focus:border-blue-500 outline-none px-1"
          value={templateName}
          onChange={e => setTemplateName(e.target.value)}
        />
        <div className="flex-1" />
        <Button variant="outline" size="sm" onClick={undo} disabled={historyIndex <= 0}>Undo</Button>
        <Button variant="outline" size="sm" onClick={redo} disabled={historyIndex >= history.length - 1}>Redo</Button>
        <Button variant="outline" size="sm" onClick={toggleOrientation}>
          {orientation === 'landscape' ? 'Landscape' : 'Portrait'}
        </Button>
        <label className="flex items-center gap-1 text-xs">
          <input type="checkbox" checked={snapToGrid} onChange={e => setSnapToGrid(e.target.checked)} />
          Grid
        </label>
        <select className="text-xs border rounded px-2 py-1" value={zoom} onChange={e => setZoom(Number(e.target.value))}>
          <option value={0.5}>50%</option>
          <option value={0.75}>75%</option>
          <option value={1}>100%</option>
          <option value={1.5}>150%</option>
        </select>
        <Button size="sm" onClick={handleSave} disabled={saving}>
          {saving ? 'Saving...' : 'Save'}
        </Button>
      </div>

      {/* Main area */}
      <div className="flex flex-1 overflow-hidden">
        {/* Field Panel (left) */}
        <div className="w-56 border-r bg-white p-3 overflow-y-auto shrink-0">
          <p className="text-xs font-semibold text-gray-500 mb-2 uppercase">Data Fields</p>
          {(['studentName', 'courseName', 'date', 'credentialId', 'instituteName', 'grade'] as FieldType[]).map(type => (
            <button key={type} onClick={() => addField(type)}
              className="w-full text-left text-sm px-3 py-2 rounded hover:bg-gray-100 mb-1">
              + {type.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase())}
            </button>
          ))}
          <p className="text-xs font-semibold text-gray-500 mb-2 mt-4 uppercase">Media</p>
          {(['qrCode', 'customImage', 'logo', 'signature', 'backgroundImage'] as FieldType[]).map(type => (
            <button key={type} onClick={() => addField(type)}
              className="w-full text-left text-sm px-3 py-2 rounded hover:bg-gray-100 mb-1">
              + {type.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase())}
            </button>
          ))}
          <p className="text-xs font-semibold text-gray-500 mb-2 mt-4 uppercase">Custom</p>
          <button onClick={() => addField('customText')}
            className="w-full text-left text-sm px-3 py-2 rounded hover:bg-gray-100 mb-1">
            + Custom Text
          </button>
        </div>

        {/* Canvas (center) */}
        <div className="flex-1 overflow-auto flex items-center justify-center bg-gray-100 p-8">
          <div
            style={{
              width: pageSize.width * zoom,
              height: pageSize.height * zoom,
              backgroundColor,
              boxShadow: '0 4px 24px rgba(0,0,0,0.12)',
              position: 'relative',
              transform: `scale(1)`,
              transformOrigin: 'center center',
            }}
          >
            {/* Canvas placeholder — will be replaced with react-konva in Task 2 */}
            <div className="absolute inset-0 flex items-center justify-center text-gray-400 text-sm">
              {fields.length === 0 ? 'Click a field type to add it to the canvas' : ''}
            </div>
            {/* Temporary: render fields as divs until konva is wired */}
            {fields.map(field => (
              <div
                key={field.id}
                onClick={() => setSelectedFieldId(field.id)}
                style={{
                  position: 'absolute',
                  left: field.x * zoom,
                  top: field.y * zoom,
                  width: (field.width || 100) * zoom,
                  height: (field.height || 30) * zoom,
                  fontSize: (field.fontSize || 14) * zoom,
                  fontWeight: field.fontWeight || 'normal',
                  color: field.color || '#333',
                  border: selectedFieldId === field.id ? '2px solid #3b82f6' : '1px dashed #ccc',
                  borderRadius: 2,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: field.alignment || 'center',
                  overflow: 'hidden',
                  padding: '0 4px',
                  backgroundColor: field.type === 'qrCode' ? '#f3f4f6' : 'transparent',
                }}
              >
                {field.type === 'qrCode' ? 'QR' : field.text || field.type}
              </div>
            ))}
          </div>
        </div>

        {/* Properties Panel (right) */}
        <div className="w-64 border-l bg-white p-3 overflow-y-auto shrink-0">
          {selectedField ? (
            <div className="space-y-3">
              <p className="text-xs font-semibold text-gray-500 uppercase">
                {selectedField.type.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase())}
              </p>
              {selectedField.type === 'customText' && (
                <div>
                  <label className="text-xs text-gray-500">Text</label>
                  <input className="w-full border rounded px-2 py-1 text-sm" value={selectedField.text || ''}
                    onChange={e => updateField(selectedField.id, { text: e.target.value })} />
                </div>
              )}
              {selectedField.fontSize !== undefined && (
                <div>
                  <label className="text-xs text-gray-500">Font Size</label>
                  <input type="number" className="w-full border rounded px-2 py-1 text-sm" value={selectedField.fontSize}
                    onChange={e => updateField(selectedField.id, { fontSize: Number(e.target.value) })} />
                </div>
              )}
              {selectedField.color && (
                <div>
                  <label className="text-xs text-gray-500">Color</label>
                  <div className="flex gap-2">
                    <input type="color" value={selectedField.color}
                      onChange={e => updateField(selectedField.id, { color: e.target.value })} />
                    <input className="flex-1 border rounded px-2 py-1 text-sm" value={selectedField.color}
                      onChange={e => updateField(selectedField.id, { color: e.target.value })} />
                  </div>
                </div>
              )}
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-xs text-gray-500">X</label>
                  <input type="number" className="w-full border rounded px-2 py-1 text-sm" value={Math.round(selectedField.x)}
                    onChange={e => updateField(selectedField.id, { x: Number(e.target.value) })} />
                </div>
                <div>
                  <label className="text-xs text-gray-500">Y</label>
                  <input type="number" className="w-full border rounded px-2 py-1 text-sm" value={Math.round(selectedField.y)}
                    onChange={e => updateField(selectedField.id, { y: Number(e.target.value) })} />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-xs text-gray-500">Width</label>
                  <input type="number" className="w-full border rounded px-2 py-1 text-sm" value={selectedField.width || 100}
                    onChange={e => updateField(selectedField.id, { width: Number(e.target.value) })} />
                </div>
                <div>
                  <label className="text-xs text-gray-500">Height</label>
                  <input type="number" className="w-full border rounded px-2 py-1 text-sm" value={selectedField.height || 30}
                    onChange={e => updateField(selectedField.id, { height: Number(e.target.value) })} />
                </div>
              </div>
              <Button variant="destructive" size="sm" className="w-full mt-4"
                onClick={() => deleteField(selectedField.id)}>
                Delete Field
              </Button>
            </div>
          ) : (
            <p className="text-sm text-gray-400 text-center mt-8">Select an element to edit its properties</p>
          )}
        </div>
      </div>
    </div>
  )
}
