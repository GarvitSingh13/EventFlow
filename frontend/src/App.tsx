import React, { useState, useEffect } from 'react';
import { 
  Activity, Radio, Shield, Key, RefreshCw, Send, CheckCircle2, 
  AlertTriangle, XCircle, Clock, ChevronRight, LogOut, Plus, Trash2, Copy, Check,
  RotateCcw, Zap, Wifi, WifiOff
} from 'lucide-react';
import { api } from './services/api';
import { AuthResponse, StatsResponse, WebhookEndpoint, ApiKey, CreatedApiKey, Delivery } from './types';

export default function App() {
  const [user, setUser] = useState<AuthResponse | null>(() => {
    const saved = localStorage.getItem('eventflow_user');
    return saved ? JSON.parse(saved) : null;
  });

  const [activeTab, setActiveTab] = useState<'overview' | 'endpoints' | 'logs' | 'dlq' | 'apikeys'>('overview');
  const [sseConnected, setSseConnected] = useState(false);

  // Auth form states
  const [isRegister, setIsRegister] = useState(false);
  const [authEmail, setAuthEmail] = useState('admin@acme.com');
  const [authPassword, setAuthPassword] = useState('SuperSecretPassword123');
  const [authOrg, setAuthOrg] = useState('Acme Corp');
  const [authError, setAuthError] = useState('');
  const [authLoading, setAuthLoading] = useState(false);

  // App Data states
  const [stats, setStats] = useState<StatsResponse | null>(null);
  const [endpoints, setEndpoints] = useState<WebhookEndpoint[]>([]);
  const [deliveries, setDeliveries] = useState<Delivery[]>([]);
  const [dlqDeliveries, setDlqDeliveries] = useState<Delivery[]>([]);
  const [apiKeys, setApiKeys] = useState<ApiKey[]>([]);

  // Modal / Drawer states
  const [selectedDelivery, setSelectedDelivery] = useState<Delivery | null>(null);
  const [showEndpointModal, setShowEndpointModal] = useState(false);
  const [showKeyModal, setShowKeyModal] = useState(false);
  const [createdKey, setCreatedKey] = useState<CreatedApiKey | null>(null);
  const [copiedText, setCopiedText] = useState<string | null>(null);

  // New endpoint form
  const [newUrl, setNewUrl] = useState('https://httpbin.org/post');
  const [newDesc, setNewDesc] = useState('');
  const [newEventTypes, setNewEventTypes] = useState('order.created, payment.succeeded');
  const [newRateLimit, setNewRateLimit] = useState(100);
  const [newTimeout, setNewTimeout] = useState(5000);

  // New key form
  const [newKeyName, setNewKeyName] = useState('Primary Ingestion Key');

  // Test ingest form
  const [testEventType, setTestEventType] = useState('order.created');
  const [testPayload, setTestPayload] = useState('{\n  "orderId": "ord_10293",\n  "amount": 89.99,\n  "customer": "John Doe"\n}');
  const [testIdempotency, setTestIdempotency] = useState('');
  const [testStatus, setTestStatus] = useState<string | null>(null);

  // Polling fallback + initial fetch
  useEffect(() => {
    if (user) {
      loadAllData();
      const interval = setInterval(loadAllData, 5000);
      return () => clearInterval(interval);
    }
  }, [user]);

  // Real-Time Server-Sent Events (SSE) stream listener
  useEffect(() => {
    if (!user) return;

    let eventSource: EventSource | null = null;
    try {
      eventSource = new EventSource(api.getDeliveryStreamUrl());

      eventSource.onopen = () => {
        setSseConnected(true);
      };

      eventSource.addEventListener('CONNECTED', () => {
        setSseConnected(true);
      });

      eventSource.addEventListener('DELIVERY_UPDATE', (e) => {
        try {
          const updated: Delivery = JSON.parse(e.data);
          setDeliveries(prev => {
            const idx = prev.findIndex(d => d.id === updated.id);
            if (idx >= 0) {
              const copy = [...prev];
              copy[idx] = updated;
              return copy;
            } else {
              return [updated, ...prev];
            }
          });

          // If updated item is in selected modal, refresh it
          setSelectedDelivery(current => (current && current.id === updated.id ? updated : current));

          // Also refresh DLQ if it became or was dead-letter
          if (updated.status === 'DEAD_LETTER') {
            loadDlqData();
          }

          // Refresh stats
          api.getStats().then(setStats).catch(() => {});
        } catch (err) {
          console.error('SSE delivery update parse error:', err);
        }
      });

      eventSource.onerror = () => {
        setSseConnected(false);
      };
    } catch (err) {
      setSseConnected(false);
    }

    return () => {
      if (eventSource) {
        eventSource.close();
      }
    };
  }, [user]);

  async function loadAllData() {
    try {
      const [s, e, d, k] = await Promise.all([
        api.getStats(),
        api.getEndpoints(),
        api.getDeliveries(0, 30),
        api.getApiKeys()
      ]);
      setStats(s);
      setEndpoints(e);
      setDeliveries(d.content || []);
      setApiKeys(k);

      if (activeTab === 'dlq') {
        await loadDlqData();
      }
    } catch (err: any) {
      if (err.message === 'Unauthorized') {
        setUser(null);
      }
    }
  }

  async function loadDlqData() {
    try {
      const res = await api.getDlq(0, 50);
      setDlqDeliveries(res.content || []);
    } catch (err) {
      console.error('Failed to load DLQ:', err);
    }
  }

  async function handleAuth(e: React.FormEvent) {
    e.preventDefault();
    setAuthLoading(true);
    setAuthError('');
    try {
      let res: AuthResponse;
      if (isRegister) {
        res = await api.register({ organizationName: authOrg, email: authEmail, password: authPassword });
      } else {
        res = await api.login({ email: authEmail, password: authPassword });
      }
      localStorage.setItem('eventflow_token', res.token);
      localStorage.setItem('eventflow_user', JSON.stringify(res));
      setUser(res);
    } catch (err: any) {
      setAuthError(err.message || 'Authentication failed');
    } finally {
      setAuthLoading(false);
    }
  }

  function handleLogout() {
    localStorage.removeItem('eventflow_token');
    localStorage.removeItem('eventflow_user');
    setUser(null);
  }

  async function handleCreateEndpoint(e: React.FormEvent) {
    e.preventDefault();
    try {
      const types = newEventTypes.split(',').map(t => t.trim()).filter(Boolean);
      await api.createEndpoint({
        url: newUrl,
        description: newDesc,
        eventTypes: types.length > 0 ? types : ['*'],
        rateLimit: newRateLimit > 0 ? newRateLimit : 100,
        timeoutMs: newTimeout > 0 ? newTimeout : 5000
      });
      setShowEndpointModal(false);
      setNewUrl('');
      setNewDesc('');
      await loadAllData();
    } catch (err: any) {
      alert(err.message);
    }
  }

  async function handleDeleteEndpoint(id: string) {
    if (confirm('Are you sure you want to delete this endpoint?')) {
      await api.deleteEndpoint(id);
      await loadAllData();
    }
  }

  async function handleCreateKey(e: React.FormEvent) {
    e.preventDefault();
    try {
      const key = await api.createApiKey(newKeyName);
      setCreatedKey(key);
      await loadAllData();
    } catch (err: any) {
      alert(err.message);
    }
  }

  async function handleRevokeKey(id: string) {
    if (confirm('Revoke this API Key? Upstream services using it will lose access immediately.')) {
      await api.revokeApiKey(id);
      await loadAllData();
    }
  }

  async function handleRetryDelivery(id: string) {
    try {
      const updated = await api.retryDelivery(id);
      setDeliveries(prev => prev.map(d => d.id === id ? updated : d));
      if (selectedDelivery && selectedDelivery.id === id) {
        setSelectedDelivery(updated);
      }
      await loadAllData();
    } catch (err: any) {
      alert('Retry failed: ' + err.message);
    }
  }

  async function handleBulkReplayDlq() {
    try {
      const res = await api.bulkReplayDlq();
      alert(`Success: ${res.message}`);
      await loadAllData();
      await loadDlqData();
    } catch (err: any) {
      alert('DLQ Replay failed: ' + err.message);
    }
  }

  async function handleTestIngest(e: React.FormEvent) {
    e.preventDefault();
    setTestStatus('Dispatching...');
    try {
      const parsed = JSON.parse(testPayload);
      const res = await api.ingestEvent({
        eventType: testEventType,
        payload: parsed,
        idempotencyKey: testIdempotency || undefined
      });
      setTestStatus(`Accepted! Event ID: ${res.eventId} (${res.deliveriesScheduled} deliveries scheduled - Status: ${res.status})`);
      await loadAllData();
    } catch (err: any) {
      setTestStatus('Error: ' + err.message);
    }
  }

  function copyToClipboard(text: string) {
    navigator.clipboard.writeText(text);
    setCopiedText(text);
    setTimeout(() => setCopiedText(null), 2000);
  }

  // ------------------ AUTH SCREEN ------------------
  if (!user) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem' }}>
        <div style={{ width: '100%', maxWidth: '420px', background: '#131b2e', border: '1px solid #1e293b', borderRadius: '12px', padding: '2rem', boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.5rem' }}>
            <div style={{ background: '#4f46e5', padding: '0.6rem', borderRadius: '8px', display: 'flex' }}>
              <Radio size={24} color="#fff" />
            </div>
            <div>
              <h1 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#f8fafc' }}>EventFlow</h1>
              <p style={{ fontSize: '0.85rem', color: '#94a3b8' }}>Production Webhook Delivery Platform</p>
            </div>
          </div>

          <div style={{ display: 'flex', background: '#0b0f19', padding: '4px', borderRadius: '8px', marginBottom: '1.5rem' }}>
            <button
              onClick={() => setIsRegister(false)}
              style={{ flex: 1, padding: '0.5rem', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600, fontSize: '0.85rem', background: !isRegister ? '#1e293b' : 'transparent', color: !isRegister ? '#fff' : '#94a3b8' }}
            >
              Sign In
            </button>
            <button
              onClick={() => setIsRegister(true)}
              style={{ flex: 1, padding: '0.5rem', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600, fontSize: '0.85rem', background: isRegister ? '#1e293b' : 'transparent', color: isRegister ? '#fff' : '#94a3b8' }}
            >
              Create Account
            </button>
          </div>

          {authError && (
            <div style={{ background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.3)', color: '#f87171', padding: '0.75rem', borderRadius: '8px', fontSize: '0.85rem', marginBottom: '1rem' }}>
              {authError}
            </div>
          )}

          <form onSubmit={handleAuth} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {isRegister && (
              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>Organization Name</label>
                <input
                  type="text"
                  required
                  value={authOrg}
                  onChange={e => setAuthOrg(e.target.value)}
                  style={{ width: '100%', padding: '0.6rem 0.8rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#fff', fontSize: '0.9rem' }}
                />
              </div>
            )}
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>Email Address</label>
              <input
                type="email"
                required
                value={authEmail}
                onChange={e => setAuthEmail(e.target.value)}
                style={{ width: '100%', padding: '0.6rem 0.8rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#fff', fontSize: '0.9rem' }}
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>Password</label>
              <input
                type="password"
                required
                value={authPassword}
                onChange={e => setAuthPassword(e.target.value)}
                style={{ width: '100%', padding: '0.6rem 0.8rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#fff', fontSize: '0.9rem' }}
              />
            </div>
            <button
              type="submit"
              disabled={authLoading}
              style={{ marginTop: '0.5rem', padding: '0.75rem', background: '#4f46e5', border: 'none', borderRadius: '6px', color: '#fff', fontWeight: 600, cursor: 'pointer', transition: 'background 0.2s' }}
            >
              {authLoading ? 'Authenticating...' : isRegister ? 'Register Organization' : 'Sign In to Console'}
            </button>
          </form>
        </div>
      </div>
    );
  }

  // ------------------ MAIN CONSOLE ------------------
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* Top Navbar */}
      <header style={{ height: '64px', borderBottom: '1px solid #1e293b', background: '#0f172a', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <div style={{ background: '#4f46e5', padding: '0.4rem', borderRadius: '6px', display: 'flex' }}>
              <Radio size={20} color="#fff" />
            </div>
            <span style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff' }}>EventFlow</span>
          </div>

          <nav style={{ display: 'flex', gap: '0.5rem' }}>
            {[
              { id: 'overview', label: 'Overview', icon: Activity },
              { id: 'endpoints', label: 'Endpoints', icon: Radio },
              { id: 'logs', label: 'Delivery Logs', icon: Clock },
              { id: 'dlq', label: 'Dead-Letter Queue', icon: AlertTriangle, count: stats?.deadLetter },
              { id: 'apikeys', label: 'API Keys', icon: Key },
            ].map(tab => {
              const Icon = tab.icon;
              const active = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => {
                    setActiveTab(tab.id as any);
                    if (tab.id === 'dlq') {
                      loadDlqData();
                    }
                  }}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '0.4rem', padding: '0.4rem 0.8rem',
                    borderRadius: '6px', border: 'none', cursor: 'pointer', fontSize: '0.85rem', fontWeight: 600,
                    background: active ? '#1e293b' : 'transparent',
                    color: active ? '#818cf8' : '#94a3b8'
                  }}
                >
                  <Icon size={16} />
                  {tab.label}
                  {tab.count !== undefined && tab.count > 0 && (
                    <span style={{ background: '#ef4444', color: '#fff', fontSize: '0.7rem', padding: '1px 6px', borderRadius: '10px', marginLeft: '4px' }}>
                      {tab.count}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
          {/* Live SSE Badge */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: '0.4rem',
            padding: '4px 10px', borderRadius: '20px',
            fontSize: '0.75rem', fontWeight: 600,
            background: sseConnected ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
            border: sseConnected ? '1px solid rgba(16, 185, 129, 0.3)' : '1px solid rgba(239, 68, 68, 0.3)',
            color: sseConnected ? '#34d399' : '#f87171'
          }}>
            {sseConnected ? (
              <>
                <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#10b981', display: 'inline-block', boxShadow: '0 0 8px #10b981' }} />
                <span>Live SSE Connected</span>
              </>
            ) : (
              <>
                <WifiOff size={12} />
                <span>Reconnecting SSE</span>
              </>
            )}
          </div>

          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#f8fafc' }}>{user.organizationName}</div>
            <div style={{ fontSize: '0.75rem', color: '#64748b' }}>{user.email}</div>
          </div>
          <button
            onClick={handleLogout}
            style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', padding: '0.4rem 0.7rem', background: '#1e293b', border: '1px solid #334155', borderRadius: '6px', color: '#cbd5e1', cursor: 'pointer', fontSize: '0.8rem' }}
          >
            <LogOut size={14} />
            Logout
          </button>
        </div>
      </header>

      {/* Main Body */}
      <main style={{ flex: 1, padding: '1.5rem', maxWidth: '1200px', margin: '0 auto', width: '100%' }}>
        
        {/* TAB 1: OVERVIEW */}
        {activeTab === 'overview' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
            {/* Stats Cards */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
              <div style={{ background: '#131b2e', border: '1px solid #1e293b', padding: '1.25rem', borderRadius: '8px' }}>
                <div style={{ fontSize: '0.8rem', color: '#94a3b8', fontWeight: 600 }}>Total Deliveries</div>
                <div style={{ fontSize: '2rem', fontWeight: 700, color: '#fff', marginTop: '0.25rem' }}>{stats?.totalDeliveries ?? 0}</div>
              </div>
              <div style={{ background: '#131b2e', border: '1px solid rgba(16, 185, 129, 0.3)', padding: '1.25rem', borderRadius: '8px' }}>
                <div style={{ fontSize: '0.8rem', color: '#34d399', fontWeight: 600 }}>Delivered (2xx)</div>
                <div style={{ fontSize: '2rem', fontWeight: 700, color: '#34d399', marginTop: '0.25rem' }}>{stats?.delivered ?? 0}</div>
              </div>
              <div style={{ background: '#131b2e', border: '1px solid rgba(168, 85, 247, 0.3)', padding: '1.25rem', borderRadius: '8px' }}>
                <div style={{ fontSize: '0.8rem', color: '#c084fc', fontWeight: 600 }}>In-Flight</div>
                <div style={{ fontSize: '2rem', fontWeight: 700, color: '#c084fc', marginTop: '0.25rem' }}>{(stats?.delivering ?? 0) + (stats?.pending ?? 0)}</div>
              </div>
              <div style={{ background: '#131b2e', border: '1px solid rgba(245, 158, 11, 0.3)', padding: '1.25rem', borderRadius: '8px' }}>
                <div style={{ fontSize: '0.8rem', color: '#fbbf24', fontWeight: 600 }}>Retrying</div>
                <div style={{ fontSize: '2rem', fontWeight: 700, color: '#fbbf24', marginTop: '0.25rem' }}>{stats?.retrying ?? 0}</div>
              </div>
              <div 
                onClick={() => { setActiveTab('dlq'); loadDlqData(); }}
                style={{ background: '#131b2e', border: '1px solid rgba(239, 68, 68, 0.3)', padding: '1.25rem', borderRadius: '8px', cursor: 'pointer', transition: 'border-color 0.2s' }}
              >
                <div style={{ fontSize: '0.8rem', color: '#f87171', fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span>Dead-Letter (Exhausted)</span>
                  <ChevronRight size={14} />
                </div>
                <div style={{ fontSize: '2rem', fontWeight: 700, color: '#f87171', marginTop: '0.25rem' }}>{stats?.deadLetter ?? 0}</div>
              </div>
            </div>

            {/* Quick Test Ingest Section */}
            <div style={{ background: '#131b2e', border: '1px solid #1e293b', borderRadius: '8px', padding: '1.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
                <div>
                  <h3 style={{ fontSize: '1rem', fontWeight: 700, color: '#fff' }}>Test Event Ingestion</h3>
                  <p style={{ fontSize: '0.8rem', color: '#94a3b8' }}>Publish a mock webhook event to trigger your registered endpoints with idempotency protection.</p>
                </div>
                <button
                  onClick={loadAllData}
                  style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', padding: '0.4rem 0.8rem', background: '#1e293b', border: '1px solid #334155', borderRadius: '6px', color: '#94a3b8', cursor: 'pointer', fontSize: '0.8rem' }}
                >
                  <RefreshCw size={14} /> Refresh
                </button>
              </div>

              <form onSubmit={handleTestIngest} style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '1rem' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>Event Type</label>
                    <input
                      type="text"
                      value={testEventType}
                      onChange={e => setTestEventType(e.target.value)}
                      placeholder="e.g. order.created"
                      style={{ width: '100%', padding: '0.5rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#fff', fontSize: '0.85rem' }}
                    />
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>Idempotency Key (Optional)</label>
                    <input
                      type="text"
                      value={testIdempotency}
                      onChange={e => setTestIdempotency(e.target.value)}
                      placeholder="e.g. idem_test_001"
                      style={{ width: '100%', padding: '0.5rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#fff', fontSize: '0.85rem' }}
                    />
                    <div style={{ fontSize: '0.7rem', color: '#64748b', marginTop: '2px' }}>
                      Duplicate keys return existing dispatch without duplicating deliveries.
                    </div>
                  </div>
                  <button
                    type="submit"
                    style={{ marginTop: 'auto', padding: '0.6rem', background: '#4f46e5', border: 'none', borderRadius: '6px', color: '#fff', fontWeight: 600, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.4rem' }}
                  >
                    <Send size={15} /> Dispatch Event
                  </button>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>JSON Payload</label>
                  <textarea
                    rows={6}
                    value={testPayload}
                    onChange={e => setTestPayload(e.target.value)}
                    style={{ width: '100%', padding: '0.5rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#a5f3fc', fontSize: '0.85rem', fontFamily: 'monospace' }}
                  />
                  {testStatus && (
                    <div style={{ marginTop: '0.5rem', fontSize: '0.8rem', color: testStatus.startsWith('Error') ? '#f87171' : '#34d399' }}>
                      {testStatus}
                    </div>
                  )}
                </div>
              </form>
            </div>
          </div>
        )}

        {/* TAB 2: ENDPOINTS */}
        {activeTab === 'endpoints' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>Webhook Endpoints</h2>
                <p style={{ fontSize: '0.85rem', color: '#94a3b8' }}>Target destination URLs configured with rate limiting, timeouts, and HMAC-SHA256 secrets.</p>
              </div>
              <button
                onClick={() => setShowEndpointModal(true)}
                style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', padding: '0.5rem 1rem', background: '#4f46e5', border: 'none', borderRadius: '6px', color: '#fff', fontWeight: 600, cursor: 'pointer' }}
              >
                <Plus size={16} /> Register Endpoint
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {endpoints.length === 0 ? (
                <div style={{ background: '#131b2e', border: '1px dashed #334155', padding: '2rem', textAlign: 'center', borderRadius: '8px', color: '#64748b' }}>
                  No webhook endpoints registered yet. Click "Register Endpoint" to add your first destination.
                </div>
              ) : (
                endpoints.map(e => (
                  <div key={e.id} style={{ background: '#131b2e', border: '1px solid #1e293b', borderRadius: '8px', padding: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <span style={{ fontWeight: 600, color: '#f8fafc', fontSize: '0.95rem' }}>{e.url}</span>
                        <span style={{ fontSize: '0.7rem', padding: '2px 6px', borderRadius: '4px', background: e.status === 'ACTIVE' ? 'rgba(16,185,129,0.2)' : 'rgba(239,68,68,0.2)', color: e.status === 'ACTIVE' ? '#34d399' : '#f87171' }}>
                          {e.status}
                        </span>
                      </div>
                      {e.description && <div style={{ fontSize: '0.8rem', color: '#94a3b8' }}>{e.description}</div>}
                      <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.25rem', alignItems: 'center', flexWrap: 'wrap' }}>
                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Events:</span>
                        {e.eventTypes.map(t => (
                          <span key={t} style={{ fontSize: '0.75rem', background: '#1e293b', padding: '2px 6px', borderRadius: '4px', color: '#cbd5e1' }}>{t}</span>
                        ))}
                        <span style={{ fontSize: '0.75rem', color: '#818cf8', background: 'rgba(99,102,241,0.1)', padding: '2px 6px', borderRadius: '4px' }}>
                          Rate Limit: {e.rateLimit || 100} req/s
                        </span>
                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Timeout: {e.timeoutMs}ms</span>
                      </div>
                      <div style={{ fontSize: '0.75rem', color: '#64748b', display: 'flex', alignItems: 'center', gap: '0.4rem', marginTop: '0.25rem' }}>
                        <span>HMAC Secret:</span>
                        <code style={{ color: '#a5f3fc', background: '#0b0f19', padding: '2px 6px', borderRadius: '4px' }}>{e.secretToken}</code>
                        <button
                          onClick={() => copyToClipboard(e.secretToken)}
                          style={{ background: 'transparent', border: 'none', color: '#94a3b8', cursor: 'pointer' }}
                        >
                          {copiedText === e.secretToken ? <Check size={12} color="#34d399" /> : <Copy size={12} />}
                        </button>
                      </div>
                    </div>
                    <button
                      onClick={() => handleDeleteEndpoint(e.id)}
                      style={{ background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.2)', color: '#f87171', padding: '0.5rem', borderRadius: '6px', cursor: 'pointer' }}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {/* TAB 3: LOGS & AUDIT HISTORY */}
        {activeTab === 'logs' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>Delivery Logs</h2>
                <p style={{ fontSize: '0.85rem', color: '#94a3b8' }}>Real-time audit history of webhook dispatches, response times, and retry attempts.</p>
              </div>
              <button
                onClick={loadAllData}
                style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', padding: '0.4rem 0.8rem', background: '#1e293b', border: '1px solid #334155', borderRadius: '6px', color: '#94a3b8', cursor: 'pointer', fontSize: '0.8rem' }}
              >
                <RefreshCw size={14} /> Refresh
              </button>
            </div>

            <div style={{ background: '#131b2e', border: '1px solid #1e293b', borderRadius: '8px', overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid #1e293b', background: '#0f172a', color: '#94a3b8' }}>
                    <th style={{ padding: '0.75rem 1rem' }}>Status</th>
                    <th style={{ padding: '0.75rem 1rem' }}>Event Type</th>
                    <th style={{ padding: '0.75rem 1rem' }}>Endpoint</th>
                    <th style={{ padding: '0.75rem 1rem' }}>Attempts</th>
                    <th style={{ padding: '0.75rem 1rem' }}>Latency</th>
                    <th style={{ padding: '0.75rem 1rem' }}>Time</th>
                    <th style={{ padding: '0.75rem 1rem', textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {deliveries.length === 0 ? (
                    <tr>
                      <td colSpan={7} style={{ padding: '2rem', textAlign: 'center', color: '#64748b' }}>
                        No deliveries recorded yet. Dispatch a test event from the Overview tab!
                      </td>
                    </tr>
                  ) : (
                    deliveries.map(d => {
                      const latestAttempt = d.attempts && d.attempts.length > 0 ? d.attempts[d.attempts.length - 1] : null;
                      return (
                        <tr 
                          key={d.id}
                          onClick={() => setSelectedDelivery(d)}
                          style={{ borderBottom: '1px solid #1e293b', cursor: 'pointer', transition: 'background 0.15s' }}
                          onMouseEnter={e => (e.currentTarget.style.background = '#1a233a')}
                          onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                        >
                          <td style={{ padding: '0.75rem 1rem' }}>
                            <span className={`badge-${d.status.toLowerCase().replace('_', '-')}`} style={{ padding: '3px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600 }}>
                              {d.status}
                            </span>
                          </td>
                          <td style={{ padding: '0.75rem 1rem', fontWeight: 600, color: '#f8fafc' }}>{d.eventType}</td>
                          <td style={{ padding: '0.75rem 1rem', color: '#94a3b8', maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{d.endpointUrl}</td>
                          <td style={{ padding: '0.75rem 1rem', color: '#cbd5e1' }}>{d.attemptCount}/{d.maxAttempts}</td>
                          <td style={{ padding: '0.75rem 1rem', color: '#34d399' }}>{latestAttempt?.executionTimeMs ? `${latestAttempt.executionTimeMs}ms` : '-'}</td>
                          <td style={{ padding: '0.75rem 1rem', color: '#64748b', fontSize: '0.8rem' }}>{new Date(d.createdAt).toLocaleTimeString()}</td>
                          <td style={{ padding: '0.75rem 1rem', textAlign: 'right' }}>
                            <button
                              onClick={(e) => { e.stopPropagation(); handleRetryDelivery(d.id); }}
                              style={{ padding: '3px 8px', background: '#312e81', border: '1px solid #4338ca', borderRadius: '4px', color: '#c7d2fe', fontSize: '0.75rem', cursor: 'pointer' }}
                            >
                              Retry
                            </button>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* TAB 4: DEAD-LETTER QUEUE (DLQ) */}
        {activeTab === 'dlq' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#f87171', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <AlertTriangle size={20} /> Dead-Letter Queue (DLQ)
                </h2>
                <p style={{ fontSize: '0.85rem', color: '#94a3b8' }}>
                  Deliveries that permanently failed all configured retries. Inspect root-cause errors and trigger bulk replay.
                </p>
              </div>
              <div style={{ display: 'flex', gap: '0.75rem' }}>
                <button
                  onClick={loadDlqData}
                  style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', padding: '0.4rem 0.8rem', background: '#1e293b', border: '1px solid #334155', borderRadius: '6px', color: '#94a3b8', cursor: 'pointer', fontSize: '0.8rem' }}
                >
                  <RefreshCw size={14} /> Refresh
                </button>
                <button
                  onClick={handleBulkReplayDlq}
                  disabled={dlqDeliveries.length === 0}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '0.4rem', padding: '0.5rem 1rem',
                    background: dlqDeliveries.length > 0 ? '#dc2626' : '#334155',
                    border: 'none', borderRadius: '6px', color: '#fff', fontWeight: 600,
                    cursor: dlqDeliveries.length > 0 ? 'pointer' : 'not-allowed'
                  }}
                >
                  <RotateCcw size={16} /> Replay All DLQ ({dlqDeliveries.length})
                </button>
              </div>
            </div>

            <div style={{ background: '#131b2e', border: '1px solid #1e293b', borderRadius: '8px', overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid #1e293b', background: '#0f172a', color: '#94a3b8' }}>
                    <th style={{ padding: '0.75rem 1rem' }}>Event Type</th>
                    <th style={{ padding: '0.75rem 1rem' }}>Target Endpoint</th>
                    <th style={{ padding: '0.75rem 1rem' }}>Attempts</th>
                    <th style={{ padding: '0.75rem 1rem' }}>Final Error</th>
                    <th style={{ padding: '0.75rem 1rem' }}>Created At</th>
                    <th style={{ padding: '0.75rem 1rem', textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {dlqDeliveries.length === 0 ? (
                    <tr>
                      <td colSpan={6} style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
                        <CheckCircle2 size={32} color="#10b981" style={{ margin: '0 auto 0.5rem auto' }} />
                        <div>No dead-lettered webhooks! All deliveries are healthy or successfully delivered.</div>
                      </td>
                    </tr>
                  ) : (
                    dlqDeliveries.map(d => {
                      const latestAttempt = d.attempts && d.attempts.length > 0 ? d.attempts[d.attempts.length - 1] : null;
                      return (
                        <tr 
                          key={d.id}
                          onClick={() => setSelectedDelivery(d)}
                          style={{ borderBottom: '1px solid #1e293b', cursor: 'pointer', transition: 'background 0.15s' }}
                          onMouseEnter={e => (e.currentTarget.style.background = '#1a233a')}
                          onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                        >
                          <td style={{ padding: '0.75rem 1rem', fontWeight: 600, color: '#f8fafc' }}>{d.eventType}</td>
                          <td style={{ padding: '0.75rem 1rem', color: '#94a3b8', maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{d.endpointUrl}</td>
                          <td style={{ padding: '0.75rem 1rem', color: '#f87171', fontWeight: 700 }}>{d.attemptCount}/{d.maxAttempts}</td>
                          <td style={{ padding: '0.75rem 1rem', color: '#fca5a5', maxWidth: '250px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {latestAttempt?.errorMessage || (latestAttempt?.responseStatusCode ? `HTTP ${latestAttempt.responseStatusCode}` : 'Exhausted')}
                          </td>
                          <td style={{ padding: '0.75rem 1rem', color: '#64748b', fontSize: '0.8rem' }}>{new Date(d.createdAt).toLocaleString()}</td>
                          <td style={{ padding: '0.75rem 1rem', textAlign: 'right' }}>
                            <button
                              onClick={(e) => { e.stopPropagation(); handleRetryDelivery(d.id); }}
                              style={{ padding: '4px 10px', background: '#dc2626', border: 'none', borderRadius: '4px', color: '#fff', fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer' }}
                            >
                              Replay Now
                            </button>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* TAB 5: API KEYS */}
        {activeTab === 'apikeys' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>Ingestion API Keys</h2>
                <p style={{ fontSize: '0.85rem', color: '#94a3b8' }}>Machine-to-machine authentication tokens passed in the <code style={{ color: '#818cf8' }}>X-API-Key</code> header to push events.</p>
              </div>
              <button
                onClick={() => setShowKeyModal(true)}
                style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', padding: '0.5rem 1rem', background: '#4f46e5', border: 'none', borderRadius: '6px', color: '#fff', fontWeight: 600, cursor: 'pointer' }}
              >
                <Plus size={16} /> Generate New Key
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {apiKeys.map(k => (
                <div key={k.id} style={{ background: '#131b2e', border: '1px solid #1e293b', borderRadius: '8px', padding: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <span style={{ fontWeight: 600, color: '#f8fafc' }}>{k.name}</span>
                      <span style={{ fontSize: '0.7rem', padding: '2px 6px', borderRadius: '4px', background: k.status === 'ACTIVE' ? 'rgba(16,185,129,0.2)' : 'rgba(239,68,68,0.2)', color: k.status === 'ACTIVE' ? '#34d399' : '#f87171' }}>
                        {k.status}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#94a3b8', marginTop: '0.2rem' }}>
                      Prefix: <code style={{ color: '#a5f3fc', background: '#0b0f19', padding: '2px 4px', borderRadius: '4px' }}>{k.prefix}...</code>
                    </div>
                  </div>
                  {k.status === 'ACTIVE' && (
                    <button
                      onClick={() => handleRevokeKey(k.id)}
                      style={{ padding: '0.4rem 0.8rem', background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.2)', color: '#f87171', borderRadius: '6px', fontSize: '0.8rem', cursor: 'pointer' }}
                    >
                      Revoke
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </main>

      {/* DETAIL DRAWER / MODAL */}
      {selectedDelivery && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', display: 'flex', justifyContent: 'flex-end', zIndex: 50 }}>
          <div style={{ width: '100%', maxWidth: '640px', background: '#0f172a', height: '100%', padding: '1.5rem', overflowY: 'auto', borderLeft: '1px solid #1e293b', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <span className={`badge-${selectedDelivery.status.toLowerCase().replace('_', '-')}`} style={{ padding: '3px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 600 }}>
                  {selectedDelivery.status}
                </span>
                <h3 style={{ fontSize: '1.2rem', fontWeight: 700, color: '#fff', marginTop: '0.5rem' }}>{selectedDelivery.eventType}</h3>
              </div>
              <button
                onClick={() => setSelectedDelivery(null)}
                style={{ background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '1.5rem', cursor: 'pointer' }}
              >
                &times;
              </button>
            </div>

            <div>
              <div style={{ fontSize: '0.75rem', color: '#64748b' }}>Target URL</div>
              <div style={{ fontSize: '0.85rem', color: '#cbd5e1', wordBreak: 'break-all' }}>{selectedDelivery.endpointUrl}</div>
            </div>

            <div>
              <div style={{ fontSize: '0.75rem', color: '#64748b', marginBottom: '4px' }}>Payload JSON</div>
              <pre style={{ background: '#0b0f19', padding: '0.75rem', borderRadius: '6px', fontSize: '0.8rem', color: '#a5f3fc', overflowX: 'auto' }}>
                {JSON.stringify(JSON.parse(selectedDelivery.eventPayload), null, 2)}
              </pre>
            </div>

            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <h4 style={{ fontSize: '0.9rem', fontWeight: 700, color: '#fff' }}>Execution Attempt Logs ({selectedDelivery.attempts?.length || 0})</h4>
                <button
                  onClick={() => handleRetryDelivery(selectedDelivery.id)}
                  style={{ padding: '0.3rem 0.7rem', background: '#4f46e5', border: 'none', borderRadius: '4px', color: '#fff', fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer' }}
                >
                  Trigger Manual Retry
                </button>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                {selectedDelivery.attempts?.map(a => (
                  <div key={a.id} style={{ background: '#131b2e', border: '1px solid #1e293b', borderRadius: '6px', padding: '0.75rem', fontSize: '0.8rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                      <span style={{ fontWeight: 600, color: '#f8fafc' }}>Attempt #{a.attemptNumber}</span>
                      <span style={{ color: a.responseStatusCode && a.responseStatusCode >= 200 && a.responseStatusCode < 300 ? '#34d399' : '#f87171', fontWeight: 700 }}>
                        {a.responseStatusCode ? `HTTP ${a.responseStatusCode}` : 'CONNECTION FAILED'}
                      </span>
                    </div>
                    <div style={{ color: '#94a3b8', fontSize: '0.75rem' }}>
                      Latency: {a.executionTimeMs}ms | Executed: {new Date(a.executedAt).toLocaleTimeString()}
                    </div>
                    {a.errorMessage && (
                      <div style={{ color: '#f87171', marginTop: '4px', background: 'rgba(239,68,68,0.1)', padding: '4px', borderRadius: '4px' }}>
                        Error: {a.errorMessage}
                      </div>
                    )}
                    {a.responseBody && (
                      <div style={{ marginTop: '4px' }}>
                        <div style={{ fontSize: '0.7rem', color: '#64748b' }}>Response Snippet:</div>
                        <code style={{ fontSize: '0.75rem', color: '#cbd5e1', wordBreak: 'break-all' }}>{a.responseBody}</code>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* REGISTER ENDPOINT MODAL */}
      {showEndpointModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: '1rem' }}>
          <div style={{ background: '#131b2e', border: '1px solid #1e293b', borderRadius: '12px', width: '100%', maxWidth: '480px', padding: '1.5rem' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff', marginBottom: '1rem' }}>Register Webhook Endpoint</h3>
            <form onSubmit={handleCreateEndpoint} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>Destination URL</label>
                <input
                  type="url"
                  required
                  value={newUrl}
                  onChange={e => setNewUrl(e.target.value)}
                  placeholder="https://api.example.com/webhook"
                  style={{ width: '100%', padding: '0.5rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#fff', fontSize: '0.85rem' }}
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>Description (Optional)</label>
                <input
                  type="text"
                  value={newDesc}
                  onChange={e => setNewDesc(e.target.value)}
                  placeholder="e.g. Shipping Partner Webhook"
                  style={{ width: '100%', padding: '0.5rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#fff', fontSize: '0.85rem' }}
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>Subscribed Events (Comma-separated, or * for all)</label>
                <input
                  type="text"
                  value={newEventTypes}
                  onChange={e => setNewEventTypes(e.target.value)}
                  placeholder="order.created, payment.succeeded"
                  style={{ width: '100%', padding: '0.5rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#fff', fontSize: '0.85rem' }}
                />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>Rate Limit (req/sec)</label>
                  <input
                    type="number"
                    min="1"
                    value={newRateLimit}
                    onChange={e => setNewRateLimit(Number(e.target.value))}
                    style={{ width: '100%', padding: '0.5rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#fff', fontSize: '0.85rem' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>Timeout (ms)</label>
                  <input
                    type="number"
                    min="500"
                    value={newTimeout}
                    onChange={e => setNewTimeout(Number(e.target.value))}
                    style={{ width: '100%', padding: '0.5rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#fff', fontSize: '0.85rem' }}
                  />
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '1rem' }}>
                <button
                  type="button"
                  onClick={() => setShowEndpointModal(false)}
                  style={{ padding: '0.5rem 1rem', background: '#1e293b', border: 'none', borderRadius: '6px', color: '#cbd5e1', cursor: 'pointer' }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  style={{ padding: '0.5rem 1rem', background: '#4f46e5', border: 'none', borderRadius: '6px', color: '#fff', fontWeight: 600, cursor: 'pointer' }}
                >
                  Save Endpoint
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CREATE API KEY MODAL */}
      {showKeyModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: '1rem' }}>
          <div style={{ background: '#131b2e', border: '1px solid #1e293b', borderRadius: '12px', width: '100%', maxWidth: '440px', padding: '1.5rem' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#fff', marginBottom: '1rem' }}>Generate Ingestion Key</h3>
            {createdKey ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div style={{ background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.3)', color: '#34d399', padding: '0.75rem', borderRadius: '6px', fontSize: '0.85rem' }}>
                  Key generated successfully! Copy it now; it will <strong>never be shown again</strong>.
                </div>
                <div>
                  <div style={{ fontSize: '0.75rem', color: '#64748b', marginBottom: '4px' }}>Secret API Key</div>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <input
                      type="text"
                      readOnly
                      value={createdKey.rawApiKey}
                      style={{ flex: 1, padding: '0.5rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#a5f3fc', fontSize: '0.8rem', fontFamily: 'monospace' }}
                    />
                    <button
                      onClick={() => copyToClipboard(createdKey.rawApiKey)}
                      style={{ padding: '0.5rem 0.8rem', background: '#4f46e5', border: 'none', borderRadius: '6px', color: '#fff', cursor: 'pointer' }}
                    >
                      {copiedText === createdKey.rawApiKey ? <Check size={16} /> : <Copy size={16} />}
                    </button>
                  </div>
                </div>
                <button
                  onClick={() => { setShowKeyModal(false); setCreatedKey(null); }}
                  style={{ padding: '0.6rem', background: '#1e293b', border: 'none', borderRadius: '6px', color: '#fff', fontWeight: 600, cursor: 'pointer' }}
                >
                  Done
                </button>
              </div>
            ) : (
              <form onSubmit={handleCreateKey} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: '#cbd5e1', marginBottom: '4px' }}>Key Label / Name</label>
                  <input
                    type="text"
                    required
                    value={newKeyName}
                    onChange={e => setNewKeyName(e.target.value)}
                    placeholder="e.g. Order Service Ingestion"
                    style={{ width: '100%', padding: '0.5rem', background: '#0b0f19', border: '1px solid #334155', borderRadius: '6px', color: '#fff', fontSize: '0.85rem' }}
                  />
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '1rem' }}>
                  <button
                    type="button"
                    onClick={() => setShowKeyModal(false)}
                    style={{ padding: '0.5rem 1rem', background: '#1e293b', border: 'none', borderRadius: '6px', color: '#cbd5e1', cursor: 'pointer' }}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    style={{ padding: '0.5rem 1rem', background: '#4f46e5', border: 'none', borderRadius: '6px', color: '#fff', fontWeight: 600, cursor: 'pointer' }}
                  >
                    Generate
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
