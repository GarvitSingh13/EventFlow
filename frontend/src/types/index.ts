export interface AuthResponse {
  token: string;
  userId: string;
  organizationId: string;
  organizationName: string;
  email: string;
  role: string;
}

export interface StatsResponse {
  totalDeliveries: number;
  delivered: number;
  pending: number;
  delivering: number;
  retrying: number;
  deadLetter: number;
}

export interface WebhookEndpoint {
  id: string;
  url: string;
  description?: string;
  secretToken: string;
  eventTypes: string[];
  status: 'ACTIVE' | 'PAUSED' | 'DISABLED';
  rateLimit: number;
  timeoutMs: number;
  createdAt: string;
}

export interface ApiKey {
  id: string;
  name: string;
  prefix: string;
  status: string;
  expiresAt?: string;
  createdAt: string;
}

export interface CreatedApiKey extends ApiKey {
  rawApiKey: string;
}

export interface DeliveryAttempt {
  id: string;
  attemptNumber: number;
  responseStatusCode?: number;
  responseHeaders?: string;
  responseBody?: string;
  executionTimeMs?: number;
  errorMessage?: string;
  executedAt: string;
}

export interface Delivery {
  id: string;
  eventId: string;
  eventType: string;
  eventPayload: string;
  endpointId: string;
  endpointUrl: string;
  status: 'PENDING' | 'DELIVERING' | 'DELIVERED' | 'RETRY_SCHEDULED' | 'DEAD_LETTER';
  attemptCount: number;
  maxAttempts: number;
  nextRetryAt: string;
  createdAt: string;
  attempts: DeliveryAttempt[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
