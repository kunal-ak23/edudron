/**
 * Extracts error message from various error formats
 */
export function extractErrorMessage(error: any): string {
  // Handle axios error response
  if (error?.response?.data) {
    const data = error.response.data
    
    // Handle structured error response: { code, error, status }
    if (data.error && typeof data.error === 'string') {
      return data.error
    }
    
    // Handle error message field
    if (data.message && typeof data.message === 'string') {
      return data.message
    }
    
    // Handle error object with message
    if (data.error?.message) {
      return data.error.message
    }
    
    // Handle array of errors
    if (Array.isArray(data.errors) && data.errors.length > 0) {
      return data.errors.map((e: any) => e.message || e).join(', ')
    }
    
    // Try to stringify if it's an object
    if (typeof data === 'object') {
      try {
        const errorStr = JSON.stringify(data)
        if (errorStr !== '{}') {
          return errorStr
        }
      } catch {
        // Ignore JSON stringify errors
      }
    }
  }
  
  // Handle direct error message
  if (error?.message) {
    return error.message
  }
  
  // Handle error string
  if (typeof error === 'string') {
    return error
  }
  
  // Default fallback
  return 'An unexpected error occurred. Please try again.'
}


