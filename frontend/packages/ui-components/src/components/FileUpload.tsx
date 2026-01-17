import React, { useRef, useState } from 'react'
import { cn } from '../utils/cn'

export interface FileUploadProps {
  label: string
  accept?: string
  maxSize?: number // in bytes
  value?: string // URL of uploaded file
  onChange?: (url: string) => void
  onUpload?: (file: File) => Promise<string> // Upload function that returns URL
  onError?: (message: string) => void // Error callback for displaying errors
  error?: string
  helperText?: string
  className?: string
  disabled?: boolean
  required?: boolean
}

export default function FileUpload({
  label,
  accept = 'image/*',
  maxSize = 10 * 1024 * 1024, // 10MB default
  value,
  onChange,
  onUpload,
  onError,
  error,
  helperText,
  className,
  disabled = false,
  required = false,
}: FileUploadProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  // Only show preview for images, not for videos or other file types
  const [preview, setPreview] = useState<string | null>(
    value && value.startsWith('data:image') ? value : null
  )

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    // Validate file size
    if (file.size > maxSize) {
      const errorMsg = `File size must be less than ${(maxSize / 1024 / 1024).toFixed(1)}MB`
      if (onError) {
        onError(errorMsg)
      }
      return
    }

    // Validate file type
    if (accept && !accept.includes('*')) {
      const acceptedTypes = accept.split(',').map(t => t.trim())
      const fileType = file.type
      const isValidType = acceptedTypes.some(type => {
        if (type.endsWith('/*')) {
          return fileType.startsWith(type.slice(0, -2))
        }
        return fileType === type
      })
      if (!isValidType) {
        const errorMsg = `File type not supported. Accepted types: ${accept}`
        if (onError) {
          onError(errorMsg)
        }
        return
      }
    }

    // Show preview only for images
    if (file.type.startsWith('image/')) {
      const reader = new FileReader()
      reader.onloadend = () => {
        setPreview(reader.result as string)
      }
      reader.readAsDataURL(file)
    } else {
      // Don't show preview for non-image files (videos, documents, etc.)
      setPreview(null)
    }

    // Upload file if onUpload function is provided
    if (onUpload) {
      setUploading(true)
      try {
        const url = await onUpload(file)
        // Only set preview for images
        if (file.type.startsWith('image/')) {
          setPreview(url)
        }
        onChange?.(url)
      } catch (err: any) {
        const errorMsg = `Upload failed: ${err.message || 'Unknown error'}`
        if (onError) {
          onError(errorMsg)
        }
        setPreview(null)
      } finally {
        setUploading(false)
        // Reset input
        if (fileInputRef.current) {
          fileInputRef.current.value = ''
        }
      }
    }
  }

  const handleRemove = () => {
    setPreview(null)
    onChange?.('')
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  // Update preview when value changes (only for images)
  React.useEffect(() => {
    if (value && value.startsWith('data:image')) {
      setPreview(value)
    } else if (!value) {
      setPreview(null)
    }
  }, [value])

  const handleClick = () => {
    if (!disabled && !uploading) {
      fileInputRef.current?.click()
    }
  }

  return (
    <div className={cn('space-y-2', className)}>
      <label className="block text-sm font-medium text-gray-700">
        {label}
        {required && <span className="text-red-500 ml-1">*</span>}
      </label>

      <div className="space-y-2">
        {/* Preview - only for images */}
        {preview && preview.startsWith('data:image') && (
          <div className="relative inline-block">
            <div className="relative">
              <img
                src={preview}
                alt="Preview"
                className="h-32 w-32 object-cover rounded-lg border border-gray-300"
              />
                {!disabled && (
                  <button
                    type="button"
                    onClick={handleRemove}
                    className="absolute -top-2 -right-2 bg-red-500 text-white rounded-full w-6 h-6 flex items-center justify-center hover:bg-red-600 transition-colors"
                  >
                    <svg
                      className="w-4 h-4"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M6 18L18 6M6 6l12 12"
                      />
                    </svg>
                  </button>
                )}
              </div>
          </div>
        )}

        {/* Upload Button */}
        <div
          onClick={handleClick}
          className={cn(
            'border-2 border-dashed rounded-lg p-4 cursor-pointer transition-colors',
            error
              ? 'border-red-300 bg-red-50'
              : 'border-gray-300 hover:border-primary-400 hover:bg-primary-50',
            disabled || uploading ? 'opacity-50 cursor-not-allowed' : ''
          )}
        >
          <input
            ref={fileInputRef}
            type="file"
            accept={accept}
            onChange={handleFileSelect}
            className="hidden"
            disabled={disabled || uploading}
          />
          <div className="text-center">
            {uploading ? (
              <div className="flex flex-col items-center">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600 mb-2"></div>
                <p className="text-sm text-gray-600">Uploading...</p>
              </div>
            ) : (
              <>
                <svg
                  className="mx-auto h-12 w-12 text-gray-400"
                  stroke="currentColor"
                  fill="none"
                  viewBox="0 0 48 48"
                >
                  <path
                    d="M28 8H12a4 4 0 00-4 4v20m32-12v8m0 0v8a4 4 0 01-4 4H12a4 4 0 01-4-4v-4m32-4l-3.172-3.172a4 4 0 00-5.656 0L28 28M8 32l9.172-9.172a4 4 0 015.656 0L28 28m0 0l4 4m4-24h8m-4-4v8m-12 4h.02"
                    strokeWidth={2}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
                <div className="mt-2">
                  <p className="text-sm text-gray-600">
                    <span className="font-medium text-primary-600 hover:text-primary-500">
                      Click to upload
                    </span>{' '}
                    or drag and drop
                  </p>
                  <p className="text-xs text-gray-500 mt-1">
                    {accept.includes('image') ? 'PNG, JPG, GIF up to' : 'File up to'}{' '}
                    {(maxSize / 1024 / 1024).toFixed(1)}MB
                  </p>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {error && (
        <p className="text-sm text-red-600">{error}</p>
      )}
      {helperText && !error && (
        <p className="text-sm text-gray-500">{helperText}</p>
      )}
    </div>
  )
}


