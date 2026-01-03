# Quick Start Guide - Testing Admin & Student Apps

## Prerequisites

1. **Backend Services Running**
   - Gateway on port 8080
   - All microservices (Identity, Content, Student, Payment)
   - Database and Redis

2. **Node.js 18+** installed
   - Check: `node --version`

## Step 1: Start Backend Services

First, make sure your backend is running:

```bash
# From edudron root directory
cd /Users/kunalsharma/datagami/edudron

# Start database and Redis
docker-compose up -d

# Start all backend services
./scripts/edudron.sh start

# Or start individually:
./gradlew :identity:bootRun &
./gradlew :content:bootRun &
./gradlew :student:bootRun &
./gradlew :payment:bootRun &
./gradlew :gateway:bootRun &
```

Verify backend is running:
```bash
curl http://localhost:8080/actuator/health
```

## Step 2: Set Up Frontend

### Install Dependencies

```bash
cd frontend

# Install root dependencies
npm install

# Build shared packages first (IMPORTANT!)
cd packages/ui-components
npm install
npm run build

cd ../shared-utils
npm install
npm run build
cd ../..
```

### Install App Dependencies

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

## Step 3: Configure Environment Variables

### Admin Dashboard

Create `apps/admin-dashboard/.env.local`:
```bash
NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080
```

### Student Portal

Create `apps/student-portal/.env.local`:
```bash
NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080
```

## Step 4: Start Frontend Apps

### Option 1: Start Both Apps (Recommended)

From `frontend` directory:
```bash
npm run dev
```

This will start:
- Admin Dashboard: http://localhost:3000
- Student Portal: http://localhost:3001

### Option 2: Start Individually

**Terminal 1 - Admin Dashboard:**
```bash
cd apps/admin-dashboard
npm run dev
```

**Terminal 2 - Student Portal:**
```bash
cd apps/student-portal
npm run dev
```

## Step 5: Create Test Users

### Create Admin User (via Backend Script)

```bash
# From edudron root
./scripts/create-super-admin.sh admin@edudron.com 'Admin123!' 'Admin User'
```

Or create via API:
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Admin User",
    "email": "admin@edudron.com",
    "password": "Admin123!",
    "role": "TENANT_ADMIN"
  }'
```

### Create Student User

You can register directly from the Student Portal at http://localhost:3001/login

## Step 6: Test the Apps

### Admin Dashboard (http://localhost:3000)

1. **Login:**
   - Go to http://localhost:3000
   - You'll be redirected to `/login`
   - Use admin credentials

2. **Dashboard:**
   - View overview statistics
   - See recent courses
   - Quick actions panel

3. **Create Course:**
   - Click "Create Course" or go to `/courses/new`
   - Fill in course details
   - Save the course

4. **Manage Courses:**
   - View all courses at `/courses`
   - Edit courses
   - Publish/draft courses

5. **Manage Batches:**
   - Go to `/batches`
   - Create new batches
   - View batch details

6. **Manage Users:**
   - Go to `/users`
   - View all users
   - Search and filter users

### Student Portal (http://localhost:3001)

1. **Register/Login:**
   - Go to http://localhost:3001
   - Click "Sign up" to create account
   - Or login with existing credentials

2. **Browse Courses:**
   - View course catalog
   - Search and filter courses
   - View course details

3. **Enroll in Course:**
   - Click on a course
   - Click "Enroll Now" or "Enroll for Free"
   - Course will be added to "My Courses"

4. **My Courses:**
   - Go to `/my-courses`
   - See enrolled courses
   - View progress
   - Continue learning

5. **Learn:**
   - Click "Start Learning" or "Continue Learning"
   - Watch videos/read content
   - Mark lectures as complete
   - Track progress

## Troubleshooting

### Port Already in Use

If port 3000 or 3001 is busy:
```bash
# Find process
lsof -i :3000
lsof -i :3001

# Kill process
kill -9 <PID>

# Or change port in package.json
```

### Module Not Found Errors

If you see `Cannot find module '@edudron/...'`:

```bash
cd frontend/packages/ui-components
npm run build

cd ../shared-utils
npm run build
```

### API Connection Errors

1. **Check Gateway is running:**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. **Check environment variables:**
   - Verify `.env.local` files exist
   - Check `NEXT_PUBLIC_API_GATEWAY_URL` is set correctly

3. **Check CORS:**
   - Gateway should have CORS enabled (already configured)

4. **Check browser console:**
   - Open DevTools (F12)
   - Check Network tab for failed requests
   - Check Console for errors

### Build Errors

If shared packages fail to build:

```bash
# Clean and rebuild
cd frontend/packages/ui-components
rm -rf node_modules dist
npm install
npm run build

cd ../shared-utils
rm -rf node_modules dist
npm install
npm run build
```

### Next.js Cache Issues

```bash
# Clear Next.js cache
cd apps/admin-dashboard
rm -rf .next
npm run dev

cd ../student-portal
rm -rf .next
npm run dev
```

## Test Checklist

### Admin Dashboard
- [ ] Login works
- [ ] Dashboard loads with stats
- [ ] Can create new course
- [ ] Can edit course
- [ ] Can view course list
- [ ] Can create batch
- [ ] Can view batches
- [ ] Can view users list
- [ ] Search and filters work

### Student Portal
- [ ] Registration works
- [ ] Login works
- [ ] Can browse courses
- [ ] Search works
- [ ] Filters work
- [ ] Can view course details
- [ ] Can enroll in course
- [ ] My Courses page works
- [ ] Can start learning
- [ ] Progress tracking works
- [ ] Mark as complete works

## Quick Test Script

Save this as `test-apps.sh`:

```bash
#!/bin/bash

echo "🚀 Testing EduDron Apps"
echo ""

# Check backend
echo "Checking backend..."
if curl -s http://localhost:8080/actuator/health > /dev/null; then
  echo "✅ Backend is running"
else
  echo "❌ Backend is not running. Start it first!"
  exit 1
fi

echo ""
echo "📱 Apps should be available at:"
echo "   Admin Dashboard: http://localhost:3000"
echo "   Student Portal: http://localhost:3001"
echo ""
echo "Make sure both apps are running with 'npm run dev'"
```

Run it:
```bash
chmod +x test-apps.sh
./test-apps.sh
```

## Next Steps

Once everything is running:
1. Create test courses in Admin Dashboard
2. Publish courses
3. Enroll as student
4. Test learning interface
5. Check progress tracking

Happy testing! 🎉

