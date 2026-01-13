# Deploy EduDron Frontends to Vercel

## Quick Start

### 1. Install Vercel CLI

```bash
npm i -g vercel
```

### 2. Login to Vercel

```bash
vercel login
```

### 3. Deploy Each App

#### Admin Dashboard

```bash
cd frontend/apps/admin-dashboard

# First deployment (interactive setup)
vercel

# Production deployment
vercel --prod
```

#### Student Portal

```bash
cd frontend/apps/student-portal

# First deployment (interactive setup)
vercel

# Production deployment
vercel --prod
```

## Environment Variables

For each app, set these in Vercel Dashboard or via CLI:

### Admin Dashboard

```bash
cd frontend/apps/admin-dashboard

vercel env add NEXT_PUBLIC_API_GATEWAY_URL production
# Enter: https://your-gateway-url.azurecontainerapps.io
```

### Student Portal

```bash
cd frontend/apps/student-portal

vercel env add NEXT_PUBLIC_API_GATEWAY_URL production
# Enter: https://your-gateway-url.azurecontainerapps.io
```

**Important:** Set environment variables for all environments:
- Production
- Preview
- Development

## GitHub Integration (Recommended)

### Method 1: Separate Vercel Projects (Recommended)

Deploy each app as a separate Vercel project for better isolation and independent scaling.

#### Admin Dashboard Setup

1. **Connect GitHub:**
   - Go to [vercel.com](https://vercel.com)
   - Click "Add New Project"
   - Import your GitHub repository (`edudron`)

2. **Configure Project:**
   - **Project Name:** `edudron-admin-dashboard` (or your preferred name)
   - **Framework Preset:** Next.js (auto-detected)
   - **Root Directory:** `frontend`
   - **Build Command:** `npm install && npx turbo run build --filter=@edudron/admin-dashboard`
   - **Output Directory:** `apps/admin-dashboard/.next`
   - **Install Command:** `npm install`
   
   **OR** use the included `vercel.json` file (recommended)

3. **Environment Variables:**
   - Go to **Settings** → **Environment Variables**
   - Add `NEXT_PUBLIC_API_GATEWAY_URL` with your gateway URL
   - Select all environments (Production, Preview, Development)

4. **Deploy:**
   - Click **Deploy** or push to your main branch

#### Student Portal Setup

1. **Create New Project:**
   - Go to Vercel Dashboard
   - Click "Add New Project"
   - Import the same GitHub repository (`edudron`)

2. **Configure Project:**
   - **Project Name:** `edudron-student-portal` (or your preferred name)
   - **Framework Preset:** Next.js
   - **Root Directory:** `frontend`
   - **Build Command:** `npm install && npx turbo run build --filter=@edudron/student-portal`
   - **Output Directory:** `apps/student-portal/.next`
   - **Install Command:** `npm install`
   
   **OR** use the included `vercel.json` file (recommended)

3. **Environment Variables:**
   - Add `NEXT_PUBLIC_API_GATEWAY_URL` with your gateway URL
   - Select all environments

4. **Deploy:**
   - Click **Deploy** or push to your main branch

### Method 2: Using vercel.json Files

If you prefer configuration files, you can use the `vercel.json` files included in each app directory.

## Monorepo Configuration

### Build Process

The monorepo structure requires building shared packages before building apps:

1. **Install dependencies** at root (`frontend/`)
2. **Build shared packages** (`ui-components` and `shared-utils`)
3. **Build the app** (`admin-dashboard` or `student-portal`)

### Using vercel.json (Included)

Each app has a `vercel.json` file that handles the monorepo build:

**Admin Dashboard** (`apps/admin-dashboard/vercel.json`):
- Builds from root directory
- Installs all dependencies
- Builds shared packages
- Builds the app

**Student Portal** (`apps/student-portal/vercel.json`):
- Same configuration as admin dashboard

### Manual Build Commands

If not using `vercel.json`, use these commands in Vercel Dashboard:

**Root Directory:** `frontend`

**Install Command:**
```bash
npm install
```

**Install Command:**
```bash
cd ../.. && npm ci
```

**Build Command (Admin Dashboard):**
```bash
cd ../.. && npx turbo run build --filter=@edudron/admin-dashboard
```

**Build Command (Student Portal):**
```bash
cd ../.. && npx turbo run build --filter=@edudron/student-portal
```

**Note:** Using `turbo run build --filter=<package-name>` leverages Turbo's dependency graph to automatically build `@edudron/ui-components` and `@edudron/shared-utils` before building the app. The `--filter` flag tells Turbo to build the specified app and all its dependencies.

**Output Directory:**
- Admin Dashboard: `apps/admin-dashboard/.next`
- Student Portal: `apps/student-portal/.next`

## Custom Domains

1. Go to Vercel Dashboard → Your Project → Settings → Domains
2. Add your domain (e.g., `admin.edudron.com` or `student.edudron.com`)
3. Follow DNS configuration instructions
4. SSL certificates are automatically provisioned

## Preview Deployments

Vercel automatically creates preview deployments for:
- Every pull request
- Every commit to non-production branches

Access them via:
- PR comments (automatic)
- Vercel Dashboard → Deployments

## Troubleshooting

### Build Fails - Monorepo Issues

**Problem:** Build fails because shared packages aren't found.

**Solution:** 
1. Ensure **Root Directory** is set to `frontend` (not `frontend/apps/admin-dashboard`)
2. Use the provided `vercel.json` files
3. Or set build commands as shown above

### Build Fails - Package Not Found

**Problem:** `@edudron/ui-components` or `@edudron/shared-utils` not found.

**Solution:**
1. Ensure packages are built before the app
2. Check that `prebuild` script in app's `package.json` is working
3. Verify `vercel.json` includes package build steps

### Environment Variables Not Working

**Solution:** 
1. Check variable names (must start with `NEXT_PUBLIC_` for client-side)
2. Redeploy after adding variables
3. Check Vercel logs for errors
4. Ensure variables are set for the correct environment (Production/Preview/Development)

### API Calls Failing

**Solution:**
1. Verify `NEXT_PUBLIC_API_GATEWAY_URL` is set correctly
2. Check CORS settings on your backend gateway
3. Ensure the gateway URL is accessible from Vercel's servers
4. Check Vercel function logs for API errors

### Build Timeout

**Solution:**
1. Optimize build by caching node_modules
2. Consider using Vercel's build cache
3. Split large dependencies if possible
4. Check Vercel plan limits (free tier has build time limits)

### npm Error: "Tracker 'idealTree' already exists"

**Problem:** npm install fails with "Tracker 'idealTree' already exists" error.

**Solution:**
1. **Clear Vercel cache:** Delete the `.vercel` directory in your app folder:
   ```bash
   rm -rf apps/student-portal/.vercel
   rm -rf apps/admin-dashboard/.vercel
   ```

2. **Update Vercel project settings:**
   - Go to Vercel Dashboard → Your Project → Settings → General
   - Update the Build Command to: `cd ../.. && npx turbo run build --filter=@edudron/student-portal`
   - Update the Install Command to: `cd ../.. && npm ci`
   - Save and redeploy

3. **Use `npm ci` instead of `npm install`:**
   - `npm ci` is more reliable for CI/CD environments
   - It does a clean install based on package-lock.json

4. **Ensure vercel.json is correct:**
   - The `vercel.json` file should NOT have `npm install` in the buildCommand
   - Install should only be in `installCommand`

## Quick Deploy Script

You can also use this script to deploy both apps:

```bash
#!/bin/bash

# Deploy Admin Dashboard
cd frontend/apps/admin-dashboard
vercel --prod

# Deploy Student Portal
cd ../student-portal
vercel --prod
```

## Production Checklist

- [ ] Both apps deployed to Vercel
- [ ] Environment variables set for all environments
- [ ] Custom domains configured (if needed)
- [ ] CORS configured on backend gateway
- [ ] SSL certificates active (automatic on Vercel)
- [ ] Preview deployments working
- [ ] Production deployments tested
- [ ] Monitoring/logging set up (optional)

## Support

For issues:
1. Check Vercel Dashboard → Deployments → Logs
2. Review build logs for errors
3. Verify environment variables
4. Check backend gateway is accessible

