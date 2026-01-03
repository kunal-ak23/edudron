#!/bin/bash

echo "🚀 EduDron Frontend Test Setup"
echo "=============================="
echo ""

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "❌ Node.js is not installed. Please install Node.js 18+ first."
    exit 1
fi

NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 18 ]; then
    echo "❌ Node.js version 18+ is required. Current version: $(node -v)"
    exit 1
fi

echo "✅ Node.js version: $(node -v)"
echo ""

# Check if backend is running
echo "Checking backend services..."
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ Backend Gateway is running on port 8080"
else
    echo "⚠️  Backend Gateway is not running on port 8080"
    echo "   Please start backend services first:"
    echo "   cd /Users/kunalsharma/datagami/edudron"
    echo "   ./scripts/edudron.sh start"
    echo ""
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo ""
echo "📦 Installing dependencies..."
echo ""

# Install root dependencies
if [ ! -d "node_modules" ]; then
    echo "Installing root dependencies..."
    npm install
else
    echo "✅ Root dependencies already installed"
fi

# Build shared packages
echo ""
echo "🔨 Building shared packages..."

cd packages/ui-components
if [ ! -d "node_modules" ]; then
    echo "Installing ui-components dependencies..."
    npm install
fi
if [ ! -d "dist" ]; then
    echo "Building ui-components..."
    npm run build
else
    echo "✅ ui-components already built"
fi

cd ../shared-utils
if [ ! -d "node_modules" ]; then
    echo "Installing shared-utils dependencies..."
    npm install
fi
if [ ! -d "dist" ]; then
    echo "Building shared-utils..."
    npm run build
else
    echo "✅ shared-utils already built"
fi

cd ../..

# Install app dependencies
echo ""
echo "📱 Installing app dependencies..."

cd apps/admin-dashboard
if [ ! -d "node_modules" ]; then
    echo "Installing admin-dashboard dependencies..."
    npm install
else
    echo "✅ admin-dashboard dependencies installed"
fi

# Create .env.local if it doesn't exist
if [ ! -f ".env.local" ]; then
    echo "Creating .env.local for admin-dashboard..."
    echo "NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080" > .env.local
    echo "✅ Created admin-dashboard/.env.local"
fi

cd ../student-portal
if [ ! -d "node_modules" ]; then
    echo "Installing student-portal dependencies..."
    npm install
else
    echo "✅ student-portal dependencies installed"
fi

# Create .env.local if it doesn't exist
if [ ! -f ".env.local" ]; then
    echo "Creating .env.local for student-portal..."
    echo "NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:8080" > .env.local
    echo "✅ Created student-portal/.env.local"
fi

cd ../..

echo ""
echo "✅ Setup complete!"
echo ""
echo "🚀 Starting apps..."
echo ""
echo "📱 Apps will be available at:"
echo "   Admin Dashboard: http://localhost:3000"
echo "   Student Portal: http://localhost:3001"
echo ""
echo "Press Ctrl+C to stop all servers"
echo ""

# Start both apps
npm run dev

