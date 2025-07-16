#!/bin/bash

# ADK Startup Script with Google API Key
# This script sets the required GOOGLE_API_KEY environment variable before starting the application

# Read Google API key from application.yml
API_KEY=$(grep -A1 "google:" src/main/resources/application.yml | grep "key:" | cut -d ':' -f 2 | tr -d ' ' | tr -d '"')

if [ -z "$API_KEY" ] || [ "$API_KEY" = "YOUR_GOOGLE_API_KEY_HERE" ]; then
    echo "❌ Error: Google API key not found in application.yml"
    echo "Please set google.api.key in src/main/resources/application.yml"
    exit 1
fi

echo "✅ Found Google API key in application.yml"
echo "🚀 Starting application with GOOGLE_API_KEY environment variable..."

# Export the environment variable and start the application
export GOOGLE_API_KEY="$API_KEY"

# Start the Spring Boot application
./gradlew bootRun