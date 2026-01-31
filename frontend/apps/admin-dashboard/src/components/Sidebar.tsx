'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useAuth } from '@kunal-ak23/edudron-shared-utils'
import { 
  LayoutDashboard, 
  BookOpen, 
  Users, 
  GraduationCap,
  Building2,
  Settings,
  LogOut,
  Menu,
  X,
  ChevronDown,
  ChevronRight,
  FileText,
  Shield,
  UserCog,
  Database,
  CreditCard,
  Sparkles,
  ClipboardList,
  BarChart3
} from 'lucide-react'

interface MenuItem {
  name: string
  href: string
  icon: React.ComponentType<{ className?: string }>
  badge?: string
  children?: MenuItem[]
  requiresTenant?: boolean
  superAdminOnly?: boolean
}

const menuItems: MenuItem[] = [
  {
    name: 'Dashboard',
    href: '/dashboard',
    icon: LayoutDashboard,
  },
  // Super Admin Only - System Management
  {
    name: 'System Management',
    href: '/super-admin',
    icon: Shield,
    superAdminOnly: true,
    children: [
      {
        name: 'Tenant Management',
        href: '/tenants',
        icon: Building2,
      },
      {
        name: 'System Users',
        href: '/users',
        icon: UserCog,
      },
      {
        name: 'System Settings',
        href: '/super-admin/settings',
        icon: Settings,
      },
      {
        name: 'Database Management',
        href: '/super-admin/database',
        icon: Database,
      },
    ]
  },
  // Tenant-specific items
  {
    name: 'Institutes',
    href: '/institutes',
    icon: Building2,
    requiresTenant: true,
  },
  {
    name: 'Courses',
    href: '/courses',
    icon: BookOpen,
    requiresTenant: true,
    children: [
      { name: 'All Courses', href: '/courses', icon: BookOpen },
      { name: 'Generate Course', href: '/courses/generate', icon: Sparkles },
      { name: 'Course Index', href: '/course-index', icon: FileText },
    ]
  },
  {
    name: 'Students',
    href: '/students',
    icon: Users,
    requiresTenant: true,
    children: [
      { name: 'All Students', href: '/students', icon: Users },
      { name: 'Bulk Import', href: '/students/import', icon: Users },
    ]
  },
  {
    name: 'Exams',
    href: '/exams',
    icon: ClipboardList,
    requiresTenant: true,
    children: [
      { name: 'All Exams', href: '/exams', icon: ClipboardList },
      { name: 'Question Bank', href: '/question-bank', icon: Database },
      { name: 'Exam Results', href: '/exams/results', icon: BarChart3 },
    ]
  },
  {
    name: 'Enrollments',
    href: '/enrollments',
    icon: Users,
    requiresTenant: true,
  },
  {
    name: 'Analytics',
    href: '/analytics',
    icon: BarChart3,
    requiresTenant: true,
  },
  {
    name: 'Payments',
    href: '/payments',
    icon: CreditCard,
    requiresTenant: true,
  },
  {
    name: 'Settings',
    href: '/settings',
    icon: Settings,
    requiresTenant: true,
  },
  {
    name: 'Profile',
    href: '/profile',
    icon: UserCog,
  },
]

interface SidebarProps {
  isOpen: boolean
  onToggle: () => void
  collapsed?: boolean
  onCollapseToggle?: () => void
}

export function Sidebar({ isOpen, onToggle, collapsed = false, onCollapseToggle }: SidebarProps) {
  const pathname = usePathname()
  const { user } = useAuth()
  
  // Filter menu items based on user role and tenant selection
  const filteredMenuItems = menuItems
    .map(item => {
      const isSystemAdmin = user?.role === 'SYSTEM_ADMIN'
      const isTenantAdmin = user?.role === 'TENANT_ADMIN'
      const isContentManager = user?.role === 'CONTENT_MANAGER'
      const isInstructor = user?.role === 'INSTRUCTOR'
      const isSupportStaff = user?.role === 'SUPPORT_STAFF'
      
      // Filter children items for INSTRUCTOR/SUPPORT_STAFF
      if ((isInstructor || isSupportStaff) && item.children) {
        const filteredChildren = item.children.filter(child => {
          // Hide "Generate Course" for view-only roles
          if (child.href === '/courses/generate') {
            return false
          }
          return true
        })
        // Return item with filtered children
        return { ...item, children: filteredChildren }
      }
      return item
    })
    .filter(item => {
      const isSystemAdmin = user?.role === 'SYSTEM_ADMIN'
      const isTenantAdmin = user?.role === 'TENANT_ADMIN'
      const isContentManager = user?.role === 'CONTENT_MANAGER'
      const isInstructor = user?.role === 'INSTRUCTOR'
      const isSupportStaff = user?.role === 'SUPPORT_STAFF'
      const canManageUsers = isSystemAdmin || isTenantAdmin
      
      // Show super admin only items only for SYSTEM_ADMIN users
      if (item.superAdminOnly && !isSystemAdmin) {
        return false
      }
      
      // Hide tenant management for non-SYSTEM_ADMIN users
      if (item.href === '/tenants' && !isSystemAdmin) {
        return false
      }
      
      // Hide user management for users who cannot manage users
      // CONTENT_MANAGER can view users but not manage them, so we'll show the link but they'll be redirected
      // Allow CONTENT_MANAGER to see users link (read-only access)
      // INSTRUCTOR and SUPPORT_STAFF cannot access user management
      if (item.href === '/users' && !canManageUsers && !isContentManager) {
        return false
      }
      
      // CONTENT_MANAGER restrictions: Hide student, enrollment management, and settings
      if (isContentManager) {
        if (item.href === '/students' || item.href === '/enrollments' || item.href === '/settings') {
          return false
        }
        // CONTENT_MANAGER can access courses and analytics, but not student management or settings
      }
      
      // INSTRUCTOR and SUPPORT_STAFF restrictions: View-only access
      // Hide user, student, enrollment management, and settings
      if (isInstructor || isSupportStaff) {
        if (item.href === '/users' || item.href === '/students' || item.href === '/enrollments' || item.href === '/settings') {
          return false
        }
        // Hide "Generate Course" submenu for INSTRUCTOR/SUPPORT_STAFF (view-only)
        if (item.href === '/courses/generate') {
          return false
        }
        // If all children are filtered out, hide the parent item
        if (item.children && item.children.length === 0) {
          return false
        }
        // INSTRUCTOR can view Dashboard (for student progress), Courses (view-only), Analytics
      }
      
      // Show tenant-specific items only if user has a tenant selected
      if (item.requiresTenant) {
        const tenantId = localStorage.getItem('tenant_id') || 
                        localStorage.getItem('clientId') || 
                        localStorage.getItem('selectedTenantId')
        return tenantId !== null && 
               tenantId !== 'PENDING_TENANT_SELECTION' && 
               tenantId !== 'SYSTEM'
      }
      
      // Show all other items (like Dashboard)
      return true
    })
  
  // Function to get the parent section name based on pathname
  const getParentSection = (path: string) => {
    if (path.startsWith('/super-admin')) return 'System Management'
    if (path.startsWith('/institutes')) return 'Institutes'
    if (path.startsWith('/courses')) return 'Courses'
    if (path.startsWith('/exams') || path.startsWith('/question-bank')) return 'Exams'
    if (path.startsWith('/enrollments')) return 'Enrollments'
    if (path.startsWith('/analytics')) return 'Analytics'
    if (path.startsWith('/payments')) return 'Payments'
    if (path.startsWith('/settings')) return 'Settings'
    if (path === '/dashboard' || path === '/') return 'Dashboard'
    return null
  }
  
  // Initialize expanded items with the current section
  const [expandedItems, setExpandedItems] = useState<string[]>(() => {
    const parentSection = getParentSection(pathname)
    return parentSection ? [parentSection] : ['Dashboard']
  })

  // Update expanded items when pathname changes
  useEffect(() => {
    const parentSection = getParentSection(pathname)
    if (parentSection && !expandedItems.includes(parentSection)) {
      setExpandedItems([...expandedItems, parentSection])
    }
  }, [pathname])

  const toggleExpanded = (itemName: string) => {
    setExpandedItems(prev => 
      prev.includes(itemName) 
        ? prev.filter(name => name !== itemName)
        : [...prev, itemName]
    )
  }

  const isActive = (href: string) => {
    if (href === '/dashboard') {
      return pathname === '/dashboard' || pathname === '/'
    }
    return pathname.startsWith(href)
  }

  const renderMenuItem = (item: MenuItem, level = 0) => {
    const hasChildren = item.children && item.children.length > 0
    const isExpanded = expandedItems.includes(item.name)
    const isItemActive = isActive(item.href)

    return (
      <div key={item.name}>
        <div className="flex items-center">
          {hasChildren ? (
            <Button
              variant="ghost"
              className={cn(
                'w-full justify-start text-left font-normal h-8 px-3 transition-colors',
                level > 0 && 'ml-4',
                isItemActive && 'bg-primary/10 text-primary border-l-2 border-primary',
                'hover:bg-primary/5 hover:text-primary'
              )}
              onClick={() => toggleExpanded(item.name)}
            >
              <item.icon className="h-4 w-4 mr-3" />
              <span className="flex-1">{item.name}</span>
              {item.badge && (
                <Badge variant="secondary" className="ml-2 text-xs">
                  {item.badge}
                </Badge>
              )}
              {isExpanded ? (
                <ChevronDown className="h-4 w-4 ml-2" />
              ) : (
                <ChevronRight className="h-4 w-4 ml-2" />
              )}
            </Button>
          ) : (
            <Button
              variant="ghost"
              asChild
              className={cn(
                'w-full justify-start text-left font-normal h-8 px-3 transition-colors',
                level > 0 && 'ml-4',
                isItemActive && 'bg-primary/10 text-primary border-l-2 border-primary',
                'hover:bg-primary/5 hover:text-primary'
              )}
            >
              <Link href={item.href}>
                <item.icon className="h-4 w-4 mr-3" />
                <span className="flex-1">{item.name}</span>
                {item.badge && (
                  <Badge variant="secondary" className="ml-2 text-xs">
                    {item.badge}
                  </Badge>
                )}
              </Link>
            </Button>
          )}
        </div>
        
        {hasChildren && isExpanded && (
          <div className="ml-2 mt-1 space-y-1">
            {item.children!.map(child => renderMenuItem(child, level + 1))}
          </div>
        )}
      </div>
    )
  }

  return (
    <>
      {/* Mobile overlay */}
      {isOpen && (
        <div 
          className="fixed inset-0 bg-black/50 z-40 lg:hidden"
          onClick={onToggle}
        />
      )}
      
      {/* Sidebar */}
      <div className={cn(
        'fixed left-0 top-0 bottom-0 z-50 transform transition-all duration-300 ease-in-out flex flex-col',
        'bg-card',
        collapsed ? 'w-0 border-0 pointer-events-none overflow-hidden' : 'w-64 border-r',
        isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
      )}>
        {!collapsed && (
          <div className="w-full h-screen flex flex-col overflow-hidden">
            {/* Toggle Menu Button - Right edge of sidebar */}
            {onCollapseToggle && (
              <button
                onClick={onCollapseToggle}
                className="absolute right-0 top-1/2 transform -translate-y-1/2 translate-x-full z-30 bg-white hover:bg-gray-50 rounded-r-lg px-2 py-4 shadow-md border-r border-t border-b border-gray-200"
                aria-label="Toggle menu"
                style={{ right: '-1px' }}
              >
                <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
              </button>
            )}
            
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b flex-shrink-0 bg-primary/5">
              <div className="flex items-center space-x-2">
                <div className="h-8 w-8 rounded-lg bg-primary flex items-center justify-center shadow-sm">
                  <span className="text-primary-foreground font-bold text-sm">E</span>
                </div>
                <span className="text-xl font-bold text-primary">EduDron</span>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={onToggle}
                className="lg:hidden hover:bg-primary/10"
              >
                <X className="h-4 w-4" />
              </Button>
            </div>

            {/* Navigation */}
            <nav className="flex-1 overflow-y-auto p-4 space-y-1 min-h-0">
              {filteredMenuItems.map(item => renderMenuItem(item))}
            </nav>

          </div>
        )}
      </div>
      
      {/* Toggle button when sidebar is collapsed */}
      {collapsed && onCollapseToggle && (
        <button
          onClick={onCollapseToggle}
          className="fixed left-0 top-1/2 transform -translate-y-1/2 z-30 bg-white border-l border-t border-b border-gray-200 rounded-r-lg px-2 py-4 shadow-md hover:bg-gray-50"
        >
          <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
        </button>
      )}
    </>
  )
}

export function SidebarToggle({ onToggle }: { onToggle: () => void }) {
  return (
    <Button
      variant="ghost"
      size="sm"
      className="lg:hidden hover:bg-primary/10 hover:text-primary transition-colors"
      onClick={onToggle}
    >
      <Menu className="h-4 w-4" />
    </Button>
  )
}

