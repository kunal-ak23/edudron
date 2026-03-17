'use client'

import { useEffect, useState } from 'react'

interface YearTransitionProps {
  yearCompleted: number
  onComplete: () => void
}

export function YearTransition({ yearCompleted, onComplete }: YearTransitionProps) {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (prefersReduced) {
      onComplete()
      return
    }

    requestAnimationFrame(() => setVisible(true))
    const timeout = setTimeout(onComplete, 1500)
    return () => clearTimeout(timeout)
  }, [onComplete])

  return (
    <div
      className="fixed inset-0 z-50 bg-[#0F1729]/95 flex items-center justify-center cursor-pointer"
      onClick={onComplete}
    >
      <div
        className={`text-center transition-all duration-700 ${
          visible ? 'opacity-100 scale-100' : 'opacity-0 scale-90'
        }`}
      >
        <h1 className="text-4xl md:text-6xl font-bold text-[#E2E8F0] tracking-wider">
          YEAR {yearCompleted}
        </h1>
        <p className="text-xl md:text-2xl text-[#0891B2] mt-2 font-light">
          COMPLETE
        </p>
      </div>
    </div>
  )
}
