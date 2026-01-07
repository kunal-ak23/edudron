#!/bin/bash

# EduDron Service Management Script
# Usage: ./scripts/edudron.sh [start|stop|restart|status]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[EDUDRON]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if we're in the right directory
check_directory() {
    if [ ! -f "build.gradle" ]; then
        print_error "Please run this script from the root of the EduDron project"
        exit 1
    fi
}

# Function to start all services
start_services() {
    print_status "Starting all EduDron services..."
    
    # Start services in dependency order
    print_status "📋 Starting Identity Service (port 8081)..."
    ./gradlew :identity:bootRun > /tmp/identity.log 2>&1 &
    sleep 5
    
    print_status "📚 Starting Content Service (port 8082)..."
    ./gradlew :content:bootRun > /tmp/content.log 2>&1 &
    sleep 3
    
    print_status "👥 Starting Student Service (port 8083)..."
    ./gradlew :student:bootRun > /tmp/student.log 2>&1 &
    sleep 3
    
    print_status "💳 Starting Payment Service (port 8084)..."
    ./gradlew :payment:bootRun > /tmp/payment.log 2>&1 &
    sleep 3
    
    print_status "🚪 Starting Gateway Service (port 8080)..."
    ./gradlew :gateway:bootRun > /tmp/gateway.log 2>&1 &
    
    print_success "All services started! Check logs for individual service status."
    print_status "🔗 Service URLs:"
    echo "   Identity:    http://localhost:8081"
    echo "   Content:     http://localhost:8082"
    echo "   Student:     http://localhost:8083"
    echo "   Payment:     http://localhost:8084"
    echo "   Gateway:     http://localhost:8080"
    echo ""
    print_status "📱 Frontend Apps:"
    echo "   Admin Dashboard: http://localhost:3000"
    echo "   Student Portal:  http://localhost:3001"
}

# Function to stop all services
stop_services() {
    print_status "Stopping all EduDron services..."
    
    pkill -f 'identity:bootRun' 2>/dev/null || true
    pkill -f 'content:bootRun' 2>/dev/null || true
    pkill -f 'student:bootRun' 2>/dev/null || true
    pkill -f 'payment:bootRun' 2>/dev/null || true
    pkill -f 'gateway:bootRun' 2>/dev/null || true
    
    print_success "All services stopped!"
}

# Function to restart all services
restart_services() {
    print_status "Restarting all EduDron services..."
    stop_services
    sleep 2
    start_services
    print_success "All services restarted!"
}

# Function to check status
check_status() {
    print_status "Checking status of all services..."
    ./gradlew statusAllServices
}

# Function to show help
show_help() {
    echo "EduDron Service Management Script"
    echo ""
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  start     Start all EduDron services"
    echo "  stop      Stop all EduDron services"
    echo "  restart   Restart all EduDron services"
    echo "  status    Check status of all services"
    echo "  help      Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 start"
    echo "  $0 status"
    echo "  $0 restart"
}

# Main script logic
main() {
    check_directory
    
    case "${1:-help}" in
        start)
            start_services
            ;;
        stop)
            stop_services
            ;;
        restart)
            restart_services
            ;;
        status)
            check_status
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            print_error "Unknown command: $1"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"


