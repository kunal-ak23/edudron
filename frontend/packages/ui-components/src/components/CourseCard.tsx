import React from 'react'
import { cn } from '../utils/cn'

export interface CourseCardProps {
  course: {
    id: string
    title: string
    description?: string
    thumbnailUrl?: string
    isFree?: boolean
    pricePaise?: number
    currency?: string
    difficultyLevel?: string
    totalDurationSeconds?: number
    totalLecturesCount?: number
    totalStudentsCount?: number
    instructors?: Array<{ name: string }>
    tags?: string[]
  }
  onClick?: () => void
  className?: string
}

export default function CourseCard({ course, onClick, className }: CourseCardProps) {
  const formatPrice = () => {
    if (course.isFree) return 'Free'
    if (course.pricePaise) {
      return `₹${(course.pricePaise / 100).toLocaleString('en-IN')}`
    }
    return 'Free'
  }

  const formatDuration = () => {
    if (!course.totalDurationSeconds) return null
    const hours = Math.floor(course.totalDurationSeconds / 3600)
    const minutes = Math.floor((course.totalDurationSeconds % 3600) / 60)
    if (hours > 0) {
      return `${hours}h ${minutes}m`
    }
    return `${minutes}m`
  }

  const instructorNames = course.instructors?.map(i => i.name).join(', ') || 'Instructor'

  return (
    <div
      className={cn(
        'bg-white rounded-lg shadow-sm hover:shadow-lg transition-shadow cursor-pointer overflow-hidden border border-gray-200',
        className
      )}
      onClick={onClick}
    >
      {/* Course Image */}
      <div className="relative w-full h-40 bg-gradient-to-br from-blue-500 to-purple-600">
        {course.thumbnailUrl ? (
          <img
            src={course.thumbnailUrl}
            alt={course.title}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-white text-2xl font-bold">
            {course.title.charAt(0).toUpperCase()}
          </div>
        )}
        {course.isFree && (
          <div className="absolute top-2 right-2 bg-green-500 text-white px-2 py-1 rounded text-xs font-semibold">
            FREE
          </div>
        )}
      </div>

      {/* Course Content */}
      <div className="p-4">
        {/* Difficulty Badge */}
        {course.difficultyLevel && (
          <div className="mb-2">
            <span className="inline-block px-2 py-1 bg-blue-100 text-blue-700 text-xs font-medium rounded">
              {course.difficultyLevel}
            </span>
          </div>
        )}

        {/* Course Title */}
        <h3 className="font-semibold text-gray-900 text-lg mb-2 line-clamp-2 min-h-[3.5rem]">
          {course.title}
        </h3>

        {/* Instructor */}
        <p className="text-sm text-gray-600 mb-3 line-clamp-1">
          {instructorNames}
        </p>

        {/* Course Stats */}
        <div className="flex items-center gap-4 text-xs text-gray-500 mb-3">
          {course.totalLecturesCount && (
            <span>{course.totalLecturesCount} lectures</span>
          )}
          {formatDuration() && <span>{formatDuration()}</span>}
          {course.totalStudentsCount && (
            <span>{course.totalStudentsCount.toLocaleString()} students</span>
          )}
        </div>

        {/* Tags */}
        {course.tags && course.tags.length > 0 && (
          <div className="flex flex-wrap gap-1 mb-3">
            {course.tags.slice(0, 2).map((tag, idx) => (
              <span
                key={idx}
                className="px-2 py-0.5 bg-gray-100 text-gray-600 text-xs rounded"
              >
                {tag}
              </span>
            ))}
          </div>
        )}

        {/* Price */}
        <div className="flex items-center justify-between pt-2 border-t border-gray-100">
          <span className="text-lg font-bold text-gray-900">{formatPrice()}</span>
          {!course.isFree && course.pricePaise && (
            <span className="text-sm text-gray-500 line-through">
              ₹{((course.pricePaise * 1.2) / 100).toLocaleString('en-IN')}
            </span>
          )}
        </div>
      </div>
    </div>
  )
}

