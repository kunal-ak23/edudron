# Publish EduDron Packages to GitHub Packages

## Quick Start

### Step 1: Get GitHub Token

1. Go to: https://github.com/settings/tokens
2. Click **"Generate new token"** → **"Generate new token (classic)"**
3. Name: `EduDron Packages`
4. Select scope: **`write:packages`**
5. Click **"Generate token"**
6. **Copy the token** (you won't see it again!)

### Step 2: Set Token

```bash
export GITHUB_TOKEN=your_token_here
```

### Step 3: Publish Packages

```bash
cd frontend/packages
./publish.sh all
```

This will:
1. Build `shared-utils`
2. Publish `shared-utils` to GitHub Packages
3. Build `ui-components`
4. Publish `ui-components` to GitHub Packages

## Publish Individual Packages

```bash
# Publish only shared-utils
./publish.sh shared-utils

# Publish only ui-components
./publish.sh ui-components
```

## Update Package Versions

To publish a new version:

```bash
cd frontend/packages/shared-utils
npm version patch  # or minor, or major
npm run build
npm publish
```

## Verify Packages Are Published

Visit: https://github.com/kunal-ak23/edudron/packages

You should see:
- `@kunal-ak23/edudron-shared-utils`
- `@kunal-ak23/edudron-ui-components`

## Using Published Packages in Apps

After publishing, the apps will automatically use the published packages because they reference:
```json
{
  "dependencies": {
    "@kunal-ak23/edudron-shared-utils": "^1.0.0",
    "@kunal-ak23/edudron-ui-components": "^1.0.0"
  }
}
```

## For Vercel Deployment

1. Make sure `GITHUB_TOKEN` is set in Vercel environment variables
2. The `.npmrc` files in the apps will handle authentication
3. Vercel will fetch packages from GitHub Packages during `npm install`

## Troubleshooting

### Error: ENEEDAUTH
- Make sure `GITHUB_TOKEN` is set
- Verify token has `write:packages` scope

### Error: Package already exists
- Update version: `npm version patch`
- Then publish again

### Error: 404 Not Found
- Check package name matches GitHub username/org
- Verify `.npmrc` is configured correctly

