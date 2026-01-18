'use client'

export interface RecommendedCourse {
  courseId: string
  title?: string
  description?: string
  reason?: string
}

export function RecommendedCourses({ courses }: { courses: RecommendedCourse[] }) {
  if (!courses || courses.length === 0) return null

  return (
    <div className="space-y-3">
      {courses.map((c) => (
        <div key={c.courseId} className="border border-gray-200 rounded-lg p-4">
          <div className="font-semibold text-gray-900">{c.title || c.courseId}</div>
          {c.reason && <div className="text-sm text-gray-600 mt-1">{c.reason}</div>}
          {c.description && <div className="text-sm text-gray-700 mt-2">{c.description}</div>}
        </div>
      ))}
    </div>
  )
}

