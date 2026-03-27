'use client'

import { useEffect, useState } from 'react'

interface YearTransitionProps {
  yearCompleted: number
  onComplete: () => void
  mentorFarewell?: {
    advisorName: string
    farewellMessage: string
    characterId?: string
  }
}

export function YearTransition({ yearCompleted, onComplete, mentorFarewell }: YearTransitionProps) {
  const [visible, setVisible] = useState(false)
  const [showFarewell, setShowFarewell] = useState(false)

  useEffect(() => {
    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (prefersReduced) {
      if (mentorFarewell) {
        setVisible(true)
        setShowFarewell(true)
        // Don't auto-advance — let user click through farewell
      } else {
        onComplete()
      }
      return
    }

    requestAnimationFrame(() => setVisible(true))

    if (mentorFarewell) {
      // Show year complete, then show farewell after a delay
      const farewellTimer = setTimeout(() => setShowFarewell(true), 1800)
      return () => clearTimeout(farewellTimer)
    } else {
      const timeout = setTimeout(onComplete, 1500)
      return () => clearTimeout(timeout)
    }
  }, [onComplete, mentorFarewell])

  return (
    <div
      className="fixed inset-0 z-50 bg-[#0F1729]/95 flex items-center justify-center cursor-pointer"
      onClick={() => {
        if (showFarewell || !mentorFarewell) onComplete()
      }}
    >
      <div className="text-center max-w-lg px-6">
        <div
          className={`transition-all duration-700 ${
            visible ? 'opacity-100 scale-100' : 'opacity-0 scale-90'
          } ${showFarewell ? 'mb-8' : ''}`}
        >
          <h1 className="text-4xl md:text-6xl font-bold text-[#E2E8F0] tracking-wider">
            YEAR {yearCompleted}
          </h1>
          <p className="text-xl md:text-2xl text-[#0891B2] mt-2 font-light">
            COMPLETE
          </p>
        </div>

        {/* Mentor farewell message */}
        {showFarewell && mentorFarewell && (
          <div className={`transition-all duration-700 ${showFarewell ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'}`}>
            <div className="bg-[#1A2744] border border-[#6cd3f7]/20 rounded-xl p-6 text-left">
              <div className="flex items-center gap-2 mb-3">
                <span className="text-lg">👋</span>
                <p className="text-xs uppercase tracking-widest text-[#6cd3f7] font-bold">
                  {mentorFarewell.advisorName}&apos;s Farewell
                </p>
              </div>
              <p className="text-sm text-[#dbe2fb] leading-relaxed italic">
                &ldquo;{mentorFarewell.farewellMessage}&rdquo;
              </p>
              <p className="text-xs text-slate-500 mt-4 text-center">
                From now on, you&apos;re on your own. Click to continue.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
