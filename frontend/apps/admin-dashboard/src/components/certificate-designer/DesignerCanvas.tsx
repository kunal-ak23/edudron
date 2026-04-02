'use client'

import { useRef, useEffect, useCallback } from 'react'
import { Stage, Layer, Text, Rect, Transformer, Group, Line } from 'react-konva'
import type Konva from 'konva'
import type { DesignerField } from './types'

interface DesignerCanvasProps {
  fields: DesignerField[]
  selectedFieldId: string | null
  pageSize: { width: number; height: number }
  backgroundColor: string
  zoom: number
  snapToGrid: boolean
  onSelectField: (id: string | null) => void
  onUpdateField: (id: string, updates: Partial<DesignerField>) => void
}

const GRID_SIZE = 20

function FieldNode({
  field,
  isSelected,
  onSelect,
  onDragEnd,
  onTransformEnd,
  snapToGrid,
}: {
  field: DesignerField
  isSelected: boolean
  onSelect: () => void
  onDragEnd: (x: number, y: number) => void
  onTransformEnd: (updates: Partial<DesignerField>) => void
  snapToGrid: boolean
}) {
  const shapeRef = useRef<Konva.Group>(null)

  const snapPosition = (val: number) =>
    snapToGrid ? Math.round(val / GRID_SIZE) * GRID_SIZE : val

  const handleDragEnd = (e: Konva.KonvaEventObject<DragEvent>) => {
    onDragEnd(snapPosition(e.target.x()), snapPosition(e.target.y()))
  }

  const handleTransformEnd = () => {
    const node = shapeRef.current
    if (!node) return
    const scaleX = node.scaleX()
    const scaleY = node.scaleY()
    node.scaleX(1)
    node.scaleY(1)
    onTransformEnd({
      x: node.x(),
      y: node.y(),
      width: Math.max(20, (field.width || 100) * scaleX),
      height: Math.max(20, (field.height || 30) * scaleY),
      rotation: node.rotation(),
    })
  }

  const isTextField = [
    'studentName', 'courseName', 'date', 'credentialId',
    'instituteName', 'grade', 'customText',
  ].includes(field.type)

  const isImageField = ['customImage', 'logo', 'signature'].includes(field.type)
  const isQrCode = field.type === 'qrCode'
  const isBackground = field.type === 'backgroundImage'

  const w = field.width || 100
  const h = field.height || 30

  return (
    <Group
      ref={shapeRef}
      id={`field-${field.id}`}
      x={field.x}
      y={field.y}
      width={w}
      height={h}
      rotation={field.rotation || 0}
      draggable={!isBackground}
      onClick={onSelect}
      onTap={onSelect}
      onDragEnd={handleDragEnd}
      onTransformEnd={handleTransformEnd}
    >
      {isTextField && (
        <>
          {/* Selection border for text */}
          {isSelected && (
            <Rect width={w} height={h} stroke="#3b82f6" strokeWidth={1} dash={[4, 4]} />
          )}
          <Text
            text={field.text || field.type}
            width={w}
            height={h}
            fontSize={field.fontSize || 14}
            fontStyle={field.fontWeight === 'bold' ? 'bold' : 'normal'}
            fill={field.color || '#333333'}
            align={field.alignment || 'center'}
            verticalAlign="middle"
            listening={false}
          />
        </>
      )}

      {isImageField && (
        <>
          <Rect
            width={w}
            height={h}
            fill={field.imageUrl ? 'transparent' : '#f1f5f9'}
            stroke={isSelected ? '#3b82f6' : '#cbd5e1'}
            strokeWidth={isSelected ? 2 : 1}
            dash={field.imageUrl ? undefined : [6, 3]}
            opacity={field.opacity ?? 1}
          />
          {!field.imageUrl && (
            <Text
              text={field.type === 'logo' ? 'Logo' : field.type === 'signature' ? 'Signature' : 'Image'}
              width={w}
              height={h}
              align="center"
              verticalAlign="middle"
              fontSize={12}
              fill="#94a3b8"
              listening={false}
            />
          )}
          {field.type === 'signature' && field.label && (
            <Text
              text={field.label}
              y={h + 4}
              width={w}
              fontSize={10}
              fill="#64748b"
              align="center"
              listening={false}
            />
          )}
        </>
      )}

      {isQrCode && (
        <>
          <Rect
            width={w}
            height={h}
            fill="#f8fafc"
            stroke={isSelected ? '#3b82f6' : '#cbd5e1'}
            strokeWidth={isSelected ? 2 : 1}
          />
          {/* Simple QR placeholder pattern */}
          <Rect x={8} y={8} width={20} height={20} fill="#1e293b" />
          <Rect x={w - 28} y={8} width={20} height={20} fill="#1e293b" />
          <Rect x={8} y={h - 28} width={20} height={20} fill="#1e293b" />
          <Text
            text="QR"
            width={w}
            height={h}
            align="center"
            verticalAlign="middle"
            fontSize={14}
            fontStyle="bold"
            fill="#64748b"
            listening={false}
          />
        </>
      )}

      {isBackground && (
        <Rect
          width={w}
          height={h}
          fill={field.imageUrl ? 'transparent' : '#f1f5f9'}
          stroke={isSelected ? '#3b82f6' : 'transparent'}
          strokeWidth={isSelected ? 2 : 0}
          opacity={field.opacity ?? 1}
        />
      )}
    </Group>
  )
}

export default function DesignerCanvas({
  fields,
  selectedFieldId,
  pageSize,
  backgroundColor,
  zoom,
  snapToGrid,
  onSelectField,
  onUpdateField,
}: DesignerCanvasProps) {
  const transformerRef = useRef<Konva.Transformer>(null)
  const stageRef = useRef<Konva.Stage>(null)
  const selectedShapeRef = useRef<Konva.Group | null>(null)

  // Attach transformer to selected node
  useEffect(() => {
    const transformer = transformerRef.current
    if (!transformer) return

    if (selectedFieldId) {
      const stage = stageRef.current
      if (!stage) return
      const node = stage.findOne(`#field-${selectedFieldId}`) as Konva.Group | undefined
      if (node) {
        const field = fields.find(f => f.id === selectedFieldId)
        const isBackground = field?.type === 'backgroundImage'
        selectedShapeRef.current = node
        transformer.nodes(isBackground ? [] : [node])
        transformer.getLayer()?.batchDraw()
        return
      }
    }
    transformer.nodes([])
    transformer.getLayer()?.batchDraw()
  }, [selectedFieldId, fields])

  const handleStageClick = useCallback((e: Konva.KonvaEventObject<MouseEvent>) => {
    if (e.target === e.target.getStage()) {
      onSelectField(null)
    }
  }, [onSelectField])

  // Sort: background first, then others
  const sortedFields = [...fields].sort((a, b) => {
    if (a.type === 'backgroundImage') return -1
    if (b.type === 'backgroundImage') return 1
    return 0
  })

  const stageWidth = pageSize.width * zoom
  const stageHeight = pageSize.height * zoom

  // Grid lines
  const gridLines: { points: number[]; key: string }[] = []
  if (snapToGrid) {
    for (let x = 0; x <= pageSize.width; x += GRID_SIZE) {
      gridLines.push({ points: [x, 0, x, pageSize.height], key: `v-${x}` })
    }
    for (let y = 0; y <= pageSize.height; y += GRID_SIZE) {
      gridLines.push({ points: [0, y, pageSize.width, y], key: `h-${y}` })
    }
  }

  return (
    <Stage
      ref={stageRef}
      width={stageWidth}
      height={stageHeight}
      scaleX={zoom}
      scaleY={zoom}
      onClick={handleStageClick}
      onTap={handleStageClick}
      style={{ backgroundColor, boxShadow: '0 4px 24px rgba(0,0,0,0.12)' }}
    >
      <Layer>
        {/* Background */}
        <Rect width={pageSize.width} height={pageSize.height} fill={backgroundColor} listening={false} />

        {/* Grid */}
        {gridLines.map(line => (
          <Line
            key={line.key}
            points={line.points}
            stroke="#e2e8f0"
            strokeWidth={0.5}
            listening={false}
          />
        ))}

        {/* Fields */}
        {sortedFields.map(field => (
          <FieldNode
            key={field.id}
            field={field}
            isSelected={selectedFieldId === field.id}
            onSelect={() => onSelectField(field.id)}
            onDragEnd={(x, y) => onUpdateField(field.id, { x, y })}
            onTransformEnd={(updates) => onUpdateField(field.id, updates)}
            snapToGrid={snapToGrid}
          />
        ))}

        {/* Transformer */}
        <Transformer
          ref={transformerRef}
          rotateEnabled
          enabledAnchors={[
            'top-left', 'top-right', 'bottom-left', 'bottom-right',
            'middle-left', 'middle-right',
          ]}
          boundBoxFunc={(oldBox, newBox) => {
            if (newBox.width < 20 || newBox.height < 20) return oldBox
            return newBox
          }}
        />
      </Layer>
    </Stage>
  )
}
