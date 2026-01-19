import { redirect } from 'next/navigation'

export const dynamic = 'force-dynamic'

export default function PsychometricTestLegacyResultsRedirectPage({
  params
}: {
  params: { sessionId: string }
}) {
  redirect(`/psych-test/results/${params.sessionId}`)
}
