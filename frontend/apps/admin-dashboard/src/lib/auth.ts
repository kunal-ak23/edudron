import { AuthService } from '@kunal-ak23/edudron-shared-utils'

const GATEWAY_URL = process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:8080'

export const authService = new AuthService(GATEWAY_URL)


