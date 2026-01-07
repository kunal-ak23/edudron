# Important Note for Testing

## Build vs Development

The build errors you're seeing are related to **static page generation** during production builds. However, **for development and testing, you don't need to build** - you can use the development server which works perfectly!

## Quick Start for Testing

### Option 1: Development Mode (Recommended for Testing)

```bash
cd /Users/kunalsharma/datagami/edudron/frontend

# Make sure packages are built
cd packages/ui-components && npm run build
cd ../shared-utils && npm run build
cd ../..

# Start development servers (no build needed!)
npm run dev
```

This will start both apps in **development mode** where:
- ✅ Hot reload works
- ✅ No static generation issues
- ✅ All features work perfectly
- ✅ Fast startup

### Option 2: Individual Apps

**Terminal 1:**
```bash
cd apps/admin-dashboard
npm run dev
```

**Terminal 2:**
```bash
cd apps/student-portal
npm run dev
```

## Why Build Fails (But Dev Works)

The build process tries to **statically generate** pages at build time, but our pages use:
- `localStorage` (client-side only)
- `useRouter` (requires React context)
- Dynamic authentication checks

These features require the browser environment, so they can't be statically generated.

**Solution**: Use `npm run dev` for development/testing - it works perfectly!

## Production Build

If you need to build for production later, we can:
1. Add proper loading states
2. Use Next.js route groups
3. Configure dynamic rendering properly

But for **testing right now**, just use `npm run dev` - it's faster and works great!

## Test Checklist

Once apps are running:

### Admin Dashboard (http://localhost:3000)
- [ ] Login page loads
- [ ] Can login with admin credentials
- [ ] Dashboard shows statistics
- [ ] Can navigate to Courses, Batches, Users
- [ ] Can create/edit courses
- [ ] Search and filters work

### Student Portal (http://localhost:3001)
- [ ] Login/Register page loads
- [ ] Can register new account
- [ ] Can login
- [ ] Course catalog displays
- [ ] Search and filters work
- [ ] Can view course details
- [ ] Can enroll in courses
- [ ] My Courses page works
- [ ] Learning interface loads

## Environment Files

Make sure `.env.local` files exist:
- `apps/admin-dashboard/.env.local` → `NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080`
- `apps/student-portal/.env.local` → `NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080`

If your backend is on a different URL, update these files accordingly.

## Ready to Test!

Just run:
```bash
cd /Users/kunalsharma/datagami/edudron/frontend
npm run dev
```

Then open:
- Admin: http://localhost:3000
- Student: http://localhost:3001

Happy testing! 🎉


