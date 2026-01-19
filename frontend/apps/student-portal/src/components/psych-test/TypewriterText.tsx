'use client'

import React, { useEffect, useMemo, useState } from 'react'

export function usePrefersReducedMotion() {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false)

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)')
    const update = () => setPrefersReducedMotion(Boolean(mq.matches))
    update()

    // Safari < 14
    if (typeof mq.addEventListener === 'function') {
      mq.addEventListener('change', update)
      return () => mq.removeEventListener('change', update)
    }
    // eslint-disable-next-line deprecation/deprecation
    mq.addListener(update)
    // eslint-disable-next-line deprecation/deprecation
    return () => mq.removeListener(update)
  }, [])

  return prefersReducedMotion
}

type TypewriterTextProps = {
  text: string
  /** Delay before typing starts */
  startDelayMs?: number
  /** Milliseconds per character (lower = faster) */
  speedMs?: number
  /** Render a blinking cursor while typing */
  cursor?: boolean
  /** Called once when typing completes */
  onDone?: () => void
  className?: string
  as?: React.ElementType
}

export function TypewriterText({
  text,
  startDelayMs = 60,
  speedMs = 18,
  cursor = true,
  onDone,
  className,
  as: As = 'span'
}: TypewriterTextProps) {
  const prefersReducedMotion = usePrefersReducedMotion()
  const safeText = useMemo(() => (text ?? '').toString(), [text])
  const [visibleCount, setVisibleCount] = useState(prefersReducedMotion ? safeText.length : 0)
  const [isTyping, setIsTyping] = useState(!prefersReducedMotion && safeText.length > 0)

  useEffect(() => {
    if (prefersReducedMotion) {
      setVisibleCount(safeText.length)
      setIsTyping(false)
      onDone?.()
      return
    }

    setVisibleCount(0)
    setIsTyping(safeText.length > 0)

    if (!safeText.length) {
      onDone?.()
      return
    }

    let startTimer: number | null = null
    let tickTimer: number | null = null
    let cancelled = false

    startTimer = window.setTimeout(() => {
      let i = 0
      tickTimer = window.setInterval(() => {
        if (cancelled) return
        i += 1
        setVisibleCount(i)
        if (i >= safeText.length) {
          if (tickTimer != null) window.clearInterval(tickTimer)
          setIsTyping(false)
          onDone?.()
        }
      }, Math.max(8, speedMs))
    }, Math.max(0, startDelayMs))

    return () => {
      cancelled = true
      if (startTimer != null) window.clearTimeout(startTimer)
      if (tickTimer != null) window.clearInterval(tickTimer)
    }
    // Intentionally reset typing when text changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [safeText, prefersReducedMotion])

  const shown = safeText.slice(0, Math.min(safeText.length, Math.max(0, visibleCount)))

  return (
    <As className={className}>
      {shown}
      {cursor && !prefersReducedMotion && isTyping && (
        <span className="inline-block w-[0.6ch] align-baseline animate-pulse">▍</span>
      )}
    </As>
  )
}

