export interface ApiEnvelope<T> {
  success: boolean
  code: string
  data: T
  message: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface DashboardSummary {
  totalUsers: number
  activeUsers: number
  suspendedUsers: number
  usersWithCredit: number
  averageCreditScore: number | null
  usersByTier: Record<string, number>
  totalEvents: number
  eventsByType: Record<string, number>
}

export interface AdminUser {
  userId: string
  serviceId: string
  externalUserId: string
  accountStatus: 'ACTIVE' | 'SUSPENDED' | string
  createdAt: string
  creditScore: number
  tier: string
}

export interface UserDetail extends AdminUser {
  anomalyScore: number | null
  lastAssessedAt: string | null
  latestAction: string | null
}

export interface UserEvent {
  eventId: number
  serviceId: string
  externalEventId: string
  userId: string
  eventType: string
  bookingId: string | null
  ticketId: string | null
  occurredAt: string
  metadata: Record<string, unknown> | null
}

export interface BookingAttempt {
  attemptId: string
  concertId: string
  userId: string
  startedAt: string
  sequence: number
  stage: string
  status: string
}

export interface BookingAttemptResult {
  bookingAttemptCount: number
  items: BookingAttempt[]
}
