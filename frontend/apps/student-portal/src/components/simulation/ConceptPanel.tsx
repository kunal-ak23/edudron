'use client'

import React from 'react'

interface ConceptPanelProps {
  concept: string
  keywords?: Array<{ term: string; explanation: string }>
  keyInsights?: string[]
  activeKeywordIndex?: number
}

export default function ConceptPanel({
  concept, keywords = [], keyInsights = [], activeKeywordIndex = 0
}: ConceptPanelProps) {
  return (
    <div className="p-6 space-y-8">
      {/* Concept Header */}
      <section>
        <div className="flex items-center gap-2 mb-4 text-[#6cd3f7]">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 6.042A8.967 8.967 0 006 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 016 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 016-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0018 18a8.967 8.967 0 00-6 2.292m0-14.25v14.25" />
          </svg>
          <span className="text-[11px] uppercase font-bold tracking-widest">Concept</span>
        </div>
        <h2 className="text-lg font-bold text-[#dbe2fb] leading-tight" style={{ fontFamily: 'Space Grotesk, sans-serif' }}>
          {concept || 'Strategic Decision Making'}
        </h2>
      </section>

      {/* Keywords Section */}
      {keywords.length > 0 && (
        <section className="space-y-4">
          <div className="text-[11px] uppercase font-bold tracking-widest text-[#879298]">Keywords</div>
          <div className="space-y-2">
            {keywords.map((kw, idx) => (
              <div
                key={idx}
                className={`p-4 rounded-lg transition-colors ${
                  idx === activeKeywordIndex
                    ? 'bg-[#222a3d] border-l-2 border-[#6cd3f7]'
                    : 'bg-[#060e1f]/50 hover:bg-[#222a3d] cursor-pointer'
                }`}
              >
                <h4 className={`text-sm font-semibold mb-1 ${idx === activeKeywordIndex ? 'text-[#6cd3f7]' : 'text-[#dbe2fb]'}`}>
                  {kw.term}
                </h4>
                <p className="text-xs text-[#bdc8ce] leading-relaxed">{kw.explanation}</p>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Key Insights Section */}
      {keyInsights.length > 0 && (
        <section className="space-y-4">
          <div className="text-[11px] uppercase font-bold tracking-widest text-[#879298]">Key Insights</div>
          <ul className="space-y-4">
            {keyInsights.map((insight, idx) => (
              <li key={idx} className="flex gap-3">
                <svg className="w-4 h-4 text-[#6cd3f7] shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M10 2a6 6 0 00-2 11.659V16a1 1 0 001 1h2a1 1 0 001-1v-2.341A6 6 0 0010 2zm-1 15a1 1 0 011-1h0a1 1 0 010 2h0a1 1 0 01-1-1z" />
                </svg>
                <span className="text-xs text-[#bdc8ce]">{insight}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
