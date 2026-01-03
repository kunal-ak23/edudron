# EduDron Frontend Setup Guide

## Quick Setup

### 1. Prerequisites

- Node.js 18+ installed
- Backend services running (Gateway on port 8080)
- npm or yarn package manager

### 2. Install Dependencies

```bash
# Navigate to frontend directory
cd frontend

# Install root dependencies
npm install

# Build shared packages first
cd packages/ui-components
npm install
npm run build

cd ../shared-utils
npm install
npm run build
cd ../..
```

### 3. Configure Environment Variables

Create `.env.local` files in each app:

**apps/admin-dashboard/.env.local:**
```bash
NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080
```

**apps/student-portal/.env.local:**
```bash
NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080
```

### 4. Install App Dependencies

```bash
# Admin Dashboard
cd apps/admin-dashboard
npm install
cd ../..

# Student Portal
cd apps/student-portal
npm install
cd ../..
```

### 5. Start Development Servers

**Option 1: Start all apps (from frontend root)**
```bash
npm run dev
```

**Option 2: Start individually**

Terminal 1 - Admin Dashboard:
```bash
cd apps/admin-dashboard
npm run dev
```

Terminal 2 - Student Portal:
```bash
cd apps/student-portal
npm run dev
```

### 6. Access Applications

- **Admin Dashboard**: http://localhost:3000
- **Student Portal**: http://localhost:3001

## First Time Login

### Admin Dashboard

1. Navigate to http://localhost:3000
2. You'll be redirected to `/login`
3. Use your admin credentials (created via backend script)
4. After login, you'll see the dashboard

### Student Portal

1. Navigate to http://localhost:3001
2. Click "Sign up" to create a new account
3. Or use existing student credentials to login
4. Browse and enroll in courses

## Troubleshooting

### Port Already in Use

If port 3000 or 3001 is already in use:

1. Find the process: `lsof -i :3000` or `lsof -i :3001`
2. Kill the process: `kill -9 <PID>`
3. Or change the port in `package.json`: `"dev": "next dev -p 3002"`

### API Connection Errors

1. Verify Gateway is running: `curl http://localhost:8080/actuator/health`
2. Check environment variables are set correctly
3. Verify CORS is enabled on Gateway
4. Check browser console for detailed error messages

### Build Errors

If shared packages fail to build:

```bash
# Clean and rebuild
cd packages/ui-components
rm -rf node_modules dist
npm install
npm run build

cd ../shared-utils
rm -rf node_modules dist
npm install
npm run build
```

### Module Resolution Errors

If you see "Cannot find module '@edudron/...'":

1. Ensure packages are built: `cd packages/ui-components && npm run build`
2. Restart the dev server
3. Clear Next.js cache: `rm -rf .next`

## Development Workflow

### Making Changes to Shared Packages

1. Edit files in `packages/ui-components` or `packages/shared-utils`
2. Rebuild the package: `npm run build`
3. Changes will be reflected in apps (may need to restart dev server)

### Adding New Pages

1. Create page file in `apps/[app-name]/src/app/[route]/page.tsx`
2. Use shared components from `@edudron/ui-components`
3. Use API clients from `@edudron/shared-utils`

### Testing API Integration

1. Ensure backend services are running
2. Check Network tab in browser DevTools
3. Verify requests go to `http://localhost:8080`
4. Check for CORS errors in console

## Production Build

```bash
# Build all packages and apps
npm run build

# Or build individually
cd apps/admin-dashboard
npm run build

cd ../student-portal
npm run build
```

## Next Steps

- Add more course management features
- Implement batch management UI
- Add user management interface
- Integrate payment flow
- Add progress tracking visualizations
- Implement assessment submission UI

