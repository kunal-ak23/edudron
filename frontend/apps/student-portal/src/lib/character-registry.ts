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
