# EduDron Frontend

A modern, scalable frontend architecture for the EduDron Learning Management System built with Next.js and TypeScript.

## Architecture

This frontend is built as a **monorepo** with multiple applications that can be deployed independently:

### Applications

1. **Admin Dashboard** (`apps/admin-dashboard`) - Port 3000
   - Complete admin interface for managing the platform
   - Course management, batch management, user management
   - Analytics and reporting

2. **Student Portal** (`apps/student-portal`) - Port 3001
   - Student-facing learning interface
   - Course browsing, enrollment, progress tracking
   - Learning content viewer

### Packages

1. **UI Components** (`packages/ui-components`)
   - Reusable React components
   - Consistent design system
   - TypeScript support

2. **Shared Utils** (`packages/shared-utils`)
   - Common utilities and services
   - API client with authentication
   - Type definitions

## Quick Start

### Prerequisites

- Node.js 18+
- npm or yarn
- Backend services running (Gateway on port 8080)

### Installation

1. **Install root dependencies:**
   ```bash
   cd frontend
   npm install
   ```

2. **Build shared packages:**
   ```bash
   cd packages/ui-components
   npm install
   npm run build
   
   cd ../shared-utils
   npm install
   npm run build
   cd ../..
   ```

3. **Install app dependencies:**
   ```bash
   cd apps/admin-dashboard
   npm install
   cd ../student-portal
   npm install
   cd ../..
   ```

4. **Set up environment variables:**
   
   Create `.env.local` in each app directory:
   
   **apps/admin-dashboard/.env.local:**
   ```bash
   NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080
   ```
   
   **apps/student-portal/.env.local:**
   ```bash
   NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080
   ```

### Development

```bash
# Start all applications
npm run dev

# Or start individual applications
cd apps/admin-dashboard && npm run dev
cd apps/student-portal && npm run dev
```

### Access the Applications

- **Admin Dashboard**: http://localhost:3000
- **Student Portal**: http://localhost:3001

## Project Structure

```
frontend/
├── apps/
│   ├── admin-dashboard/     # Admin interface
│   └── student-portal/      # Student interface
├── packages/
│   ├── ui-components/       # Shared UI components
│   └── shared-utils/       # Shared utilities
├── package.json            # Workspace configuration
└── turbo.json              # Turbo configuration
```

## API Integration

All apps use the shared API client which connects to the Gateway at `http://localhost:8080`.

### Authentication

- Login: `POST /auth/login`
- Register: `POST /auth/register`
- Refresh Token: `POST /auth/refresh`

### Key Endpoints

**Content:**
- `GET /content/courses` - List courses
- `GET /content/courses/{id}` - Get course details
- `POST /content/courses` - Create course (admin)
- `PUT /content/courses/{id}` - Update course (admin)

**Student:**
- `GET /api/enrollments` - List my enrollments
- `POST /api/courses/{courseId}/enroll` - Enroll in course
- `GET /api/courses/{courseId}/progress` - Get progress
- `PUT /api/courses/{courseId}/progress` - Update progress

**Payment:**
- `GET /api/subscription-plans` - List plans
- `POST /api/subscriptions` - Create subscription
- `GET /api/payments` - List payments

## Development

### Adding New Components

1. Add components to `packages/ui-components/src/components/`
2. Export from `packages/ui-components/src/index.ts`
3. Use in any app: `import { Button } from '@edudron/ui-components'`

### Adding New API Methods

1. Add API methods to `packages/shared-utils/src/api/`
2. Export from `packages/shared-utils/src/index.ts`
3. Use in apps: `import { coursesApi } from '@edudron/shared-utils'`

## Building for Production

```bash
# Build all packages and apps
npm run build

# Build individual app
cd apps/admin-dashboard
npm run build
```

## Troubleshooting

### Port Conflicts

If ports 3000 or 3001 are already in use, modify the port in the app's `package.json`:
```json
"dev": "next dev -p 3002"
```

### API Connection Issues

1. Ensure the Gateway is running on port 8080
2. Check that `NEXT_PUBLIC_API_GATEWAY_URL` is set correctly
3. Verify CORS is enabled on the Gateway

### Package Build Issues

If shared packages fail to build:
1. Delete `node_modules` and `dist` folders
2. Run `npm install` in each package directory
3. Run `npm run build` in each package

## Next Steps

- Add more course management features
- Implement progress tracking UI
- Add assessment submission interface
- Integrate payment flow
- Add real-time notifications

