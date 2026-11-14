import type {
  AdminUser,
  ApiEnvelope,
  BookingAttemptResult,
  DashboardSummary,
  PageResponse,
  UserDetail,
  UserEvent,
} from './types'

export class ApiError extends Error {
  status: number
  code: string
  constructor(message: string, status: number, code = 'REQUEST_FAILED') {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

async function request<T>(path: string, apiKey: string, params?: Record<string, string | number | undefined>): Promise<T> {
  const url = new URL(path, window.location.origin)
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== '') url.searchParams.set(key, String(value))
  })

  let response: Response
  try {
    response = await fetch(url.toString(), {
      method: 'GET',
      headers: { 'X-API-Key': apiKey, Accept: 'application/json' },
    })
  } catch {
    throw new ApiError('백엔드에 연결할 수 없습니다. 서버 실행 및 주소를 확인해 주세요.', 0, 'NETWORK_ERROR')
  }

  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null
  if (!response.ok || !body?.success) {
    throw new ApiError(body?.message || `요청에 실패했습니다. (HTTP ${response.status})`, response.status, body?.code)
  }
  return body.data
}

export const api = {
  summary: (adminKey: string, serviceId: string) =>
    request<DashboardSummary>('/api/v1/dashboard/summary', adminKey, { serviceId }),

  users: (adminKey: string, query: Record<string, string | number | undefined>) =>
    request<PageResponse<AdminUser>>('/api/v1/admin/users', adminKey, query),

  userDetail: (serviceKey: string, userId: string) =>
    request<UserDetail>(`/api/v1/users/${encodeURIComponent(userId)}`, serviceKey),

  userEvents: (serviceKey: string, userId: string) =>
    request<PageResponse<UserEvent>>(`/api/v1/users/${encodeURIComponent(userId)}/events`, serviceKey, {
      page: 0,
      size: 100,
      sort: 'occurredAt,desc',
    }),

  bookingAttempts: (serviceKey: string, query: { concertId?: string; userId?: string; page?: number; size?: number }) =>
    request<BookingAttemptResult>('/api/v1/events/booking-attempts', serviceKey, {
      concertId: query.concertId,
      userId: query.userId,
      page: query.page ?? 0,
      size: query.size ?? 100,
    }),
}
