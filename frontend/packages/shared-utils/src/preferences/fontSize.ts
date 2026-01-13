/**
 * Font size preference management utilities
 * Stores user's font size preference in localStorage
 */

const STORAGE_KEY = 'edudron_font_size'
const DEFAULT_FONT_SIZE = 0.8 // 80%
const MIN_FONT_SIZE = 0.5 // 50%
const MAX_FONT_SIZE = 1.5 // 150%

/**
 * Get the current font size from localStorage or return default
 */
export function getFontSize(): number {
  if (typeof window === 'undefined') {
    return DEFAULT_FONT_SIZE
  }

  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === null) {
      return DEFAULT_FONT_SIZE
    }

    const fontSize = parseFloat(stored)
    // Validate the stored value
    if (isNaN(fontSize) || fontSize < MIN_FONT_SIZE || fontSize > MAX_FONT_SIZE) {
      return DEFAULT_FONT_SIZE
    }

    return fontSize
  } catch (error) {
    console.warn('[FontSize] Error reading from localStorage:', error)
    return DEFAULT_FONT_SIZE
  }
}

/**
 * Set the font size preference in localStorage and update CSS variable
 */
export function setFontSize(value: number): void {
  if (typeof window === 'undefined') {
    return
  }

  // Clamp the value to valid range
  const clampedValue = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, value))

  try {
    localStorage.setItem(STORAGE_KEY, clampedValue.toString())
    // Update CSS variable immediately
    document.documentElement.style.setProperty('--app-scale', clampedValue.toString())
  } catch (error) {
    console.warn('[FontSize] Error saving to localStorage:', error)
  }
}

/**
 * Reset font size to default value
 */
export function resetFontSize(): void {
  setFontSize(DEFAULT_FONT_SIZE)
}

/**
 * Apply font size from localStorage to CSS variable
 * Call this on page load to initialize
 */
export function applyFontSize(): void {
  if (typeof window === 'undefined') {
    return
  }

  const fontSize = getFontSize()
  document.documentElement.style.setProperty('--app-scale', fontSize.toString())
}
