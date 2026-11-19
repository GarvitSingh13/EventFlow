import { AuthResponse, StatsResponse, WebhookEndpoint, ApiKey, CreatedApiKey, Delivery, PageResponse } from '../types';

const API_BASE = '/api/v1';

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem('eventflow_token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    localStorage.removeItem('eventflow_token');
    localStorage.removeItem('eventflow_user');
    window.dispatchEvent(new Event('auth-change'));
    throw new Error('Unauthorized');
  }
  if (!res.ok) {
    const errorText = await res.text();
    let msg = errorText;
    try {
      const parsed = JSON.parse(errorText);
      msg = parsed.message || parsed.error || errorText;
    } catch {}
    throw new Error(msg || `Request failed with status ${res.status}`);
  }
  if (res.status === 204) {
    return {} as T;
  }
  return res.json();
}

export const api = {
  // Auth
  async register(data: { organizationName: string; email: string; password: string }): Promise<AuthResponse> {
    const res = await fetch(`${API_BASE}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<AuthResponse>(res);
  },

  async login(data: { email: string; password: string }): Promise<AuthResponse> {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<AuthResponse>(res);
  },

  // Stats
  async getStats(): Promise<StatsResponse> {
    const res = await fetch(`${API_BASE}/deliveries/stats`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<StatsResponse>(res);
  },

  // Deliveries
  async getDeliveries(page = 0, size = 20): Promise<PageResponse<Delivery>> {
    const res = await fetch(`${API_BASE}/deliveries?page=${page}&size=${size}`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<PageResponse<Delivery>>(res);
  },

  async retryDelivery(deliveryId: string): Promise<Delivery> {
    const res = await fetch(`${API_BASE}/deliveries/${deliveryId}/retry`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });
    return handleResponse<Delivery>(res);
  },

  // Dead-Letter Queue (DLQ)
  async getDlq(page = 0, size = 20): Promise<PageResponse<Delivery>> {
    const res = await fetch(`${API_BASE}/deliveries/dlq?page=${page}&size=${size}`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<PageResponse<Delivery>>(res);
  },

  async bulkReplayDlq(deliveryIds?: string[]): Promise<{ replayedCount: number; message: string }> {
    const res = await fetch(`${API_BASE}/deliveries/dlq/replay`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(deliveryIds && deliveryIds.length > 0 ? { deliveryIds } : {}),
    });
    return handleResponse<{ replayedCount: number; message: string }>(res);
  },

  getDeliveryStreamUrl(): string {
    const token = localStorage.getItem('eventflow_token') || '';
    return `${API_BASE}/deliveries/stream?token=${encodeURIComponent(token)}`;
  },

  // Endpoints
  async getEndpoints(): Promise<WebhookEndpoint[]> {
    const res = await fetch(`${API_BASE}/endpoints`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<WebhookEndpoint[]>(res);
  },

  async createEndpoint(data: {
    url: string;
    description?: string;
    eventTypes: string[];
    rateLimit?: number;
    timeoutMs?: number;
  }): Promise<WebhookEndpoint> {
    const res = await fetch(`${API_BASE}/endpoints`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse<WebhookEndpoint>(res);
  },

  async deleteEndpoint(id: string): Promise<void> {
    const res = await fetch(`${API_BASE}/endpoints/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
    });
    return handleResponse<void>(res);
  },

  // API Keys
  async getApiKeys(): Promise<ApiKey[]> {
    const res = await fetch(`${API_BASE}/api-keys`, {
      headers: getAuthHeaders(),
    });
    return handleResponse<ApiKey[]>(res);
  },

  async createApiKey(name: string): Promise<CreatedApiKey> {
    const res = await fetch(`${API_BASE}/api-keys`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ name }),
    });
    return handleResponse<CreatedApiKey>(res);
  },

  async revokeApiKey(id: string): Promise<void> {
    const res = await fetch(`${API_BASE}/api-keys/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
    });
    return handleResponse<void>(res);
  },

  // Test Ingestion
  async ingestEvent(data: { eventType: string; payload: Record<string, any>; idempotencyKey?: string }): Promise<any> {
    const res = await fetch(`${API_BASE}/events`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse<any>(res);
  },
};
