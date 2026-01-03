'use client'

import { useState } from 'react'
import { usePathname } from 'next/navigation'
import { Sidebar, SidebarToggle } from './Sidebar'
import { TenantSelector } from './TenantSelector'
import { useAuth } from '@edudron/shared-utils'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Bell, X } from 'lucide-react'

interface AppLayoutProps {
  children: React.ReactNode
}

// Function to get page title based on pathname
function getPageTitle(pathname: string): string {
  if (pathname === '/' || pathname === '/dashboard') return 'Dashboard'
  if (pathname === '/tenants') return 'Tenant Management'
  if (pathname === '/users') return 'User Management'
  if (pathname.startsWith('/super-admin')) {
    if (pathname === '/super-admin') return 'System Management'
    if (pathname === '/super-admin/settings') return 'System Settings'
    if (pathname === '/super-admin/database') return 'Database Management'
    return 'System Management'
  }
  if (pathname === '/institutes') return 'Institutes'
  if (pathname.startsWith('/institutes')) return 'Institute Details'
  if (pathname === '/classes') return 'Classes'
  if (pathname.startsWith('/classes')) return 'Class Details'
  if (pathname === '/courses') return 'Courses'
  if (pathname.startsWith('/courses')) {
    if (pathname === '/courses/generate') return 'Generate Course'
    if (pathname === '/course-index') return 'Course Index'
    return 'Course Details'
  }
  if (pathname === '/enrollments') return 'Enrollments'
  if (pathname === '/payments') return 'Payments'
  if (pathname === '/settings') return 'Settings'
  return 'Dashboard'
}

// Function to get page subtitle based on pathname
function getPageSubtitle(pathname: string): string {
  if (pathname === '/' || pathname === '/dashboard') return 'Welcome to your EduDron admin dashboard'
  if (pathname === '/tenants') return 'Manage all tenants in the system'
  if (pathname === '/users') return 'Manage users with system-wide roles'
  if (pathname.startsWith('/super-admin')) {
    if (pathname === '/super-admin') return 'System-wide management and administration'
    if (pathname === '/super-admin/settings') return 'Configure global application settings'
    if (pathname === '/super-admin/database') return 'Database maintenance and migrations'
    return 'System-wide management and administration'
  }
  if (pathname === '/institutes') return 'Manage educational institutes'
  if (pathname.startsWith('/institutes')) return 'View and edit institute details'
  if (pathname === '/classes') return 'Manage classes'
  if (pathname.startsWith('/classes')) return 'View and edit class details'
  if (pathname === '/courses') return 'Manage your course catalog'
  if (pathname.startsWith('/courses')) {
    if (pathname === '/courses/generate') return 'Generate courses using AI'
    if (pathname === '/course-index') return 'Manage course generation indexes'
    return 'View and edit course details'
  }
  if (pathname === '/enrollments') return 'Manage student enrollments'
  if (pathname === '/payments') return 'Manage payment transactions'
  if (pathname === '/settings') return 'Configure system settings'
  return 'Welcome to your EduDron admin dashboard'
}

export function AppLayout({ children }: AppLayoutProps) {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false)
  const { user, logout } = useAuth()
  const pathname = usePathname()
  const pageTitle = getPageTitle(pathname)
  const pageSubtitle = getPageSubtitle(pathname)

  const toggleSidebar = () => {
    setIsSidebarOpen(!isSidebarOpen)
  }

  return (
    <div className="min-h-screen bg-background flex">
      {/* Sidebar */}
      <Sidebar isOpen={isSidebarOpen} onToggle={toggleSidebar} />
      
      {/* Main content area */}
      <div className="flex-1 flex flex-col ml-0 lg:ml-64">
        {/* Top Header - Fixed */}
        <header className="fixed top-0 right-0 left-0 lg:left-64 z-30 bg-card border-b shadow-sm bg-gradient-to-r from-primary/5 to-accent/5">
          <div className="flex items-center justify-between px-4 py-3">
            <div className="flex items-center space-x-4">
              <SidebarToggle onToggle={toggleSidebar} />
              <div className="flex-1">
                <h1 className="text-lg font-semibold text-primary">{pageTitle}</h1>
                <p className="text-sm text-muted-foreground">{pageSubtitle}</p>
              </div>
              <TenantSelector />
            </div>
            
            <div className="flex items-center space-x-4">
              {/* Notifications */}
              <Button variant="ghost" size="sm" className="relative hover:bg-primary/10 hover:text-primary transition-colors">
                <Bell className="h-4 w-4" />
                <span className="absolute -top-1 -right-1 h-3 w-3 bg-accent rounded-full text-xs flex items-center justify-center text-white">
                  0
                </span>
              </Button>
              
              {/* User menu */}
              <div className="flex items-center space-x-2">
                <Avatar className="h-8 w-8 ring-2 ring-primary/20">
                  <AvatarImage src="" />
                  <AvatarFallback className="bg-primary text-primary-foreground">
                    {user?.name ? user.name.charAt(0).toUpperCase() : 'A'}
                  </AvatarFallback>
                </Avatar>
                <div className="hidden sm:block">
                  <p className="text-sm font-medium">{user?.name || 'Admin User'}</p>
                  <p className="text-xs text-muted-foreground">{user?.email || 'admin@edudron.com'}</p>
                </div>
                <Button 
                  variant="ghost" 
                  size="sm" 
                  onClick={async () => {
                    await logout()
                    window.location.href = '/login'
                  }} 
                  className="hover:bg-primary/10 hover:text-primary transition-colors"
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>
        </header>

        {/* Page Content - Scrollable */}
        <main className="flex-1 pt-20 overflow-y-auto">
          <div className="p-6">
            {children}
          </div>
        </main>
      </div>
    </div>
  )
}

