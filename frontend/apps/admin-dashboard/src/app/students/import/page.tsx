'use client'

import React, { useState, useCallback, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@edudron/shared-utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { Upload, Download, Loader2, CheckCircle2, XCircle, AlertCircle } from 'lucide-react'
import { studentsApi } from '@/lib/api'
import type { BulkStudentImportResult, StudentImportRowResult } from '@edudron/shared-utils'
import { useToast } from '@/hooks/use-toast'
import { extractErrorMessage } from '@/lib/error-utils'

export default function BulkImportPage() {
  const router = useRouter()
  const { toast } = useToast()
  const { user, isAuthenticated } = useAuth()
  const [file, setFile] = useState<File | null>(null)
  const [importing, setImporting] = useState(false)
  const [result, setResult] = useState<BulkStudentImportResult | null>(null)
  const [options, setOptions] = useState({
    autoGeneratePassword: true,
    upsertExisting: false,
    autoEnroll: false,
  })

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0]
    if (selectedFile) {
      const validExtensions = ['.csv', '.xlsx', '.xls']
      const fileExtension = selectedFile.name.toLowerCase().substring(selectedFile.name.lastIndexOf('.'))
      if (!validExtensions.includes(fileExtension)) {
        toast({
          variant: 'destructive',
          title: 'Invalid file type',
          description: 'Please select a CSV or Excel file (.csv, .xlsx, .xls)',
        })
        return
      }
      setFile(selectedFile)
      setResult(null)
    }
  }

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    const droppedFile = e.dataTransfer.files[0]
    if (droppedFile) {
      const validExtensions = ['.csv', '.xlsx', '.xls']
      const fileExtension = droppedFile.name.toLowerCase().substring(droppedFile.name.lastIndexOf('.'))
      if (!validExtensions.includes(fileExtension)) {
        toast({
          variant: 'destructive',
          title: 'Invalid file type',
          description: 'Please select a CSV or Excel file (.csv, .xlsx, .xls)',
        })
        return
      }
      setFile(droppedFile)
      setResult(null)
    }
  }, [toast])

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
  }, [])

  const downloadTemplate = () => {
    const csvContent = `name,email,phone,password,instituteId,classId,sectionId,courseId
John Doe,john.doe@example.com,1234567890,SecurePass123,inst_123,class_456,section_789,course_101
Jane Smith,jane.smith@example.com,0987654321,,inst_123,class_456,,
Bob Johnson,bob.johnson@example.com,,,,inst_123,,,`

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement('a')
    const url = URL.createObjectURL(blob)
    link.setAttribute('href', url)
    link.setAttribute('download', 'student_import_template.csv')
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  const handleImport = async () => {
    if (!file) {
      toast({
        variant: 'destructive',
        title: 'No file selected',
        description: 'Please select a file to import',
      })
      return
    }

    setImporting(true)
    try {
      const importResult = await studentsApi.bulkImport(file, options)
      setResult(importResult)
      toast({
        title: 'Import completed',
        description: `Successfully imported ${importResult.successfulRows} of ${importResult.totalRows} students`,
      })
    } catch (err: any) {
      console.error('Error importing students:', err)
      const errorMessage = extractErrorMessage(err)
      toast({
        variant: 'destructive',
        title: 'Import failed',
        description: errorMessage,
      })
    } finally {
      setImporting(false)
    }
  }

  // Role-based access control
  useEffect(() => {
    if (!isAuthenticated() || !user) {
      router.push('/login')
      return
    }
    
    const allowedRoles = ['SYSTEM_ADMIN', 'TENANT_ADMIN']
    if (!allowedRoles.includes(user.role)) {
      router.push('/unauthorized')
    }
  }, [user, isAuthenticated, router])

  if (!user || !isAuthenticated()) {
    return null
  }

  const allowedRoles = ['SYSTEM_ADMIN', 'TENANT_ADMIN']
  if (!allowedRoles.includes(user.role)) {
    return null
  }

  return (
    <div>
      <div className="mb-6">
        <p className="text-gray-600">Import students from CSV or Excel file</p>
      </div>

      <div className="grid gap-6">
          {/* Import Options */}
          <Card>
            <CardHeader>
              <CardTitle>Import Options</CardTitle>
              <CardDescription>Configure how students should be imported</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="autoGeneratePassword"
                  checked={options.autoGeneratePassword}
                  onCheckedChange={(checked) =>
                    setOptions({ ...options, autoGeneratePassword: checked as boolean })
                  }
                />
                <Label htmlFor="autoGeneratePassword" className="cursor-pointer">
                  Auto-generate passwords for students without passwords
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="upsertExisting"
                  checked={options.upsertExisting}
                  onCheckedChange={(checked) =>
                    setOptions({ ...options, upsertExisting: checked as boolean })
                  }
                />
                <Label htmlFor="upsertExisting" className="cursor-pointer">
                  Update existing students (by email)
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="autoEnroll"
                  checked={options.autoEnroll}
                  onCheckedChange={(checked) =>
                    setOptions({ ...options, autoEnroll: checked as boolean })
                  }
                />
                <Label htmlFor="autoEnroll" className="cursor-pointer">
                  Auto-enroll students in courses (if courseId provided in CSV)
                </Label>
              </div>
            </CardContent>
          </Card>

          {/* File Upload */}
          <Card>
            <CardHeader>
              <CardTitle>Upload File</CardTitle>
              <CardDescription>Select a CSV or Excel file to import</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div
                className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center hover:border-gray-400 transition-colors cursor-pointer"
                onDrop={handleDrop}
                onDragOver={handleDragOver}
              >
                <input
                  type="file"
                  id="file-upload"
                  className="hidden"
                  accept=".csv,.xlsx,.xls"
                  onChange={handleFileSelect}
                />
                <label htmlFor="file-upload" className="cursor-pointer">
                  <Upload className="mx-auto h-12 w-12 text-gray-400 mb-4" />
                  <p className="text-sm text-gray-600 mb-2">
                    {file ? file.name : 'Click to upload or drag and drop'}
                  </p>
                  <p className="text-xs text-gray-500">CSV or Excel (.csv, .xlsx, .xls)</p>
                </label>
              </div>

              <div className="flex gap-2">
                <Button onClick={downloadTemplate} variant="outline" className="flex-1">
                  <Download className="mr-2 h-4 w-4" />
                  Download Template
                </Button>
                <Button
                  onClick={handleImport}
                  disabled={!file || importing}
                  className="flex-1"
                >
                  {importing ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Importing...
                    </>
                  ) : (
                    <>
                      <Upload className="mr-2 h-4 w-4" />
                      Import Students
                    </>
                  )}
                </Button>
              </div>
            </CardContent>
          </Card>

          {/* Results */}
          {result && (
            <Card>
              <CardHeader>
                <CardTitle>Import Results</CardTitle>
                <CardDescription>
                  {result.successfulRows} successful, {result.failedRows} failed out of{' '}
                  {result.totalRows} total
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-4 gap-4 mb-6">
                  <div className="text-center p-4 bg-gray-50 rounded-lg">
                    <div className="text-2xl font-bold text-gray-900">{result.totalRows}</div>
                    <div className="text-sm text-gray-600">Total Rows</div>
                  </div>
                  <div className="text-center p-4 bg-green-50 rounded-lg">
                    <div className="text-2xl font-bold text-green-600">
                      {result.successfulRows}
                    </div>
                    <div className="text-sm text-gray-600">Successful</div>
                  </div>
                  <div className="text-center p-4 bg-red-50 rounded-lg">
                    <div className="text-2xl font-bold text-red-600">{result.failedRows}</div>
                    <div className="text-sm text-gray-600">Failed</div>
                  </div>
                  <div className="text-center p-4 bg-yellow-50 rounded-lg">
                    <div className="text-2xl font-bold text-yellow-600">
                      {result.skippedRows}
                    </div>
                    <div className="text-sm text-gray-600">Skipped</div>
                  </div>
                </div>

                {result.rowResults && result.rowResults.length > 0 && (
                  <div className="mt-6">
                    <h3 className="text-lg font-semibold mb-4">Detailed Results</h3>
                    <div className="border rounded-lg overflow-hidden">
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead>Row</TableHead>
                            <TableHead>Email</TableHead>
                            <TableHead>Name</TableHead>
                            <TableHead>Status</TableHead>
                            <TableHead>Message</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {result.rowResults.map((row: StudentImportRowResult, index: number) => (
                            <TableRow key={index}>
                              <TableCell>{row.rowNumber}</TableCell>
                              <TableCell>{row.email}</TableCell>
                              <TableCell>{row.name}</TableCell>
                              <TableCell>
                                {row.success ? (
                                  <Badge variant="default" className="bg-green-500">
                                    <CheckCircle2 className="mr-1 h-3 w-3" />
                                    Success
                                  </Badge>
                                ) : (
                                  <Badge variant="destructive">
                                    <XCircle className="mr-1 h-3 w-3" />
                                    Failed
                                  </Badge>
                                )}
                              </TableCell>
                              <TableCell>
                                {row.success ? (
                                  <span className="text-green-600">Student ID: {row.studentId}</span>
                                ) : (
                                  <span className="text-red-600 flex items-center">
                                    <AlertCircle className="mr-1 h-4 w-4" />
                                    {row.errorMessage}
                                  </span>
                                )}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          )}
      </div>
    </div>
  )
}

