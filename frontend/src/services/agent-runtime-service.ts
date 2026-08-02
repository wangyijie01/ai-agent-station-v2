import { API_ENDPOINTS, DEFAULT_HEADERS } from '../config';

export interface ApiResponse<T> {
  code: string;
  info: string;
  data: T;
}

export interface AgentSummary {
  agentId: string;
  agentName: string;
  description?: string;
  strategy?: string;
  status?: number;
}

export interface AgentSkill {
  id: string;
  name: string;
  version: string;
  description: string;
  scenes: string[];
  triggerWords: string[];
  allowedTools: string[];
  riskLevel: string;
  priority: number;
}

export interface AgentRunEvent {
  eventId?: string;
  runId?: string;
  traceId?: string;
  sequence?: number;
  type: string;
  subType?: string;
  step?: number;
  content?: string;
  completed?: boolean;
  timestamp?: number;
  runStatus?: string;
}

export interface AgentRun {
  runId: string;
  traceId: string;
  agentId: string;
  strategy: string;
  status: string;
  selectedSkillIds: string[];
  allowedTools: string[];
  currentStep: number;
  maxStep: number;
  eventCount: number;
  waitingQuestion?: string;
  errorMessage?: string;
  createdAt: number;
  updatedAt: number;
}

export interface AgentEvaluation {
  runId: string;
  score: number;
  successful: boolean;
  dimensions: Record<string, number>;
  findings: string[];
  evaluatedAt: number;
}

export interface ToolAuditRecord {
  auditId: string;
  toolName: string;
  riskLevel: string;
  decision: string;
  inputSummary: string;
  success: boolean;
  idempotencyHit: boolean;
  attempt: number;
  durationMs: number;
  errorMessage?: string;
  timestamp: number;
}

export interface ExecuteAgentRequest {
  aiAgentId: string;
  message: string;
  sessionId: string;
  maxStep: number;
  idempotencyKey: string;
  resumeRunId?: string;
  requestedSkillIds: string[];
  approvedToolNames: string[];
  maxToolCalls: number;
}

export class AgentRuntimeService {
  private static readonly BASE_URL = API_ENDPOINTS.AI_AGENT.BASE;

  private static async json<T>(url: string, init?: RequestInit): Promise<T> {
    const response = await fetch(url, { ...init, headers: { ...DEFAULT_HEADERS, ...init?.headers } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const result: ApiResponse<T> = await response.json();
    if (result.code !== '0000') throw new Error(result.info || '请求失败');
    return result.data;
  }

  static listSkills(): Promise<AgentSkill[]> {
    return this.json(`${this.BASE_URL}${API_ENDPOINTS.AI_AGENT.SKILLS}`);
  }

  static listAgents(): Promise<AgentSummary[]> {
    return this.json(`${this.BASE_URL}${API_ENDPOINTS.AI_AGENT.QUERY_AVAILABLE}`);
  }

  static queryRun(runId: string): Promise<AgentRun> {
    return this.json(`${this.BASE_URL}${API_ENDPOINTS.AI_AGENT.RUNS}/${encodeURIComponent(runId)}`);
  }

  static evaluate(runId: string): Promise<AgentEvaluation> {
    return this.json(`${this.BASE_URL}${API_ENDPOINTS.AI_AGENT.RUNS}/${encodeURIComponent(runId)}/evaluation`);
  }

  static audits(runId: string): Promise<ToolAuditRecord[]> {
    return this.json(`${this.BASE_URL}${API_ENDPOINTS.AI_AGENT.RUNS}/${encodeURIComponent(runId)}/tool-audits`);
  }

  static cancel(runId: string): Promise<boolean> {
    return this.json(`${this.BASE_URL}${API_ENDPOINTS.AI_AGENT.RUNS}/${encodeURIComponent(runId)}/cancel`, {
      method: 'POST',
    });
  }

  static async execute(
    request: ExecuteAgentRequest,
    onEvent: (event: AgentRunEvent) => void,
    signal?: AbortSignal,
  ): Promise<void> {
    const response = await fetch(`${this.BASE_URL}${API_ENDPOINTS.AI_AGENT.AUTO_AGENT}`, {
      method: 'POST',
      headers: DEFAULT_HEADERS,
      body: JSON.stringify(request),
      signal,
    });
    if (!response.ok || !response.body) throw new Error(`SSE 连接失败：HTTP ${response.status}`);

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
    const payload = frame
      .split(/\r?\n/)
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
