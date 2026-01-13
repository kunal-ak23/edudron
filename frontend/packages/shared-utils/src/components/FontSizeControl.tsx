'use client'

import { useState, useEffect } from 'react'
import { getFontSize, setFontSize, applyFontSize } from '../preferences/fontSize'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from './Tooltip'

const MIN_FONT_SIZE = 0.5 // 50%
const MAX_FONT_SIZE = 1.5 // 150%
const STEP = 0.04 // 4% increments

interface FontSizeControlProps {
  className?: string
}

/**
 * Font size control component with A- / A+ buttons
 * Allows users to adjust the application font size
 * This component uses fixed pixel sizes to prevent it from scaling with font size changes
 */
export function FontSizeControl({ className = '' }: FontSizeControlProps) {
  const [fontSize, setFontSizeState] = useState(0.8)

  useEffect(() => {
    // Initialize from localStorage and apply to CSS
    const currentSize = getFontSize()
    setFontSizeState(currentSize)
    applyFontSize()
  }, [])

  const handleDecrease = () => {
    const newSize = Math.max(MIN_FONT_SIZE, fontSize - STEP)
    setFontSizeState(newSize)
    setFontSize(newSize)
  }

  const handleIncrease = () => {
    const newSize = Math.min(MAX_FONT_SIZE, fontSize + STEP)
    setFontSizeState(newSize)
    setFontSize(newSize)
  }

  const percentage = Math.round(fontSize * 100)

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <div
            className={`font-size-control-wrapper ${className}`}
            style={{
              // Set fixed font-size in pixels to prevent scaling with --app-scale
              // This ensures the component maintains a constant size regardless of user's font size preference
              fontSize: '12px',
              lineHeight: '1.5',
            }}
          >
            <div 
              className="flex items-center rounded-md border border-border bg-background"
              style={{
                gap: '4px',
                padding: '4px 6px',
              }}
            >
        <button
          onClick={handleDecrease}
          disabled={fontSize <= MIN_FONT_SIZE}
          className="flex items-center justify-center rounded px-2 py-1 font-medium text-primary transition-colors hover:bg-primary/10 hover:text-primary disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-1"
          aria-label="Decrease font size"
          type="button"
          style={{ minWidth: '32px', height: '24px' }}
        >
          <span style={{ fontSize: '10px' }}>A</span>
          <span style={{ fontSize: '13px' }}>−</span>
        </button>
        <span 
          className="text-center font-medium text-foreground"
          style={{ minWidth: '36px', fontSize: '11px' }}
        >
          {percentage}%
        </span>
        <button
          onClick={handleIncrease}
          disabled={fontSize >= MAX_FONT_SIZE}
          className="flex items-center justify-center rounded px-2 py-1 font-medium text-primary transition-colors hover:bg-primary/10 hover:text-primary disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-1"
          aria-label="Increase font size"
          type="button"
          style={{ minWidth: '32px', height: '24px' }}
        >
          <span style={{ fontSize: '16px' }}>A</span>
          <span style={{ fontSize: '13px' }}>+</span>
        </button>
            </div>
          </div>
        </TooltipTrigger>
        <TooltipContent>
          <p>Adjust font size</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
