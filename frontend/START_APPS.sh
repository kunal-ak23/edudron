#!/bin/bash

echo "🚀 Starting EduDron Frontend Apps"
echo "=================================="
echo ""

# Check if packages are built
if [ ! -d "packages/ui-components/dist" ] || [ ! -d "packages/shared-utils/dist" ]; then
    echo "📦 Building shared packages first..."
    cd packages/ui-components
    npm install
    npm run build
    cd ../shared-utils
    npm install
    npm run build
    cd ../..
    echo "✅ Packages built"
    echo ""
fi

# Check environment files
if [ ! -f "apps/admin-dashboard/.env.local" ]; then
    echo "Creating admin-dashboard/.env.local..."
    echo "NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080" > apps/admin-dashboard/.env.local
fi

if [ ! -f "apps/student-portal/.env.local" ]; then
    echo "Creating student-portal/.env.local..."
    echo "NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080" > apps/student-portal/.env.local
fi

echo "✅ Environment files ready"
echo ""
echo "🚀 Starting development servers..."
echo ""
echo "📱 Apps will be available at:"
echo "   Admin Dashboard: http://localhost:3000"
echo "   Student Portal: http://localhost:3001"
echo ""
echo "Press Ctrl+C to stop all servers"
echo ""

# Start both apps
npm run dev


