'use client'

type DomainKey = 'R' | 'I' | 'A' | 'S' | 'E' | 'C'

const LABELS: Record<DomainKey, string> = {
  R: 'Realistic',
  I: 'Investigative',
  A: 'Artistic',
  S: 'Social',
  E: 'Enterprising',
  C: 'Conventional'
}

export function RiasecBarChart({
  domainScores
}: {
  domainScores: Record<string, { score?: number; confidence?: number }> | null
}) {
  const keys: DomainKey[] = ['R', 'I', 'A', 'S', 'E', 'C']

  return (
    <div className="space-y-3">
      {keys.map((k) => {
        const score = Number(domainScores?.[k]?.score ?? 0)
        const pct = Math.min(100, Math.max(0, score))
        const conf = domainScores?.[k]?.confidence
        return (
          <div key={k}>
            <div className="flex items-center justify-between mb-1">
              <div className="text-sm font-medium text-gray-700">
                {k} · {LABELS[k]}
              </div>
              <div className="text-sm text-gray-600">
                {pct.toFixed(1)}%{typeof conf === 'number' ? ` · conf ${(conf * 100).toFixed(0)}%` : ''}
              </div>
            </div>
            <div className="w-full bg-gray-200 rounded-full h-2">
              <div className="bg-primary-600 h-2 rounded-full" style={{ width: `${pct}%` }} />
            </div>
          </div>
        )
      })}
    </div>
  )
}

