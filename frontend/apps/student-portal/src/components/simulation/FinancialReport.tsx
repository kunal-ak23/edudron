'use client'

import { Button } from '@/components/ui/button'

interface FinancialReportProps {
  report: {
    departments: Record<string, {
      invested: number
      return: number | null
      roi: string | null
      note: string | null
    }>
    totalInvested: number
    totalReturns: number
    endingBudget: number
  }
  currency: string
  onDismiss: () => void
}

function formatAmount(amount: number | null, currency: string): string {
  if (amount === null) return '—'
  if (Math.abs(amount) >= 1_000_000) return `${currency}${(amount / 1_000_000).toFixed(1)}M`
  if (Math.abs(amount) >= 1_000) return `${currency}${(amount / 1_000).toFixed(0)}K`
  return `${currency}${amount.toLocaleString()}`
}

export function FinancialReport({ report, currency, onDismiss }: FinancialReportProps) {
  const departments = Object.entries(report.departments)

  return (
    <div className="bg-[#1A2744] border border-[#1E3A5F]/30 rounded-xl p-6 space-y-4">
      <h3 className="text-lg font-medium text-[#E2E8F0]">Financial Report</h3>

      {/* Table */}
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-[#94A3B8] text-xs">
              <th className="text-left pb-2 font-medium">Department</th>
              <th className="text-right pb-2 font-medium">Invested</th>
              <th className="text-right pb-2 font-medium">Return</th>
              <th className="text-right pb-2 font-medium">ROI</th>
            </tr>
          </thead>
          <tbody>
            {departments.map(([deptId, data]) => (
              <tr key={deptId} className="border-t border-[#1E3A5F]/20">
                <td className="py-2 text-[#E2E8F0] capitalize">{deptId.replace(/_/g, ' ')}</td>
                <td className="py-2 text-right font-mono text-[#E2E8F0]">
                  {data.invested ? formatAmount(data.invested, currency) : '—'}
                </td>
                <td className="py-2 text-right font-mono text-[#E2E8F0]">
                  {data.return !== null ? formatAmount(data.return, currency) : (
                    <span className="text-[#94A3B8] text-xs">{data.note}</span>
                  )}
                </td>
                <td className={`py-2 text-right font-mono ${
                  data.roi && data.roi.startsWith('+') ? 'text-green-400' :
                  data.roi && data.roi.startsWith('-') ? 'text-red-400' : 'text-[#94A3B8]'
                }`}>
                  {data.roi || '—'}
                </td>
              </tr>
            ))}

            {/* Total Row */}
            <tr className="border-t-2 border-[#1E3A5F]/50 font-medium">
              <td className="py-2 text-[#E2E8F0]">Total</td>
              <td className="py-2 text-right font-mono text-[#E2E8F0]">
                {formatAmount(report.totalInvested, currency)}
              </td>
              <td className="py-2 text-right font-mono text-[#E2E8F0]">
                {formatAmount(report.totalReturns, currency)}
              </td>
              <td className="py-2" />
            </tr>
          </tbody>
        </table>
      </div>

      {/* Ending Budget */}
      <div className="flex items-center justify-between pt-2 border-t border-[#1E3A5F]/30">
        <span className="text-sm text-[#94A3B8]">Next Year Budget</span>
        <span className="text-lg font-mono text-[#0891B2] font-medium">
          {formatAmount(report.endingBudget, currency)}
        </span>
      </div>

      <Button onClick={onDismiss} className="w-full">Continue</Button>
    </div>
  )
}
