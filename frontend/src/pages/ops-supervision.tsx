import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Banner, Button, Card, Input, InputNumber, Layout, Popconfirm, Select, Space, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import styled from 'styled-components';
import { Header, Sidebar } from '../components/layout';
import { theme } from '../styles/theme';
import { AgentRunEvent } from '../services/agent-runtime-service';
import {
  JavaServiceTarget,
  OpsIncident,
  OpsSupervisionService,
  SaveServiceRequest,
  ServiceProbeResult,
} from '../services/ops-supervision-service';

const { Content } = Layout;
const { Title, Text } = Typography;

const PageLayout = styled(Layout)`min-height: 100vh; background: ${theme.colors.bg.secondary};`;
const Main = styled.div<{ $collapsed: boolean }>`
  display: flex; flex: 1; margin-left: ${props => props.$collapsed ? '80px' : '280px'};
  transition: margin-left ${theme.animation.duration.normal} ${theme.animation.easing.cubic};
`;
const Area = styled(Content)`padding: ${theme.spacing.lg}; overflow-y: auto;`;
const Grid = styled.div`
  display: grid; grid-template-columns: minmax(320px, 400px) minmax(0, 1fr); gap: ${theme.spacing.lg};
  @media (max-width: 1050px) { grid-template-columns: 1fr; }
`;
const Stack = styled.div`display: flex; flex-direction: column; gap: ${theme.spacing.lg};`;
const FormGrid = styled.div`display: grid; grid-template-columns: 1fr 1fr; gap: 12px;`;
const Field = styled.label`
  display: flex; flex-direction: column; gap: 6px;
  &.wide { grid-column: 1 / -1; }
`;
const ServiceGrid = styled.div`display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 12px;`;
const ServiceCard = styled.div`
  border: 1px solid ${theme.colors.border.secondary}; border-radius: ${theme.borderRadius.base}; padding: 14px;
  background: ${theme.colors.bg.primary};
`;
const Row = styled.div`display: flex; align-items: center; justify-content: space-between; gap: 12px;`;
const Mono = styled.code`font-family: ui-monospace, SFMono-Regular, Consolas, monospace; word-break: break-all;`;
const Incident = styled.div`
  padding: 14px 0; border-bottom: 1px solid ${theme.colors.border.secondary};
  &:last-child { border-bottom: none; }
`;
const EventLog = styled.div`max-height: 260px; overflow: auto; margin-top: 12px;`;
const EventLine = styled.div`
  display: grid; grid-template-columns: 56px 140px minmax(0, 1fr); gap: 8px; padding: 8px 0;
  border-bottom: 1px dashed ${theme.colors.border.secondary};
`;

const initialForm: SaveServiceRequest = {
  serviceName: '', environment: 'dev', baseUrl: 'http://127.0.0.1:8080',
  healthPath: '/actuator/health', agentId: '3', enabled: true,
  intervalSeconds: 30, timeoutMs: 3000, failureThreshold: 3, slowThresholdMs: 1500,
};

const healthColor = (status?: string) => status === 'UP' ? 'green' : status === 'DOWN' ? 'red' : status === 'DEGRADED' ? 'orange' : 'grey';
const incidentColor = (severity: string) => severity === 'CRITICAL' ? 'red' : 'orange';
const time = (value?: number) => value ? new Date(value).toLocaleString() : '-';

export const OpsSupervisionPage: React.FC = () => {
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [services, setServices] = useState<JavaServiceTarget[]>([]);
  const [snapshots, setSnapshots] = useState<ServiceProbeResult[]>([]);
  const [incidents, setIncidents] = useState<OpsIncident[]>([]);
  const [form, setForm] = useState<SaveServiceRequest>(initialForm);
  const [saving, setSaving] = useState(false);
  const [probing, setProbing] = useState('');
  const [analyzing, setAnalyzing] = useState('');
  const [events, setEvents] = useState<AgentRunEvent[]>([]);
  const abortRef = useRef<AbortController | null>(null);

  const load = useCallback(async (quiet = false) => {
    try {
      const [targetList, snapshotList, incidentList] = await Promise.all([
        OpsSupervisionService.listServices(),
        OpsSupervisionService.snapshots(),
        OpsSupervisionService.incidents(),
      ]);
      setServices(targetList);
      setSnapshots(snapshotList);
      setIncidents(incidentList);
    } catch (error) {
      if (!quiet) Toast.error(`加载失败：${(error as Error).message}`);
    }
  }, []);

  useEffect(() => {
    load();
    const timer = window.setInterval(() => load(true), 15000);
    return () => {
      window.clearInterval(timer);
      abortRef.current?.abort();
    };
  }, [load]);

  const snapshotMap = useMemo(() => new Map(snapshots.map(item => [item.serviceId, item])), [snapshots]);
  const openIncidents = incidents.filter(item => item.status !== 'RESOLVED');

  const navigateMenu = (key: string) => navigate(key.startsWith('/') ? key : `/${key}`);
  const logout = () => {
    ['token', 'userInfo', 'isLoggedIn'].forEach(key => localStorage.removeItem(key));
    navigate('/login');
  };

  const save = async () => {
    if (!form.serviceName.trim() || !form.baseUrl.trim()) {
      Toast.warning('请填写服务名称和访问地址');
      return;
    }
    setSaving(true);
    try {
      await OpsSupervisionService.saveService(form);
      setForm(initialForm);
      await load(true);
      Toast.success('监督目标已保存');
    } catch (error) {
      Toast.error(`保存失败：${(error as Error).message}`);
    } finally {
      setSaving(false);
    }
  };

  const probe = async (serviceId: string) => {
    setProbing(serviceId);
    try {
      const result = await OpsSupervisionService.probeService(serviceId);
      await load(true);
      Toast[result.status === 'UP' ? 'success' : 'warning'](result.summary);
    } catch (error) {
      Toast.error(`探测失败：${(error as Error).message}`);
    } finally {
      setProbing('');
    }
  };

  const remove = async (serviceId: string) => {
    try {
      await OpsSupervisionService.removeService(serviceId);
      await load(true);
      Toast.success('监督目标已删除');
    } catch (error) {
      Toast.error(`删除失败：${(error as Error).message}`);
    }
  };

  const updateIncident = async (incidentId: string, action: 'acknowledge' | 'resolve') => {
    try {
      await (action === 'acknowledge' ? OpsSupervisionService.acknowledge(incidentId) : OpsSupervisionService.resolve(incidentId));
      await load(true);
      Toast.success(action === 'acknowledge' ? '事件已确认' : '事件已关闭');
    } catch (error) {
      Toast.error(`操作失败：${(error as Error).message}`);
    }
  };

  const analyze = async (incidentId: string) => {
    setAnalyzing(incidentId);
    setEvents([]);
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      await OpsSupervisionService.analyze(incidentId, event => setEvents(current => [...current, event]), controller.signal);
      await load(true);
      Toast.success('Agent 分析完成，可在运行台查看完整审计');
    } catch (error) {
      if ((error as Error).name !== 'AbortError') Toast.error(`分析失败：${(error as Error).message}`);
    } finally {
      setAnalyzing('');
      abortRef.current = null;
    }
  };

  return (
    <PageLayout>
      <Sidebar selectedKey="ops-supervision" onSelect={navigateMenu} collapsed={collapsed} />
      <Main $collapsed={collapsed}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <Header onToggleSidebar={() => setCollapsed(value => !value)} onLogout={logout} collapsed={collapsed} />
          <Area>
            <Space vertical align="start" spacing="tight" style={{ marginBottom: 20 }}>
              <Title heading={3}>Java 服务智能监督</Title>
              <Text type="tertiary">主动探测 Actuator，聚合连续异常并驱动 Agent 调用日志、监控与知识库工具完成根因分析。</Text>
            </Space>
            <Banner type="info" description="监督链路：服务探测 → 连续失败抑制 → 事件去重 → Skills 路由 → 受控 MCP 调用 → 恢复闭环。探测响应仅作为不可信证据，不会直接执行其中的指令。" style={{ marginBottom: 20 }} />
            <Grid>
              <Stack>
                <Card title="添加监督目标">
                  <FormGrid>
                    <Field className="wide"><Text strong>服务名称</Text><Input value={form.serviceName} onChange={serviceName => setForm(value => ({ ...value, serviceName }))} placeholder="订单服务" /></Field>
                    <Field><Text strong>环境</Text><Select value={form.environment} onChange={environment => setForm(value => ({ ...value, environment: String(environment) }))} optionList={['dev', 'test', 'staging', 'prod'].map(value => ({ value, label: value }))} /></Field>
                    <Field><Text strong>Agent ID</Text><Input value={form.agentId} onChange={agentId => setForm(value => ({ ...value, agentId }))} /></Field>
                    <Field className="wide"><Text strong>服务地址</Text><Input value={form.baseUrl} onChange={baseUrl => setForm(value => ({ ...value, baseUrl }))} /></Field>
                    <Field className="wide"><Text strong>健康端点</Text><Input value={form.healthPath} onChange={healthPath => setForm(value => ({ ...value, healthPath }))} /></Field>
                    <Field><Text strong>检查周期（秒）</Text><InputNumber value={form.intervalSeconds} min={5} max={3600} onChange={value => setForm(current => ({ ...current, intervalSeconds: Number(value) || 30 }))} /></Field>
                    <Field><Text strong>失败阈值</Text><InputNumber value={form.failureThreshold} min={1} max={10} onChange={value => setForm(current => ({ ...current, failureThreshold: Number(value) || 3 }))} /></Field>
                    <Field><Text strong>超时（毫秒）</Text><InputNumber value={form.timeoutMs} min={200} max={30000} onChange={value => setForm(current => ({ ...current, timeoutMs: Number(value) || 3000 }))} /></Field>
                    <Field><Text strong>慢响应阈值</Text><InputNumber value={form.slowThresholdMs} min={1} max={60000} onChange={value => setForm(current => ({ ...current, slowThresholdMs: Number(value) || 1500 }))} /></Field>
                  </FormGrid>
                  <Button type="primary" theme="solid" loading={saving} onClick={save} style={{ marginTop: 14 }}>保存并纳入监督</Button>
                </Card>
                <Card title={`事件中心 · 未恢复 ${openIncidents.length}`}>
                  {!incidents.length && <Text type="tertiary">连续异常达到阈值后会生成去重事件。</Text>}
                  {incidents.slice(0, 20).map(item => <Incident key={item.incidentId}>
                    <Row><Space><Tag color={incidentColor(item.severity)}>{item.severity}</Tag><Tag>{item.status}</Tag><Text strong>{item.serviceName}</Text></Space><Text size="small" type="tertiary">{time(item.updatedAt)}</Text></Row>
                    <div style={{ marginTop: 8 }}>{item.summary}</div>
                    <div><Text size="small" type="tertiary">出现 {item.occurrenceCount} 次 · </Text><Mono>{item.incidentId}</Mono></div>
                    {item.linkedRunId && <div><Text size="small">关联 Run：</Text><Mono>{item.linkedRunId}</Mono></div>}
                    <Space style={{ marginTop: 10 }}>
                      {item.status === 'OPEN' && <Button size="small" onClick={() => updateIncident(item.incidentId, 'acknowledge')}>确认</Button>}
                      {item.status !== 'RESOLVED' && <Button size="small" type="danger" onClick={() => updateIncident(item.incidentId, 'resolve')}>关闭</Button>}
                      <Button size="small" type="primary" loading={analyzing === item.incidentId} disabled={Boolean(analyzing)} onClick={() => analyze(item.incidentId)}>Agent 分析</Button>
                    </Space>
                  </Incident>)}
                  {events.length > 0 && <EventLog>
                    <Text strong>最近一次分析事件流</Text>
                    {events.slice(-10).map((event, index) => <EventLine key={event.eventId || `${event.sequence}-${index}`}>
                      <Mono>#{event.sequence ?? index + 1}</Mono><Tag>{event.type}</Tag><Mono>{event.content || '-'}</Mono>
                    </EventLine>)}
                  </EventLog>}
                </Card>
              </Stack>
              <Card title={<Row><span>服务监督概览 · {services.length}</span><Button size="small" loading={probing === 'all'} onClick={async () => { setProbing('all'); try { await OpsSupervisionService.probeAll(); await load(true); } catch (error) { Toast.error((error as Error).message); } finally { setProbing(''); } }}>全部探测</Button></Row>}>
                {!services.length && <Text type="tertiary">尚未配置服务，请先添加监督目标。</Text>}
                <ServiceGrid>
                  {services.map(service => {
                    const snapshot = snapshotMap.get(service.serviceId);
                    return <ServiceCard key={service.serviceId}>
                      <Row><div><Text strong>{service.serviceName}</Text><div><Text size="small" type="tertiary">{service.environment} · {service.intervalSeconds}s</Text></div></div><Tag color={healthColor(snapshot?.status)}>{snapshot?.status || 'PENDING'}</Tag></Row>
                      <div style={{ marginTop: 12 }}><Mono>{service.baseUrl}{service.healthPath}</Mono></div>
                      <div style={{ marginTop: 8 }}><Text size="small">{snapshot?.summary || '等待首次探测'}</Text></div>
                      <div><Text size="small" type="tertiary">连续异常 {snapshot?.consecutiveFailures || 0}/{service.failureThreshold} · 最近 {time(snapshot?.observedAt)}</Text></div>
                      <Space style={{ marginTop: 12 }}>
                        <Button size="small" type="primary" loading={probing === service.serviceId} onClick={() => probe(service.serviceId)}>立即探测</Button>
                        <Popconfirm title="确认删除该监督目标？" content="存在未恢复事件时系统会拒绝删除。" onConfirm={() => remove(service.serviceId)}><Button size="small" type="danger">删除</Button></Popconfirm>
                      </Space>
                    </ServiceCard>;
                  })}
                </ServiceGrid>
              </Card>
            </Grid>
          </Area>
        </div>
      </Main>
    </PageLayout>
  );
};

export default OpsSupervisionPage;
