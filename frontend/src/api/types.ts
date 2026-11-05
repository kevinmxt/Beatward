export type MonitorType = 'HTTP' | 'HEARTBEAT'

export type MonitorStatus = 'UNKNOWN' | 'UP' | 'DOWN'

export type HttpMethod = 'GET' | 'HEAD'

export type IncidentStatus = 'OPEN' | 'RESOLVED'

export type NotificationDeliveryStatus = 'PENDING' | 'SENT' | 'FAILED'

export type AppRole = 'ADMIN' | 'USER'

export type AccountStatus = 'ACTIVE' | 'DISABLED'

export interface InviteCode {
  id: number
  label: string | null
  maxUses: number
  usedCount: number
  expiresAt: string
  revokedAt: string | null
  createdAt: string
}

export interface InviteCodeCreateInput {
  label?: string | null
  maxUses?: number | null
  expiresInHours?: number | null
}

export interface InviteCodeCreateResponse {
  id: number
  code: string
  label: string | null
  maxUses: number
  expiresAt: string
}

export interface AdminUser {
  id: number
  username: string
  role: AppRole
  status: AccountStatus
  disabledAt: string | null
  lastLoginAt: string | null
  createdAt: string
  monitorCount: number
}

export interface ResetCodeResponse {
  code: string
  expiresAt: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
}

export interface Monitor {
  id: number
  name: string
  type: MonitorType
  targetUrl: string | null
  method: HttpMethod | null
  intervalSeconds: number
  connectTimeoutMs: number | null
  responseTimeoutMs: number | null
  expectedStatusMin: number | null
  expectedStatusMax: number | null
  enabled: boolean
  failureThreshold: number | null
  recoveryThreshold: number | null
  heartbeatGraceSeconds: number | null
  currentStatus: MonitorStatus
  lastCheckedAt: string | null
  lastSuccessAt: string | null
  lastHeartbeatAt: string | null
  createdAt: string
  updatedAt: string
  heartbeatToken: string | null
  lastCheckDurationMs: number | null
}

export interface MonitorInput {
  name: string
  type?: MonitorType
  targetUrl?: string | null
  method?: HttpMethod | null
  intervalSeconds?: number | null
  connectTimeoutMs?: number | null
  responseTimeoutMs?: number | null
  expectedStatusMin?: number | null
  expectedStatusMax?: number | null
  enabled?: boolean
  failureThreshold?: number | null
  recoveryThreshold?: number | null
  heartbeatGraceSeconds?: number | null
}

export interface CheckResult {
  id: number
  startedAt: string
  finishedAt: string
  durationMs: number
  success: boolean
  failureType: string | null
  httpStatusCode: number | null
  errorSummary: string | null
}

export interface Incident {
  id: number
  monitorId: number
  monitorName: string | null
  startedAt: string
  resolvedAt: string | null
  status: IncidentStatus
  failureType: string
  summary: string
}

export interface NotificationDelivery {
  id: number
  channelId: number | null
  channel: string
  channelName: string | null
  eventType: string
  status: NotificationDeliveryStatus
  attemptCount: number
  lastAttemptAt: string | null
  sentAt: string | null
  lastErrorSummary: string | null
}

export type NotificationChannelType = 'PUSHPLUS' | 'XIZHI' | 'EMAIL'

export type ChannelTestStatus = 'SUCCESS' | 'FAILED'

export interface NotificationChannel {
  id: number
  type: NotificationChannelType
  name: string
  enabled: boolean
  maskedConfig: string
  lastTestAt: string | null
  lastTestStatus: ChannelTestStatus | null
  lastTestError: string | null
  createdAt: string
  updatedAt: string
}

export interface NotificationChannelInput {
  type: NotificationChannelType
  name: string
  enabled?: boolean
  config: Record<string, string>
}

export interface NotificationChannelUpdateInput {
  name: string
  enabled?: boolean
  config?: Record<string, string>
}

export interface NotificationChannelTestResult {
  status: ChannelTestStatus
  message: string | null
  testedAt: string
}

export interface IncidentDetail extends Incident {
  deliveries: NotificationDelivery[]
}

export interface HeartbeatTokenResponse {
  heartbeatToken: string
}

export interface SessionInfo {
  authenticated: boolean
  username: string | null
  role: string | null
  csrfToken: string
}

export interface LoginResponse {
  username: string
  role: string
}

export interface PasswordChangeInput {
  currentPassword: string
  newPassword: string
}

export interface RegisterInput {
  inviteCode: string
  username: string
  password: string
}

export interface ResetPasswordInput {
  resetCode: string
  newPassword: string
}

export interface IncidentQuery {
  status?: IncidentStatus
  monitorId?: number
  page?: number
  size?: number
}
