import { downloadBlob } from './downloadFile'

function escapeCell(value) {
  if (value == null) return ''
  const text = String(value)
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

// Builds a CSV from a header row + data rows and downloads it. A UTF-8 BOM is prepended so
// Excel opens translated (non-ASCII) headers correctly.
export function downloadCsv(filename, headers, rows) {
  const lines = [headers, ...rows].map((row) => row.map(escapeCell).join(','))
  const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  downloadBlob(blob, filename)
}
