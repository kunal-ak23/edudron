'use client'

import { useEffect, useState } from 'react'
import { Award, ChevronRight } from 'lucide-react'

interface PromotionCelebrationProps {
  oldTitle: string
  newTitle: string
  onDismiss: () => void
}

export function PromotionCelebration({ oldTitle, newTitle, onDismiss }: PromotionCelebrationProps) {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    requestAnimationFrame(() => setVisible(true))
    const timeout = setTimeout(onDismiss, 3000)
    return () => clearTimeout(timeout)
  }, [onDismiss])

  return (
    <div
      className="fixed inset-0 z-50 bg-[#0F1729]/95 flex items-center justify-center cursor-pointer"
      onClick={onDismiss}
    >
      <div
        className={`text-center space-y-4 transition-all duration-700 ${
          visible ? 'opacity-100 scale-100' : 'opacity-0 scale-75'
        }`}
      >
        <Award className="w-16 h-16 text-[#F97316] mx-auto" />
        <h2 className="text-3xl font-bold text-[#F97316]">Promoted!</h2>
        <div className="flex items-center gap-3 justify-center text-lg">
          <span className="text-[#94A3B8]">{oldTitle}</span>
          <ChevronRight className="w-5 h-5 text-[#F97316]" />
          <span className="text-[#E2E8F0] font-medium">{newTitle}</span>
        </div>
      </div>
    </div>
  )
}
