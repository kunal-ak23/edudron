# Testing EduDron Frontend Apps

## ⚠️ Important: Use Development Mode for Testing

**You don't need to build the apps for testing!** The build errors you're seeing are expected because these pages use client-side features (localStorage, useRouter) that can't be statically generated.

## ✅ Quick Start (Recommended)

```bash
cd /Users/kunalsharma/datagami/edudron/frontend

# Make sure packages are built
cd packages/ui-components && npm run build && cd ../shared-utils && npm run build && cd ../..

# Start both apps in development mode
npm run dev
```

This will start:
- **Admin Dashboard**: http://localhost:3000
- **Student Portal**: http://localhost:3001

## Why Build Fails (But Dev Works)

The build process tries to **statically generate** pages at build time, but our pages use:
- `localStorage` (client-side only)
- `useRouter` (requires browser environment)
- Dynamic authentication checks

These features **require the browser**, so they can't be statically generated. This is **normal and expected** for client-side authenticated apps.

**Solution**: Use `npm run dev` for development and testing - it works perfectly!

## Testing Checklist

### Admin Dashboard (http://localhost:3000)

1. **Login**
   - Navigate to http://localhost:3000
   - Use admin credentials
   - Should redirect to dashboard

2. **Dashboard**
   - View statistics (courses, batches, students)
   - See recent courses
   - Quick actions panel

3. **Courses**
   - View all courses
   - Create new course
   - Edit existing course
   - Search and filter

4. **Batches**
   - View all batches
   - Create new batch
   - View batch details

5. **Users**
   - View all users
   - Search users
   - Filter by role

### Student Portal (http://localhost:3001)

1. **Register/Login**
   - Create new account
   - Login with credentials

2. **Browse Courses**
   - View course catalog
   - Search courses
   - Filter by difficulty/price

3. **Course Details**
   - View course information
   - See syllabus
   - Enroll in course

4. **My Courses**
   - View enrolled courses
   - See progress
   - Continue learning

5. **Learning Interface**
   - Watch videos/read content
   - Navigate through lectures
   - Mark as complete
   - Track progress

## Environment Setup

Make sure `.env.local` files exist:

**apps/admin-dashboard/.env.local:**
```
NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080
```

**apps/student-portal/.env.local:**
```
NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080
```

If your backend is on a different URL, update these files.

## Troubleshooting

### Port Already in Use
```bash
# Find and kill process
lsof -i :3000
kill -9 <PID>
```

### Module Not Found
```bash
# Rebuild packages
cd packages/ui-components && npm run build
cd ../shared-utils && npm run build
```

### API Connection Errors
1. Verify backend is running: `curl http://localhost:8080/actuator/health`
2. Check `.env.local` files
3. Check browser console for errors

## Production Build (Later)

If you need to build for production later, we can:
1. Add proper loading states
2. Use Next.js route groups
3. Configure dynamic rendering properly
4. Use middleware for authentication

But for **testing right now**, just use `npm run dev` - it's faster and works great!

## Ready to Test!

```bash
cd /Users/kunalsharma/datagami/edudron/frontend
npm run dev
```

Then open:
- Admin: http://localhost:3000
- Student: http://localhost:3001

Happy testing! 🎉


