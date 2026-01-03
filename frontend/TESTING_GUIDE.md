# Testing Guide - Admin & Student Apps

## Quick Start

### Option 1: Automated Setup (Recommended)

Run the test script:
```bash
cd /Users/kunalsharma/datagami/edudron/frontend
./test-apps.sh
```

This will:
- Check prerequisites
- Install all dependencies
- Build shared packages
- Create environment files
- Start both apps

### Option 2: Manual Setup

Follow the steps in [QUICK_START.md](./QUICK_START.md)

## Prerequisites Check

Before testing, ensure:

1. **Backend is running:**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. **Node.js 18+ installed:**
   ```bash
   node --version
   ```

## Testing Admin Dashboard

### Access
- URL: http://localhost:3000
- Port: 3000

### Test Flow

1. **Login**
   - Navigate to http://localhost:3000
   - You'll be redirected to `/login`
   - Use admin credentials:
     - Email: `admin@edudron.com` (or your admin email)
     - Password: Your admin password
   - If you don't have admin user, create one:
     ```bash
     cd /Users/kunalsharma/datagami/edudron
     ./scripts/create-super-admin.sh admin@edudron.com 'Admin123!' 'Admin User'
     ```

2. **Dashboard**
   - Should see overview statistics
   - Check: Total Courses, Active Batches, Total Students
   - Verify quick actions panel

3. **Create Course**
   - Click "Create Course" or go to `/courses/new`
   - Fill in:
     - Title: "Introduction to Web Development"
     - Description: "Learn the basics of web development"
     - Price: 0 (or any amount)
     - Status: Draft
   - Click "Create Course"
   - Should redirect to courses list

4. **View Courses**
   - Go to `/courses`
   - Should see your created course
   - Try search functionality
   - Try filters (All, Published, Drafts)

5. **Edit Course**
   - Click on a course or "Edit" button
   - Modify course details
   - Change status to "Published"
   - Save changes

6. **Create Batch**
   - Go to `/batches`
   - Click "Create Batch"
   - Fill in batch details
   - Save

7. **View Users**
   - Go to `/users`
   - Should see list of users
   - Try search functionality

## Testing Student Portal

### Access
- URL: http://localhost:3001
- Port: 3001

### Test Flow

1. **Register New Account**
   - Navigate to http://localhost:3001
   - Click "Sign up" or "Don't have an account? Sign up"
   - Fill in:
     - Name: "Test Student"
     - Email: "student@test.com"
     - Password: "Student123!"
   - Click "Sign up"
   - Should redirect to courses page

2. **Browse Courses**
   - Should see course catalog
   - Try search: Type "web" or "development"
   - Try filters:
     - Difficulty: Beginner, Intermediate, Advanced
     - Price: Free, Paid
   - Click on a course card to view details

3. **View Course Details**
   - Click on any course
   - Should see:
     - Course title and description
     - Instructor information
     - Learning objectives
     - Course content/syllabus
     - Enrollment sidebar with price
   - Click "Enroll Now" or "Enroll for Free"

4. **My Courses**
   - Go to `/my-courses`
   - Should see enrolled courses
   - Check progress bars
   - Click "Continue Learning" on a course

5. **Learning Interface**
   - Click "Start Learning" or "Continue Learning"
   - Should see:
     - Video player area (or content area)
     - Course content sidebar on right
     - Progress indicator
     - Lecture list with completion status
   - Click on different lectures in sidebar
   - Click "Mark as Complete"
   - Verify progress updates

6. **Progress Tracking**
   - Complete a few lectures
   - Check progress bar updates
   - Go back to "My Courses"
   - Verify progress percentage updated

## Common Test Scenarios

### Scenario 1: Complete Course Flow
1. Admin creates course
2. Admin publishes course
3. Student browses and finds course
4. Student enrolls
5. Student completes lectures
6. Student sees progress update

### Scenario 2: Search and Filter
1. Admin creates multiple courses with different:
   - Difficulties (Beginner, Intermediate, Advanced)
   - Prices (Free, Paid)
   - Categories/Tags
2. Student searches for specific course
3. Student filters by difficulty
4. Student filters by price
5. Verify results update correctly

### Scenario 3: Batch Management
1. Admin creates batch for a course
2. Admin sets batch capacity
3. Multiple students enroll in batch
4. Admin views batch details
5. Admin sees enrollment count

## Troubleshooting

### Apps Won't Start

**Error: Port already in use**
```bash
# Find and kill process
lsof -i :3000
kill -9 <PID>

lsof -i :3001
kill -9 <PID>
```

**Error: Cannot find module**
```bash
cd frontend/packages/ui-components
npm run build

cd ../shared-utils
npm run build
```

### API Errors

**401 Unauthorized**
- Check if you're logged in
- Try logging out and logging back in
- Check token in localStorage (DevTools > Application > Local Storage)

**404 Not Found**
- Verify backend is running
- Check API endpoint URL
- Verify Gateway is routing correctly

**CORS Errors**
- Gateway should have CORS enabled (already configured)
- Check Gateway logs for CORS issues

### UI Issues

**Components not rendering**
- Clear Next.js cache: `rm -rf .next`
- Rebuild shared packages
- Restart dev server

**Styling issues**
- Check Tailwind CSS is configured
- Verify `globals.css` is imported

## Debugging Tips

1. **Browser DevTools**
   - Open DevTools (F12)
   - Check Console for errors
   - Check Network tab for API calls
   - Verify requests go to `http://localhost:8080`

2. **Check Local Storage**
   - DevTools > Application > Local Storage
   - Should see: `auth_token`, `user`, `tenant_id`

3. **Backend Logs**
   - Check backend service logs
   - Verify requests are reaching backend
   - Check for errors in Gateway logs

4. **Network Requests**
   - All API calls should go through Gateway
   - Check request headers include `Authorization: Bearer <token>`
   - Check for `X-Client-Id` header if multi-tenant

## Expected Behavior

### Admin Dashboard
- ✅ Login redirects to dashboard
- ✅ Dashboard shows statistics
- ✅ Can create/edit/delete courses
- ✅ Can create/manage batches
- ✅ Can view users
- ✅ Search and filters work
- ✅ Status badges show correctly

### Student Portal
- ✅ Registration creates account
- ✅ Login works
- ✅ Course catalog displays
- ✅ Search and filters work
- ✅ Course details show all info
- ✅ Enrollment works
- ✅ Learning interface loads
- ✅ Progress tracking works
- ✅ Mark as complete updates progress

## Next Steps After Testing

1. Create more test data (courses, batches, users)
2. Test edge cases (empty states, errors)
3. Test responsive design (mobile, tablet)
4. Test with multiple users
5. Test payment flow (if implemented)
6. Test batch enrollment limits

## Need Help?

If you encounter issues:
1. Check browser console for errors
2. Check backend logs
3. Verify environment variables
4. Ensure all dependencies are installed
5. Try rebuilding shared packages

Happy testing! 🎉

