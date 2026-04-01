'use client'

import { useEffect, useState } from 'react'
import { useParams } from 'next/navigation'
import type { CertificateVerification } from '@kunal-ak23/edudron-shared-utils'
import {
  CheckCircle2,
  XCircle,
  Download,
  Loader2,
  AlertTriangle,
  Calendar,
  Hash,
  ShieldCheck,
} from 'lucide-react'

export const dynamic = 'force-dynamic'

const GATEWAY_URL = process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:8080'

export default function VerifyCertificatePage() {
  const params = useParams()
  const credentialId = params?.credentialId as string

  const [data, setData] = useState<CertificateVerification | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!credentialId) return

    const fetchVerification = async () => {
      try {
        const res = await fetch(`${GATEWAY_URL}/api/verify/${credentialId}`)
        if (!res.ok) {
          if (res.status === 404) {
            setError('Certificate not found. Please check the credential ID and try again.')
          } else {
            setError('An error occurred while verifying the certificate.')
          }
          return
        }
        const json = await res.json()
        setData(json)
      } catch (err) {
        setError('Unable to connect to the verification service. Please try again later.')
      } finally {
        setLoading(false)
      }
    }

    fetchVerification()
  }, [credentialId])

  const formatDate = (dateStr: string) => {
    try {
      return new Date(dateStr).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    } catch {
      return dateStr
    }
  }

  const handleDownload = () => {
    if (data?.pdfUrl) {
      window.open(data.pdfUrl, '_blank')
    }
  }

  // Loading
  if (loading) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center px-4">
        <div className="text-center">
          <Loader2 className="h-10 w-10 animate-spin text-primary-600 mx-auto mb-4" />
          <p className="text-gray-600 font-medium">Verifying certificate...</p>
        </div>
      </div>
    )
  }

  // Error / Not Found
  if (error || !data) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center px-4">
        <div className="bg-white rounded-2xl shadow-lg border border-gray-200 p-8 max-w-md w-full text-center">
          <AlertTriangle className="h-12 w-12 text-amber-500 mx-auto mb-4" />
          <h1 className="text-xl font-bold text-gray-900 mb-2">Verification Failed</h1>
          <p className="text-gray-600">
            {error || 'Certificate data could not be loaded.'}
          </p>
        </div>
      </div>
    )
  }

  const isValid = data.valid && !data.revoked

  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center px-4 py-10">
      <div className="bg-white rounded-2xl shadow-lg border border-gray-200 max-w-lg w-full overflow-hidden">
        {/* Header with institution info */}
        <div className="bg-gray-50 border-b border-gray-200 px-6 py-5 text-center">
          {data.institutionLogoUrl ? (
            <img
              src={data.institutionLogoUrl}
              alt={data.institutionName || 'Institution'}
              className="h-14 w-auto mx-auto mb-3 object-contain"
            />
          ) : (
            <ShieldCheck className="h-10 w-10 text-primary-600 mx-auto mb-3" />
          )}
          {data.institutionName && (
            <p className="text-sm font-semibold text-gray-700">
              {data.institutionName}
            </p>
          )}
          <p className="text-xs text-gray-500 mt-1">Certificate Verification</p>
        </div>

        {/* Status Badge */}
        <div className="px-6 pt-6 pb-2 text-center">
          {isValid ? (
            <div className="inline-flex items-center gap-2 bg-green-50 border border-green-200 rounded-full px-5 py-2">
              <CheckCircle2 className="h-5 w-5 text-green-600" />
              <span className="text-sm font-bold text-green-700">Valid Certificate</span>
            </div>
          ) : (
            <div className="inline-flex items-center gap-2 bg-red-50 border border-red-200 rounded-full px-5 py-2">
              <XCircle className="h-5 w-5 text-red-600" />
              <span className="text-sm font-bold text-red-700">Revoked Certificate</span>
            </div>
          )}
        </div>

        {/* Certificate Details */}
        <div className="px-6 py-6 space-y-4">
          {/* Student Name */}
          <div className="text-center">
            <p className="text-xs uppercase tracking-wider text-gray-400 mb-1">Awarded to</p>
            <h2 className="text-2xl font-bold text-gray-900">{data.studentName}</h2>
          </div>

          {/* Course Name */}
          {data.courseName && (
            <div className="text-center">
              <p className="text-xs uppercase tracking-wider text-gray-400 mb-1">Course</p>
              <p className="text-lg font-semibold text-gray-800">{data.courseName}</p>
            </div>
          )}

          {/* Details Row */}
          <div className="flex items-center justify-center gap-6 pt-2 text-sm text-gray-500">
            <span className="flex items-center gap-1.5">
              <Calendar className="h-4 w-4" />
              {formatDate(data.issuedAt)}
            </span>
            <span className="flex items-center gap-1.5">
              <Hash className="h-4 w-4" />
              <code className="font-mono text-xs">{data.credentialId}</code>
            </span>
          </div>

          {/* Revocation Alert */}
          {data.revoked && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 mt-4">
              <div className="flex items-start gap-3">
                <XCircle className="h-5 w-5 text-red-500 flex-shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm font-semibold text-red-800">
                    This certificate has been revoked
                  </p>
                  {data.revokedReason && (
                    <p className="text-sm text-red-700 mt-1">
                      Reason: {data.revokedReason}
                    </p>
                  )}
                  {data.revokedAt && (
                    <p className="text-xs text-red-600 mt-1">
                      Revoked on {formatDate(data.revokedAt)}
                    </p>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Download Button */}
          {isValid && data.pdfUrl && (
            <div className="pt-2 text-center">
              <button
                onClick={handleDownload}
                className="inline-flex items-center gap-2 px-6 py-2.5 bg-primary-600 text-white rounded-lg font-medium text-sm cursor-pointer hover:bg-primary-700 transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
                aria-label="Download certificate PDF"
              >
                <Download className="h-4 w-4" />
                Download Certificate
              </button>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="bg-gray-50 border-t border-gray-200 px-6 py-3 text-center">
          <p className="text-xs text-gray-400">
            Verified by EduDron Certificate System
          </p>
        </div>
      </div>
    </div>
  )
}
