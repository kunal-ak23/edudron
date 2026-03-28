'use client'

import { useState, useRef } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Upload, Download, Loader2, CheckCircle, AlertCircle } from 'lucide-react'
import { calendarEventsApi } from '@/lib/api'
import type { CalendarEventImportResult } from '@kunal-ak23/edudron-shared-utils'

interface EventImportModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onImportComplete: () => void
}

export function EventImportModal({ open, onOpenChange, onImportComplete }: EventImportModalProps) {
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [result, setResult] = useState<CalendarEventImportResult | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0]
    if (selected) {
      setFile(selected)
      setResult(null)
    }
  }

  const handleUpload = async () => {
    if (!file) return
    setUploading(true)
    try {
      const importResult = await calendarEventsApi.importEvents(file)
      setResult(importResult)
      if (importResult.created > 0) {
        onImportComplete()
      }
    } catch (error) {
      setResult({ created: 0, errors: 1, errorDetails: [{ row: 0, message: 'Upload failed. Please check your file format and try again.' }] })
    } finally {
      setUploading(false)
    }
  }

  const handleDownloadTemplate = async () => {
    try {
      const blob = await calendarEventsApi.getImportTemplate()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'calendar-events-template.csv'
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      // silently fail
    }
  }

  const handleClose = (open: boolean) => {
    if (!open) {
      setFile(null)
      setResult(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
    onOpenChange(open)
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Import Calendar Events</DialogTitle>
          <DialogDescription>
            Upload a CSV file to bulk-import events into the calendar.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 mt-2">
          {/* Download template */}
          <Button variant="outline" size="sm" onClick={handleDownloadTemplate} className="w-full">
            <Download className="w-4 h-4 mr-2" />
            Download CSV Template
          </Button>

          {/* File upload */}
          <div className="border-2 border-dashed rounded-lg p-6 text-center">
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv"
              onChange={handleFileChange}
              className="hidden"
              id="csv-upload"
            />
            <label htmlFor="csv-upload" className="cursor-pointer">
              <Upload className="w-8 h-8 mx-auto text-muted-foreground mb-2" />
              <p className="text-sm text-muted-foreground">
                {file ? file.name : 'Click to select a CSV file'}
              </p>
            </label>
          </div>

          {/* Upload button */}
          <Button
            onClick={handleUpload}
            disabled={!file || uploading}
            className="w-full"
          >
            {uploading ? (
              <>
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                Importing...
              </>
            ) : (
              <>
                <Upload className="w-4 h-4 mr-2" />
                Upload and Import
              </>
            )}
          </Button>

          {/* Results */}
          {result && (
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-sm">
                <CheckCircle className="w-4 h-4 text-green-600" />
                <span>{result.created} event{result.created !== 1 ? 's' : ''} imported successfully</span>
              </div>
              {result.errors > 0 && (
                <div>
                  <div className="flex items-center gap-2 text-sm text-red-600 mb-2">
                    <AlertCircle className="w-4 h-4" />
                    <span>{result.errors} error{result.errors !== 1 ? 's' : ''}</span>
                  </div>
                  <div className="max-h-40 overflow-y-auto border rounded text-xs">
                    <table className="w-full">
                      <thead className="bg-muted sticky top-0">
                        <tr>
                          <th className="px-2 py-1 text-left">Row</th>
                          <th className="px-2 py-1 text-left">Error</th>
                        </tr>
                      </thead>
                      <tbody>
                        {result.errorDetails.map((err, i) => (
                          <tr key={i} className="border-t">
                            <td className="px-2 py-1">{err.row}</td>
                            <td className="px-2 py-1">{err.message}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
