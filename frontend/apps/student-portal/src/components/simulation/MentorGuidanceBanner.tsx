'use client'

import { useState } from 'react'
import type { MentorGuidance } from '@kunal-ak23/edudron-shared-utils'

interface MentorGuidanceBannerProps {
  guidance: MentorGuidance
}

export function MentorGuidanceBanner({ guidance }: MentorGuidanceBannerProps) {
  const isLight = guidance.guidanceLevel === 'LIGHT'
  const [expanded, setExpanded] = useState(!isLight) // FULL: open by default, LIGHT: collapsed

  if (!guidance.courseConnection && !guidance.realWorldExample && !guidance.mentorNote) return null

  return (
    <div className={`rounded-xl overflow-hidden ${
      isLight
        ? 'bg-[#1E3A5F]/15 border border-slate-600/20'
        : 'bg-[#1E3A5F]/30 border border-[#6cd3f7]/20'
    }`}>
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-[#1E3A5F]/20 transition-colors"
      >
        <span className="text-lg flex-shrink-0">
          {isLight ? '📝' : '🎓'}
        </span>
        <div className="flex-1 min-w-0">
          <span className={`text-xs uppercase tracking-widest font-bold ${
            isLight ? 'text-slate-400' : 'text-[#6cd3f7]'
          }`}>
            {isLight ? "Mentor's Notes" : 'Mentor Guidance'}
          </span>
          {isLight && !expanded && guidance.mentorNote && (
            <p className="text-[11px] text-slate-500 truncate mt-0.5 italic">
              &ldquo;{guidance.mentorNote}&rdquo;
            </p>
          )}
        </div>
        {isLight && !expanded && (
          <span className="text-[10px] uppercase tracking-widest text-slate-500 font-bold whitespace-nowrap">
            Check notes
          </span>
        )}
        <svg
          className={`w-4 h-4 text-slate-400 transition-transform flex-shrink-0 ${expanded ? 'rotate-180' : ''}`}
          fill="none" viewBox="0 0 24 24" stroke="currentColor"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {expanded && (
        <div className="px-4 pb-4 space-y-3">
          {/* Mentor note — personal voice */}
          {guidance.mentorNote && (
            <div className={`rounded-lg p-3 ${
              isLight ? 'bg-[#0F1729]/50 border border-slate-700/30' : 'bg-[#0F1729]/80 border border-[#6cd3f7]/10'
            }`}>
              <p className={`text-sm leading-relaxed italic ${isLight ? 'text-slate-400' : 'text-[#dbe2fb]/80'}`}>
                &ldquo;{guidance.mentorNote}&rdquo;
              </p>
            </div>
          )}

          {guidance.courseConnection && (
            <div className="flex gap-2">
              <span className="text-amber-400 flex-shrink-0 mt-0.5">📚</span>
              <div>
                <p className="text-[10px] uppercase tracking-widest text-amber-400/70 font-bold mb-0.5">Course Connection</p>
                <p className="text-sm text-[#dbe2fb]/90 leading-relaxed">{guidance.courseConnection}</p>
              </div>
            </div>
          )}
          {guidance.realWorldExample && (
            <div className="flex gap-2">
              <span className="text-emerald-400 flex-shrink-0 mt-0.5">🌍</span>
              <div>
                <p className="text-[10px] uppercase tracking-widest text-emerald-400/70 font-bold mb-0.5">Real-World Parallel</p>
                <p className="text-sm text-[#dbe2fb]/90 leading-relaxed">{guidance.realWorldExample}</p>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
