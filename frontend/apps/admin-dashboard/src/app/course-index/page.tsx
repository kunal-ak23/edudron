'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ProtectedRoute } from '@edudron/ui-components'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Plus, Trash2, X, Loader2, FileText } from 'lucide-react'
import { courseGenerationIndexApi } from '@/lib/api'
import type { CourseGenerationIndex } from '@edudron/shared-utils'

export const dynamic = 'force-dynamic'

export default function CourseIndexPage() {
  const router = useRouter()
  const [indexes, setIndexes] = useState<CourseGenerationIndex[]>([])
  const [loading, setLoading] = useState(true)
  const [showUploadModal, setShowUploadModal] = useState(false)
  const [uploadType, setUploadType] = useState<'REFERENCE_CONTENT' | 'WRITING_FORMAT'>('REFERENCE_CONTENT')
  const [uploadForm, setUploadForm] = useState({
    title: '',
    description: '',
    writingFormat: '',
    file: null as File | null
  })

  useEffect(() => {
    loadIndexes()
  }, [])

  const loadIndexes = async () => {
    try {
      const data = await courseGenerationIndexApi.listIndexes()
      setIndexes(data)
    } catch (error) {
      console.error('Failed to load indexes:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleUpload = async () => {
    if (!uploadForm.title.trim()) {
      alert('Title is required')
      return
    }

    if (uploadType === 'REFERENCE_CONTENT' && !uploadForm.file) {
      alert('File is required for reference content')
      return
    }

    if (uploadType === 'WRITING_FORMAT' && !uploadForm.writingFormat.trim() && !uploadForm.file) {
      alert('Either writing format text or file is required')
      return
    }

    try {
      if (uploadType === 'REFERENCE_CONTENT') {
        await courseGenerationIndexApi.uploadReferenceContent(
          uploadForm.title,
          uploadForm.description || undefined,
          uploadForm.file!
        )
      } else {
        await courseGenerationIndexApi.createWritingFormat(
          uploadForm.title,
          uploadForm.description || undefined,
          uploadForm.writingFormat || undefined,
          uploadForm.file || undefined
        )
      }
      setShowUploadModal(false)
      setUploadForm({ title: '', description: '', writingFormat: '', file: null })
      await loadIndexes()
    } catch (error: any) {
      console.error('Failed to upload:', error)
      alert(error.response?.data?.message || 'Failed to upload. Please try again.')
    }
  }

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this index?')) return
    
    try {
      await courseGenerationIndexApi.deleteIndex(id)
      await loadIndexes()
    } catch (error) {
      alert('Failed to delete index')
    }
  }

  const referenceIndexes = indexes.filter(i => i.indexType === 'REFERENCE_CONTENT')
  const writingFormats = indexes.filter(i => i.indexType === 'WRITING_FORMAT')

  return (
    <ProtectedRoute requiredRoles={['SYSTEM_ADMIN', 'TENANT_ADMIN', 'CONTENT_MANAGER', 'INSTRUCTOR']}>
      <div className="min-h-screen bg-gray-50">
        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="mb-6 flex items-center justify-between">
            <div>
              <h1 className="text-3xl font-bold text-gray-900 mb-2">Course Generation Index</h1>
              <p className="text-gray-600">Manage reference content and writing formats for AI course generation</p>
            </div>
            <Button onClick={() => setShowUploadModal(true)}>
              <Plus className="w-5 h-5 mr-2" />
              Add Index
            </Button>
          </div>

          {/* Reference Content Section */}
          <Card className="mb-6">
            <CardHeader>
              <CardTitle>Reference Content</CardTitle>
            </CardHeader>
            <CardContent>
              {loading ? (
                <div className="text-center py-8">
                  <Loader2 className="h-6 w-6 animate-spin text-primary mx-auto" />
                </div>
              ) : referenceIndexes.length === 0 ? (
                <div className="text-center py-8 text-muted-foreground">No reference content uploaded yet</div>
              ) : (
                <div className="space-y-3">
                  {referenceIndexes.map((index) => (
                    <div key={index.id} className="border rounded-lg p-4">
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <h3 className="font-medium">{index.title}</h3>
                          {index.description && (
                            <p className="text-sm text-muted-foreground mt-1">{index.description}</p>
                          )}
                          <div className="mt-2 text-xs text-muted-foreground">
                            {index.fileSizeBytes && (
                              <span>Size: {(index.fileSizeBytes / 1024).toFixed(1)} KB</span>
                            )}
                            {index.extractedText && (
                              <span className="ml-4">
                                Text extracted: {index.extractedText.length} characters
                              </span>
                            )}
                          </div>
                        </div>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDelete(index.id)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Writing Formats Section */}
          <Card>
            <CardHeader>
              <CardTitle>Writing Formats</CardTitle>
            </CardHeader>
            <CardContent>
              {loading ? (
                <div className="text-center py-8">
                  <Loader2 className="h-6 w-6 animate-spin text-primary mx-auto" />
                </div>
              ) : writingFormats.length === 0 ? (
                <div className="text-center py-8 text-muted-foreground">No writing formats created yet</div>
              ) : (
                <div className="space-y-3">
                  {writingFormats.map((index) => (
                    <div key={index.id} className="border rounded-lg p-4">
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <h3 className="font-medium">{index.title}</h3>
                          {index.description && (
                            <p className="text-sm text-muted-foreground mt-1">{index.description}</p>
                          )}
                          {index.writingFormat && (
                            <p className="text-xs text-muted-foreground mt-2 line-clamp-2">
                              {index.writingFormat.substring(0, 200)}...
                            </p>
                          )}
                        </div>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDelete(index.id)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Upload Modal */}
          <Dialog open={showUploadModal} onOpenChange={setShowUploadModal}>
            <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle>
                  Add {uploadType === 'REFERENCE_CONTENT' ? 'Reference Content' : 'Writing Format'}
                </DialogTitle>
                <DialogDescription>
                  Upload reference content or create a writing format template for AI course generation
                </DialogDescription>
              </DialogHeader>

              <div className="space-y-4">
                <div className="space-y-2">
                  <Label>Type</Label>
                  <Select
                    value={uploadType}
                    onValueChange={(value) => setUploadType(value as 'REFERENCE_CONTENT' | 'WRITING_FORMAT')}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="REFERENCE_CONTENT">Reference Content</SelectItem>
                      <SelectItem value="WRITING_FORMAT">Writing Format</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label>
                    Title <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    type="text"
                    value={uploadForm.title}
                    onChange={(e) => setUploadForm({...uploadForm, title: e.target.value})}
                    placeholder="Enter title"
                  />
                </div>

                <div className="space-y-2">
                  <Label>Description</Label>
                  <Textarea
                    value={uploadForm.description}
                    onChange={(e) => setUploadForm({...uploadForm, description: e.target.value})}
                    rows={2}
                    placeholder="Enter description (optional)"
                  />
                </div>

                {uploadType === 'WRITING_FORMAT' && (
                  <div className="space-y-2">
                    <Label>Writing Format Text</Label>
                    <Textarea
                      value={uploadForm.writingFormat}
                      onChange={(e) => setUploadForm({...uploadForm, writingFormat: e.target.value})}
                      rows={6}
                      placeholder="Enter writing format/style template..."
                    />
                    <p className="text-xs text-muted-foreground">Or upload a file below</p>
                  </div>
                )}

                <div className="space-y-2">
                  <Label>
                    {uploadType === 'REFERENCE_CONTENT' ? 'File *' : 'File (optional)'}
                  </Label>
                  <Input
                    type="file"
                    onChange={(e) => setUploadForm({...uploadForm, file: e.target.files?.[0] || null})}
                    accept={uploadType === 'REFERENCE_CONTENT' ? '.pdf,.doc,.docx,.txt' : '.pdf,.doc,.docx,.txt'}
                  />
                </div>
              </div>

              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={() => {
                    setShowUploadModal(false)
                    setUploadForm({ title: '', description: '', writingFormat: '', file: null })
                  }}
                >
                  Cancel
                </Button>
                <Button onClick={handleUpload}>
                  <FileText className="h-4 w-4 mr-2" />
                  Upload
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </main>
      </div>
    </ProtectedRoute>
  )
}

