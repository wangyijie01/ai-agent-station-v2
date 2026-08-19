import { API_ENDPOINTS, DEFAULT_HEADERS } from '../config';
import { AgentRunEvent } from './agent-runtime-service';

interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export type ServiceHealthStatus = 'UP' | 'DEGRADED' | 'DOWN';
export type OpsIncidentStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';

export interface JavaServiceTarget {
  serviceId: string;
  serviceName: string;
  environment: string;
  baseUrl: string;
  healthPath: string;
  agentId?: string;
  enabled: boolean;
  intervalSeconds: number;
  timeoutMs: number;
  failureThreshold: number;
  slowThresholdMs: number;
  createdAt: number;
  updatedAt: number;
}

export interface ServiceProbeResult {
  serviceId: string;
  status: ServiceHealthStatus;
  httpStatus?: number;
  latencyMs: number;
  observedAt: number;
  summary: string;
  evidence: string;
  consecutiveFailures: number;
}

export interface OpsIncident {
  incidentId: string;
  serviceId: string;
  serviceName: string;
  environment: string;
  status: OpsIncidentStatus;
  severity: 'WARNING' | 'CRITICAL';
  summary: string;
  evidence: string[];
  analysisPrompt: string;
  linkedRunId?: string;
  occurrenceCount: number;
  openedAt: number;
  updatedAt: number;
  acknowledgedAt?: number;
  resolvedAt?: number;
}

export interface SaveServiceRequest {
  serviceName: string;
  environment: string;
  baseUrl: string;
  healthPath: string;
  agentId?: string;
  enabled: boolean;
  intervalSeconds: number;
  timeoutMs: number;
  failureThreshold: number;
  slowThresholdMs: number;
}

export class OpsSupervisionService {
  private static readonly BASE_URL = API_ENDPOINTS.OPS.BASE;

  private static async json<T>(url: string, init?: RequestInit): Promise<T> {
    const response = await fetch(url, { ...init, headers: { ...DEFAULT_HEADERS, ...init?.headers } });
    const result: ApiResponse<T> = await response.json().catch(() => ({
      code: '0001', info: `HTTP ${response.status}`, data: undefined as T,
    }));
    if (!response.ok || result.code !== '0000') throw new Error(result.info || `HTTP ${response.status}`);
    return result.data;
  }

  static listServices(): Promise<JavaServiceTarget[]> {
    return this.json(`${this.BASE_URL}/services`);
  }

  static saveService(request: SaveServiceRequest): Promise<JavaServiceTarget> {
    return this.json(`${this.BASE_URL}/services`, { method: 'POST', body: JSON.stringify(request) });
  }

  static removeService(serviceId: string): Promise<boolean> {
    return this.json(`${this.BASE_URL}/services/${encodeURIComponent(serviceId)}`, { method: 'DELETE' });
  }

  static probeService(serviceId: string): Promise<ServiceProbeResult> {
    return this.json(`${this.BASE_URL}/services/${encodeURIComponent(serviceId)}/probe`, { method: 'POST' });
  }

  static probeAll(): Promise<ServiceProbeResult[]> {
    return this.json(`${this.BASE_URL}/services/probe-all`, { method: 'POST' });
  }

  static snapshots(): Promise<ServiceProbeResult[]> {
    return this.json(`${this.BASE_URL}/snapshots`);
  }

  static incidents(): Promise<OpsIncident[]> {
    return this.json(`${this.BASE_URL}/incidents`);
  }

  static acknowledge(incidentId: string): Promise<OpsIncident> {
    return this.json(`${this.BASE_URL}/incidents/${encodeURIComponent(incidentId)}/acknowledge`, { method: 'POST' });
  }

  static resolve(incidentId: string): Promise<OpsIncident> {
    return this.json(`${this.BASE_URL}/incidents/${encodeURIComponent(incidentId)}/resolve`, { method: 'POST' });
  }

  static async analyze(
    incidentId: string,
    onEvent: (event: AgentRunEvent) => void,
    signal?: AbortSignal,
  ): Promise<void> {
    const response = await fetch(`${this.BASE_URL}/incidents/${encodeURIComponent(incidentId)}/analyze`, {
      method: 'POST',
      headers: DEFAULT_HEADERS,
      body: JSON.stringify({ maxStep: 6, maxToolCalls: 8, approvedToolNames: [] }),
      signal,
    });
    if (!response.ok || !response.body) {
      const error = await response.json().catch(() => null);
      throw new Error(error?.info || `SSE 连接失败：HTTP ${response.status}`);
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    while (true) {
      const { value, done } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      const frames = buffer.split(/\r?\n\r?\n/);
      buffer = frames.pop() || '';
      frames.forEach(frame => this.parseFrame(frame, onEvent));
      if (done) break;
    }
    if (buffer.trim()) this.parseFrame(buffer, onEvent);
  }

  private static parseFrame(frame: string, onEvent: (event: AgentRunEvent) => void) {
    const payload = frame.split(/\r?\n/)
      .filter(line => line.startsWith('data:'))
      .map(line => line.slice(5).trim())
      .join('\n');
    if (!payload) return;
    try {
      onEvent(JSON.parse(payload) as AgentRunEvent);
    } catch {
      onEvent({ type: 'message', subType: 'raw', content: payload });
    }
  }
}
