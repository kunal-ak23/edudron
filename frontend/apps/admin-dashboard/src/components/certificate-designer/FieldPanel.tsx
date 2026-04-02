'use client'

import type { FieldType } from './types'
import { FIELD_LABELS } from './types'
import {
  Type, ImageIcon, QrCode, Pen, FileImage, Building2,
  GraduationCap, Calendar, Hash, Award, Star,
} from 'lucide-react'

interface FieldPanelProps {
  onAddField: (type: FieldType) => void
}

const FIELD_ICONS: Record<FieldType, React.ReactNode> = {
  studentName: <GraduationCap className="h-4 w-4" />,
  courseName: <Award className="h-4 w-4" />,
  date: <Calendar className="h-4 w-4" />,
  credentialId: <Hash className="h-4 w-4" />,
  instituteName: <Building2 className="h-4 w-4" />,
  grade: <Star className="h-4 w-4" />,
  qrCode: <QrCode className="h-4 w-4" />,
  customImage: <ImageIcon className="h-4 w-4" />,
  logo: <FileImage className="h-4 w-4" />,
  signature: <Pen className="h-4 w-4" />,
  backgroundImage: <FileImage className="h-4 w-4" />,
  customText: <Type className="h-4 w-4" />,
}

const DATA_FIELDS: FieldType[] = ['studentName', 'courseName', 'date', 'credentialId', 'instituteName', 'grade']
const MEDIA_FIELDS: FieldType[] = ['qrCode', 'customImage', 'logo', 'signature', 'backgroundImage']
const CUSTOM_FIELDS: FieldType[] = ['customText']

function FieldButton({ type, onAdd }: { type: FieldType; onAdd: () => void }) {
  return (
    <button
      onClick={onAdd}
      className="w-full flex items-center gap-2 text-sm px-3 py-2 rounded hover:bg-gray-100 transition-colors text-left"
    >
      <span className="text-gray-500">{FIELD_ICONS[type]}</span>
      {FIELD_LABELS[type]}
    </button>
  )
}

function Section({ title, types, onAddField }: { title: string; types: FieldType[]; onAddField: (t: FieldType) => void }) {
  return (
    <div>
      <p className="text-xs font-semibold text-gray-500 mb-1 uppercase tracking-wider">{title}</p>
      {types.map(type => (
        <FieldButton key={type} type={type} onAdd={() => onAddField(type)} />
      ))}
    </div>
  )
}

export default function FieldPanel({ onAddField }: FieldPanelProps) {
  return (
    <div className="w-56 border-r bg-white p-3 overflow-y-auto shrink-0 space-y-4">
      <p className="text-sm font-semibold text-gray-700">Fields</p>
      <Section title="Data Fields" types={DATA_FIELDS} onAddField={onAddField} />
      <Section title="Media" types={MEDIA_FIELDS} onAddField={onAddField} />
      <Section title="Custom" types={CUSTOM_FIELDS} onAddField={onAddField} />
    </div>
  )
}
