'use client'

import { useEffect, useState } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'
import dynamic from 'next/dynamic'
import { useRequireAuth } from '@/hooks/useRequireAuth'
import { certificatesApi } from '@/lib/api'
import { useDesignerState } from '@/components/certificate-designer/useDesignerState'
import FieldPanel from '@/components/certificate-designer/FieldPanel'
import PropertiesPanel from '@/components/certificate-designer/PropertiesPanel'
import DesignerToolbar from '@/components/certificate-designer/DesignerToolbar'
import type { TemplateConfig } from '@/components/certificate-designer/types'

// react-konva requires window/document — must be loaded client-side only
const DesignerCanvas = dynamic(
  () => import('@/components/certificate-designer/DesignerCanvas'),
  { ssr: false, loading: () => <CanvasPlaceholder /> }
)

function CanvasPlaceholder() {
  return (
    <div className="flex items-center justify-center h-full text-gray-400 text-sm">
      Loading canvas...
    </div>
  )
}

export default function CertificateDesignerPage() {
  const { user } = useRequireAuth(['TENANT_ADMIN', 'SYSTEM_ADMIN', 'CONTENT_MANAGER', 'INSTRUCTOR'])
  const searchParams = useSearchParams()
  const router = useRouter()
  const templateId = searchParams.get('id')

  const [templateName, setTemplateName] = useState('Untitled Template')
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(!!templateId)
  const [exporting, setExporting] = useState(false)
  const [importing, setImporting] = useState(false)

  const designer = useDesignerState()

  // Load existing template
  useEffect(() => {
    if (!templateId) return
    setLoading(true)
    certificatesApi.listTemplates()
      .then((templates: any[]) => {
        const template = templates.find((t: any) => t.id === templateId)
        if (template) {
          setTemplateName(template.name || 'Untitled Template')
          if (template.config) {
            designer.loadConfig(template.config as TemplateConfig)
          }
        }
      })
      .catch(err => console.error('Failed to load template:', err))
      .finally(() => setLoading(false))
  }, [templateId]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleSave = async () => {
    setSaving(true)
    try {
      const config = designer.toConfig() as unknown as Record<string, unknown>
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

  const handleSaveAsNew = async () => {
    setSaving(true)
    try {
      const config = designer.toConfig() as unknown as Record<string, unknown>
      const created = await certificatesApi.createTemplate({
        name: `${templateName} (Copy)`,
        description: '',
        config,
      })
      router.replace(`/certificates/designer?id=${created.id}`)
    } catch (err) {
      console.error('Failed to save as new template:', err)
    } finally {
      setSaving(false)
    }
  }

  const handleExport = async () => {
    if (!templateId) return
    setExporting(true)
    try {
      const blob = await certificatesApi.exportTemplate(templateId)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `template-${templateName.replace(/\s+/g, '-').toLowerCase()}.zip`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
    } catch (err) {
      console.error('Failed to export template:', err)
    } finally {
      setExporting(false)
    }
  }

  const handleImport = async (file: File) => {
    setImporting(true)
    try {
      const created = await certificatesApi.importTemplate(file)
      router.replace(`/certificates/designer?id=${created.id}`)
    } catch (err) {
      console.error('Failed to import template:', err)
    } finally {
      setImporting(false)
    }
  }

  const selectedField = designer.fields.find(f => f.id === designer.selectedFieldId) || null

  if (loading) {
    return <div className="flex items-center justify-center h-screen">Loading template...</div>
  }

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      <DesignerToolbar
        templateName={templateName}
        onNameChange={setTemplateName}
        canUndo={designer.canUndo}
        canRedo={designer.canRedo}
        onUndo={designer.undo}
        onRedo={designer.redo}
        zoom={designer.zoom}
        onZoomChange={designer.setZoom}
        orientation={designer.orientation}
        onToggleOrientation={designer.toggleOrientation}
        snapToGrid={designer.snapToGrid}
        onSnapToGridChange={designer.setSnapToGrid}
        backgroundColor={designer.backgroundColor}
        onBackgroundColorChange={designer.setBackgroundColor}
        saving={saving}
        onSave={handleSave}
        onSaveAsNew={handleSaveAsNew}
        isEditing={!!templateId}
        onExport={handleExport}
        onImport={handleImport}
        exporting={exporting}
        importing={importing}
        onBack={() => router.push('/certificates')}
      />

      <div className="flex flex-1 overflow-hidden">
        <FieldPanel onAddField={designer.addField} />

        {/* Canvas area */}
        <div className="flex-1 overflow-auto flex items-center justify-center bg-gray-100 p-8">
          <DesignerCanvas
            fields={designer.fields}
            selectedFieldId={designer.selectedFieldId}
            pageSize={designer.pageSize}
            backgroundColor={designer.backgroundColor}
            zoom={designer.zoom}
            snapToGrid={designer.snapToGrid}
            onSelectField={designer.setSelectedFieldId}
            onUpdateField={designer.updateField}
          />
        </div>

        <PropertiesPanel
          field={selectedField}
          onUpdateField={designer.updateField}
          onDeleteField={designer.deleteField}
        />
      </div>
    </div>
  )
}
