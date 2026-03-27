'use client'

import { useState, useEffect, useRef } from 'react'
import { LottieCharacter } from './LottieCharacter'

interface NpcResponse {
  round: number
  playerRange: { min: number; max: number }
  response: string
  npcCounterOffer: number | null
}

interface NegotiationConfig {
  rounds: number
  unit: string
  npcName: string
  initialOffer: number
  npcResponses: NpcResponse[]
  outcomes: Array<{ condition: string; choiceId: string }>
}

interface NegotiationInputProps {
  config: NegotiationConfig
  onSubmit: (data: { input: Record<string, any> }) => void
  disabled?: boolean
  negotiationHint?: string
}

export function NegotiationInput({ config, onSubmit, disabled, negotiationHint }: NegotiationInputProps) {
  const rounds = config.rounds ?? 3
  const unit = config.unit ?? '$'
  const npcName = config.npcName ?? 'Counterparty'

  const [currentRound, setCurrentRound] = useState(1)
  const [offerAmount, setOfferAmount] = useState('')
  const [dialogHistory, setDialogHistory] = useState<Array<{ speaker: string; text: string }>>([
    { speaker: npcName, text: `I'm proposing ${unit}${config.initialOffer.toLocaleString()}. What do you think?` }
  ])
  const [lastNpcOffer, setLastNpcOffer] = useState(config.initialOffer)
  const [lastPlayerOffer, setLastPlayerOffer] = useState<number | null>(null)
  const [resolved, setResolved] = useState(false)
  const chatEndRef = useRef<HTMLDivElement>(null)

  // Auto-scroll chat to bottom when new messages arrive
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [dialogHistory])

  // Determine negotiation direction and suggested range from NPC responses
  const getNegotiationHint = (): { direction: string; range?: string } => {
    if (!config.npcResponses?.length) return { direction: 'Make your counter-offer' }
    const round1 = config.npcResponses.filter(r => r.round === 1)
    if (round1.length === 0) return { direction: 'Make your counter-offer' }

    // Get the player range from round 1 to suggest a ballpark
    const ranges = round1.map(r => r.playerRange).filter(Boolean)
    const allMins = ranges.map(r => r.min)
    const allMaxs = ranges.map(r => r.max)
    const suggestedMin = allMins.length ? Math.min(...allMins) : null
    const suggestedMax = allMaxs.length ? Math.max(...allMaxs) : null

    const firstWithCounter = round1.find(r => r.npcCounterOffer !== null)
    let direction = 'Make your counter-offer'
    if (firstWithCounter && firstWithCounter.npcCounterOffer !== null) {
      if (firstWithCounter.npcCounterOffer < config.initialOffer) {
        direction = `Push higher than their ${unit}${config.initialOffer.toLocaleString()}`
      } else {
        direction = `Negotiate lower than ${unit}${config.initialOffer.toLocaleString()}`
      }
    }

    const range = suggestedMin && suggestedMax
      ? `Aim around ${unit}${suggestedMin.toLocaleString()} – ${unit}${suggestedMax.toLocaleString()}`
      : undefined

    return { direction, range }
  }

  const handleCounter = () => {
    const amount = parseInt(offerAmount.replace(/,/g, ''), 10)
    if (isNaN(amount) || amount < 0) return

    setLastPlayerOffer(amount)
    setDialogHistory(prev => [...prev, { speaker: 'You', text: `${unit}${amount.toLocaleString()}` }])

    const response = config.npcResponses?.find(
      r => r.round === currentRound && amount >= r.playerRange.min && amount <= r.playerRange.max
    ) || config.npcResponses?.find(r => r.round === currentRound)

    if (response) {
      if (response.npcCounterOffer === null) {
        // NPC accepts
        setDialogHistory(prev => [...prev, { speaker: npcName, text: response.response }])
        setResolved(true)
        onSubmit({ input: { finalAmount: amount, acceptedRound: currentRound, walkedAway: false } })
        return
      }

      // Show NPC response and their counter-offer
      const counterText = `${response.response} My counter: ${unit}${response.npcCounterOffer.toLocaleString()}.`
      setDialogHistory(prev => [...prev, { speaker: npcName, text: counterText }])
      setLastNpcOffer(response.npcCounterOffer)
    }

    if (currentRound >= rounds) {
      // Final round -- auto-submit with the user's last offer
      setResolved(true)
      onSubmit({ input: { finalAmount: amount, acceptedRound: currentRound, walkedAway: false } })
      return
    }

    setCurrentRound(prev => prev + 1)
    setOfferAmount('')
  }

  const handleAccept = () => {
    setResolved(true)
    setDialogHistory(prev => [...prev, { speaker: 'You', text: `I accept ${unit}${lastNpcOffer.toLocaleString()}.` }])
    onSubmit({ input: { finalAmount: lastNpcOffer, acceptedRound: currentRound, walkedAway: false } })
  }

  const handleWalkAway = () => {
    setResolved(true)
    setDialogHistory(prev => [...prev, { speaker: 'You', text: 'I\'m walking away from this deal.' }])
    onSubmit({ input: { finalAmount: 0, acceptedRound: currentRound, walkedAway: true } })
  }

  // Calculate gap between offers for the visual indicator
  const gap = lastPlayerOffer !== null ? Math.abs(lastPlayerOffer - lastNpcOffer) : null
  const isLastRound = currentRound === rounds

  return (
    <div className="bg-[#222a3d] rounded-xl p-5 space-y-4">
      {/* Header with round indicator */}
      <div className="flex items-center justify-between">
        <h3 className="text-xs uppercase tracking-widest text-slate-400 font-bold">
          Negotiation
        </h3>
        {!resolved && (
          <div className="flex items-center gap-3">
            {isLastRound && (
              <span className="text-[10px] uppercase tracking-widest text-[#ffb4ab] font-bold animate-pulse">
                Final round
              </span>
            )}
            <span className="text-xs uppercase tracking-widest text-[#6cd3f7] font-bold">
              Round {currentRound} of {rounds}
            </span>
          </div>
        )}
        {resolved && (
          <span className="text-xs uppercase tracking-widest text-green-400 font-bold">
            Concluded
          </span>
        )}
      </div>

      {/* Negotiation strategy hint from mentor */}
      {!resolved && negotiationHint && (
        <div className="flex items-start gap-2 px-3 py-2 bg-[#6cd3f7]/5 border border-[#6cd3f7]/10 rounded-lg">
          <span className="text-[#6cd3f7] text-sm flex-shrink-0">💡</span>
          <p className="text-xs text-[#6cd3f7]/80 leading-relaxed">{negotiationHint}</p>
        </div>
      )}

      {/* Offer comparison bar — shows when there's a gap to close */}
      {!resolved && (
        <div className="bg-[#0F1729] rounded-lg p-3 space-y-2">
          <div className="flex justify-between items-center text-xs">
            <div>
              <span className="text-slate-500 uppercase tracking-wider text-[10px]">Their offer</span>
              <p className="text-[#ffb4ab] font-bold text-sm">{unit}{lastNpcOffer.toLocaleString()}</p>
            </div>
            {lastPlayerOffer !== null ? (
              <div className="text-center">
                <span className="text-slate-500 uppercase tracking-wider text-[10px]">Gap</span>
                <p className="text-yellow-400 font-bold text-sm">{unit}{gap?.toLocaleString()}</p>
              </div>
            ) : (
              <div className="text-center max-w-[200px]">
                <p className="text-[#6cd3f7] text-[10px] font-bold">{getNegotiationHint().direction}</p>
                {getNegotiationHint().range && (
                  <p className="text-amber-400 text-[10px] mt-0.5">{getNegotiationHint().range}</p>
                )}
              </div>
            )}
            <div className="text-right">
              <span className="text-slate-500 uppercase tracking-wider text-[10px]">Your offer</span>
              <p className="text-[#6cd3f7] font-bold text-sm">
                {lastPlayerOffer !== null ? `${unit}${lastPlayerOffer.toLocaleString()}` : '—'}
              </p>
            </div>
          </div>
          {/* Visual gap bar */}
          {lastPlayerOffer !== null && gap !== null && gap > 0 && (
            <div className="relative h-1.5 bg-[#1E3A5F]/30 rounded-full overflow-hidden">
              <div
                className="absolute inset-y-0 left-0 bg-gradient-to-r from-[#ffb4ab] to-[#6cd3f7] rounded-full transition-all duration-500"
                style={{ width: `${Math.max(10, 100 - (gap / Math.max(lastNpcOffer, lastPlayerOffer)) * 100)}%` }}
              />
            </div>
          )}
        </div>
      )}

      {/* Chat history */}
      <div className="bg-[#1A2744] border border-white/5 rounded-xl p-4 max-h-72 overflow-y-auto space-y-3">
        {dialogHistory.map((msg, i) => (
          <div key={i} className={`flex ${msg.speaker === 'You' ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-[80%] rounded-lg px-4 py-2.5 ${
              msg.speaker === 'You'
                ? 'bg-[#0891B2]/20 border border-[#0891B2]/20'
                : 'bg-[#1E3A5F]/50 border border-[#1E3A5F]/30'
            }`}>
              <div className="flex items-center gap-2 mb-1">
                {msg.speaker !== 'You' && (config as any).npcCharacterId && (
                  <LottieCharacter characterId={(config as any).npcCharacterId} mood="neutral" size={20} />
                )}
                <p className="text-[10px] uppercase tracking-widest text-slate-400 font-bold">{msg.speaker}</p>
              </div>
              <p className="text-sm text-[#dbe2fb] leading-relaxed">{msg.text}</p>
            </div>
          </div>
        ))}
        <div ref={chatEndRef} />
      </div>

      {/* Input controls */}
      {!resolved && !disabled && (
        <div className="space-y-3">
          {/* Counter-offer input */}
          <div className="flex gap-2">
            <div className="flex-1 relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-sm">{unit}</span>
              <input
                type="text"
                value={offerAmount}
                onChange={(e) => setOfferAmount(e.target.value.replace(/[^0-9,]/g, ''))}
                placeholder="Enter your counter-offer"
                className="w-full pl-7 pr-3 py-2.5 bg-[#0F1729] border border-white/5 rounded-lg text-[#dbe2fb] text-sm focus:outline-none focus:border-[#6cd3f7]/50 placeholder:text-slate-600"
                onKeyDown={(e) => { if (e.key === 'Enter' && offerAmount) handleCounter() }}
              />
            </div>
            <button
              type="button"
              onClick={handleCounter}
              disabled={!offerAmount}
              className="px-5 py-2.5 bg-[#6cd3f7] text-[#003543] font-bold uppercase tracking-widest text-xs hover:brightness-110 active:scale-95 transition-all rounded-lg disabled:opacity-40 disabled:cursor-not-allowed"
            >
              Counter
            </button>
          </div>

          {/* Accept / Walk Away */}
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handleAccept}
              className="flex-1 px-4 py-2.5 border border-[#6cd3f7]/30 text-[#6cd3f7] rounded-lg text-xs uppercase tracking-widest font-bold hover:bg-[#6cd3f7]/10 transition-colors"
            >
              Accept {unit}{lastNpcOffer.toLocaleString()}
            </button>
            <button
              type="button"
              onClick={handleWalkAway}
              className="flex-1 px-4 py-2.5 border border-[#ffb4ab]/30 text-[#ffb4ab] rounded-lg text-xs uppercase tracking-widest font-bold hover:bg-[#ffb4ab]/10 transition-colors"
            >
              Walk Away
            </button>
          </div>

          {/* Rounds remaining hint */}
          {currentRound < rounds && (
            <p className="text-[10px] text-slate-500 text-center">
              {rounds - currentRound} round{rounds - currentRound > 1 ? 's' : ''} remaining to reach a deal
            </p>
          )}
        </div>
      )}
    </div>
  )
}
