import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Checkbox, Input, InputNumber, Layout, Select, Space, Tag, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import styled from 'styled-components';
import { Header, Sidebar } from '../components/layout';
import { theme } from '../styles/theme';
import {
  AgentEvaluation,
  AgentRunEvent,
  AgentRuntimeService,
  AgentSkill,
  AgentSummary,
  ToolAuditRecord,
} from '../services/agent-runtime-service';

const { Content } = Layout;
const { Title, Text, Paragraph } = Typography;

const PageLayout = styled(Layout)`min-height: 100vh; background: ${theme.colors.bg.secondary};`;
const Main = styled.div<{ $collapsed: boolean }>`
  display: flex; flex: 1; margin-left: ${props => props.$collapsed ? '80px' : '280px'};
  transition: margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
`;
const Area = styled(Content)`padding: ${theme.spacing.lg}; overflow-y: auto;`;
const Grid = styled.div`
  display: grid; grid-template-columns: minmax(320px, 420px) minmax(0, 1fr); gap: ${theme.spacing.lg};
  @media (max-width: 1050px) { grid-template-columns: 1fr; }
`;
const Stack = styled.div`display: flex; flex-direction: column; gap: ${theme.spacing.lg};`;
const SkillItem = styled.label`
  display: block; padding: 10px; margin-top: 8px; border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base}; cursor: pointer;
`;
const EventList = styled.div`max-height: 560px; overflow: auto;`;
const Event = styled.div`
  display: grid; grid-template-columns: 64px 145px minmax(0, 1fr); gap: 10px;
  padding: 10px 0; border-bottom: 1px solid ${theme.colors.border.secondary};
  code { word-break: break-all; white-space: pre-wrap; }
`;
const Mono = styled.code`font-family: ui-monospace, SFMono-Regular, Consolas, monospace; word-break: break-all;`;
const MetaGrid = styled.div`display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px;`;
const Meta = styled.div`padding: 12px; background: ${theme.colors.bg.secondary}; border-radius: ${theme.borderRadius.base};`;

const statusColor = (status?: string) => {
  if (status === 'SUCCEEDED') return 'green';
  if (status === 'FAILED' || status === 'CANCELED') return 'red';
  if (status === 'WAITING_USER_INPUT') return 'orange';
  return 'blue';
};

export const AgentRuntimePage: React.FC = () => {
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [agents, setAgents] = useState<AgentSummary[]>([]);
  const [skills, setSkills] = useState<AgentSkill[]>([]);
  const [agentId, setAgentId] = useState('');
  const [message, setMessage] = useState('请分析生产环境最近 30 分钟的订单服务告警，结合 TraceId 定位根因并给出止损方案。');
  const [skillIds, setSkillIds] = useState<string[]>([]);
  const [approvedTools, setApprovedTools] = useState('');
  const [maxStep, setMaxStep] = useState(5);
  const [maxToolCalls, setMaxToolCalls] = useState(8);
  const [events, setEvents] = useState<AgentRunEvent[]>([]);
  const [runId, setRunId] = useState('');
  const [traceId, setTraceId] = useState('');
  const [status, setStatus] = useState('IDLE');
  const [waitingQuestion, setWaitingQuestion] = useState('');
  const [evaluation, setEvaluation] = useState<AgentEvaluation | null>(null);
  const [audits, setAudits] = useState<ToolAuditRecord[]>([]);
  const [running, setRunning] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    Promise.all([AgentRuntimeService.listSkills(), AgentRuntimeService.listAgents()])
      .then(([skillList, agentList]) => {
        setSkills(skillList);
        setAgents(agentList);
        if (agentList[0]) setAgentId(agentList[0].agentId);
      })
      .catch(error => Toast.error(`初始化失败：${error.message}`));
    return () => abortRef.current?.abort();
  }, []);

  const selectedSkills = useMemo(() => skills.filter(skill => skillIds.includes(skill.id)), [skills, skillIds]);

  const navigateMenu = (key: string) => navigate(key.startsWith('/') ? key : `/${key}`);
  const logout = () => {
    ['token', 'userInfo', 'isLoggedIn'].forEach(key => localStorage.removeItem(key));
    navigate('/login');
  };

  const toggleSkill = (skillId: string, checked: boolean) => {
    setSkillIds(current => checked ? [...new Set([...current, skillId])] : current.filter(id => id !== skillId));
  };

  const loadDiagnostics = async (currentRunId: string) => {
    try {
      const [run, score, toolAudits] = await Promise.all([
        AgentRuntimeService.queryRun(currentRunId),
        AgentRuntimeService.evaluate(currentRunId),
        AgentRuntimeService.audits(currentRunId),
      ]);
      setStatus(run.status);
      setWaitingQuestion(run.waitingQuestion || '');
      setEvaluation(score);
      setAudits(toolAudits);
    } catch (error) {
      Toast.warning(`诊断数据暂不可用：${(error as Error).message}`);
    }
  };

  const execute = async (resume = false) => {
    if (!agentId || !message.trim()) {
      Toast.warning('请选择 Agent 并输入任务');
      return;
    }
    if (!resume) {
      setEvents([]);
      setRunId('');
      setTraceId('');
      setEvaluation(null);
      setAudits([]);
    }
    setRunning(true);
    setStatus('RUNNING');
    const controller = new AbortController();
    abortRef.current = controller;
    let activeRunId = resume ? runId : '';
    try {
      await AgentRuntimeService.execute({
        aiAgentId: agentId,
        message: message.trim(),
        sessionId: `console-${agentId}`,
        maxStep,
        maxToolCalls,
        idempotencyKey: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        resumeRunId: resume ? runId : undefined,
        requestedSkillIds: skillIds,
        approvedToolNames: approvedTools.split(',').map(value => value.trim()).filter(Boolean),
      }, event => {
        activeRunId = event.runId || activeRunId;
        setEvents(current => [...current, event]);
        if (event.runId) setRunId(event.runId);
        if (event.traceId) setTraceId(event.traceId);
        if (event.runStatus) setStatus(event.runStatus);
        if (event.type === 'waiting_user_input') setWaitingQuestion(event.content || '请补充信息');
      }, controller.signal);
      if (activeRunId) await loadDiagnostics(activeRunId);
    } catch (error) {
      if ((error as Error).name !== 'AbortError') {
        setStatus('FAILED');
        Toast.error(`执行失败：${(error as Error).message}`);
      }
    } finally {
      setRunning(false);
      abortRef.current = null;
    }
  };

  const cancel = async () => {
    if (!runId) return;
    try {
      await AgentRuntimeService.cancel(runId);
      abortRef.current?.abort();
      setStatus('CANCELED');
      Toast.success('运行已取消');
    } catch (error) {
      Toast.error(`取消失败：${(error as Error).message}`);
    }
  };

  return (
    <PageLayout>
      <Sidebar selectedKey="agent-runtime" onSelect={navigateMenu} collapsed={collapsed} />
      <Main $collapsed={collapsed}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <Header onToggleSidebar={() => setCollapsed(value => !value)} onLogout={logout} collapsed={collapsed} />
          <Area>
            <Space vertical align="start" spacing="tight" style={{ marginBottom: 20 }}>
              <Title heading={3}>Agent V2 运行台</Title>
              <Text type="tertiary">可演示 Skills 路由、受控工具调用、SSE 事件、检查点恢复与运行评测。</Text>
            </Space>
            <Grid>
              <Stack>
                <Card title="运行配置">
                  <Space vertical align="start" style={{ width: '100%' }}>
                    <Text strong>Agent</Text>
                    <Select value={agentId} onChange={value => setAgentId(String(value))} style={{ width: '100%' }}
                      optionList={agents.map(agent => ({ value: agent.agentId, label: `${agent.agentName} · ${agent.strategy || '-'}` }))} />
                    <Text strong>任务 / 恢复时的用户补充</Text>
                    <TextArea value={message} onChange={setMessage} rows={6} />
                    <Space>
                      <div><Text strong>最大步骤</Text><InputNumber value={maxStep} min={1} max={20} onChange={value => setMaxStep(Number(value) || 1)} /></div>
                      <div><Text strong>工具预算</Text><InputNumber value={maxToolCalls} min={0} max={50} onChange={value => setMaxToolCalls(Number(value) || 0)} /></div>
                    </Space>
                    <Text strong>已人工批准的高风险工具（逗号分隔）</Text>
                    <Input value={approvedTools} onChange={setApprovedTools} placeholder="例如 rollback_service" />
                    <Space>
                      <Button type="primary" loading={running} disabled={running} onClick={() => execute(false)}>开始新运行</Button>
                      {status === 'WAITING_USER_INPUT' && <Button type="warning" onClick={() => execute(true)}>从检查点恢复</Button>}
                      {runId && !['SUCCEEDED', 'FAILED', 'CANCELED'].includes(status) && <Button type="danger" onClick={cancel}>取消</Button>}
                    </Space>
                  </Space>
                </Card>
                <Card title={`Skills（${skills.length}）`}>
                  <Paragraph type="tertiary">不勾选时由触发词自动路由；勾选后显式选择优先。</Paragraph>
                  {skills.map(skill => <SkillItem key={skill.id}>
                    <Checkbox checked={skillIds.includes(skill.id)} onChange={event => toggleSkill(skill.id, Boolean(event.target.checked))}>
                      <Text strong>{skill.name}</Text> <Tag size="small">{skill.riskLevel}</Tag>
                    </Checkbox>
                    <div><Text size="small" type="tertiary">{skill.description}</Text></div>
                    <div>{skill.allowedTools.slice(0, 6).map(tool => <Tag key={tool} size="small" color="blue">{tool}</Tag>)}</div>
                  </SkillItem>)}
                </Card>
              </Stack>
              <Stack>
                <Card title="运行状态">
                  <MetaGrid>
                    <Meta><Text type="tertiary">状态</Text><div><Tag color={statusColor(status)}>{status}</Tag></div></Meta>
                    <Meta><Text type="tertiary">Run ID</Text><div><Mono>{runId || '-'}</Mono></div></Meta>
                    <Meta><Text type="tertiary">Trace ID</Text><div><Mono>{traceId || '-'}</Mono></div></Meta>
                    <Meta><Text type="tertiary">已选 Skills</Text><div>{selectedSkills.map(skill => <Tag key={skill.id}>{skill.name}</Tag>)}{!selectedSkills.length && '自动路由'}</div></Meta>
                  </MetaGrid>
                  {waitingQuestion && <div style={{ marginTop: 12 }}><Tag color="orange">等待用户输入</Tag> {waitingQuestion}</div>}
                </Card>
                <Card title={`实时事件（${events.length}）`}>
                  <EventList>
                    {!events.length && <Text type="tertiary">执行后，这里会按 sequence 展示统一 SSE 事件。</Text>}
                    {events.map((event, index) => <Event key={event.eventId || `${event.sequence}-${index}`}>
                      <Mono>#{event.sequence ?? index + 1}</Mono>
                      <div><Tag color={statusColor(event.runStatus)}>{event.type}</Tag><div><Text size="small" type="tertiary">{event.subType}</Text></div></div>
                      <code>{event.content || '-'}</code>
                    </Event>)}
                  </EventList>
                </Card>
                <Card title="评测与工具审计">
                  <MetaGrid>
                    <Meta><Text type="tertiary">运行得分</Text><Title heading={4}>{evaluation?.score ?? '-'}</Title></Meta>
                    <Meta><Text type="tertiary">工具调用</Text><Title heading={4}>{audits.length}</Title></Meta>
                    <Meta><Text type="tertiary">策略结果</Text><div>{evaluation ? <Tag color={evaluation.successful ? 'green' : 'orange'}>{evaluation.successful ? 'PASS' : 'NEEDS_REVIEW'}</Tag> : '-'}</div></Meta>
                  </MetaGrid>
                  {evaluation?.findings?.map(finding => <div key={finding}>• {finding}</div>)}
                  {audits.slice(-8).map(audit => <Event key={audit.auditId}>
                    <Tag color={audit.success ? 'green' : 'red'}>{audit.success ? '成功' : '拒绝/失败'}</Tag>
                    <Mono>{audit.toolName}</Mono>
                    <code>{audit.decision} · {audit.durationMs}ms{audit.idempotencyHit ? ' · 幂等命中' : ''}</code>
                  </Event>)}
                </Card>
              </Stack>
            </Grid>
          </Area>
        </div>
      </Main>
    </PageLayout>
  );
};

export default AgentRuntimePage;
