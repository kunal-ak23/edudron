# Lottie Character System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace Lucide icon placeholders with animated 3D Lottie characters for the advisor and NPCs across the simulation play experience.

**Architecture:** Character Registry pattern — a TypeScript config maps character IDs to Lottie file paths. AI picks character IDs during generation. `LottieCharacter` component fetches and renders Lottie JSON with caching. All Lottie files bundled as static assets under `public/characters/`.

**Tech Stack:** lottie-react, Next.js static assets, TypeScript, existing SimulationGenerationService (Java/Spring Boot).

---

## Task 1: Install lottie-react

**Files:**
- Modify: `frontend/apps/student-portal/package.json`

**Steps:**

```bash
cd frontend/apps/student-portal && npm install lottie-react
```

**Commit:** `chore: add lottie-react to student portal`

---

## Task 2: Source and Add Lottie Character Files

**Files:**
- Create: `frontend/apps/student-portal/public/characters/` directory structure

**Steps:**

Go to [LottieFiles 3D Characters](https://lottiefiles.com/free-animations/3d-character) and download 4 character sets. Each character needs 5 mood variants (neutral, concerned, excited, disappointed, proud). If exact mood matches aren't available, use similar expressions (e.g., happy→excited, sad→disappointed, thinking→concerned, celebrating→proud).

Create directory structure:
```
frontend/apps/student-portal/public/characters/
  mentor_female_1/
    neutral.json
    concerned.json
    excited.json
    disappointed.json
    proud.json
  exec_male_1/
    neutral.json
    concerned.json
    excited.json
    disappointed.json
    proud.json
  tech_young_1/
    neutral.json
    concerned.json
    excited.json
    disappointed.json
    proud.json
  medical_female_1/
    neutral.json
    concerned.json
    excited.json
    disappointed.json
    proud.json
```

If sourcing 20 unique Lotties is impractical, use the SAME Lottie file for all 5 moods per character as a placeholder — the system will still work and can be upgraded with proper mood variants later.

**Commit:** `feat(characters): add 4 Lottie character sets with mood variants`

---

## Task 3: Create Character Registry

**Files:**
- Create: `frontend/apps/student-portal/src/lib/character-registry.ts`

**Implementation:**

```typescript
export interface CharacterDefinition {
  id: string
  name: string
  description: string
  category: 'mentor' | 'executive' | 'technical' | 'medical' | 'general'
  moods: Record<string, string>
}

const MOODS = ['neutral', 'concerned', 'excited', 'disappointed', 'proud'] as const

function buildMoods(characterId: string): Record<string, string> {
  const moods: Record<string, string> = {}
  for (const mood of MOODS) {
    moods[mood] = `/characters/${characterId}/${mood}.json`
  }
  return moods
}

export const CHARACTER_REGISTRY: Record<string, CharacterDefinition> = {
  mentor_female_1: {
    id: 'mentor_female_1',
    name: 'Professional Mentor',
    description: 'Professional woman, 40s, warm and authoritative demeanor',
    category: 'mentor',
    moods: buildMoods('mentor_female_1'),
  },
  exec_male_1: {
    id: 'exec_male_1',
    name: 'Corporate Executive',
    description: 'Corporate executive, 50s, serious and decisive demeanor',
    category: 'executive',
    moods: buildMoods('exec_male_1'),
  },
  tech_young_1: {
    id: 'tech_young_1',
    name: 'Tech Professional',
    description: 'Young tech professional, 20s-30s, enthusiastic and innovative',
    category: 'technical',
    moods: buildMoods('tech_young_1'),
  },
  medical_female_1: {
    id: 'medical_female_1',
    name: 'Medical Professional',
    description: 'Doctor or scientist, 30s-40s, analytical and precise',
    category: 'medical',
    moods: buildMoods('medical_female_1'),
  },
}

export const DEFAULT_CHARACTER_ID = 'mentor_female_1'

export function getCharacter(id: string): CharacterDefinition {
  return CHARACTER_REGISTRY[id] || CHARACTER_REGISTRY[DEFAULT_CHARACTER_ID]
}

export function getCharacterManifest(): Array<{ id: string; description: string }> {
  return Object.values(CHARACTER_REGISTRY).map(c => ({ id: c.id, description: c.description }))
}
```

**Commit:** `feat(characters): add character registry with 4 initial characters`

---

## Task 4: Create LottieCharacter Component

**Files:**
- Create: `frontend/apps/student-portal/src/components/simulation/LottieCharacter.tsx`

**Implementation:**

```typescript
'use client'

import { useState, useEffect, useRef } from 'react'
import Lottie from 'lottie-react'
import { getCharacter } from '@/lib/character-registry'
import { User } from 'lucide-react'

// Module-level cache for fetched Lottie data
const lottieCache = new Map<string, any>()

interface LottieCharacterProps {
  characterId: string
  mood: string
  size?: number
  className?: string
}

export function LottieCharacter({ characterId, mood, size = 64, className }: LottieCharacterProps) {
  const [animationData, setAnimationData] = useState<any>(null)
  const [error, setError] = useState(false)
  const prevDataRef = useRef<any>(null)

  useEffect(() => {
    const character = getCharacter(characterId)
    const path = character.moods[mood] || character.moods['neutral']
    if (!path) {
      setError(true)
      return
    }

    // Check cache first
    if (lottieCache.has(path)) {
      const data = lottieCache.get(path)
      setAnimationData(data)
      prevDataRef.current = data
      setError(false)
      return
    }

    // Fetch Lottie JSON
    fetch(path)
      .then(res => {
        if (!res.ok) throw new Error('Failed to load')
        return res.json()
      })
      .then(data => {
        lottieCache.set(path, data)
        setAnimationData(data)
        prevDataRef.current = data
        setError(false)
      })
      .catch(() => {
        setError(true)
      })
  }, [characterId, mood])

  if (error || !animationData) {
    // Fallback: Lucide icon (matches current behavior)
    return (
      <div
        className={`rounded-full bg-[#94A3B8]/10 flex items-center justify-center shrink-0 ${className || ''}`}
        style={{ width: size, height: size }}
      >
        <User className="text-[#94A3B8]" style={{ width: size * 0.5, height: size * 0.5 }} />
      </div>
    )
  }

  return (
    <div
      className={`shrink-0 ${className || ''}`}
      style={{ width: size, height: size }}
    >
      <Lottie
        animationData={animationData}
        loop
        autoplay
        style={{ width: size, height: size }}
      />
    </div>
  )
}
```

**Commit:** `feat(characters): add LottieCharacter component with caching and fallback`

---

## Task 5: Update AdvisorDialog to Use LottieCharacter

**Files:**
- Modify: `frontend/apps/student-portal/src/components/simulation/AdvisorDialog.tsx`

**Changes:**

1. Add `characterId?: string` to `AdvisorDialogProps`
2. Replace the Lucide icon circle (lines 66-69) with `<LottieCharacter>`:
   - If `characterId` is provided: `<LottieCharacter characterId={characterId} mood={mood} size={64} />`
   - If not provided: keep existing Lucide icon as fallback (backward compatible with old simulations)
3. Remove the Lucide mood icon imports that are no longer needed as primary display (keep them for the fallback path)

**Commit:** `feat(characters): integrate LottieCharacter into AdvisorDialog`

---

## Task 6: Update NegotiationInput with NPC Character

**Files:**
- Modify: `frontend/apps/student-portal/src/components/simulation/NegotiationInput.tsx`

**Changes:**

1. Add `import { LottieCharacter } from './LottieCharacter'`
2. Accept `npcCharacterId` from `config.npcCharacterId`
3. In the NPC speech bubble (left side), add `<LottieCharacter characterId={config.npcCharacterId} mood="neutral" size={48} />` before the NPC name
4. Only show if `config.npcCharacterId` exists — otherwise no change (backward compatible)

**Commit:** `feat(characters): add NPC character to NegotiationInput`

---

## Task 7: Update HireFireInput with Candidate Characters

**Files:**
- Modify: `frontend/apps/student-portal/src/components/simulation/HireFireInput.tsx`

**Changes:**

1. Add `import { LottieCharacter } from './LottieCharacter'`
2. In each candidate card, before the name/title section, add:
   ```tsx
   {candidate.characterId && (
     <LottieCharacter characterId={candidate.characterId} mood="neutral" size={40} />
   )}
   ```
3. Only show if `candidate.characterId` exists

**Commit:** `feat(characters): add candidate characters to HireFireInput`

---

## Task 8: Update StakeholderMeetingInput with Stakeholder Characters

**Files:**
- Modify: `frontend/apps/student-portal/src/components/simulation/StakeholderMeetingInput.tsx`

**Changes:**

1. Add `import { LottieCharacter } from './LottieCharacter'`
2. In stakeholder cards (both select and reveal phases), replace the initial-letter circle with:
   ```tsx
   {s.characterId ? (
     <LottieCharacter characterId={s.characterId} mood="neutral" size={40} />
   ) : (
     <div className="w-8 h-8 rounded-full bg-[#0891B2]/20 flex items-center justify-center text-[#0891B2] text-sm font-medium">
       {s.name.charAt(0)}
     </div>
   )}
   ```

**Commit:** `feat(characters): add stakeholder characters to StakeholderMeetingInput`

---

## Task 9: Update Backend — Pass characterId in Advisor Dialog

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationService.java`

**Changes:**

In `getCurrentState()`, where the advisor dialog is built (around line 400), add `characterId` from `advisorCharacter`:

```java
// Existing code builds dialog with mood, text, advisorName
// Add:
if (advisorCharacter != null && advisorCharacter.get("characterId") != null) {
    dialog.put("characterId", advisorCharacter.get("characterId"));
}
```

**Commit:** `feat(characters): pass characterId in advisor dialog state`

---

## Task 10: Update AI Generation — Character Assignment

**Files:**
- Modify: `content/src/main/java/com/datagami/edudron/content/simulation/service/SimulationGenerationService.java`

**Changes:**

### Phase 1 prompt update:

Add to the `advisorCharacter` section in the system prompt:
```
- characterId: pick ONE from the available characters below that best fits the advisor role:
  [{"id": "mentor_female_1", "description": "Professional woman, 40s, warm and authoritative"},
   {"id": "exec_male_1", "description": "Corporate executive, 50s, serious and decisive"},
   {"id": "tech_young_1", "description": "Young tech professional, 20s-30s, enthusiastic"},
   {"id": "medical_female_1", "description": "Doctor/scientist, 30s-40s, analytical"}]
```

Remove the old `portraitSet` field from the prompt.

### Phase 2 prompt update:

Add to the decision type schemas that have NPCs:

```
For NEGOTIATION: add "npcCharacterId" — pick from available characters (different from advisor)
For HIRE_FIRE candidates: add "characterId" per candidate — pick from available characters
For STAKEHOLDER_MEETING stakeholders: add "characterId" per stakeholder — pick from available characters
Do NOT reuse the advisor's characterId for the same NPC appearing across decisions.
```

**Commit:** `feat(characters): update AI generation to assign character IDs`

---

## Task 11: Update Frontend Types

**Files:**
- Modify: `frontend/packages/shared-utils/src/api/simulations.ts`

**Changes:**

Update `AdvisorDialog` interface:
```typescript
export interface AdvisorDialog {
  mood: 'neutral' | 'concerned' | 'excited' | 'disappointed' | 'proud'
  text: string
  advisorName?: string
  characterId?: string  // NEW
}
```

Build shared-utils: `cd frontend/packages/shared-utils && npm run build`

**Commit:** `feat(characters): add characterId to AdvisorDialog type`

---

## Task 12: Update Play Page — Pass characterId to AdvisorDialog

**Files:**
- Modify: `frontend/apps/student-portal/src/app/simulations/[id]/play/[playId]/page.tsx`

**Changes:**

Where `AdvisorDialog` is rendered (both ADVISOR_SETUP and ADVISOR_REACTION), pass the `characterId`:

```tsx
<AdvisorDialog
  mood={state.advisorDialog.mood}
  text={state.advisorDialog.text}
  advisorName={state.advisorDialog.advisorName || 'Advisor'}
  characterId={state.advisorDialog.characterId}  // NEW
  onDismiss={handleAdvisorDismiss}
/>
```

**Commit:** `feat(characters): pass characterId through play page to AdvisorDialog`

---

## Task 13: Build, Verify, Commit All

**Steps:**

1. Build shared-utils:
```bash
cd frontend/packages/shared-utils && npm run build
```

2. Verify backend compiles:
```bash
cd /path/to/worktree && ./gradlew :content:compileJava
```

3. Verify student portal builds:
```bash
cd frontend/apps/student-portal && npm run build
```

4. Push:
```bash
git push origin feat/simulation
```

**Commit:** `chore: verify Lottie character system builds`

---

## Implementation Order

```
Task 1  (lottie-react install)    ──┐
Task 2  (Lottie files)            ──┤── Foundation
Task 3  (Character registry)      ──┤
Task 4  (LottieCharacter component)─┘
                                    │
Task 5  (AdvisorDialog)           ──┐
Task 6  (NegotiationInput)        ──┤── Frontend integration
Task 7  (HireFireInput)           ──┤
Task 8  (StakeholderMeetingInput) ──┘
                                    │
Task 9  (Backend characterId)     ──┐── Backend + AI
Task 10 (AI generation prompts)   ──┘
                                    │
Task 11 (Shared types)            ──┐── Wiring
Task 12 (Play page)               ──┘
                                    │
Task 13 (Verify + push)           ──── Final
```

Tasks 1-4 are sequential (each depends on previous).
Tasks 5-8 can run in parallel (independent component updates).
Tasks 9-10 can run in parallel with 5-8.
Tasks 11-12 depend on 9.
Task 13 depends on all.
