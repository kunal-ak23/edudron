export type FieldType =
  | 'studentName' | 'courseName' | 'date' | 'credentialId'
  | 'instituteName' | 'grade' | 'qrCode' | 'customText'
  | 'customImage' | 'logo' | 'signature' | 'backgroundImage'

export interface DesignerField {
  id: string
  type: FieldType
  x: number
  y: number
  width?: number
  height?: number
  rotation?: number
  // Text properties
  text?: string
  fontSize?: number
  fontWeight?: 'normal' | 'bold'
  color?: string
  alignment?: 'left' | 'center' | 'right'
  format?: string
  // Image properties
  imageUrl?: string
  opacity?: number
  // QR properties
  size?: number
  // Label
  label?: string
}

export interface TemplateConfig {
  fields: DesignerField[]
  pageSize: { width: number; height: number }
  orientation: 'landscape' | 'portrait'
  backgroundColor?: string
}

export const PAGE_SIZES = {
  'a4-landscape': { width: 842, height: 595 },
  'a4-portrait': { width: 595, height: 842 },
} as const

export const FIELD_DEFAULTS: Record<FieldType, Partial<DesignerField>> = {
  studentName: { text: 'Student Name', fontSize: 36, fontWeight: 'bold', color: '#1E3A5F', width: 300, height: 50 },
  courseName: { text: 'Course Name', fontSize: 22, fontWeight: 'bold', color: '#0891B2', width: 300, height: 40 },
  date: { text: 'April 01, 2026', fontSize: 14, color: '#666666', format: 'MMMM dd, yyyy', width: 200, height: 30 },
  credentialId: { text: 'EDU-2026-XXXXX', fontSize: 10, color: '#999999', width: 180, height: 25 },
  instituteName: { text: 'University Name', fontSize: 18, fontWeight: 'bold', color: '#1E3A5F', width: 250, height: 35 },
  grade: { text: 'Grade: A+', fontSize: 16, color: '#333333', width: 150, height: 30 },
  qrCode: { size: 100, width: 100, height: 100 },
  customText: { text: 'Custom Text', fontSize: 16, color: '#333333', width: 200, height: 30 },
  customImage: { width: 150, height: 100, opacity: 1 },
  logo: { width: 120, height: 80, opacity: 1 },
  signature: { width: 150, height: 60, opacity: 1, label: 'Authorized Signature' },
  backgroundImage: { opacity: 1 },
}

export const FIELD_LABELS: Record<FieldType, string> = {
  studentName: 'Student Name',
  courseName: 'Course Name',
  date: 'Date Issued',
  credentialId: 'Credential ID',
  instituteName: 'Institute Name',
  grade: 'Grade/Score',
  qrCode: 'QR Code',
  customText: 'Custom Text',
  customImage: 'Custom Image',
  logo: 'Logo',
  signature: 'Signature',
  backgroundImage: 'Background Image',
}
