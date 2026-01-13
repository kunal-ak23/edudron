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

## Package Build Strategy

EduDron uses **local package builds** (similar to gulliyo's approach):

- Packages are built automatically via `prebuild` scripts before each app build
- No need to publish packages to GitHub Packages for Vercel deployments
- Packages are referenced as workspace dependencies (`*`)

**How it works:**
- Each app has a `prebuild` script that builds `shared-utils` and `ui-components`
- When Vercel runs `npm run build`, it automatically runs `prebuild` first
- Packages are built locally during the Vercel build process

**Alternative:** If you prefer to use published packages (like gulliyo does now), see `frontend/packages/PUBLISH_PACKAGES.md` for instructions.

## Environment Variables

For each app, set these in Vercel Dashboard or via CLI:

### Required Variables

1. **GITHUB_TOKEN** (Required for installing packages)
   - Get token from: https://github.com/settings/tokens
   - Required scope: `read:packages` (or `write:packages` if publishing)
   - Set for: Production, Preview, Development

2. **NEXT_PUBLIC_API_GATEWAY_URL**
   - Your gateway URL (e.g., `https://your-gateway.azurecontainerapps.io`)

### Admin Dashboard

```bash
cd frontend/apps/admin-dashboard

vercel env add GITHUB_TOKEN production
# Enter: your_github_token

vercel env add NEXT_PUBLIC_API_GATEWAY_URL production
# Enter: https://your-gateway-url.azurecontainerapps.io
```

### Student Portal

```bash
cd frontend/apps/student-portal

vercel env add GITHUB_TOKEN production
# Enter: your_github_token

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
   - **Root Directory:** `frontend/apps/admin-dashboard` (default - where you ran `vercel` from)
   - **Build Command:** (leave empty - uses `vercel.json`)
   - **Output Directory:** (leave empty - uses `vercel.json`)
   - **Install Command:** (leave empty - uses `vercel.json`)
   
   The included `vercel.json` file handles everything automatically!

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
   - **Root Directory:** `frontend/apps/student-portal` (default - where you ran `vercel` from)
   - **Build Command:** (leave empty - uses `vercel.json`)
   - **Output Directory:** (leave empty - uses `vercel.json`)
   - **Install Command:** (leave empty - uses `vercel.json`)
   
   The included `vercel.json` file handles everything automatically!

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

Each app has a `vercel.json` file that handles the monorepo build automatically:

**Admin Dashboard** (`apps/admin-dashboard/vercel.json`):
```json
{
  "buildCommand": "npm run build",
  "outputDirectory": ".next",
  "installCommand": "cd ../.. && npm install",
  "framework": "nextjs"
}
```

**Student Portal** (`apps/student-portal/vercel.json`):
```json
{
  "buildCommand": "npm run build",
  "outputDirectory": ".next",
  "installCommand": "cd ../.. && npm install",
  "framework": "nextjs"
}
```

**How it works:**
1. **Vercel Root Directory:** `frontend/apps/admin-dashboard` (or `frontend/apps/student-portal`)
2. **Install Command:** `cd ../.. && npm install` - Goes up to `frontend` root and installs workspace packages
3. **Build Command:** `npm run build` - Runs from app directory
4. **prebuild script** (in package.json) automatically runs first:
   - Builds `shared-utils` package
   - Builds `ui-components` package
5. Then `next build` runs to build the Next.js app

**Note:** The `prebuild` script ensures packages are built before the app tries to use them, just like when you run `npm run build` locally.

### Manual Build Commands

If not using `vercel.json`, use these commands in Vercel Dashboard:

**Root Directory:** `frontend`

**Install Command:**
```bash
npm install
```

**Root Directory:** `frontend/apps/admin-dashboard` (or `frontend/apps/student-portal`)

**Install Command:**
```bash
cd ../.. && npm install
```

**Build Command:**
```bash
npm run build
```

**Output Directory:**
```
.next
```

**Note:** The `prebuild` script in each app's `package.json` automatically builds `shared-utils` and `ui-components` before building the Next.js app. This is the same approach used in gulliyo.

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

**Problem:** `@kunal-ak23/edudron-ui-components` or `@kunal-ak23/edudron-shared-utils` not found.

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

### npm Error: "Tracker 'idealTree' already exists" or "Command exited with 1"

**Problem:** npm install fails during Vercel build.

**Solution:**
1. **Clear Vercel cache:** Delete the `.vercel` directory in your app folder:
   ```bash
   rm -rf apps/student-portal/.vercel
   rm -rf apps/admin-dashboard/.vercel
   ```

2. **Check Vercel Root Directory Setting:**
   - Go to Vercel Dashboard → Your Project → Settings → General → Build & Development Settings
   - **Root Directory should be:** `frontend/apps/student-portal` (or `frontend/apps/admin-dashboard`)
   - This is the default when you run `vercel` from the app directory
   - The `installCommand` in `vercel.json` uses `cd ../..` to navigate to `frontend` root for installing dependencies

3. **Update Vercel project settings manually (if vercel.json isn't working):**
   - **Root Directory:** `frontend/apps/student-portal`
   - **Install Command:** `cd ../.. && npm install`
   - **Build Command:** `npm run build`
   - **Output Directory:** `.next`
   - Save and redeploy

4. **Alternative: Use simpler install command:**
   - Try `npm install --legacy-peer-deps` instead of `npm install`
   - This handles peer dependency conflicts better

5. **Ensure GITHUB_TOKEN is set:**
   - Go to Vercel Dashboard → Settings → Environment Variables
   - Make sure `GITHUB_TOKEN` is set for all environments
   - This is required for installing `@kunal-ak23` scoped packages

6. **Verify .npmrc files exist:**
   - `frontend/.npmrc` should exist
   - `frontend/apps/student-portal/.npmrc` should exist
   - Both should reference `${GITHUB_TOKEN}`

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

