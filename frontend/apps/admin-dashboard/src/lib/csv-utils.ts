export function downloadCSV(filename: string, headers: string[], rows: string[][]) {
  const csv = [
    headers.join(','),
    ...rows.map((r) =>
      r.map((c) => `"${(c || '').replace(/"/g, '""')}"`).join(',')
    ),
  ].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export function parseCSV(text: string): string[][] {
  const rows: string[][] = []
  let current = ''
  let inQuotes = false
  let row: string[] = []

  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    const next = text[i + 1]

    if (inQuotes) {
      if (ch === '"' && next === '"') {
        current += '"'
        i++ // skip escaped quote
      } else if (ch === '"') {
        inQuotes = false
      } else {
        current += ch
      }
    } else {
      if (ch === '"') {
        inQuotes = true
      } else if (ch === ',') {
        row.push(current.trim())
        current = ''
      } else if (ch === '\n' || (ch === '\r' && next === '\n')) {
        row.push(current.trim())
        if (row.some((c) => c !== '')) rows.push(row)
        row = []
        current = ''
        if (ch === '\r') i++ // skip \n after \r
      } else {
        current += ch
      }
    }
  }
  // Last row
  row.push(current.trim())
  if (row.some((c) => c !== '')) rows.push(row)

  return rows
}
