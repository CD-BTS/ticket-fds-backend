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

/** GET /api/v1/admin/users, 공연별 사용자의 user 필드. LocalDateTime은 타임존 없이 내려옵니다. */
export interface AdminUser {
  userId: string
  serviceId: string
  externalUserId: string
  accountStatus: 'ACTIVE' | 'SUSPENDED' | string
  createdAt: string
  creditScore: number | null
  tier: string | null
  anomalyScore: number | null
  lastAssessedAt: string | null
}

export interface UserDetail extends AdminUser {
  latestAction: string | null
}

export interface PolicyDecision {
  decisionId: number
  userId: string
  context: string
  creditScore: number
  anomalyScore: number
  action: string
  reason: string
  decidedAt: string
}

/** GET /api/v1/admin/users/{userId} */
export interface AdminUserDetail {
  user: UserDetail
  latestPolicy: PolicyDecision | null
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

export interface Assessment {
  assessmentId: number
  anomalyScore: number | null
  policyAction: string | null
  modelVersion: string | null
  assessedAt: string
}

/** 시각은 UTC 오프셋이 붙은 ISO 문자열입니다. */
export interface Concert {
  serviceId: string
  concertId: string
  name: string
  startsAt: string
  endsAt: string
  venue: string
}

export interface ConcertDetail {
  concert: Concert
  bookingAttemptCount: number
  bookingAttemptUserCount: number
}

export interface ConcertUser {
  user: AdminUser
  lastBookingAttemptAt: string | null
}
