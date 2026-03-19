'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
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
}

export function NegotiationInput({ config, onSubmit, disabled }: NegotiationInputProps) {
  const [currentRound, setCurrentRound] = useState(1)
  const [offerAmount, setOfferAmount] = useState('')
  const [dialogHistory, setDialogHistory] = useState<Array<{ speaker: string; text: string }>>([
    { speaker: config.npcName, text: `I'm proposing ${config.unit}${config.initialOffer.toLocaleString()}. What do you think?` }
  ])
  const [lastNpcOffer, setLastNpcOffer] = useState(config.initialOffer)
  const [resolved, setResolved] = useState(false)

  const handleCounter = () => {
    const amount = parseInt(offerAmount.replace(/,/g, ''), 10)
    if (isNaN(amount) || amount < 0) return

    setDialogHistory(prev => [...prev, { speaker: 'You', text: `${config.unit}${amount.toLocaleString()}` }])

    const response = config.npcResponses.find(
      r => r.round === currentRound && amount >= r.playerRange.min && amount <= r.playerRange.max
    ) || config.npcResponses.find(r => r.round === currentRound)

    if (response) {
      setDialogHistory(prev => [...prev, { speaker: config.npcName, text: response.response }])
      if (response.npcCounterOffer === null) {
        // NPC accepts
        setResolved(true)
        onSubmit({ input: { finalAmount: amount, acceptedRound: currentRound, walkedAway: false } })
        return
      }
      setLastNpcOffer(response.npcCounterOffer)
    }

    if (currentRound >= config.rounds) {
      setResolved(true)
      onSubmit({ input: { finalAmount: amount, acceptedRound: currentRound, walkedAway: false } })
      return
    }

    setCurrentRound(prev => prev + 1)
    setOfferAmount('')
  }

  const handleAccept = () => {
    setResolved(true)
    setDialogHistory(prev => [...prev, { speaker: 'You', text: `I accept ${config.unit}${lastNpcOffer.toLocaleString()}.` }])
    onSubmit({ input: { finalAmount: lastNpcOffer, acceptedRound: currentRound, walkedAway: false } })
  }

  const handleWalkAway = () => {
    setResolved(true)
    setDialogHistory(prev => [...prev, { speaker: 'You', text: 'I\'m walking away from this deal.' }])
    onSubmit({ input: { finalAmount: 0, acceptedRound: currentRound, walkedAway: true } })
  }

  return (
    <div className="space-y-4">
      <div className="bg-[#1A2744] border border-[#1E3A5F]/30 rounded-xl p-4 max-h-80 overflow-y-auto space-y-3">
        {dialogHistory.map((msg, i) => (
          <div key={i} className={`flex ${msg.speaker === 'You' ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-[75%] rounded-lg px-4 py-2 flex gap-2 items-start ${
              msg.speaker === 'You'
                ? 'bg-[#0891B2]/20 text-[#E2E8F0]'
                : 'bg-[#1E3A5F]/50 text-[#E2E8F0]'
            }`}>
              {msg.speaker !== 'You' && (config as any).npcCharacterId && (
                <LottieCharacter characterId={(config as any).npcCharacterId} mood="neutral" size={32} />
              )}
              <p className="text-xs text-[#94A3B8] mb-1">{msg.speaker}</p>
              <p className="text-sm">{msg.text}</p>
            </div>
          </div>
        ))}
      </div>

      {!resolved && !disabled && (
        <div className="space-y-3">
          <div className="text-xs text-[#94A3B8]">Round {currentRound} of {config.rounds}</div>
          <div className="flex gap-2">
            <div className="flex-1 relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-[#94A3B8] text-sm">{config.unit}</span>
              <input
                type="text"
                value={offerAmount}
                onChange={(e) => setOfferAmount(e.target.value.replace(/[^0-9,]/g, ''))}
                placeholder="Enter amount"
                className="w-full pl-7 pr-3 py-2 bg-[#0F1729] border border-[#1E3A5F]/50 rounded-lg text-[#E2E8F0] text-sm focus:outline-none focus:border-[#0891B2]"
              />
            </div>
            <Button onClick={handleCounter} disabled={!offerAmount} size="sm">Counter</Button>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={handleAccept} className="flex-1" size="sm">
              Accept {config.unit}{lastNpcOffer.toLocaleString()}
            </Button>
            <Button variant="destructive" onClick={handleWalkAway} className="flex-1" size="sm">
              Walk Away
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
