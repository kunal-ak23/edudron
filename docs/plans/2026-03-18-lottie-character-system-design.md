# Lottie Character System — Design Document

**Date:** 2026-03-18
**Status:** Approved
**Feature Flag:** Part of `SIMULATION` (premium, default off)

## Overview

Replace Lucide icon placeholders with animated 3D Lottie characters across the simulation play experience. Characters appear as the advisor mentor, negotiation NPCs, hire/fire candidates, and stakeholder meeting participants.

## Character Bank

10 character slots, starting with 4. Each character has 5 mood variants (neutral, concerned, excited, disappointed, proud) as Lottie JSON files.

### Initial Characters (4)

| ID | Description | Use Case |
|---|---|---|
| `mentor_female_1` | Professional woman, 40s, warm & authoritative | Advisor, senior stakeholder |
| `exec_male_1` | Corporate executive, 50s, serious demeanor | CFO, board member, investor NPC |
| `tech_young_1` | Young tech professional, 20s-30s, enthusiastic | Junior hire candidate, team member |
| `medical_female_1` | Doctor/scientist, 30s-40s, analytical | Medical professional, researcher NPC |

Slots 5-10 reserved for future expansion.

### File Structure

```
frontend/apps/student-portal/public/characters/
  mentor_female_1/
    neutral.json
    concerned.json
    excited.json
    disappointed.json
    proud.json
  exec_male_1/
    neutral.json, concerned.json, excited.json, disappointed.json, proud.json
  tech_young_1/
    neutral.json, concerned.json, excited.json, disappointed.json, proud.json
  medical_female_1/
    neutral.json, concerned.json, excited.json, disappointed.json, proud.json
```

Lottie JSONs sourced from LottieFiles (free, commercial-use licensed). ~50KB each, total ~1MB for 4 characters.

## Character Registry

TypeScript config mapping character IDs to metadata and Lottie file paths:

```typescript
// src/lib/character-registry.ts

export interface CharacterDefinition {
  id: string
  name: string
  description: string
  category: 'mentor' | 'executive' | 'technical' | 'medical' | 'general'
  moods: Record<string, string>  // mood → Lottie JSON path under /characters/
}

export const CHARACTER_REGISTRY: Record<string, CharacterDefinition> = {
  mentor_female_1: {
    id: 'mentor_female_1',
    name: 'Professional Mentor',
    description: 'Professional woman, 40s, warm and authoritative demeanor',
    category: 'mentor',
    moods: {
      neutral: '/characters/mentor_female_1/neutral.json',
      concerned: '/characters/mentor_female_1/concerned.json',
      excited: '/characters/mentor_female_1/excited.json',
      disappointed: '/characters/mentor_female_1/disappointed.json',
      proud: '/characters/mentor_female_1/proud.json',
    },
  },
  // ... other characters follow same pattern
}

export const DEFAULT_CHARACTER_ID = 'mentor_female_1'

export function getCharacter(id: string): CharacterDefinition {
  return CHARACTER_REGISTRY[id] || CHARACTER_REGISTRY[DEFAULT_CHARACTER_ID]
}

export function getCharacterManifest(): Array<{ id: string; description: string }> {
  return Object.values(CHARACTER_REGISTRY)
    .filter(c => !c.id.startsWith('reserved_'))
    .map(c => ({ id: c.id, description: c.description }))
}
```

## LottieCharacter Component

```typescript
// src/components/simulation/LottieCharacter.tsx

interface LottieCharacterProps {
  characterId: string
  mood: string
  size?: number  // px, default 64
}
```

- Fetches Lottie JSON from `/characters/{id}/{mood}.json`
- Caches fetched data in a module-level Map to avoid re-fetching
- Renders with `lottie-react` (loops by default for idle animation)
- Falls back to current Lucide icon if character not found or loading fails
- Crossfade on mood change via CSS opacity transition

## Data Model Changes

All within existing `simulation_data` JSONB — no DB schema changes.

### Advisor Character

```json
{
  "advisorCharacter": {
    "name": "Dr. Rivera",
    "characterId": "mentor_female_1",
    "role": "Your mentor and former division head",
    "personality": "Wise, direct, occasionally sarcastic"
  }
}
```

Replaces the existing `portraitSet` field with `characterId`.

### NPC Characters in Decisions

**NEGOTIATION:**
```json
{
  "decisionConfig": {
    "npcName": "CFO",
    "npcCharacterId": "exec_male_1",
    ...
  }
}
```

**HIRE_FIRE candidates:**
```json
{
  "candidates": [
    { "id": "patel", "name": "Dr. Patel", "characterId": "medical_female_1", ... }
  ]
}
```

**STAKEHOLDER_MEETING stakeholders:**
```json
{
  "stakeholders": [
    { "id": "cmo", "name": "Dr. Hayes", "characterId": "medical_female_1", ... }
  ]
}
```

## Component Integration

| Component | Character Source | Size | Mood |
|---|---|---|---|
| `AdvisorDialog` | `advisorCharacter.characterId` | 64px | Per-decision mood from AI |
| `NegotiationInput` | `config.npcCharacterId` | 48px | `neutral` (static) |
| `HireFireInput` | `candidate.characterId` | 40px | `neutral` (static) |
| `StakeholderMeetingInput` | `stakeholder.characterId` | 40px | `neutral` (static) |

All components fall back gracefully to the current Lucide icon / initial-letter circle if `characterId` is missing or not in the registry.

## Backend Changes

### SimulationService

Pass `characterId` in the advisor dialog state response:

```json
{
  "advisorDialog": {
    "mood": "concerned",
    "text": "The board is watching...",
    "advisorName": "Dr. Rivera",
    "characterId": "mentor_female_1"
  }
}
```

### AI Generation Prompts

**Phase 1:** Add character manifest to system prompt so AI picks `characterId` for advisor:
```
Available characters: [{"id": "mentor_female_1", "description": "..."}, ...]
Pick the most appropriate characterId for the advisor.
```

**Phase 2:** Add character manifest so AI assigns `npcCharacterId` / `characterId` to NPCs in negotiation, hire/fire, and stakeholder meeting decisions:
```
For NPCs, assign a characterId from the available characters bank.
Do not reuse the advisor's characterId for NPCs.
```

## Dependencies

- `lottie-react` npm package (~50KB gzipped)
- 4 Lottie character sets from LottieFiles (free, commercial-use)

## Not In Scope

- Admin UI for managing characters
- Azure Blob storage for custom characters
- Characters 5-10 (expansion phase)
- Backend DB schema changes
