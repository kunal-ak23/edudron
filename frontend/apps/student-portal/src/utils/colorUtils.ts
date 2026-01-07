/**
 * Convert hex color to HSL values for CSS variables
 */
export function hexToHsl(hex: string): string {
  // Remove # if present
  hex = hex.replace('#', '')
  
  // Parse RGB
  const r = parseInt(hex.substring(0, 2), 16) / 255
  const g = parseInt(hex.substring(2, 4), 16) / 255
  const b = parseInt(hex.substring(4, 6), 16) / 255

  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  let h: number, s: number, l: number

  l = (max + min) / 2

  if (max === min) {
    h = s = 0 // achromatic
  } else {
    const d = max - min
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
    
    switch (max) {
      case r:
        h = ((g - b) / d + (g < b ? 6 : 0)) / 6
        break
      case g:
        h = ((b - r) / d + 2) / 6
        break
      case b:
        h = ((r - g) / d + 4) / 6
        break
      default:
        h = 0
    }
  }

  h = Math.round(h * 360)
  s = Math.round(s * 100)
  l = Math.round(l * 100)

  return `${h} ${s}% ${l}%`
}

/**
 * Generate primary color shades from a base color
 */
export function generatePrimaryShades(primaryHex: string): Record<string, string> {
  const baseHsl = hexToHsl(primaryHex)
  const [h, s, l] = baseHsl.split(' ').map((v, i) => 
    i === 0 ? parseFloat(v) : parseFloat(v.replace('%', ''))
  )

  // Generate shades (50-900)
  // Ensure 600+ shades are dark enough for white text (lightness < 50%)
  const shades: Record<string, string> = {
    50: `${h} ${Math.max(0, s - 50)}% ${Math.min(100, l + 45)}%`,
    100: `${h} ${Math.max(0, s - 40)}% ${Math.min(100, l + 35)}%`,
    200: `${h} ${Math.max(0, s - 30)}% ${Math.min(100, l + 25)}%`,
    300: `${h} ${Math.max(0, s - 20)}% ${Math.min(100, l + 15)}%`,
    400: `${h} ${Math.max(0, s - 10)}% ${Math.min(100, l + 5)}%`,
    500: `${h} ${s}% ${l}%`, // Base color
    600: `${h} ${Math.min(100, s + 10)}% ${Math.max(0, Math.min(45, l - 5))}%`, // Ensure max lightness of 45% for readability
    700: `${h} ${Math.min(100, s + 20)}% ${Math.max(0, Math.min(40, l - 15))}%`,
    800: `${h} ${Math.min(100, s + 30)}% ${Math.max(0, Math.min(35, l - 25))}%`,
    900: `${h} ${Math.min(100, s + 40)}% ${Math.max(0, Math.min(30, l - 35))}%`,
  }

  return shades
}

/**
 * Apply tenant branding colors to CSS variables
 */
export function applyTenantBranding(primaryColor: string) {
  if (typeof window === 'undefined') return

  const shades = generatePrimaryShades(primaryColor)
  const root = document.documentElement

  // Set primary color shades
  Object.entries(shades).forEach(([shade, value]) => {
    root.style.setProperty(`--primary-${shade}`, value)
  })

  // Set main primary color
  root.style.setProperty('--primary', shades[500])
  
  // Set primary foreground (white for dark colors, dark for light colors)
  const [h, s, l] = shades[500].split(' ').map((v, i) => 
    i === 0 ? parseFloat(v) : parseFloat(v.replace('%', ''))
  )
  const foreground = l > 50 ? '222.2 47.4% 11.2%' : '210 40% 98%'
  root.style.setProperty('--primary-foreground', foreground)
}

