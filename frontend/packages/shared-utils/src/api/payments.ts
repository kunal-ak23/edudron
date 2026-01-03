import { ApiClient } from './ApiClient'

export interface SubscriptionPlan {
  id: string
  name: string
  description?: string
  price: number
  duration: number
  durationUnit: 'DAYS' | 'MONTHS' | 'YEARS'
  features?: string[]
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface Subscription {
  id: string
  planId: string
  studentId: string
  status: 'ACTIVE' | 'CANCELLED' | 'EXPIRED'
  startDate: string
  endDate: string
  autoRenew: boolean
  createdAt: string
  updatedAt: string
}

export interface Payment {
  id: string
  studentId: string
  courseId?: string
  subscriptionId?: string
  amount: number
  currency: string
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED'
  paymentMethod: string
  provider: string
  providerRef?: string
  createdAt: string
  updatedAt: string
}

export class PaymentsApi {
  constructor(private apiClient: ApiClient) {}

  // Subscription Plans
  async listPlans(): Promise<SubscriptionPlan[]> {
    const response = await this.apiClient.get<SubscriptionPlan[]>('/api/subscription-plans')
    return Array.isArray(response.data) ? response.data : []
  }

  async getPlan(id: string): Promise<SubscriptionPlan> {
    const response = await this.apiClient.get<SubscriptionPlan>(`/api/subscription-plans/${id}`)
    return response.data
  }

  async createPlan(plan: Partial<SubscriptionPlan>): Promise<SubscriptionPlan> {
    const response = await this.apiClient.post<SubscriptionPlan>('/api/subscription-plans', plan)
    return response.data
  }

  async updatePlan(id: string, plan: Partial<SubscriptionPlan>): Promise<SubscriptionPlan> {
    const response = await this.apiClient.put<SubscriptionPlan>(`/api/subscription-plans/${id}`, plan)
    return response.data
  }

  async deletePlan(id: string): Promise<void> {
    await this.apiClient.delete(`/api/subscription-plans/${id}`)
  }

  // Subscriptions
  async listSubscriptions(): Promise<Subscription[]> {
    const response = await this.apiClient.get<Subscription[]>('/api/subscriptions')
    return Array.isArray(response.data) ? response.data : []
  }

  async getSubscription(id: string): Promise<Subscription> {
    const response = await this.apiClient.get<Subscription>(`/api/subscriptions/${id}`)
    return response.data
  }

  async createSubscription(subscription: Partial<Subscription>): Promise<Subscription> {
    const response = await this.apiClient.post<Subscription>('/api/subscriptions', subscription)
    return response.data
  }

  async cancelSubscription(id: string): Promise<void> {
    await this.apiClient.post(`/api/subscriptions/${id}/cancel`)
  }

  async getActiveSubscription(): Promise<Subscription | null> {
    try {
      const response = await this.apiClient.get<Subscription>('/api/subscriptions/active')
      return response.data
    } catch {
      return null
    }
  }

  // Payments
  async listPayments(): Promise<Payment[]> {
    const response = await this.apiClient.get<Payment[]>('/api/payments')
    return Array.isArray(response.data) ? response.data : []
  }

  async getPayment(id: string): Promise<Payment> {
    const response = await this.apiClient.get<Payment>(`/api/payments/${id}`)
    return response.data
  }

  async createPayment(payment: Partial<Payment>): Promise<Payment> {
    const response = await this.apiClient.post<Payment>('/api/payments', payment)
    return response.data
  }
}

