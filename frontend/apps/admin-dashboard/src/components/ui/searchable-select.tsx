'use client'

import { useState, useRef, useEffect } from 'react'
import { Check, ChevronsUpDown, Search, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

export interface SearchableSelectOption {
  value: string
  label: string
  searchText?: string // Optional: text to search in addition to label
}

interface SearchableSelectProps {
  options: SearchableSelectOption[]
  value?: string
  onValueChange: (value: string) => void
  placeholder?: string
  disabled?: boolean
  emptyMessage?: string
  className?: string
  onSearch?: (query: string) => void // Optional: callback when user searches
  loading?: boolean // Optional: show loading state
}

export function SearchableSelect({
  options,
  value,
  onValueChange,
  placeholder = 'Select...',
  disabled = false,
  emptyMessage = 'No options found',
  className,
  onSearch,
  loading = false,
}: SearchableSelectProps) {
  const [open, setOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  const selectedOption = options.find(opt => opt.value === (value || ''))

  // Filter options based on search query
  const filteredOptions = options.filter(option => {
    if (!searchQuery.trim()) return true
    const query = searchQuery.toLowerCase()
    const labelMatch = option.label.toLowerCase().includes(query)
    const searchTextMatch = option.searchText?.toLowerCase().includes(query)
    return labelMatch || searchTextMatch
  })

  // Focus search input when popover opens
  useEffect(() => {
    if (open && inputRef.current) {
      setTimeout(() => {
        inputRef.current?.focus()
      }, 100)
    } else {
      setSearchQuery('') // Clear search when closed
    }
  }, [open])

  const handleSelect = (optionValue: string) => {
    onValueChange(optionValue)
    setOpen(false)
    setSearchQuery('')
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className={cn(
            'w-full justify-between',
            !selectedOption && 'text-muted-foreground',
            className
          )}
          disabled={disabled}
        >
          <span className="truncate">
            {selectedOption 
              ? selectedOption.label 
              : (value === '' && options.some(opt => opt.value === '')) 
                ? 'None' 
                : placeholder}
          </span>
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[var(--radix-popover-trigger-width)] p-0" align="start" sideOffset={4}>
        <div className="flex items-center border-b px-3">
          <Search className="mr-2 h-4 w-4 shrink-0 opacity-50" />
          <Input
            ref={inputRef}
            placeholder="Search..."
            value={searchQuery}
            onChange={(e) => {
              const newQuery = e.target.value
              setSearchQuery(newQuery)
              // Call onSearch callback if provided (for server-side search)
              if (onSearch) {
                onSearch(newQuery)
              }
            }}
            className="border-0 focus-visible:ring-0 focus-visible:ring-offset-0"
          />
          {searchQuery && (
            <Button
              variant="ghost"
              size="sm"
              className="h-6 w-6 p-0"
              onClick={(e) => {
                e.stopPropagation()
                setSearchQuery('')
                inputRef.current?.focus()
              }}
            >
              <X className="h-4 w-4" />
            </Button>
          )}
        </div>
        <div className="max-h-[300px] overflow-auto p-1">
          {loading ? (
            <div className="py-6 text-center text-sm text-muted-foreground">
              Loading...
            </div>
          ) : filteredOptions.length === 0 ? (
            <div className="py-6 text-center text-sm text-muted-foreground">
              {emptyMessage}
            </div>
          ) : (
            filteredOptions.map((option) => (
              <button
                key={option.value}
                className={cn(
                  'relative flex w-full cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none',
                  'hover:bg-accent hover:text-accent-foreground',
                  'focus:bg-accent focus:text-accent-foreground',
                  (value || '') === option.value && 'bg-accent text-accent-foreground'
                )}
                onClick={() => handleSelect(option.value)}
              >
                <Check
                  className={cn(
                    'mr-2 h-4 w-4',
                    (value || '') === option.value ? 'opacity-100' : 'opacity-0'
                  )}
                />
                <span className="truncate">{option.label}</span>
              </button>
            ))
          )}
        </div>
      </PopoverContent>
    </Popover>
  )
}
