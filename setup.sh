#!/bin/bash

# ChatAgent - Quick Setup Script
# This script sets up the local development environment

set -e

echo "🚀 ChatAgent Setup Script"
echo "=========================="
echo ""

# Check Java
echo "✓ Checking Java..."
if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Please install Java 21+"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -1)
echo "  $JAVA_VERSION"

# Check Maven
echo "✓ Checking Maven..."
if [ ! -f "./mvnw" ]; then
    echo "❌ Maven wrapper not found"
    exit 1
fi
echo "  Maven wrapper found"

# Check Git
echo "✓ Checking Git..."
if ! command -v git &> /dev/null; then
    echo "❌ Git not found"
    exit 1
fi
echo "  Git found"

# Create .env file if it doesn't exist
echo "✓ Setting up environment..."
if [ ! -f ".env" ]; then
    cp .env.example .env
    echo "  Created .env file (update with your settings)"
else
    echo "  .env file already exists"
fi

# Build project
echo "✓ Building project..."
./mvnw clean package -DskipTests -q
echo "  Build successful!"

# Display status
echo ""
echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "  1. Review and update .env with your database settings"
echo "  2. Start PostgreSQL database (or use: make docker-compose-up)"
echo "  3. Run: make run"
echo "  4. Visit: http://localhost:8080"
echo ""
echo "For more information, see:"
echo "  - README.md - Project overview"
echo "  - SETUP.md - Development setup guide"
echo "  - GITHUB_SETUP.md - GitHub repository setup"
echo ""
