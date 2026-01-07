import React from 'react'
import { cn } from '../utils/cn'

export interface FilterOption {
  label: string
  value: string
}

export interface FilterBarProps {
  filters: {
    difficulty?: FilterOption[]
    category?: FilterOption[]
    price?: FilterOption[]
  }
  selectedFilters: {
    difficulty?: string
    category?: string
    price?: string
  }
  onFilterChange: (filterType: string, value: string) => void
  className?: string
}

export default function FilterBar({
  filters,
  selectedFilters,
  onFilterChange,
  className
}: FilterBarProps) {
  return (
    <div className={cn('flex flex-wrap gap-4 p-4 bg-gray-50 rounded-lg', className)}>
      {filters.difficulty && filters.difficulty.length > 0 && (
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Difficulty
          </label>
          <select
            value={selectedFilters.difficulty || ''}
            onChange={(e) => onFilterChange('difficulty', e.target.value)}
            className="block w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-primary-500 focus:border-primary-500"
          >
            <option value="">All Levels</option>
            {filters.difficulty.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      )}

      {filters.category && filters.category.length > 0 && (
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Category
          </label>
          <select
            value={selectedFilters.category || ''}
            onChange={(e) => onFilterChange('category', e.target.value)}
            className="block w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-primary-500 focus:border-primary-500"
          >
            <option value="">All Categories</option>
            {filters.category.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      )}

      {filters.price && filters.price.length > 0 && (
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Price
          </label>
          <select
            value={selectedFilters.price || ''}
            onChange={(e) => onFilterChange('price', e.target.value)}
            className="block w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-primary-500 focus:border-primary-500"
          >
            <option value="">All Prices</option>
            {filters.price.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      )}
    </div>
  )
}


