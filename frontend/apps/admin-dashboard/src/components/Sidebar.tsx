'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useAuth } from '@edudron/shared-utils'
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
  Sparkles
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
    name: 'Classes',
    href: '/classes',
    icon: GraduationCap,
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
    name: 'Enrollments',
    href: '/enrollments',
    icon: Users,
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
]

interface SidebarProps {
  isOpen: boolean
  onToggle: () => void
}

export function Sidebar({ isOpen, onToggle }: SidebarProps) {
  const pathname = usePathname()
  const { user } = useAuth()
  
  // Filter menu items based on user role and tenant selection
  const filteredMenuItems = menuItems.filter(item => {
    // Show super admin only items only for SYSTEM_ADMIN users
    if (item.superAdminOnly && user?.role !== 'SYSTEM_ADMIN') {
      return false
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
    if (path.startsWith('/classes')) return 'Classes'
    if (path.startsWith('/courses')) return 'Courses'
    if (path.startsWith('/enrollments')) return 'Enrollments'
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
        'fixed left-0 top-0 z-50 h-full w-64 border-r transform transition-transform duration-300 ease-in-out flex flex-col',
        'bg-card',
        isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
      )}>
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

        {/* Footer */}
        <div className="p-4 border-t flex-shrink-0 bg-primary/5">
          <Button
            variant="ghost"
            className="w-full justify-start text-left font-normal h-8 px-3 text-muted-foreground hover:text-primary hover:bg-primary/10 transition-colors"
            onClick={async () => {
              const { useAuth } = await import('@edudron/shared-utils')
              // Note: This won't work directly in the component, need to use hook properly
              // For now, just redirect and clear localStorage
              localStorage.removeItem('auth_token')
              localStorage.removeItem('refresh_token')
              localStorage.removeItem('tenant_id')
              localStorage.removeItem('clientId')
              localStorage.removeItem('selectedTenantId')
              localStorage.removeItem('user')
              localStorage.removeItem('available_tenants')
              window.location.href = '/login'
            }}
          >
            <LogOut className="h-4 w-4 mr-3" />
            <span>Sign Out</span>
          </Button>
        </div>
      </div>
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

