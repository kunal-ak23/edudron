'use client'

import { useReducer, useState, useCallback } from 'react'
import type { DesignerField, FieldType, TemplateConfig } from './types'
import { PAGE_SIZES, FIELD_DEFAULTS } from './types'

const MAX_HISTORY = 50

// --- History reducer (avoids stale-closure bugs) ---

interface HistoryState {
  entries: DesignerField[][]
  index: number
}

type HistoryAction =
  | { type: 'push'; fields: DesignerField[] }
  | { type: 'undo' }
  | { type: 'redo' }
  | { type: 'reset'; fields: DesignerField[] }

function historyReducer(state: HistoryState, action: HistoryAction): HistoryState {
  switch (action.type) {
    case 'push': {
      const trimmed = state.entries.slice(0, state.index + 1)
      trimmed.push(action.fields)
      if (trimmed.length > MAX_HISTORY) trimmed.shift()
      return { entries: trimmed, index: trimmed.length - 1 }
    }
    case 'undo': {
      if (state.index <= 0) return state
      return { ...state, index: state.index - 1 }
    }
    case 'redo': {
      if (state.index >= state.entries.length - 1) return state
      return { ...state, index: state.index + 1 }
    }
    case 'reset': {
      return { entries: [action.fields], index: 0 }
    }
  }
}

// --- Hook ---

export function useDesignerState(initialConfig?: TemplateConfig) {
  const initialFields = initialConfig?.fields || []

  const [hist, dispatchHist] = useReducer(historyReducer, {
    entries: [initialFields],
    index: 0,
  })

  // Current fields are always derived from history
  const fields = hist.entries[hist.index] || []

  const [selectedFieldId, setSelectedFieldId] = useState<string | null>(null)
  const [pageSize, setPageSize] = useState(
    initialConfig?.pageSize || PAGE_SIZES['a4-landscape']
  )
  const [orientation, setOrientation] = useState<'landscape' | 'portrait'>(
    initialConfig?.orientation || 'landscape'
  )
  const [backgroundColor, setBackgroundColor] = useState(
    initialConfig?.backgroundColor || '#FFFFFF'
  )
  const [zoom, setZoom] = useState(0.75)
  const [snapToGrid, setSnapToGrid] = useState(false)

  const addField = useCallback((type: FieldType) => {
    const defaults = FIELD_DEFAULTS[type]
    const w = type === 'backgroundImage' ? pageSize.width : (defaults.width || 100)
    const h = type === 'backgroundImage' ? pageSize.height : (defaults.height || 50)
    const newField: DesignerField = {
      id: crypto.randomUUID(),
      type,
      x: type === 'backgroundImage' ? 0 : pageSize.width / 2 - w / 2,
      y: type === 'backgroundImage' ? 0 : pageSize.height / 2 - h / 2,
      width: w,
      height: h,
      ...defaults,
    }
    if (type === 'backgroundImage') {
      newField.width = pageSize.width
      newField.height = pageSize.height
    }
    const newFields = [...fields, newField]
    dispatchHist({ type: 'push', fields: newFields })
    setSelectedFieldId(newField.id)
  }, [fields, pageSize])

  const updateField = useCallback((id: string, updates: Partial<DesignerField>) => {
    const newFields = fields.map(f => f.id === id ? { ...f, ...updates } : f)
    dispatchHist({ type: 'push', fields: newFields })
  }, [fields])

  const deleteField = useCallback((id: string) => {
    const newFields = fields.filter(f => f.id !== id)
    dispatchHist({ type: 'push', fields: newFields })
    setSelectedFieldId(null)
  }, [fields])

  const undo = useCallback(() => {
    dispatchHist({ type: 'undo' })
    setSelectedFieldId(null)
  }, [])

  const redo = useCallback(() => {
    dispatchHist({ type: 'redo' })
    setSelectedFieldId(null)
  }, [])

  const toggleOrientation = useCallback(() => {
    const next = orientation === 'landscape' ? 'portrait' : 'landscape'
    setOrientation(next)
    setPageSize(next === 'landscape' ? PAGE_SIZES['a4-landscape'] : PAGE_SIZES['a4-portrait'])
  }, [orientation])

  const toConfig = useCallback((): TemplateConfig => ({
    fields,
    pageSize,
    orientation,
    backgroundColor,
  }), [fields, pageSize, orientation, backgroundColor])

  const loadConfig = useCallback((config: TemplateConfig) => {
    dispatchHist({ type: 'reset', fields: config.fields || [] })
    if (config.pageSize) setPageSize(config.pageSize)
    if (config.orientation) setOrientation(config.orientation)
    if (config.backgroundColor) setBackgroundColor(config.backgroundColor)
    setSelectedFieldId(null)
  }, [])

  const canUndo = hist.index > 0
  const canRedo = hist.index < hist.entries.length - 1

  return {
    fields,
    selectedFieldId,
    setSelectedFieldId,
    pageSize,
    orientation,
    backgroundColor,
    setBackgroundColor,
    zoom,
    setZoom,
    snapToGrid,
    setSnapToGrid,
    addField,
    updateField,
    deleteField,
    undo,
    redo,
    canUndo,
    canRedo,
    toggleOrientation,
    toConfig,
    loadConfig,
  }
}
