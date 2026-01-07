# Service Integration Guide

This document describes how the EduDron microservices integrate with each other.

## Service Communication

The services communicate via REST APIs through the Gateway. For production, consider adding:
- Event-driven communication (Kafka/RabbitMQ)
- Service discovery
- Circuit breakers for resilience

## Integration Points

### Content ↔ Student Integration

**Student Service validates course existence:**
- Before enrollment, Student Service should call Content Service to verify course exists and is published
- After enrollment, Content Service should be notified to update `totalStudentsCount`

**Content Service checks enrollment:**
- Before allowing access to course content, Content Service should verify enrollment via Student Service
- This can be done via REST call: `GET /api/enrollments?studentId={id}&courseId={id}`

### Content ↔ Payment Integration

**Payment Service validates course pricing:**
- Before creating payment, Payment Service should call Content Service to get course price
- Payment Service validates the amount matches the course price

**Content Service checks payment status:**
- Before allowing access to paid courses, Content Service should verify payment via Payment Service
- This can be done via REST call: `GET /api/payments?studentId={id}&courseId={id}&status=SUCCESS`

### Payment ↔ Student Integration

**Automatic enrollment after payment:**
- After successful payment, Payment Service webhook handler should call Student Service to enroll student
- REST call: `POST /api/courses/{courseId}/enroll` (with student ID from payment)

**Subscription-based access:**
- Content Service should check subscription status via Payment Service
- REST call: `GET /api/subscriptions/active?studentId={id}`

## Implementation Notes

Currently, services are designed to work independently. Full integration requires:

1. **REST Client Configuration**: Add Feign or RestTemplate clients for inter-service communication
2. **Circuit Breakers**: Use Resilience4j or Hystrix for fault tolerance
3. **Event Bus**: Implement event-driven architecture for async operations
4. **Service Discovery**: Use Eureka or Consul for dynamic service discovery

## Example Integration Flow

### Student Enrolls in Paid Course

1. Student calls `POST /api/courses/{courseId}/enroll`
2. Student Service validates:
   - Course exists (calls Content Service)
   - Course is published (from Content Service response)
   - Course is free OR payment exists (calls Payment Service)
3. If paid and no payment, return error with payment link
4. If payment exists or course is free, create enrollment
5. Notify Content Service to increment `totalStudentsCount`

### Payment Success Webhook

1. Razorpay sends webhook to `POST /api/webhooks/razorpay`
2. Payment Service updates payment status
3. Payment Service calls Student Service to enroll student
4. Student Service creates enrollment
5. Student Service notifies Content Service to update statistics


