'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { AlignLeft, AlignCenter, AlignRight, Trash2, Bold } from 'lucide-react'
import type { DesignerField } from './types'
import { FIELD_LABELS } from './types'

interface PropertiesPanelProps {
  field: DesignerField | null
  onUpdateField: (id: string, updates: Partial<DesignerField>) => void
  onDeleteField: (id: string) => void
}

function NumberInput({
  label, value, onChange, min, max, step,
}: {
  label: string; value: number; onChange: (v: number) => void; min?: number; max?: number; step?: number
}) {
  return (
    <div>
      <label className="text-xs text-gray-500">{label}</label>
      <input
        type="number"
        className="w-full border rounded px-2 py-1 text-sm"
        value={Math.round(value * 100) / 100}
        onChange={e => onChange(Number(e.target.value))}
        min={min}
        max={max}
        step={step || 1}
      />
    </div>
  )
}

export default function PropertiesPanel({ field, onUpdateField, onDeleteField }: PropertiesPanelProps) {
  const [confirmDelete, setConfirmDelete] = useState(false)

  if (!field) {
    return (
      <div className="w-64 border-l bg-white p-3 overflow-y-auto shrink-0">
        <p className="text-sm text-gray-400 text-center mt-8">Select an element to edit its properties</p>
      </div>
    )
  }

  const update = (updates: Partial<DesignerField>) => onUpdateField(field.id, updates)

  const isTextField = [
    'studentName', 'courseName', 'date', 'credentialId',
    'instituteName', 'grade', 'customText',
  ].includes(field.type)

  const isImageField = ['customImage', 'logo', 'signature'].includes(field.type)
  const isQrCode = field.type === 'qrCode'
  const isBackground = field.type === 'backgroundImage'
  const isEditable = field.type === 'customText'

  return (
    <div className="w-64 border-l bg-white p-3 overflow-y-auto shrink-0">
      <div className="space-y-3">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
          {FIELD_LABELS[field.type]}
        </p>

        {/* Text content */}
        {isTextField && (
          <div>
            <label className="text-xs text-gray-500">{isEditable ? 'Text' : 'Placeholder'}</label>
            <input
              className="w-full border rounded px-2 py-1 text-sm"
              value={field.text || ''}
              onChange={e => update({ text: e.target.value })}
              readOnly={!isEditable}
            />
            {!isEditable && (
              <p className="text-xs text-gray-400 mt-0.5">Auto-filled at generation</p>
            )}
          </div>
        )}

        {/* Font size */}
        {isTextField && (
          <NumberInput
            label="Font Size"
            value={field.fontSize || 14}
            onChange={v => update({ fontSize: Math.max(8, Math.min(72, v)) })}
            min={8}
            max={72}
          />
        )}

        {/* Font weight */}
        {isTextField && (
          <div>
            <label className="text-xs text-gray-500">Font Weight</label>
            <div className="flex gap-1 mt-1">
              <Button
                variant={field.fontWeight === 'bold' ? 'default' : 'outline'}
                size="sm"
                className="h-8 w-8 p-0"
                onClick={() => update({ fontWeight: field.fontWeight === 'bold' ? 'normal' : 'bold' })}
              >
                <Bold className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
        )}

        {/* Color */}
        {isTextField && (
          <div>
            <label className="text-xs text-gray-500">Color</label>
            <div className="flex gap-2 mt-1">
              <input
                type="color"
                value={field.color || '#333333'}
                onChange={e => update({ color: e.target.value })}
                className="h-8 w-8 rounded cursor-pointer"
              />
              <input
                className="flex-1 border rounded px-2 py-1 text-sm font-mono"
                value={field.color || '#333333'}
                onChange={e => update({ color: e.target.value })}
              />
            </div>
          </div>
        )}

        {/* Text alignment */}
        {isTextField && (
          <div>
            <label className="text-xs text-gray-500">Alignment</label>
            <div className="flex gap-1 mt-1">
              {([
                { value: 'left', icon: <AlignLeft className="h-3.5 w-3.5" /> },
                { value: 'center', icon: <AlignCenter className="h-3.5 w-3.5" /> },
                { value: 'right', icon: <AlignRight className="h-3.5 w-3.5" /> },
              ] as const).map(opt => (
                <Button
                  key={opt.value}
                  variant={(field.alignment || 'center') === opt.value ? 'default' : 'outline'}
                  size="sm"
                  className="h-8 w-8 p-0"
                  onClick={() => update({ alignment: opt.value })}
                >
                  {opt.icon}
                </Button>
              ))}
            </div>
          </div>
        )}

        {/* Date format */}
        {field.type === 'date' && (
          <div>
            <label className="text-xs text-gray-500">Date Format</label>
            <input
              className="w-full border rounded px-2 py-1 text-sm"
              value={field.format || 'MMMM dd, yyyy'}
              onChange={e => update({ format: e.target.value })}
              placeholder="MMMM dd, yyyy"
            />
          </div>
        )}

        {/* Signature label */}
        {field.type === 'signature' && (
          <div>
            <label className="text-xs text-gray-500">Label</label>
            <input
              className="w-full border rounded px-2 py-1 text-sm"
              value={field.label || ''}
              onChange={e => update({ label: e.target.value })}
              placeholder="Authorized Signature"
            />
          </div>
        )}

        {/* Opacity */}
        {(isImageField || isBackground) && (
          <div>
            <label className="text-xs text-gray-500">Opacity</label>
            <input
              type="range"
              className="w-full mt-1"
              min={0}
              max={1}
              step={0.05}
              value={field.opacity ?? 1}
              onChange={e => update({ opacity: Number(e.target.value) })}
            />
            <span className="text-xs text-gray-400">{Math.round((field.opacity ?? 1) * 100)}%</span>
          </div>
        )}

        {/* QR size */}
        {isQrCode && (
          <NumberInput
            label="Size"
            value={field.size || 100}
            onChange={v => {
              const s = Math.max(40, Math.min(300, v))
              update({ size: s, width: s, height: s })
            }}
            min={40}
            max={300}
          />
        )}

        {/* Position */}
        {!isBackground && (
          <>
            <div className="grid grid-cols-2 gap-2">
              <NumberInput label="X" value={field.x} onChange={v => update({ x: v })} />
              <NumberInput label="Y" value={field.y} onChange={v => update({ y: v })} />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <NumberInput label="Width" value={field.width || 100} onChange={v => update({ width: Math.max(20, v) })} min={20} />
              <NumberInput label="Height" value={field.height || 30} onChange={v => update({ height: Math.max(20, v) })} min={20} />
            </div>
            <NumberInput
              label="Rotation"
              value={field.rotation || 0}
              onChange={v => update({ rotation: v })}
              min={-360}
              max={360}
            />
          </>
        )}

        {/* Delete with confirmation */}
        {confirmDelete ? (
          <div className="mt-4 space-y-2">
            <p className="text-xs text-red-600">Delete this field?</p>
            <div className="flex gap-2">
              <Button
                variant="destructive"
                size="sm"
                className="flex-1"
                onClick={() => { onDeleteField(field.id); setConfirmDelete(false) }}
              >
                Confirm
              </Button>
              <Button
                variant="outline"
                size="sm"
                className="flex-1"
                onClick={() => setConfirmDelete(false)}
              >
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <Button
            variant="destructive"
            size="sm"
            className="w-full mt-4"
            onClick={() => setConfirmDelete(true)}
          >
            <Trash2 className="h-3.5 w-3.5 mr-1" />
            Delete Field
          </Button>
        )}
      </div>
    </div>
  )
}
