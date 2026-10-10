import usePageRefresh from './components/usePageRefresh';
import React from 'react';
import {
  ShieldCheck, LayoutDashboard, FileLock2, GitBranch, ScanSearch, Play, Bot,
  Activity, CheckCircle2, ScrollText, Radio, Network, Route, Siren, Gauge, Brain, GitCompareArrows,
  BookOpen, RefreshCw, Settings, ChevronRight, AlertTriangle, XCircle, Check,
  CircleHelp, Copy, ExternalLink, Terminal, Zap, Database, Lock, Users,
  Clock, Crosshair, FolderOpen, FileSearch, Send, Sparkles, History, TrendingUp, Menu, PanelLeftClose
} from 'lucide-react';
import './style.css';

import { API, TENANT, WORKSPACE, apiGet, apiPost } from './api/client';
import {
  DATA_PLANE_REFRESH_INTERVAL_MS,
  DATA_PLANE_URL,
  DASHBOARD_REFRESH_INTERVAL_MS,
  PRIVATE_LLM_DEFAULT_URL,
  RISK_THRESHOLDS,
} from './config/risk';
import { DashboardRole, can } from './rbac/permissions';
import PolicyStudio from './pages/PolicyStudio';
import SecurityCopilot from './pages/SecurityCopilot';
import PolicyTests from './pages/PolicyTests';
import ApprovalExecution from './pages/ApprovalExecution';
import McpGateway from './pages/McpGateway';
import StitchAccessPoc from './pages/StitchAccessPoc';
import StitchIntegration from './pages/StitchIntegration';
import RetrievalAccess from './pages/RetrievalAccess';
import ActionToastHost from './components/ActionFeedback';
import ScopeBanner from './components/ScopeBanner';
import SubjectSelect from './components/SubjectSelect';
import ProductOperationsPage from './pages/ProductOperations';
import AgentDetails from './pages/AgentDetails';
import AgentRisk from './pages/AgentRisk';
import RiskForecast from './pages/RiskForecast';
import ControlLoop from './pages/ControlLoop';
import ControlLoopFeedback from './pages/ControlLoopFeedback';
import PolicyAutoTuning from './pages/PolicyAutoTuning';
const money=(n:any)=>typeof n==='number'?new Intl.NumberFormat('ko-KR').format(n):String(n??'');
const time=(x:any)=>x?new Date(x).toLocaleString():'—';
const riskClass=(n:number)=>n >= RISK_THRESHOLDS.critical
    ? 'critical'
    : n >= RISK_THRESHOLDS.high
      ? 'high'
      : n >= RISK_THRESHOLDS.medium
        ? 'medium'
        : 'low';

const nav=[
 ['Overview',LayoutDashboard],['Enterprise',Users],['Product Operations',
 Settings],['Policy Studio',FileLock2],['Lifecycle',GitBranch],['Runtime Intelligence',
 Brain],['Continuous Agent Risk',Activity],['Agent Risk Forecast',TrendingUp],
 ['Preventive Control Loop',ShieldCheck],['Control Loop Verification',ShieldCheck],
 ['Policy Auto-Tuning',Zap],['Policy Tests',Play],['Execution Records',History],['MCP Gateway',ShieldCheck],['Stitch Access PoC',FolderOpen],['Stitch Integration',Lock],['Retrieval Access',Lock],
 ['Data Plane',Database],['Governance',ScanSearch],
 ['Runtime Gateway',Zap],['Agents',Bot],['Agent Behavior',
 Activity],['Approvals',CheckCircle2],['Audit Logs',ScrollText],
 ['SIEM',Radio],['Compliance Evidence',FileSearch],['Security Graph',Network],
 ['Attack Paths',Route],['Blast Radius',Crosshair],['Response Center',Siren],
 ['Incident Response',FolderOpen],['Command Center',Siren],['Decision Engine',
 Gauge],
 ['Kubernetes Policies',Database],['Setup Guide',BookOpen]
] as const;

// Legacy test page IDs resolve to tabs in the consolidated workspace.
const navigationGroups = [
 {id:'overview',label:'Overview',icon:LayoutDashboard,pages:['Overview','Command Center']},
 {id:'connections',label:'Agents & Connections',icon:Bot,pages:['Agents','MCP Gateway','Stitch Access PoC','Retrieval Access','Stitch Integration','Runtime Gateway','Data Plane','Kubernetes Policies']},
 {id:'policies',label:'Policies',icon:FileLock2,pages:['Policy Studio','Lifecycle','Policy Tests','Policy Auto-Tuning']},
 {id:'approvals',label:'Approvals & Execution',icon:CheckCircle2,pages:['Approvals','Execution Records']},
 {id:'analysis',label:'Audit & Analysis',icon:ScanSearch,pages:['Audit Logs','Runtime Intelligence','Continuous Agent Risk','Agent Risk Forecast','Agent Behavior','Preventive Control Loop','Control Loop Verification','Security Graph','Attack Paths','Blast Radius','Response Center','Incident Response','Compliance Evidence','SIEM','Governance','Decision Engine']},
 {id:'settings',label:'Settings',icon:Settings,pages:['Enterprise','Product Operations','Setup Guide']},
];
const pageLabels:Record<string,string> = {
 'Overview':'Summary','Command Center':'Security Summary',
 'Lifecycle':'Changes & Rollout','Policy What-if':'Change Impact',
 'Shadow Replay':'Historical Replay','Simulator':'Request Simulation',
 'Policy Auto-Tuning':'Policy Improvement Proposals',
 'Continuous Agent Risk':'Current Agent Risk','Agent Risk Forecast':'Risk Forecast',
 'Preventive Control Loop':'Preventive Controls','Control Loop Verification':'Control Effectiveness',
 'Governance':'Governance Review','Kubernetes Policies':'Kubernetes Draft Sync',
 'Product Operations':'Operational Settings','Setup Guide':'Setup & Connections',
};

type PageProps={refresh:()=>void};

class DashboardErrorBoundary extends React.Component<{children: React.ReactNode}, {error: Error | null}> {
 state={error:null as Error|null};
 static getDerivedStateFromError(error: Error){ return {error};
 }
 render(){
  if(this.state.error) return <div className="fatal-error">
<ShieldCheck size={
      42}/>
<h1>ZT Security Dashboard</h1>
<p>The dashboard shell is healthy, but a page failed to render.</p>
<pre>{
      this.state.error.message}
</pre>
<button onClick={()=>location.reload()}
  >Reload dashboard</button>
</div>;
  return this.props.children;
 }
}

const clampSidebarWidth=(width:number)=>Math.min(420,Math.max(200,width));
function readSidebarPreference(key:string){
 try{return localStorage.getItem(key)}catch{return null}
}

function App(){
 const [sidebarWidth,setSidebarWidth]=React.useState(()=>{
   const saved=Number(readSidebarPreference('zt-sidebar-width'));
   return Number.isFinite(saved)&&saved>0?clampSidebarWidth(saved):250;
 });
 const [sidebarOpen,setSidebarOpen]=React.useState(()=>readSidebarPreference('zt-sidebar-open')!=='false');
 const [mobile,setMobile]=React.useState(()=>window.matchMedia('(max-width:700px)').matches);
 const [mobileOpen,setMobileOpen]=React.useState(false);
 const [resizing,setResizing]=React.useState(false);
 const sidebarToggle=React.useRef<HTMLButtonElement>(null);
 const drag=React.useRef<{pointerId:number;startX:number;startWidth:number}|null>(null);
 const menuOpen=mobile?mobileOpen:sidebarOpen;
 const closeMenu=()=>{
   if(mobile)setMobileOpen(false);else setSidebarOpen(false);
   sidebarToggle.current?.focus();
 };
 React.useEffect(()=>{
   const query=window.matchMedia('(max-width:700px)');
   const change=()=>{setMobile(query.matches);setMobileOpen(false);setResizing(false);drag.current=null};
   query.addEventListener('change',change);
   return()=>query.removeEventListener('change',change);
 },[]);
 React.useEffect(()=>{
   try{localStorage.setItem('zt-sidebar-width',String(sidebarWidth))}catch{}
 },[sidebarWidth]);
 React.useEffect(()=>{
   try{localStorage.setItem('zt-sidebar-open',String(sidebarOpen))}catch{}
 },[sidebarOpen]);
 React.useEffect(()=>{
   if(!menuOpen)return;
   const escape=(event:KeyboardEvent)=>{
     if(event.key==='Escape'){
       if(mobile)setMobileOpen(false);else setSidebarOpen(false);
       sidebarToggle.current?.focus();
     }
   };
   window.addEventListener('keydown',escape);
   return()=>window.removeEventListener('keydown',escape);
 },[menuOpen,mobile]);
 const [tab,setTab]=React.useState('Overview');
 const activePage=['Simulator','Policy What-if','Shadow Replay'].includes(tab)?'Policy Tests':tab;
 const [contextSubject,setContextSubject]=React.useState('');
 const [copilotOpen,setCopilotOpen]=React.useState(false);
 const copilotToggle=React.useRef<HTMLButtonElement>(null);
 const navigate=(page:string,subject='')=>{if(page==='AI Security Copilot'){setCopilotOpen(true);return}setContextSubject(subject);setTab(page)};
 const [openGroups,setOpenGroups]=React.useState<string[]>(['overview']);
 React.useEffect(()=>{
   const group=navigationGroups.find(item=>item.pages.includes(activePage));
   if(group)setOpenGroups(current=>current.includes(group.id)?current:[...current,group.id]);
 },[activePage]);
 const [role,setRole]=React.useState<DashboardRole>('CISO');
 React.useEffect(()=>{const open=(e:Event)=>{const page=(e as CustomEvent).detail?.page;if(can(role,page))navigate(page);else setToast('Choose a navigation view that includes '+page)};window.addEventListener('zt-navigate',open);return()=>window.removeEventListener('zt-navigate',open)},[role]);
 React.useEffect(()=>{if(!can(role,'AI Security Copilot'))setCopilotOpen(false)},[role]);
 React.useEffect(()=>{if(!copilotOpen)return;const close=(event:KeyboardEvent)=>{if(event.key==='Escape'){event.stopImmediatePropagation();setCopilotOpen(false);copilotToggle.current?.focus()}};window.addEventListener('keydown',close,true);return()=>window.removeEventListener('keydown',close,true)},[copilotOpen]);
 const [boot,setBoot]=React.useState(true);
 const [health,setHealth]=React.useState<any>(null);
 const [toast,setToast]=React.useState('');
 const [lastRefresh,setLastRefresh]=React.useState<Date|null>(null);
 React.useEffect(()=>{const updated=()=>setLastRefresh(new Date());window.addEventListener('zt-api-success',updated);return()=>window.removeEventListener('zt-api-success',updated)},[]);
 usePageRefresh(()=>apiGet('/v1/health').then(setHealth).catch(()=>setHealth({status:'DOWN'})));
 const refresh=()=>{
     window.dispatchEvent(new Event('zt-refresh'));
 };
 React.useEffect(()=>{apiGet('/v1/health').then(setHealth).catch(()=>setHealth({
         status:'DOWN'})).finally(()=>setBoot(false));
         },[]);
 React.useEffect(()=>{if(toast){const t=setTimeout(()=>setToast(''),3500);return()=>clearTimeout(t)}},[toast]);
 if(boot)return <div className="boot">
<ShieldCheck size={42}/>
<h1>ZT Security</h1>
<p>Loading Security Command Center…</p>
</div>;
 return <DashboardErrorBoundary>
<div className={`app${menuOpen?' sidebar-open':' sidebar-hidden'}${resizing?' sidebar-resizing':''}`}
 style={{'--sidebar-width':`${sidebarWidth}px`} as React.CSSProperties}>
  {mobile&&menuOpen&&<button className="sidebar-backdrop" aria-label="Close navigation" onClick={closeMenu}/>}
  <aside id="dashboard-sidebar" className="dashboard-sidebar" aria-label="Main navigation" hidden={!menuOpen}>
   <div className="brand">
<div className="brand-mark">
<ShieldCheck/>
</div>
<div>
<b>ZT Security</b>
<small>AI Agent Security</small>
</div>
<button className="sidebar-close icon-btn" aria-label="Hide navigation" title="Hide navigation" onClick={closeMenu}>
<PanelLeftClose size={18}/>
</button>
</div>
   <div className="env">
<label className="navigation-profile-label" htmlFor="navigation-profile">Navigation view</label>
<select id="navigation-profile" title="Changes visible menus only; API access is controlled by your signed-in account." value={role} onChange={e=>{const r=e.target.value as DashboardRole;
           setRole(r);
           if(!can(r,activePage))navigate('Overview')}}>
<option>CISO</option>
<option>SOC_ANALYST</option>
<option>DEVOPS</option>
</select>
<span className={
       health?.status==='UP'?'dot up':'dot'}>
</span>
<span>API {health?.status==='UP'?'ONLINE':'OFFLINE'}
</span>

</div>
   <nav aria-label="Workspace sections">{navigationGroups.map(group=>{
     const items=group.pages.flatMap(page=>nav.filter(([name])=>name===page&&can(role,name)));
     if(!items.length)return null;
     const expanded=openGroups.includes(group.id);
     const selected=group.pages.includes(activePage);
     const GroupIcon=group.icon;
     return <section className="navigation-group" key={group.id}>
       <button className={`navigation-group-toggle${selected?' selected':''}`}
         aria-expanded={expanded} aria-controls={`navigation-${group.id}`}
         onClick={()=>setOpenGroups(current=>expanded?current.filter(id=>id!==group.id):[...current,group.id])}>
         <GroupIcon size={17}/><span>{group.label}</span>
         <ChevronRight size={14} className={expanded?'group-chevron expanded':'group-chevron'}/>
       </button>
       <div id={`navigation-${group.id}`} className="navigation-group-pages" hidden={!expanded}>
         {items.map(([name,Icon])=><button key={name} className={activePage===name?'navigation-page active':'navigation-page'}
           aria-current={activePage===name?'page':undefined} title={name}
           onClick={()=>{navigate(name);if(mobile)closeMenu()}}>
           <Icon size={15}/><span>{pageLabels[name]||name}</span>
         </button>)}
       </div>
     </section>;
   })}</nav>
   <div className="side-footer">
<div>
<small>TENANT</small>
<b>{TENANT.slice(0,
       8)}…</b>
</div>
<button onClick={()=>{navigate('Setup Guide');if(mobile)closeMenu()}}>
<Settings size={
       15}/> Setup</button>
</div>
  <div className="sidebar-resize-handle" role="separator" tabIndex={0}
   aria-label="Navigation width" aria-orientation="vertical" aria-valuemin={200} aria-valuemax={420} aria-valuenow={sidebarWidth}
   title="Drag to resize; double-click to reset"
   onDoubleClick={()=>setSidebarWidth(250)}
   onKeyDown={event=>{
     if(event.key==='ArrowLeft'||event.key==='ArrowRight'){
       event.preventDefault();setSidebarWidth(width=>clampSidebarWidth(width+(event.key==='ArrowRight'?10:-10)));
     }else if(event.key==='Home'||event.key==='End'){
       event.preventDefault();setSidebarWidth(event.key==='Home'?200:420);
     }
   }}
   onPointerDown={event=>{
     if(event.button!==0||mobile)return;
     event.preventDefault();event.currentTarget.setPointerCapture(event.pointerId);
     drag.current={pointerId:event.pointerId,startX:event.clientX,startWidth:sidebarWidth};setResizing(true);
   }}
   onPointerMove={event=>{
     const current=drag.current;
     if(current&&current.pointerId===event.pointerId)setSidebarWidth(clampSidebarWidth(current.startWidth+event.clientX-current.startX));
   }}
   onPointerUp={event=>{
     drag.current=null;setResizing(false);
     if(event.currentTarget.hasPointerCapture(event.pointerId))event.currentTarget.releasePointerCapture(event.pointerId);
   }}
   onPointerCancel={()=>{drag.current=null;setResizing(false)}}
   onLostPointerCapture={()=>{drag.current=null;setResizing(false)}}/>
  </aside>
  <main><ScopeBanner/><ActionToastHost/>
   <header>
<div className="dashboard-heading">
<button ref={sidebarToggle} className="icon-btn sidebar-toggle" aria-label={menuOpen?'Hide navigation':'Show navigation'}
 title={menuOpen?'Hide navigation':'Show navigation'} aria-expanded={menuOpen} aria-controls="dashboard-sidebar"
 onClick={()=>{if(mobile)setMobileOpen(open=>!open);else setSidebarOpen(open=>!open)}}><Menu size={20}/></button>
<div>
<small className="eyebrow">ZERO TRUST CONTROL PLANE · ENTERPRISE DATA PLANE</small>
<h1>{
       pageLabels[activePage]||activePage}
</h1>
</div>
</div>
<div className="header-actions">
{can(role,'AI Security Copilot')&&<button ref={copilotToggle} aria-expanded={copilotOpen} aria-controls="security-copilot-panel" onClick={()=>setCopilotOpen(open=>!open)}><Sparkles size={16}/> Copilot</button>}
<span className="last">Last API response {
       time(lastRefresh)}
</span>
<button className="icon-btn" onClick={refresh}
   >
<RefreshCw size={16}/>
</button>
</div>
</header>
   <div className="content">
    {tab==='Overview'&&<Overview go={navigate} refresh={refresh}/>}
    {activePage==='Policy Tests'&&<PolicyTests initialMode={tab==='Shadow Replay'?'replay':tab==='Simulator'?'live':'impact'} renderLive={<Simulator/>}/>}
    {tab==='Data Plane'&&<DataPlane/>} {tab==='Runtime Intelligence'&&<RuntimeIntelligence/>}
    {tab==='Continuous Agent Risk'&&<AgentRisk/>} {tab==='Agent Risk Forecast'&&<RiskForecast/>}
    {tab==='Preventive Control Loop'&&<ControlLoop/>} {tab==='Control Loop Verification'&&<ControlLoopFeedback/>}
    {tab==='Policy Auto-Tuning'&&<PolicyAutoTuning/>} {tab==='MCP Gateway'&&<McpGateway/>} {tab==='Stitch Access PoC'&&<StitchAccessPoc/>} {tab==='Stitch Integration'&&<StitchIntegration/>} {tab==='Retrieval Access'&&<RetrievalAccess/>}
    {tab==='Enterprise'&&<Enterprise/>} {tab==='Product Operations'&&<ProductOperationsPage/>}
    {tab==='Policy Studio'&&<PolicyStudio/>} {tab==='Lifecycle'&&<Lifecycle/>}
    {tab==='Governance'&&<Governance/>}
    {tab==='Runtime Gateway'&&<RuntimeGateway/>} {tab==='Agents'&&<AgentDetails subject={contextSubject} role={role} onNavigate={navigate}/>}
    {tab==='Agent Behavior'&&<AgentBehavior subject={contextSubject}/>}
    {(tab==='Approvals'||tab==='Execution Records')&&<ApprovalExecution subject={contextSubject} initialView={tab==='Execution Records'?'executions':'pending'} onAgent={can(role,'Agents')?subject=>navigate('Agents',subject):undefined}/>}
    {tab==='Audit Logs'&&<AuditLogs subject={contextSubject}/>}
    {tab==='SIEM'&&<SIEM/>} {tab==='Compliance Evidence'&&<ComplianceEvidence/>}
    {tab==='Security Graph'&&<SecurityGraph/>} {tab==='Attack Paths'&&<AttackPaths/>}
    {tab==='Blast Radius'&&<BlastRadius go={navigate}/>} {tab==='Response Center'&&<ResponseCenter/>}
    {tab==='Incident Response'&&<IncidentResponse/>}
    {tab==='Command Center'&&<CommandCenter/>} {tab==='Decision Engine'&&<DecisionEngine/>}
    {tab==='Setup Guide'&&<SetupGuide/>} {tab==='Kubernetes Policies'&&<KubernetesPolicies/>}
</div>
  </main>
  {copilotOpen&&<aside className="copilot-panel" id="security-copilot-panel" aria-label="AI Security Copilot"><div className="copilot-panel-heading"><h2>AI Security Copilot</h2><button autoFocus aria-label="Close Copilot" onClick={()=>{setCopilotOpen(false);copilotToggle.current?.focus()}}>Close</button></div><SecurityCopilot contextLabel={`${pageLabels[activePage]||activePage}${contextSubject?' · Agent: '+contextSubject:''}`} initialScenario={`Review security risks for ${contextSubject?'agent '+contextSubject:pageLabels[activePage]||activePage} and suggest policy changes for human review.`}/></aside>}
  {toast&&<div className="toast">
<CheckCircle2 size={17}/>{toast}
</div>}
</div>
</DashboardErrorBoundary>
}


function DataPlane(){
 const [health,setHealth]=React.useState<any>(null),[bundle,setBundle]=React.useState<any>(null),
 [stats,setStats]=React.useState<any>(null),[identity,setIdentity]=React.useState<any>(null),
 [attestation,setAttestation]=React.useState<any>(null),[err,setErr]=React.useState('');
const load=async()=>{try{const [h,b,st,id,at]=await Promise.all([fetch(`${DATA_PLANE_URL}/health`).then(r=>r.json()),
         fetch(`${DATA_PLANE_URL}/v1/fast/policy-bundle`).then(r=>r.json()),
fetch(`${DATA_PLANE_URL}/v1/fast/stats`).then(r=>r.json()),
fetch(`${DATA_PLANE_URL}/v1/fast/identity`).then(r=>r.json()),
         fetch(`${DATA_PLANE_URL}/v1/fast/attestation`).then(r=>r.json())]);
         setHealth(h);
         setBundle(b);
         setStats(st);
         setIdentity(id);
         setAttestation(at);
         setErr('')}catch(e:any){setErr('Rust data plane is not reachable at ${DATA_PLANE_URL}')}
 };
 usePageRefresh(load);
 React.useEffect(()=>{load();const i=setInterval(load,DATA_PLANE_REFRESH_INTERVAL_MS);return()=>clearInterval(i)},[]);
 return <div>
<div className="page-header">
<div>
<h2>High-Performance Data Plane</h2>
<p>Rust fast-path enforcement runs outside the Spring control plane with local policy evaluation,
 HA workers and measurable tail latency.</p>
</div>
<button onClick={load}
 >
<RefreshCw size={14}/> Refresh</button>
</div>{err&&<ErrorBanner text={
         err}/>}
<div className="metric-grid">
<Metric icon={Zap} label="Engine" value={
     health?.engine||'—'}/>
<Metric icon={Activity} label="Status" value={health?.status||'—'}
 />
<Metric icon={Database} label="Bundle policies" value={health?.policies??'—'}
 />
<Metric icon={GitBranch} label="Bundle version" value={health?.bundleVersion||'—'}
 />
</div>
 <div className="metric-grid compact">
<Metric icon={Lock} label="Workload identity" value={
     identity?.workloadIdentity?String(identity.workloadIdentity).slice(0,28)+"…":"NOT PRESENT"}
 />
<Metric icon={ShieldCheck} label="Evidence signing" value={stats?.bundle_version?"ED25519 READY":"—"}
 />
<Metric icon={GitBranch} label="Bundle hash" value={health?.bundleHash?String(health.bundleHash).slice(0,
     12)+"…":'—'}/>
<Metric icon={Lock} label="Bundle pin" value={health?.bundlePin||'NONE'}
 />
<Metric icon={Gauge} label="P50" value={stats?stats.p50_us+' µs':'—'}
 />
<Metric icon={Gauge} label="P95" value={stats?stats.p95_us+' µs':'—'}
 />
<Metric icon={Gauge} label="P99" value={stats?stats.p99_us+' µs':'—'}
 />
<Metric icon={Gauge} label="P99.9" value={stats?stats.p999_us+' µs':'—'}
 />
<Metric icon={Activity} label="Max" value={stats?stats.max_us+' µs':'—'}
 />
<Metric icon={ShieldCheck} label="Bundle signature" value={stats?.bundle_signature_valid?'VALID':'—'}
 />
</div>
 <div className="grid-2">
<Panel title="3.2 Enterprise HA architecture">
<div className="policy-flow">
<span>Agent / MCP / API</span>
<i>→</i>
<span>mTLS Ingress</span>
<i>→</i>
<span>Rust DP ×3+</span>
<i>→</i>
<span className="final">Atomic L1 Bundle</span>
</div>
<p className="muted">Kubernetes/EKS runs multiple Rust workers behind a health-aware service. Canary routing,
 rolling updates, PDB/HPA and client-certificate verification are included in the 3.2 deployment profile.</p>
</Panel>
<Panel title="3.5 hardware-rooted trust">
<div className="feature-grid">
<span>
<Check size={
     14}/>mTLS-authenticated workload identity</span>
<span>
<Check size={14}
 />SPIFFE/SPIRE-ready URI SAN</span>
<span>
<Check size={14}/>Ed25519 decision evidence</span>
<span>
<Check size={
     14}/>SHA-256 evidence hash</span>
<span>
<Check size={14}/>Nitro attestation gate</span>
<span>
<Check size={
     14}/>PCR3 / PCR8 policy contract</span>
<span>
<Check size={14}/>Nonce freshness contract</span>
<span>
<Check size={
     14}/>KMS key binding</span>
</div>
<p className="muted">Attestation status: <b>{
     attestation?.status||"—"}
</b> · Provider: {attestation?.provider||"—"}
</p>
</Panel>
<Panel title="Reliability posture">
<div className="feature-grid">
<span>
<Check size={
     14}/>No DB/Redis in inline evaluation</span>
<span>
<Check size={14}/>3+ replicas + HPA</span>
<span>
<Check size={
     14}/>Last-known-good atomic bundle</span>
<span>
<Check size={14}/>Signed bundle + optional version pin</span>
<span>
<Check size={
     14}/>mTLS ingress + NetworkPolicy</span>
<span>
<Check size={14}/>Prometheus / P99.9 telemetry</span>
</div>
</Panel>
</div>
 <Panel title="Distributed policy bundle">
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Priority</th>
<th>Policy</th>
<th>Effect</th>
<th>Principal</th>
<th>Actions</th>
<th>Resource</th>
<th>Fast Path</th>
</tr>
</thead>
<tbody>{
     (bundle?.policies||[]).map((x:any)=>
<tr key={x.name+'-'+x.version}>
<td>{
         x.priority}
</td>
<td>
<b>{x.name}
</b> v{x.version}
</td>
<td>
<Status value={
         x.effect}/>
</td>
<td>{x.principalType||'—'}
</td>
<td>{(x.actions||[]).join(', ')||'—'}
</td>
<td>{x.resourceType||'—'}
</td>
<td>
<Status value={x.fastPath?'ENABLED':'DEFER'}
     />
</td>
</tr>)}
</tbody>
</table>
</div>
</Panel>
 </div>
}


function RuntimeIntelligence(){
 const [overview,setOverview]=React.useState<any>(null),[agent,setAgent]=React.useState(''),
 [data,setData]=React.useState<any>(null),[err,setErr]=React.useState('');
 const requestTicket=React.useRef(0);
 const load=async()=>{const ticket=++requestTicket.current;try{const [o,a]=await Promise.all([apiGet('/v1/runtime/intelligence/overview'),agent?apiGet('/v1/runtime/intelligence/'+encodeURIComponent(agent)):Promise.resolve(null)]);if(ticket!==requestTicket.current)return;setOverview(o);setData(a);setErr('')}catch(e:any){if(ticket===requestTicket.current){setData(null);setErr(e.message)}}};
 React.useEffect(()=>()=>{requestTicket.current++},[]);
 usePageRefresh(load);
 React.useEffect(()=>{load()},[agent]);
 const signals=data?.threatSummary?.byType||{};
 return <div className="runtime-intelligence">
  {err&&<ErrorBanner text={err}/>}
<div className="page-header">
<div>
<h2>Runtime Security Intelligence</h2>
<p>실행 중인 AI Agent의 행동·위협·세션을 하나의 runtime security view로 분석합니다.</p>
</div>
<button onClick={
      load}>
<RefreshCw size={14}/> Refresh</button>
</div>
  <div className="metric-grid">
<Metric icon={Zap} label="Active sessions" value={
      overview?.activeSessions??'—'}/>
<Metric icon={Bot} label="Observed agents" value={
      overview?.agents??'—'}/>
<Metric icon={AlertTriangle} label="Recent anomalies" value={
      overview?.threatSummary?.total??'—'}/>
<Metric icon={Siren} label="High / critical" value={
      overview?.threatSummary?.highOrCritical??'—'}/>
</div>
  <div className="grid-2">
   <Panel title="Agent runtime posture">
<div className="inline-form">
<Field label="Agent">
<SubjectSelect includeClients value={agent} onChange={v=>{requestTicket.current++;setAgent(v);setData(null);setErr('')}}/>
</Field>
</div>
    <div className="stats">
<div>
<b>{data?.activeSessions??0}
</b>
<small>active sessions</small>
</div>
<div>
<b>{
        data?.anomalies?.length??0}
</b>
<small>anomalies</small>
</div>
<div>
<b>{
        data?.adaptiveDecisions?.length??0}
</b>
<small>adaptive decisions</small>
</div>
</div>
   </Panel>
   <Panel title="Threat signal distribution">
<div className="chips">{Object.keys(signals).length?Object.entries(signals).map(([k,
       v]:any)=>
<span key={k} className="threat-chip">
<b>{k}
</b> {v}
</span>):<span className="muted">No runtime threats observed.</span>}
</div>
</Panel>
  </div>
  <div className="grid-2">
   <Panel title="Recent runtime anomalies">
<table>
<thead>
<tr>
<th>Type</th>
<th>Severity</th>
<th>Score</th>
<th>Reason</th>
<th>Time</th>
</tr>
</thead>
<tbody>{
       (data?.anomalies||[]).slice(0,15).map((x:any)=>
<tr key={x.id}>
<td>
<b>{
           x.anomalyType}
</b>
</td>
<td>
<Status value={x.severity}/>
</td>
<td>{Math.round(x.score)}
</td>
<td>{x.reason}
</td>
<td>{time(x.createdAt)}
</td>
</tr>)}
</tbody>
</table>{
       !data?.anomalies?.length&&<Empty icon={ShieldCheck} text="No anomalies for this agent."/>}
</Panel>
   <Panel title="Adaptive decisions">
<table>
<thead>
<tr>
<th>Decision</th>
<th>Behavior score</th>
<th>Request</th>
<th>Time</th>
</tr>
</thead>
<tbody>{
       (data?.adaptiveDecisions||[]).slice(0,15).map((x:any)=>
<tr key={x.requestId}
       >
<td>
<Status value={x.decision}/>
</td>
<td>{Math.round(x.behaviorScore)}
</td>
<td className="mono">{String(x.requestId).slice(0,12)}…</td>
<td>{
           time(x.createdAt)}
</td>
</tr>)}
</tbody>
</table>{!data?.adaptiveDecisions?.length&&<Empty icon={
           Activity} text="No adaptive decisions yet."/>}
</Panel>
  </div>
  <Panel title="Runtime security model">
<div className="policy-flow">
<span>Agent Identity</span>
<i>→</i>
<span>Task</span>
<i>→</i>
<span>Tool / MCP</span>
<i>→</i>
<span>Behavior</span>
<i>→</i>
<span>Threat Signals</span>
<i>→</i>
<span>Policy</span>
<i>→</i>
<span className="final">ALLOW / STEP_UP / DENY</span>
</div>
<p className="muted small">High-confidence critical runtime threats can short-circuit to DENY. Normal
requests continue through behavior,
  risk and policy evaluation.</p>
</Panel>
 </div>
}

function Enterprise(){
 const [roles,setRoles]=React.useState<any[]>([]),[users,setUsers]=React.useState<any[]>([]),
 [sso,setSso]=React.useState<any[]>([]),[perms,setPerms]=React.useState<any[]>([]),
 [err,setErr]=React.useState('');
 const [preflight,setPreflight]=React.useState<any>({}),[llm,setLlm]=React.useState<any>(null),
[llmProvider,setLlmProvider]=React.useState('VLLM'),[llmEndpoint,
setLlmEndpoint]=React.useState(PRIVATE_LLM_DEFAULT_URL),
 [llmModel,setLlmModel]=React.useState('security-copilot');
 const load=async()=>{try{const [r,u,s,p]=await Promise.all([apiGet('/v1/rbac/roles'),
         apiGet('/v1/enterprise/directory/users'),apiGet('/v1/enterprise/sso'),
         apiGet('/v1/rbac/me/permissions')]);
         setRoles(r);
         setUsers(u);
         setSso(s);
         setPerms(p.permissions||[]);
         setErr('')}catch(e:any){setErr(e.message)}
 };
 usePageRefresh(load);
 React.useEffect(()=>{load()},[]);
 const runPreflight=async(provider:string)=>{try{setPreflight({...preflight,
             [provider]:await apiPost('/v1/enterprise/sso/preflight/'+encodeURIComponent(provider),
             {})})}catch(e:any){setPreflight({...preflight,[provider]:{status:'ERROR',
                 detail:e.message}})}};
 const checkLlm=async()=>{try{setLlm(await apiPost('/v1/security/private-llm/health',
         {provider:llmProvider,endpoint:llmEndpoint,model:llmModel}))}catch(e:any){
         setLlm({result:{ok:false,error:e.message}})}};
 return <>{err&&<ErrorBanner text={err}/>}
<div className="metric-grid">
<Metric icon={
     Users} label="Directory users" value={users.length}/>
<Metric icon={Lock}
 label="Enterprise roles" value={roles.length}/>
<Metric icon={ShieldCheck}
 label="SSO connections" value={sso.length}/>
<Metric icon={CheckCircle2}
 label="My permissions" value={perms.length}/>
</div>
 <div className="grid-2">
<Panel title="SSO connections">
<table>
<thead>
<tr>
<th>Provider</th>
<th>Issuer</th>
<th>Status</th>
<th>Preflight</th>
</tr>
</thead>
<tbody>{
     sso.map(x=>
<tr key={x.id}>
<td>
<b>{x.provider}
</b>
</td>
<td className="mono">{
         x.issuerUri}
</td>
<td>
<Status value={x.enabled?'ENABLED':'DISABLED'}/>
</td>
<td>
<button onClick={
         ()=>runPreflight(x.provider)}>Run</button>{preflight[x.provider]&&<Status value={
             preflight[x.provider].status}/>}
</td>
</tr>)}
</tbody>
</table>{!sso.length&&<Empty icon={
         Lock} text="No tenant SSO configured."/>}
</Panel>
<Panel title="Private LLM runtime">
<div className="inline-form">
<Field label="Provider">
<select value={
     llmProvider} onChange={e=>setLlmProvider(e.target.value)}>
<option>VLLM</option>
<option>OLLAMA</option>
<option>OPENAI_COMPATIBLE</option>
<option>BEDROCK_VPC</option>
</select>
</Field>
<Field label="Endpoint">
<input value={
     llmEndpoint} onChange={e=>setLlmEndpoint(e.target.value)}/>
</Field>
<Field label="Model">
<input value={
     llmModel} onChange={e=>setLlmModel(e.target.value)}/>
</Field>
</div>
<button className="primary" onClick={
     checkLlm}>Health check</button>{llm&&<pre className="json">{JSON.stringify(llm,
         null,2)}
</pre>}
<p className="muted small">Private-only runtime;
API secrets are referenced by environment variable name,
 not stored in tenant data.</p>
</Panel>
</div>
 <div className="grid-2">
<Panel title="Enterprise RBAC">
<table>
<thead>
<tr>
<th>Role</th>
<th>Description</th>
<th>Permissions</th>
</tr>
</thead>
<tbody>{
     roles.map(x=>
<tr key={x.id}>
<td>
<b>{x.name}
</b>
</td>
<td>{x.description}
</td>
<td>{String(x.permissions||'[]').replace(/[\[\]"]+/g,'').split(',').filter(Boolean).length}
</td>
</tr>)}
</tbody>
</table>
</Panel>
<Panel title="SCIM / Directory">
<p className="muted">SCIM 2.0 endpoint is available at <code>/scim/v2</code>. Configure
<code>ZT_SCIM_TOKEN</code> and send <code>X-Tenant-Id</code>.</p>
<table>
<thead>
<tr>
<th>User</th>
<th>Email</th>
<th>Source</th>
<th>Status</th>
</tr>
</thead>
<tbody>{users.slice(0,20).map(x=>
<tr key={x.id}>
<td>
<b>{x.userName}
</b>
</td>
<td>{x.email||'—'}
</td>
<td>{x.source}
</td>
<td>
<Status value={x.active?'ACTIVE':'DISABLED'}/>
</td>
</tr>)}
</tbody>
</table>
</Panel>
</div>
 <Panel title="Current principal permissions">
<div className="chips">{
     perms.map(p=>
<span key={p}>{p}
</span>)}
</div>{!perms.length&&<Empty icon={
         Lock} text="No database RBAC permissions resolved for this principal."/>}
</Panel>
</>
}

function Overview({go}:any){
 const [dec,setDec]=React.useState<any[]>([]),[pol,setPol]=React.useState<any[]>([]),
 [inc,setInc]=React.useState<any[]>([]),[assets,setAssets]=React.useState<any[]>([]),
 [err,setErr]=React.useState('');
 const load=async()=>{try{const [d,p,i,a]=await Promise.all([apiGet('/v1/security/decisions'),
         apiGet('/v1/policies/all'),apiGet('/v1/security/incidents'),apiGet('/v1/security/decisions/assets')]);
         setDec(d);
         setPol(p);
         setInc(i);
         setAssets(a);
         setErr('')}catch(e:any){setErr(e.message)}
 };
 React.useEffect(()=>{load();window.addEventListener('zt-refresh',load);
     return()=>window.removeEventListener('zt-refresh',load)},[]);
 const denied=dec.filter(x=>x.decision==='DENY').length, step=dec.filter(x=>x.decision==='STEP_UP').length,
 critical=dec.filter(x=>Number(x.compositeScore)>=RISK_THRESHOLDS.critical).length;
 return <>
  {err&&<ErrorBanner text={err}/>}
<div className="hero-grid">
<div className="hero">
<div className="hero-icon">
<ShieldCheck/>
</div>
<div>
<span className="pill live">CONTROL PLANE ONLINE</span>
<h2>See what every AI agent can do — and why.</h2>
<p>Policy,
  behavior, attack-path and asset risk are evaluated together before an action is allowed.</p>
<div className="hero-actions">
<button className="primary" onClick={
      ()=>go('Command Center')}>Open Command Center <ChevronRight size={16}/>
</button>
<button onClick={
      ()=>go('Setup Guide')}>How to run this demo</button>
</div>
</div>
</div>
<div className="health-card">
<small>SYSTEM HEALTH</small>
<div className="health-row">
<span className="dot up">
</span>
<b>All core services</b>
</div>
<div className="health-list">
<span>PostgreSQL <b>OK</b>
</span>
<span>Redis <b>OK</b>
</span>
<span>Authorization API <b>OK</b>
</span>
<span>Demo data <b>{
      dec.length?'LOADED':'EMPTY'}
</b>
</span>
</div>
</div>
</div>
  <div className="metric-grid">
<Metric icon={Activity} label="Security decisions" value={
      dec.length}/>
<Metric icon={XCircle} label="Denied" value={denied} tone="danger"/>
<Metric icon={
      AlertTriangle} label="Step-up" value={step} tone="warn"/>
<Metric icon={
      Siren} label="Critical risk" value={critical} tone="danger"/>
</div>
  <div className="grid-2">
<Panel title="Decision timeline" action={<button className="link-btn" onClick={
          ()=>go('Decision Engine')}>View all <ChevronRight size={14}/>
</button>}
  >
<Timeline rows={dec.slice(0,8)}/>
</Panel>
<Panel title="Protected assets">
<AssetList rows={
      assets.slice(0,6)}/>
</Panel>
</div>
  <div className="grid-2">
<Panel title="Policy posture">
<div className="posture">
<div>
<b>{
      pol.filter(x=>x.status==='ACTIVE').length}
</b>
<span>active policies</span>
</div>
<div>
<b>{
      pol.length}
</b>
<span>total versions</span>
</div>
<div>
<b>{inc.filter(x=>x.status==='OPEN').length}
</b>
<span>open incidents</span>
</div>
</div>
<button className="wide" onClick={
      ()=>go('Lifecycle')}>Open policy lifecycle <ChevronRight size={15}/>
</button>
</Panel>
<Panel title="What to try">
<Quick title="1. Run a high-value transfer" text="Simulator → payment.transfer → ₩15,000,000" onClick={
      ()=>go('Simulator')}/>
<Quick title="2. Inspect the compromised-agent path" text="Attack Paths → select an agent" onClick={
      ()=>go('Attack Paths')}/>
<Quick title="3. Explain the decision" text="Command Center → Explain" onClick={
      ()=>go('Command Center')}/>
</Panel>
</div>
 </>
}

function Lifecycle(){
 const [rows,setRows]=React.useState<any[]>([]),[deployments,setDeployments]=React.useState<any[]>([]),
 [observations,setObservations]=React.useState<any[]>([]),[name,setName]=React.useState('payment_guard'),
 [version,setVersion]=React.useState(2),[canary,setCanary]=React.useState(10),
 [text,setText]=React.useState(`policy "payment_guard" {
 priority 100
 effect deny
 description "Protect high-value payment transfers"
 mode "enforce"
 tags ["banking","ai-agent"]
 principal.type == "AI_AGENT"
 action == "payment.transfer"
 condition {
  context.amount > 10000000
 }
}`),[msg,setMsg]=React.useState(''),[diff,setDiff]=React.useState<any>(null);
 const load=async()=>{try{const [c,d]=await Promise.all([apiGet('/v1/policy-lifecycle/changes'),
         apiGet('/v1/policy-lifecycle/deployments')]);
         setRows(c);
         setDeployments(d);
         if(d[0]?.stage==='CANARY')setObservations(await apiGet(`/v1/policy-lifecycle/deployments/${
             d[0].id}/observations`))}catch(e:any){setMsg(e.message)}};
 usePageRefresh(load);
 React.useEffect(()=>{load()},[]);
 const propose=async()=>{try{const r=await apiPost('/v1/policy-lifecycle/propose',
         {source:'DASHBOARD',name,version,policyText:text,requestedBy:'dashboard-admin',
             canaryPercent:canary});
             setMsg('Change '+r.id+' proposed. Review Diff before approval.');
         await load()}catch(e:any){setMsg(e.message)}};
 const action=async(id:string,kind:'approve'|'publish')=>{try{if(kind==='approve')await
apiPost(`/v1/policy-lifecycle/changes/${
             id}/approve`,{approver:'dashboard-approver',comment:'Reviewed in Policy Lifecycle'}
         );
         else await apiPost(`/v1/policy-lifecycle/changes/${id}/publish`,{});
         await load()}catch(e:any){setMsg(e.message)}};
 const showDiff=async(id:string)=>{try{setDiff(await apiGet(`/v1/policy-lifecycle/changes/${
             id}/diff`))}catch(e:any){setMsg(e.message)}};
 const deploymentAction=async(id:string,kind:'promote'|'abort')=>{try{
         if(kind==='promote')await apiPost(`/v1/policy-lifecycle/deployments/${
             id}/promote`,{});else await apiPost(`/v1/policy-lifecycle/deployments/${
             id}/abort?reason=manual%20canary%20abort`,{});await load()}catch(e:any){
         setMsg(e.message)}};
 return <div>
  <div className="grid-2 wide-left">
   <Panel title="Create policy change">
<div className="form-grid">
<Field label="Policy">
<input value={
       name} onChange={e=>setName(e.target.value)}/>
</Field>
<Field label="Version">
<input type="number" value={
       version} onChange={e=>setVersion(+e.target.value)}/>
</Field>
<Field label="Canary %">
<input type="number" min="0" max="100" value={
       canary} onChange={e=>setCanary(+e.target.value)}/>
</Field>
</div>
<textarea className="code-editor small" value={
       text} onChange={e=>setText(e.target.value)}/>
<button className="primary" onClick={
       propose}>
<GitBranch size={15}/> Propose change</button>
<Notice text={msg}
   />
</Panel>
   <Panel title="Lifecycle queue">
<ChangeTable rows={rows} onAction={action} onDiff={showDiff}/>
</Panel>
  </div>
  {diff&&<div className="panel lifecycle-diff">
<div className="panel-title-row">
<h3>Policy Diff</h3>
<button onClick={
          ()=>setDiff(null)}>Close</button>
</div>
<div className="decision-preview">
<div>
<span>From</span>
<b>{
          diff.fromVersion?`v${diff.fromVersion}`:'NEW'}
</b>
</div>
<div>
<span>To</span>
<b>{
          diff.toVersion?`v${diff.toVersion}`:'—'}
</b>
</div>
<div>
<span>Risk</span>
<b>{
          diff.risk}
</b>
</div>
<div>
<span>Approval</span>
<b>{diff.requiresApproval?'REQUIRED':'STANDARD'}
</b>
</div>
</div>{diff.changes?.map((x:any,i:number)=>
<div className="diff-row" key={
              i}>
<span className="mono">{x.field}
</span>
<code>{x.before||'—'}
</code>
<b>→</b>
<code>{
              x.after||'—'}
</code>
<Status value={x.risk}/>
</div>)}{!diff.changes?.length&&<div className="empty">
<CheckCircle2 size={
              24}/>
<b>No semantic changes detected</b>
</div>}
</div>}
<div className="panel">
<div className="panel-title-row">
<h3>Canary guard observations</h3>
<span className="muted">automatic rollback guard</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Observed</th>
<th>Canary events</th>
<th>Baseline</th>
<th>Risk Δ</th>
<th>Deny Δ</th>
<th>Status</th>
</tr>
</thead>
<tbody>{
      observations.slice(0,10).map((x:any)=>
<tr key={x.observationId}>
<td>{time(x.observedAt)}
</td>
<td>{x.canaryEvents}
</td>
<td>{x.baselineEvents}
</td>
<td>{Number(x.riskDelta).toFixed(1)}
</td>
<td>{(Number(x.decisionDelta)*100).toFixed(1)}%</td>
<td>
<Status value={
          x.status}/>
</td>
</tr>)}
</tbody>
</table>
</div>{!observations.length&&<div className="empty">
<ShieldCheck size={
          24}/>
<b>No canary observations yet</b>
</div>}
</div>
  <div className="panel">
<div className="panel-title-row">
<h3>Canary deployments</h3>
<span className="muted">controlled rollout</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Policy</th>
<th>Version</th>
<th>Stage</th>
<th>Traffic</th>
<th>Status</th>
<th>Started</th>
<th/>
</tr>
</thead>
<tbody>{
      deployments.map((x:any)=>
<tr key={x.id}>
<td>
<b>{x.policyName}
</b>
</td>
<td>v{
          x.version}
</td>
<td>{x.stage}
</td>
<td>{x.canaryPercent}%</td>
<td>
<Status value={
          x.status}/>
</td>
<td>{time(x.startedAt)}
</td>
<td>{x.stage==='CANARY'&&x.status==='ACTIVE'&&<>
<button onClick={
              ()=>deploymentAction(x.id,'promote')}>
<Check size={13}/> Promote</button>
<button onClick={
              ()=>deploymentAction(x.id,'abort')}>
<XCircle size={13}/> Abort</button>
</>}
</td>
</tr>)}
</tbody>
</table>
</div>{!deployments.length&&<div className="empty">
<GitBranch size={
          24}/>
<b>No deployments yet</b>
</div>}
</div>
 </div>
}

function Governance(){const [draft,setDraft]=React.useState(''),[lint,
    setLint]=React.useState<any>(null),[blast,setBlast]=React.useState<any>(null);
    const runLint=async()=>{try{setLint(await apiPost('/v1/governance/policies/lint',
            {policyText:draft}))}catch(e:any){setLint({error:e.message})}};
            return <div className="grid-2">
<Panel title="Policy lint">
<p className="muted">Paste a policy and check for dangerous patterns before approval.</p>
<textarea value={
        draft} onChange={e=>setDraft(e.target.value)} placeholder="Paste policy DSL here…"/>
<button onClick={
        runLint}>
<ScanSearch size={15}/> Run lint</button>{lint&&<pre className="json">{
            JSON.stringify(lint,null,2)}
</pre>}
</Panel>
<Panel title="Blast radius">
<p className="muted">Use the lifecycle proposal to calculate affected actions/resources before publishing.</p>
<button className="primary" onClick={
        ()=>apiGet('/v1/policy-lifecycle/changes').then(x=>setBlast(x[0]?.blastRadiusJson||x[0]||{
        }))}>
<Route size={15}/> Load latest analysis</button>{blast&&<pre className="json">{
        JSON.stringify(blast,null,2)}
</pre>}
</Panel>
</div>}

function Simulator(){const [agent,setAgent]=React.useState(''),[action,setAction]=React.useState(''),
    [amount,setAmount]=React.useState(15000000),[resource,setResource]=React.useState('ACC-1001'),
    [out,setOut]=React.useState<any>(null),[busy,setBusy]=React.useState(false),
    [err,setErr]=React.useState('');
 const run=async()=>{if(!agent||!action)return;setBusy(true);
     setErr('');
     try{const x=await apiPost('/v1/actions/evaluate',
         {principal:{id:agent,type:'AI_AGENT',attributes:{model:'demo-agent'}
             },action:{name:action},resource:{type:action.startsWith('refund')?'customer_record':'bank_account',
id:resource,attributes:{}},context:{amount,currency:'KRW',
task_id:action.startsWith('refund')?'refund-demo-task':'payment-demo-task',
tool_id:action.startsWith('refund')?'33333333-3333-3333-3333-333333333302':'33333333-3333-3333-3333-333333333301'}
     },{'Idempotency-Key':crypto.randomUUID()});
     setOut(x)}catch(e:any){setErr(e.message)}
 finally{setBusy(false)}};
 return <div className="grid-2">
<Panel title="Live authorization simulator">
<div className="scenario">
<div className="scenario-icon">
<Play/>
</div>
<div>
<b>Simulate an AI-agent action</b>
<span>The request goes through Agent Boundary → Behavior → Policy → Continuous Decision → Audit.</span>
</div>
</div>
<Field label="Agent">
<SubjectSelect value={agent} onChange={value=>{setAgent(value);setOut(null)}}/>
</Field>
<Field label="Action">
<select value={
     action} onChange={e=>{setAction(e.target.value);setOut(null)}}>
<option value="">Select a sample action</option><option>payment.transfer</option>
<option>refund.create</option>
</select>
</Field>
<Field label="Amount (KRW)">
<input type="number" value={
     amount} onChange={e=>{setAmount(+e.target.value);setOut(null)}}/>
</Field>
<Field label="Resource ID">
<input value={
     resource} onChange={e=>{setResource(e.target.value);setOut(null)}}/>
</Field>
<button className="primary wide" disabled={
     busy||!agent||!action} onClick={run}>{busy?'Evaluating…':'Evaluate request'}
<ChevronRight size={
     15}/>
</button>{err&&<ErrorBanner text={err}/>}
</Panel>
<Panel title="Decision result">{
     out?<DecisionCard out={out}/>:<Empty icon={Gauge} text="Run a request to see the full decision."/>}
</Panel>
</div>}

function RuntimeGateway(){
const [sessions,setSessions]=React.useState<any[]>([]),[sid,
setSid]=React.useState(''),
 [agent,setAgent]=React.useState(''),[runtimeScenario,setRuntimeScenario]=React.useState(''),[amount,setAmount]=React.useState(15000000),
 [decision,setDecision]=React.useState<any>(null),[detail,setDetail]=React.useState<any>(null),
 [err,setErr]=React.useState('');
 const workspace=WORKSPACE;
 const load=async()=>{try{const x=await apiGet('/v1/runtime/sessions');
         setSessions(x);

         setErr('')}catch(e:any){setErr(e.message)}
 };
 usePageRefresh(load);
 React.useEffect(()=>{load()},[]);
 const start=async()=>{if(!agent||!runtimeScenario)return;try{const x=await apiPost('/v1/runtime/sessions',
         {agent,taskId:runtimeScenario==='payment'?'payment-demo-task':'refund-demo-task',
             source:'DASHBOARD',metadata:{scenario:'runtime-demo'}},{'X-Workspace-Id':workspace}
         );
         setSid(x.id);
         await load()}catch(e:any){setErr(e.message)}};
 const check=async()=>{if(!agent||!sid||!runtimeScenario)return;try{const x=await apiPost('/v1/runtime/sessions/'+sid+'/check',
{principal:{id:agent,type:'AI_AGENT',attributes:{}},action:{name:runtimeScenario==='payment'?'payment.transfer':'refund.create'}
,resource:{type:runtimeScenario==='payment'?'bank_account':'order',id:runtimeScenario==='payment'?'ACC-1001':'ORDER-1001',
                 attributes:{}},context:{amount,task_id:runtimeScenario==='payment'?'payment-demo-task':'refund-demo-task',
tool_id:runtimeScenario==='payment'?'33333333-3333-3333-3333-333333333301':'33333333-3333-3333-3333-333333333302'}
         },{'X-Workspace-Id':workspace});
         setDecision(x);
         const d=await apiGet('/v1/runtime/sessions/'+sid);
     setDetail(d);
     await load()}catch(e:any){setErr(e.message)}};
 const inspect=async()=>{if(!sid)return;try{setDetail(await apiGet('/v1/runtime/sessions/'+sid))}catch(e:any){setErr(e.message)}};
 return <>
<div className="hero-grid">
<div className="hero">
<div className="hero-icon">
<Zap/>
</div>
<div>
<span className="pill live">RUNTIME GATEWAY</span>
<h2>Evaluate session-bound actions before execution.</h2>
<p>This page checks policy and risk; it does not execute an external MCP tool. Session → behavior → policy → risk → decision → evidence. The gateway keeps a tenant/workspace
boundary around the runtime.</p>
</div>
</div>
<div className="health-card">
<small>RUNTIME MODEL</small>
<div className="health-list">
<span>Session identity <b>{sid?'Selected':'Not selected'}</b>
</span>
<span>Workspace <b>ISOLATED</b>
</span>
<span>Risk engine <b>LIVE</b>
</span>
<span>Evidence <b>{decision?'See result':'No check yet'}</b>
</span>
</div>
</div>
</div>
 <div className="grid-2">
<Panel title="Live runtime check">
<Field label="Agent">
<SubjectSelect value={agent} onChange={v=>{setAgent(v);setSid('');setDecision(null);setDetail(null)}}/>
</Field>
<Field label="Demo scenario"><select value={runtimeScenario} onChange={e=>{setRuntimeScenario(e.target.value);setDecision(null)}}><option value="">Select payment/refund sample</option><option value="payment">Payment transfer sample</option><option value="refund">Refund sample</option></select></Field><Field label="Session ID">
<select value={sid} onChange={e=>{setSid(e.target.value);setDecision(null);setDetail(null)}} disabled={!agent}><option value="">Select an existing session</option>{sessions.filter(x=>x.agent===agent).map(x=><option key={x.id} value={x.id}>{x.id} · {x.status}</option>)}</select>
</Field>
<Field label="Amount">
<input type="number" value={
     amount} onChange={e=>setAmount(+e.target.value)}/>
</Field>
<div className="button-row">
<button onClick={
     start} disabled={!agent||!runtimeScenario}>
<Zap size={15}/> New session</button>
<button className="primary" onClick={
     check} disabled={!agent||!sid||!runtimeScenario}>Runtime check <ChevronRight size={15}/>
</button>
<button onClick={
     inspect} disabled={!sid}>Inspect session</button>
</div>{err&&<ErrorBanner text={err}/>}
</Panel>
<Panel title="Runtime decision">{decision?<DecisionCard out={decision}
     />:<Empty icon={Zap} text="Start or select a session, then run Runtime check."/>}
</Panel>
</div>
 <Panel title="Recent agent sessions">
<div className="table">
<div className="tr th">
<span>Agent</span>
<span>Status</span>
<span>Source</span>
<span>Last seen</span>
<span>Action</span>
</div>{
     sessions.map((s:any)=>
<div className="tr" key={s.id}>
<span>
<b>{s.agent}
</b>
<small>{String(s.id).slice(0,12)}…</small>
</span>
<span className={
         s.status==='ACTIVE'?'ok':''}>{s.status}
</span>
<span>{s.source}
</span>
<span>{
         time(s.lastSeenAt)}
</span>
<span>
<button className="link-btn" onClick={
         ()=>{setAgent(s.agent);setSid(s.id);setDecision(null);
             setDetail(null)}}>Select</button>
</span>
</div>)}
</div>
</Panel>
 {detail&&<Panel title={`Session event stream · ${detail.eventCount} events`}
     >
<div className="event-stream">{(detail.events||[]).map((e:any)=>
<div className="event-row" key={
             e.id}>
<span>{time(e.createdAt)}
</span>
<b>{e.action}
</b>
<span>{e.resourceId}
</span>
<strong className={riskClass(Number(e.riskScore||0))}>{e.decision}
</strong>
<span>risk {Number(e.riskScore||0).toFixed(0)}
</span>
</div>)}
</div>
</Panel>}
</>
}

function AgentBehavior({subject=''}:{subject?:string}){const [profiles,setProfiles]=React.useState<any[]>([]),
    [agent,setAgent]=React.useState(subject||''),[anoms,setAnoms]=React.useState<any[]>([]),
    [dec,setDec]=React.useState<any[]>([]),[behaviorError,setBehaviorError]=React.useState('');
    React.useEffect(()=>{if(subject)setAgent(subject)},[subject]);
    const requestTicket=React.useRef(0);
    const load=async()=>{const ticket=++requestTicket.current;try{const [p,a,d]=await Promise.all([apiGet('/v1/agents/behavior/profiles'),agent?apiGet('/v1/agents/behavior/'+encodeURIComponent(agent)+'/anomalies'):Promise.resolve([]),agent?apiGet('/v1/agents/behavior/'+encodeURIComponent(agent)+'/decisions'):Promise.resolve([])]);if(ticket!==requestTicket.current)return;setProfiles(p);setAnoms(a);setDec(d);setBehaviorError('')}catch(e:any){if(ticket===requestTicket.current){setBehaviorError(e.message);setAnoms([]);setDec([])}}};
    React.useEffect(()=>()=>{requestTicket.current++},[]);
        usePageRefresh(load);
 React.useEffect(()=>{load()},[agent]);
        return <>
<div className="metric-grid">
<Metric label="Profiles" value={
        profiles.length} icon={Bot}/>
<Metric label="Anomalies" value={anoms.length}
    icon={AlertTriangle} tone="warn"/>
<Metric label="Adaptive decisions" value={
        dec.length} icon={Gauge}/>
<Metric label="Selected agent" value={agent}
    icon={Users}/>
</div>
<div className="grid-2">
{behaviorError&&<ErrorBanner text={behaviorError}/>}<Panel title="Behavior profiles">
<ProfileTable rows={
        profiles}/>
</Panel>
<Panel title="Anomaly feed">
<Field label="Inspect agent">
<SubjectSelect includeClients value={agent} onChange={v=>{requestTicket.current++;setAgent(v);setAnoms([]);setDec([]);setBehaviorError('')}}/>
</Field>
<AnomalyTable rows={
        anoms}/>
</Panel>
</div>
</>}

function AuditLogs({subject=''}:{subject?:string}){
 const [revision,setRevision]=React.useState(0);usePageRefresh(()=>setRevision(x=>x+1));
 const [rows,setRows]=React.useState<any[]>([]),[verify,setVerify]=React.useState<any>(null),[error,setError]=React.useState(''),[loading,setLoading]=React.useState(false);
 React.useEffect(()=>{let current=true;setRows([]);setError('');setLoading(true);setVerify(null);
 (async()=>{try{const audit=await apiGet('/v1/audit');let filtered=audit;
 if(subject){const identities=await apiGet('/v1/identities');const identity=identities.find((row:any)=>row.externalId===subject&&row.identityType==='AI_AGENT');filtered=identity?audit.filter((row:any)=>{let metadata:any={};try{metadata=typeof row.metadata==='string'?JSON.parse(row.metadata):row.metadata||{}}catch{}return row.identityId===identity.id||metadata.principal===subject}):[]}
 if(current)setRows(filtered)}catch(e:any){if(current)setError(e.message)}finally{if(current)setLoading(false)}})();return()=>{current=false};},[subject,revision]);
 return <Panel title="Immutable audit trail" action={<button onClick={()=>apiGet('/v1/audit/verify').then(setVerify).catch((e:any)=>setError(e.message))}><CheckCircle2 size={15}/> Verify tenant chain</button>}>
 {subject&&<p className="workflow-context">Agent: <strong>{subject}</strong>. Filtered from the 100 newest tenant audit records; this is not the complete agent history.</p>}
 {error&&<ErrorBanner text={error}/>} {loading?<p role="status">Loading audit records…</p>:<AuditTable rows={rows}/>}
 {verify&&<pre className="json">{JSON.stringify(verify,null,2)}</pre>}
 </Panel>;
}
function SIEM(){const [rows,setRows]=React.useState<any[]>([]),[name,setName]=React.useState('Demo SIEM'),
    [endpoint,setEndpoint]=React.useState('http://host.docker.internal:9999/zt-audit'),
    [msg,setMsg]=React.useState('');
    const load=()=>apiGet('/v1/siem/sinks').then(x=>{setRows(x);setMsg('')}).catch((e:any)=>{setMsg('Could not load SIEM sinks: '+e.message);setRows([])});
    usePageRefresh(load);
 React.useEffect(()=>{load()},[]);
    const add=async()=>{try{await apiPost('/v1/siem/sinks',
            {name,endpoint,enabled:true,eventTypes:'["AUDIT"]'});
            setMsg('SIEM sink saved');
            load()}catch(e:any){setMsg(e.message)}};
            return <div className="grid-2">
<Panel title="SIEM sink">
<Field label="Name">
<input value={
        name} onChange={e=>setName(e.target.value)}/>
</Field>
<Field label="Webhook endpoint">
<input value={
        endpoint} onChange={e=>setEndpoint(e.target.value)}/>
</Field>
<button onClick={
        add}>
<Radio size={15}/> Save sink</button>
<Notice text={msg}/>
</Panel>
<Panel title="Configured sinks">
<table>
<thead>
<tr>
<th>Name</th>
<th>Endpoint</th>
<th>Enabled</th>
</tr>
</thead>
<tbody>{
        rows.map(x=>
<tr key={x.id}>
<td>{x.name}
</td>
<td className="mono">{x.endpoint}
</td>
<td>
<Status value={x.enabled?'ENABLED':'DISABLED'}/>
</td>
</tr>)}
</tbody>
</table>
</Panel>
</div>}

function SecurityGraph(){const [g,setG]=React.useState<any>(null),[err,
    setErr]=React.useState('');
    const load=async()=>{try{setG(await apiGet('/v1/security-graph?windowMinutes=120'));
            setErr('')}catch(e:any){setErr(e.message)}};
            usePageRefresh(load);
 React.useEffect(()=>{load();
        const i=setInterval(load,DASHBOARD_REFRESH_INTERVAL_MS);
        return()=>clearInterval(i)},[]);
        if(err)return <ErrorBanner text={
        err}/>;
        return <>
<div className="metric-grid">
<Metric label="Graph nodes" value={
        g?.metrics?.nodeCount??'—'} icon={Network}/>
<Metric label="Edges" value={
        g?.metrics?.edgeCount??'—'} icon={GitBranch}/>
<Metric label="Max risk" value={
        g?.metrics?.maxRisk??'—'} icon={AlertTriangle} tone="danger"/>
<Metric label="Anomalies" value={
        g?.metrics?.anomalies??'—'} icon={Siren} tone="warn"/>
<Metric label="MCP gateways" value={
        (g?.nodes||[]).filter((x:any)=>x.type==='MCP_GATEWAY').length} icon={ShieldCheck}
    />
</div>
<GraphCanvas graph={g}/>
</>}
function AttackPaths(){const [agent,setAgent]=React.useState(''),
    [out,setOut]=React.useState<any>(null),[err,setErr]=React.useState('');
    const requestTicket=React.useRef(0);
    const run=async()=>{if(!agent)return;const ticket=++requestTicket.current;setOut(null);try{const result=await apiGet('/v1/security/attack-paths/agents/'+encodeURIComponent(agent));if(ticket!==requestTicket.current)return;setOut(result);
            setErr('')}catch(e:any){setErr(e.message)}};
            usePageRefresh(run);
            return <>
<Panel title="Compromise simulation">
<div className="inline-form">
<Field label="Agent">
<SubjectSelect includeClients value={agent} onChange={v=>{requestTicket.current++;setAgent(v);setOut(null);setErr('')}}/>
</Field>
<button className="primary" onClick={
        run} disabled={!agent}>
<Route size={15}/> Assess attack paths</button>
</div>{err&&<ErrorBanner text={
            err}/>}
</Panel>{out?<div className="grid-2">
<Panel title="Assessment">
<div className="metric-grid compact">
<Metric label="Risk" value={
            out.riskScore??out.risk??'—'} icon={AlertTriangle} tone="danger"/>
<Metric label="Reachable" value={
            out.reachableResources??'—'} icon={Zap}/>
<Metric label="Critical paths" value={
            out.criticalPaths??'—'} icon={Siren}/>
<Metric label="Blocked" value={out.blockedPaths??'—'}
        icon={Lock}/>
</div>
<pre className="json">{JSON.stringify(out,null,2)}
</pre>
</Panel>
<Panel title="How to read this">
<SecurityChain/>
<p className="muted">This is a defensive reachability simulation. It now includes the MCP Security
Gateway as an explicit trust boundary and does not execute an attack.</p>
</Panel>
</div>:<Empty icon={
            Route} text="Run a compromise simulation for an agent."/>}
</>}

function CommandCenter(){const [rows,setRows]=React.useState<any[]>([]),
    [inc,setInc]=React.useState<any[]>([]),[selected,setSelected]=React.useState<any>(null),
    [err,setErr]=React.useState('');
    const load=async()=>{try{const [d,i]=await
Promise.all([apiGet('/v1/security/decisions'),
            apiGet('/v1/security/incidents')]);
            setRows(d);
            setInc(i);
            setErr('')}catch(e:any){
            setErr(e.message)}};
            usePageRefresh(load);
 React.useEffect(()=>{load();const i=setInterval(load,
        5000);
        return()=>clearInterval(i)},[]);
        const explain=async(x:any)=>{try{
            const e=await apiGet('/v1/security/decisions/'+x.id+'/explain');
            setSelected({
                ...x,...e})}catch(e:any){setSelected(x)}};
                const openIncident=async(x:any)=>{
        await apiPost('/v1/security/incidents',{title:`${x.action} · ${x.principalId}
            `,severity:Number(x.compositeScore)>=RISK_THRESHOLDS.critical?'CRITICAL':'HIGH',principalId:x.principalId,
            sourceDecisionId:x.id,summary:x.reason,evidence:[{risk:x.compositeScore}
            ]});
            load()};
            return <>{err&&<ErrorBanner text={err}/>}
<div className="command-banner">
<div>
<span className="pill live">LIVE</span>
<h2>AI Agent Security Command Center</h2>
<p>One place to answer: what happened,
    why was it allowed, and what could happen next?</p>
</div>
<div className="command-score">
<small>Highest current risk</small>
<b>{
        rows.length?Math.max(...rows.map(x=>Number(x.compositeScore)||0)).toFixed(0):'—'}
</b>
</div>
</div>
<div className="grid-2">
<Panel title="Decision timeline">
<Timeline rows={
        rows.slice(0,20)} onExplain={explain}/>
</Panel>
<Panel title="Incident queue">
<IncidentTable rows={
        inc}/>
</Panel>
</div>
<Panel title="High-risk decisions">
<HighRisk rows={
        rows.filter(x=>Number(x.compositeScore)>=RISK_THRESHOLDS.high).slice(0,15)} explain={explain}
    incident={openIncident}/>
</Panel>{selected&&<ExplainModal x={selected}
        close={()=>setSelected(null)}/>}
</>}

function DecisionEngine(){const [rows,setRows]=React.useState<any[]>([]),
    [assets,setAssets]=React.useState<any[]>([]);
    const load=()=>Promise.all([apiGet('/v1/security/decisions'),
    apiGet('/v1/security/decisions/assets')]).then(([a,b])=>{setRows(a);setAssets(b)}
    ).catch(()=>{});
    usePageRefresh(load);
 React.useEffect(()=>{load();const i=setInterval(load,DASHBOARD_REFRESH_INTERVAL_MS);
        return()=>clearInterval(i)},[]);
        return <>
<div className="metric-grid">
<Metric label="Evaluations" value={
        rows.length} icon={Gauge}/>
<Metric label="ALLOW" value={rows.filter(x=>x.decision==='ALLOW').length}
    icon={Check} tone="success"/>
<Metric label="STEP_UP" value={rows.filter(x=>x.decision==='STEP_UP').length}
    icon={AlertTriangle} tone="warn"/>
<Metric label="DENY" value={rows.filter(x=>x.decision==='DENY').length}
    icon={XCircle} tone="danger"/>
</div>
<div className="grid-2">
<Panel title="Continuous decisions">
<Timeline rows={
        rows}/>
</Panel>
<Panel title="Asset criticality">
<AssetList rows={assets}
    />
</Panel>
</div>
</>}

function BlastRadius({go}:any){
 const [agent,setAgent]=React.useState(''),[out,setOut]=React.useState<any>(null),
 [history,setHistory]=React.useState<any[]>([]),[err,setErr]=React.useState(''),
 [loading,setLoading]=React.useState(false);
const requestTicket=React.useRef(0);
const run=async()=>{if(!agent)return;const ticket=++requestTicket.current;setOut(null);setHistory([]);setLoading(true);
    try{const x=await apiGet('/v1/security/blast-radius/agents/'+encodeURIComponent(agent)+'?windowMinutes=120&maxDepth=12');
         const history=await apiGet('/v1/security/blast-radius/agents/'+encodeURIComponent(agent)+'/history');if(ticket!==requestTicket.current)return;
         setOut(x);setHistory(history);
         setErr('')}catch(e:any){if(ticket===requestTicket.current)setErr(e.message)}finally{if(ticket===requestTicket.current)setLoading(false)}};
 usePageRefresh(run);
 React.useEffect(()=>{setOut(null);setHistory([]);setErr('')},[agent]);
 const recs=out?.recommendations||[];
 return <div>
<div className="page-header">
<div>
<h2>Blast Radius & Impact Analysis</h2>
<p>침해된 AI Agent가 실제 기업 자산에 미칠 수 있는 영향과 권고 대응을 계산합니다.</p>
</div>
<button onClick={
     run} disabled={loading||!agent}>
<RefreshCw size={14}/> {loading?'Assessing…':'Re-assess'}
</button>
</div>{err&&<ErrorBanner text={err}/>}
<Panel title="Compromised agent">
<div className="inline-form">
<Field label="Agent">
<SubjectSelect includeClients value={agent} onChange={v=>{requestTicket.current++;setAgent(v);setLoading(false);setOut(null);setErr('')}}/>
</Field>
<button className="primary" onClick={run} disabled={!agent||loading}>
<Crosshair size={
 15}/> Calculate blast radius</button>
</div>
</Panel>{out?.assessmentId&&<div className="response-cta">
<div>
<b>Impact analysis complete</b>
<span>Turn this assessment into a human-approved response plan.</span>
</div>
<button className="primary" onClick={
     async()=>{try{await apiPost('/v1/security/responses/propose',{assessmentId:out.assessmentId,
                 requestedBy:'dashboard-admin'});
                 go('Response Center')}catch(e:any){setErr(e.message)}
     }}>
<Siren size={15}/> Create response plan</button>
</div>}{out&&<>
<div className="metric-grid">
<Metric label="Blast score" value={
 out.blastRadiusScore??'—'} icon={Crosshair} tone={out.severity==='CRITICAL'?'danger':'warn'}
 />
<Metric label="Impacted assets" value={out.impactedAssets??'—'} icon={
     Database}/>
<Metric label="Critical assets" value={out.criticalAssets??'—'}
 icon={Siren} tone="danger"/>
<Metric label="Restricted / Confidential" value={
     out.restrictedAssets??'—'} icon={Lock} tone="warn"/>
<Metric label="Potential payment" value={
     out.potentialPaymentImpact??'—'} icon={Zap}/>
</div>
<div className="grid-2">
<Panel title="Impact summary">
<div className="impact-banner">
<span className={
     'pill '+riskClass(Number(out.blastRadiusScore||0))}>{out.severity}
</span>
<b>{
     out.blastRadiusScore}/100</b>
<span>combined blast-radius score</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Resource</th>
<th>Classification</th>
<th>Criticality</th>
<th>Impact</th>
<th>Owner</th>
</tr>
</thead>
<tbody>{
     (out.assets||[]).map((x:any)=>
<tr key={x.id}>
<td>
<b>{x.resourceType}/{
         x.resourceId}
</b>
</td>
<td>
<Status value={x.classification}/>
</td>
<td>{
         x.criticality}
</td>
<td>
<Status value={x.severity}/>
</td>
<td>{x.owner}
</td>
</tr>)}
</tbody>
</table>
</div>{!(out.assets||[]).length&&<Empty icon={Database}
     text="No registered Security Assets matched the reachable resource types."/>}
</Panel>
<Panel title="Recommended remediation">
<div className="remediation-list">{
     recs.map((x:any,i:number)=>
<div className="remediation-card" key={i}>
<div>
<Status value={
         x.priority}/>
<b>{x.title}
</b>
<small>{x.type}
</small>
</div>
<p>{x.control}
</p>
<code>{x.target||('count: '+x.count)}
</code>
</div>)}
</div>
<p className="muted small">Recommendations are non-destructive. They do not terminate sessions,
 revoke tools or rotate credentials automatically.</p>
</Panel>
</div>
<Panel title="Attack paths contributing to blast radius">
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Risk</th>
<th>Severity</th>
<th>Resource</th>
<th>Depth</th>
<th>Path</th>
</tr>
</thead>
<tbody>{
     (out.attackPaths||[]).slice(0,30).map((x:any,i:number)=>
<tr key={i}>
<td>{
         x.risk}
</td>
<td>
<Status value={x.severity}/>
</td>
<td>{x.resourceLabel||x.resource}
</td>
<td>{x.depth}
</td>
<td>
<code>{(x.path||[]).join(' → ')}
</code>
</td>
</tr>)}
</tbody>
</table>
</div>
</Panel>
<Panel title="Assessment history">
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Time</th>
<th>Score</th>
<th>Severity</th>
<th>Assets</th>
<th>Critical</th>
<th>Recommendations</th>
</tr>
</thead>
<tbody>{
     history.map((x:any)=>
<tr key={x.id}>
<td>{time(x.generatedAt)}
</td>
<td>{
         x.riskScore}
</td>
<td>
<Status value={x.severity}/>
</td>
<td>{x.impactedAssets}
</td>
<td>{x.criticalAssets}
</td>
<td>{x.recommendedActions}
</td>
</tr>)}
</tbody>
</table>
</div>
</Panel>
</>}
</div>
}

function IncidentResponse(){
 const [rows,setRows]=React.useState<any[]>([]),[selected,setSelected]=React.useState<any>(null),
 [err,setErr]=React.useState(''),[status,setStatus]=React.useState(''),
 [busy,setBusy]=React.useState(false);
 const load=async()=>{try{setRows(await apiGet('/v1/security/cases'));
         setErr('')}catch(e:any){setErr(e.message)}};
 usePageRefresh(load);
 React.useEffect(()=>{load()},[]);
 const open=async(id:string)=>{try{setSelected(await apiGet('/v1/security/cases/'+id));
         setErr('')}catch(e:any){setErr(e.message)}};
 const changeStatus=async(value:string)=>{if(!selected?.case?.id)return;
setBusy(true);
try{await apiPost('/v1/security/cases/'+selected.case.id+'/status?value='+encodeURIComponent(value)+'&actor=dashboard-analyst',
         {});
         await open(selected.case.id);
         await load()}catch(e:any){setErr(e.message)}
     finally{setBusy(false)}};
 const handoff=async()=>{if(!selected?.case?.id)return;
     setBusy(true);
     try{
         const r=await apiPost('/v1/security/cases/'+selected.case.id+'/handoff',
{connector:'GENERIC_SOAR',actor:'dashboard-analyst'});
setStatus(r.handoff?.externalRef||'SOAR handoff created');
         await open(selected.case.id);
         await load()}catch(e:any){setErr(e.message)}
     finally{setBusy(false)}};
 return <div>
<div className="page-header">
<div>
<h2>Incident Response & Evidence</h2>
<p>Security Case를 만들고,
 대응 근거를 보존하고, SOAR/SOC로 안전하게 handoff합니다.</p>
</div>
<button onClick={load}
 >
<RefreshCw size={14}/> Refresh</button>
</div>{err&&<ErrorBanner text={
         err}/>} {status&&<div className="notice">
<CheckCircle2 size={15}/>{status}
</div>}
<div className="metric-grid">
<Metric icon={FolderOpen} label="Open cases" value={
      rows.filter(x=>['OPEN','INVESTIGATING','CONTAINED'].includes(x.status)).length}
  />
<Metric icon={Siren} label="Critical" value={rows.filter(x=>x.severity==='CRITICAL').length}
  tone="danger"/>
<Metric icon={FileSearch} label="Evidence" value={rows.reduce((n,
      x)=>n+(x.evidenceCount||0),0)}/>
<Metric icon={Send} label="SOAR handoffs" value={
      rows.filter(x=>x.externalRef&&x.externalRef!=='null').length}/>
</div>
  <div className="grid-2">
<Panel title="Security cases">
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Severity</th>
<th>Case</th>
<th>Status</th>
<th>Evidence</th>
<th>Assigned</th>
</tr>
</thead>
<tbody>{
      rows.map(x=>
<tr key={x.id} onClick={()=>open(x.id)} style={{cursor:'pointer'}
      }>
<td>
<Status value={x.severity}/>
</td>
<td>
<b>{x.title}
</b>
<small className="block muted">{
      x.id}
</small>
</td>
<td>
<Status value={x.status}/>
</td>
<td>{x.evidenceCount}
</td>
<td>{x.assignedTo}
</td>
</tr>)}
</tbody>
</table>
</div>{!rows.length&&<Empty icon={
      FolderOpen} text="No security cases yet. Create a case from an approved response action."/>}
</Panel>
   <Panel title="Case workflow">
<div className="policy-flow">
<span>Detection</span>
<i>→</i>
<span>Evidence</span>
<i>→</i>
<span>Investigation</span>
<i>→</i>
<span>Containment</span>
<i>→</i>
<span className="final">SOAR / SOC</span>
</div>
<p className="muted">Evidence is stored with a SHA-256 content hash. Handoff emits a durable security
event instead of silently calling an external production system.</p>{
       selected?.case&&<div className="feature-grid">
<span>
<b>Case</b> {selected.case.id}
</span>
<span>
<b>Source</b> {selected.case.sourceType}
</span>
<span>
<b>External</b> {
           selected.case.externalRef}
</span>
<span>
<b>Evidence</b> {selected.case.evidenceCount}
</span>
</div>}
</Panel>
</div>
  {selected?.case&&<div className="grid-2">
<Panel title={selected.case.title}
      >
<div className="inline-form">
<button disabled={busy} onClick={()=>changeStatus('INVESTIGATING')}
      >Investigating</button>
<button disabled={busy} onClick={()=>changeStatus('CONTAINED')}
      >Contained</button>
<button disabled={busy} onClick={()=>changeStatus('RESOLVED')}
      >Resolve</button>
<button className="primary" disabled={busy} onClick={
          handoff}>
<Send size={14}/> SOAR Handoff</button>
</div>
<p>{selected.case.summary}
</p>
<pre className="json">{JSON.stringify(selected.case,null,2)}
</pre>
</Panel>
<Panel title="Evidence timeline">
<div className="timeline">{
          (selected.evidence||[]).map((e:any)=>
<div className="timeline-item" key={
              e.id}>
<div>
<Status value={e.type}/>
<b>{e.sourceRef}
</b>
</div>
<small>{time(e.createdAt)}
          · {e.createdBy}
</small>
<code>{e.hash}
</code>
<pre className="json">{JSON.stringify(e.payload,
              null,2)}
</pre>
</div>)}
</div>
</Panel>
</div>}
</div>
}

function ResponseCenter(){
 const [rows,setRows]=React.useState<any[]>([]),[err,setErr]=React.useState(''),[busy,setBusy]=React.useState('');
 const load=async()=>{try{setRows(await apiGet('/v1/security/responses'));
         setErr('')}catch(e:any){setErr(e.message)}};
         usePageRefresh(load);
 React.useEffect(()=>{load()}
 ,[]);
 const act=async(id:string,kind:string)=>{setBusy(id+kind);
     setErr('');
try{const q=kind==='approve'?'/approve?approver=security-approver':kind==='execute'?'/execute?executor=security-operator':'/reject?approver=security-approver';
         await apiPost('/v1/security/responses/'+id+q,kind==='reject'?{reason:'Rejected by security approver'}
         :{});
         await load()}catch(e:any){setErr(e.message)}finally{setBusy('')}}
 ;
 const pending=rows.filter(x=>x.status==='PENDING').length,approved=rows.filter(x=>x.status==='APPROVED').length,
 executed=rows.filter(x=>x.status==='EXECUTED').length;
 return <div>
<div className="page-header">
<div>
<h2>Automated Security Response</h2>
<p>Detect → Impact → Approve → Execute → Verify. Destructive response actions require a separate human approver.</p>
</div>
<button onClick={
     load}>
<RefreshCw size={14}/> Refresh</button>
</div>{err&&<ErrorBanner text={
         err}/>}
<div className="metric-grid">
<Metric icon={Clock} label="Awaiting approval" value={
     pending} tone={pending?'warn':''}/>
<Metric icon={CheckCircle2} label="Approved" value={
     approved}/>
<Metric icon={ShieldCheck} label="Executed" value={executed}
 />
<Metric icon={Siren} label="Control model" value="HUMAN-IN-THE-LOOP"/>
</div>
<Panel title="Response queue">
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Priority</th>
<th>Action</th>
<th>Target</th>
<th>Requested by</th>
<th>Status</th>
<th>Reason</th>
<th>Control</th>
</tr>
</thead>
<tbody>{
     rows.map((x:any)=>
<tr key={x.id}>
<td>
<Status value={x.priority}/>
</td>
<td>
<b>{
         x.actionType}
</b>
</td>
<td className="mono">{x.target}
</td>
<td>{x.requestedBy}
</td>
<td>
<Status value={x.status}/>
</td>
<td>{x.reason}
</td>
<td>{x.status==='PENDING'&&<>
<button disabled={
             busy===x.id+'approve'} onClick={()=>act(x.id,'approve')}>
<Check size={
             13}/> Approve</button>
<button disabled={busy===x.id+'reject'} onClick={
             ()=>act(x.id,'reject')}>
<XCircle size={13}/> Reject</button>
</>}{x.status==='APPROVED'&&<button className="primary" disabled={
             busy===x.id+'execute'} onClick={()=>act(x.id,'execute')}>
<Zap size={13}
         /> Execute</button>}{x.status==='EXECUTED'&&<button onClick={async()=>{
                 try{await apiPost('/v1/security/cases/from-response/'+x.id,{requestedBy:'dashboard-analyst'}
                     );
                     window.dispatchEvent(new Event('zt-refresh'))}catch(e:any){setErr(e.message)}
             }}>
<FolderOpen size={13}/> Create Case</button>}
</td>
</tr>)}
</tbody>
</table>
</div>{
 !rows.length&&<Empty icon={Siren} text="No response actions yet. Run Blast Radius and create a response plan."/>}
</Panel>
<Panel title="Response guardrails">
<div className="policy-flow">
<span>Blast Radius</span>
<i>→</i>
<span>Recommendation</span>
<i>→</i>
<span>Human Approval</span>
<i>→</i>
<span>Execution</span>
<i>→</i>
<span className="final">Audit Evidence</span>
</div>
<div className="feature-grid">
<span>
<ShieldCheck size={
     14}/>Agent isolation ends active runtime sessions</span>
<span>
<Lock size={
     14}/>MCP tool revocation disables future tool exposure</span>
<span>
<FileLock2 size={
     14}/>Policy changes remain in Policy Control Plane</span>
<span>
<ScrollText size={
     14}/>Every approval/execution emits a security event</span>
</div>
<p className="muted small">Credential rotation is represented as an external-control action until a
tenant secret manager is connected. The platform never silently changes production policy or rotates
credentials from this screen.</p>
</Panel>
</div>
}

function ComplianceEvidence(){
 const [framework,setFramework]=React.useState('SOC2'),[days,setDays]=React.useState(30),
 [rows,setRows]=React.useState<any[]>([]),[report,setReport]=React.useState<any>(null),
 [err,setErr]=React.useState(''),[busy,setBusy]=React.useState(false);
 const load=()=>apiGet('/v1/compliance/assessments').then(setRows).catch(e=>setErr(e.message));
 usePageRefresh(load);
 React.useEffect(()=>{load()},[]);
 const generate=async()=>{setBusy(true);
     setErr('');
     try{const r=await apiPost('/v1/compliance/assessments/generate',
         {framework,days,generatedBy:'dashboard-admin'});
         setReport(r);
         load()}catch(e:any){
         setErr(e.message)}finally{setBusy(false)}};
 return <div>
<div className="page-header">
<div>
<h2>Compliance Evidence</h2>
<p>감사 대응에 필요한 정책·결정·사고·대응 evidence를 control 단위로 묶고,
 보고서에 SHA-256 무결성 해시를 부여합니다.</p>
</div>
<button onClick={generate} disabled={
     busy}>
<FileSearch size={14}/>{busy?'Generating…':'Generate Evidence Report'}
</button>
</div>{err&&<ErrorBanner text={err}/>}
<div className="grid-2">
<Panel title="Assessment scope">
<div className="inline-form">
<Field label="Framework">
<select value={
     framework} onChange={e=>setFramework(e.target.value)}>
<option>SOC2</option>
<option>ISO27001</option>
<option>FINANCIAL</option>
</select>
</Field>
<Field label="Period (days)">
<input type="number" min="1" max="365" value={
     days} onChange={e=>setDays(Number(e.target.value)||30)}/>
</Field>
</div>
<div className="policy-flow">
<span>Audit</span>
<i>+</i>
<span>Policy / Response</span>
<i>+</i>
<span>Incident Evidence</span>
<i>→</i>
<span className="final">Signed Report Hash</span>
</div>
</Panel>
<Panel title="What this proves">
<div className="feature-grid">
<span>
<Check size={
     14}/>Tenant-aware evidence</span>
<span>
<Check size={14}/>Control mapping</span>
<span>
<Check size={
     14}/>Evidence counts</span>
<span>
<Check size={14}/>SHA-256 report hash</span>
<span>
<Check size={
     14}/>Period-bounded report</span>
<span>
<Check size={14}/>Audit trail</span>
</div>
</Panel>
</div>{
     report&&<Panel title="Generated report">
<div className="metric-grid compact">
<Metric icon={
         ShieldCheck} label="Status" value={report.status}/>
<Metric icon={Gauge}
     label="Score" value={Number(report.score).toFixed(1)+'%'}/>
<Metric icon={
         Database} label="Evidence" value={report.evidenceCount}/>
<Metric icon={
         Lock} label="Report hash" value={String(report.reportHash||'').slice(0,
         18)+'…'}/>
</div>
<table>
<thead>
<tr>
<th>Control</th>
<th>Description</th>
<th>Met</th>
<th>Evidence</th>
</tr>
</thead>
<tbody>{
         (report.controls||[]).map((x:any)=>
<tr key={x.id}>
<td>
<b>{x.id}
</b>
</td>
<td>{
             x.name}
</td>
<td>
<Status value={x.met?'MET':'GAP'}/>
</td>
<td>{x.evidence}
</td>
</tr>)}
</tbody>
</table>
<details>
<summary>Generated Markdown</summary>
<pre className="json">{
         report.markdown}
</pre>
</details>
<p className="muted small">Internal evidence package only — this does not constitute SOC 2/ISO 27001
certification or an independent audit opinion.</p>
</Panel>}
 {<Panel title="Assessment history">
<table>
<thead>
<tr>
<th>Framework</th>
<th>Status</th>
<th>Score</th>
<th>Evidence</th>
<th>Hash</th>
<th>Created</th>
</tr>
</thead>
<tbody>{
         rows.map((x:any)=>
<tr key={x.id}>
<td>
<b>{x.framework}
</b>
</td>
<td>
<Status value={
             x.status}/>
</td>
<td>{Number(x.score).toFixed(1)}%</td>
<td>{x.evidenceCount}
</td>
<td className="mono">{String(x.reportHash).slice(0,16)}…</td>
<td>{
             time(x.createdAt)}
</td>
</tr>)}
</tbody>
</table>{!rows.length&&<Empty icon={
             FileSearch} text="No compliance assessments generated yet."/>}
</Panel>}
</div>
}

function SetupGuide(){const [copied,setCopied]=React.useState('');
    const copy=(s:string)=>{
        navigator.clipboard?.writeText(s);
        setCopied(s);
        setTimeout(()=>setCopied(''),
        1500)};
        return <div className="setup">
<div className="setup-hero">
<BookOpen/>
<div>
<span className="pill">2.0.0 QUICKSTART</span>
<h2>From ZIP to running Security Command Center</h2>
<p>This guide assumes Windows 11 + Docker Desktop. You do not need Maven or Node installed to use the Docker path.</p>
</div>
</div>
<Step n="1" title="Install the prerequisites">
<p>Install <b>Docker Desktop</b> with WSL2 enabled. Confirm:</p>
<Code text="docker --version\ndocker compose version" copy={
        copy}/>
<p className="muted">Optional local development: Java 17+, Maven 3.9+,
    Node 22+.</p>
</Step>
<Step n="2" title="Start the complete stack">
<p>Open PowerShell in the extracted <b>zt-security-mvp-2.0.0</b> folder:</p>
<Code text="docker compose -f docker/docker-compose.yml up --build -d" copy={
        copy}/>
<p>Wait about 30–90 seconds on first run. Flyway creates the schema and loads synthetic demo data automatically.</p>
</Step>
<Step n="3" title="Open the product">
<div className="link-cards">
<a href={window.location.origin} target="_blank">
<LayoutDashboard/> Dashboard <ExternalLink size={
        14}/>
</a>
<a href={`${API}/swagger-ui.html`} target="_blank">
<BookOpen/> Swagger API <ExternalLink size={
        14}/>
</a>
<a href={`${API}/v1/health`} target="_blank">
<Activity/> Health <ExternalLink size={
        14}/>
</a>
</div>
</Step>
<Step n="4" title="Run the first demo">
<ol>
<li>Open <b>Simulator</b>.</li>
<li>Leave <b>payment.transfer</b> and amount at <b>15,
    000,000 KRW</b>.</li>
<li>Click <b>Evaluate request</b>.</li>
<li>Open <b>Command Center</b> and click <b>Explain</b>.</li>
<li>Open <b>Attack Paths</b> and select a registered agent.</li>
<li>Open <b>Security Graph</b> to see Agent → Task → MCP Gateway → Tool → Action → Resource.</li>
</ol>
</Step>
<Step n="5" title="Stop / reset">
<Code
  text={
    "docker compose -f docker/docker-compose.yml down\n\n#" +
    " Full reset, including demo database:\ndocker compose" +
    " -f docker/docker-compose.yml down -v"
  }
  copy={copy}
/>
<p className="warning">
<AlertTriangle size={15}/> <b>down -v deletes the local PostgreSQL volume.</b> Use it when you want a
clean demo from scratch.</p>
</Step>
<Step n="6" title="What is included">
<div className="feature-grid">{
        ['OIDC/JWT + RBAC','Tenant RLS','Policy DSL + lifecycle','Simulation + blast radius',
        'Automated response + containment','Canary + rollback','Agent Task / Tool',
        'Behavior baseline','Sequence + peer anomaly','Security Graph','Attack Path analysis',
        'Continuous risk decision','Explainability','Human approval','Immutable audit',
        'SIEM webhook','OpenTelemetry'].map(x=>
<span key={x}>
<Check size={14}/>{
            x}
</span>)}
</div>
</Step>
<Step n="7" title="Local development without Docker">
<p>Use Docker only for Postgres/Redis/Keycloak,
    then run the API and dashboard locally.</p>
<Code
  text={
    "docker compose -f docker/docker-compose.yml up -d" +
    " postgres redis keycloak otel-collector\n\n# API\nmvn" +
    " -pl apps/authorization-api spring-boot:run\n\n#" +
    " Dashboard (new terminal)\ncd dashboard/web\nnpm" +
    " install\nnpm run dev"
  }
  copy={copy}
/>
<p className="muted">The dashboard uses <code>/api</code> through its same-origin proxy. The dev API
key is <code>dev-master-key</code>.</p>
</Step>{copied&&<div className="copy-ok">
<Copy size={14}/> Copied</div>}
</div>}

function Step({n,title,children}:any){return <section className="setup-step">
<div className="step-no">{
        n}
</div>
<div>
<h3>{title}
</h3>{children}
</div>
</section>}
function Code({text,copy}:any){return <div className="code-wrap">
<pre>{
        text}
</pre>
<button onClick={()=>copy(text)}>
<Copy size={14}/> Copy</button>
</div>}
function SecurityChain(){return <div className="chain">{[['Human','Identity'],
        ['Agent','Task'],['Task','Tool'],['Tool','Action'],['Action','Resource'],
        ['Behavior','Risk'],['Risk','Policy'],['Policy','Decision']].map(([a,b],
        i)=>
<React.Fragment key={i}>
<span>{a}
</span>
<ChevronRight size={14}/>
<span>{
            b}
</span>{i<7&&<i>→</i>}
</React.Fragment>)}
</div>}
function GraphCanvas({graph}:any){if(!graph)return <Empty icon={Network}
    text="No graph data yet."/>;
    const by:any={};
    for(const n of graph.nodes||[]) (by[n.type]??=[]).push(n);
    const cols=['IDENTITY','AGENT','TASK','MCP_GATEWAY','TOOL','ACTION','RESOURCE',
    'BEHAVIOR','POLICY'];
    return <div className="graph-card">
<div className="graph-head">
<div>
<b>Live Security Graph</b>
<span>{
        graph.generatedAt?time(graph.generatedAt):''}
</span>
</div>
<div className="graph-legend">
<span className="dot low">
</span>normal <span className="dot high">
</span>high <span className="dot critical">
</span>critical</div>
</div>
<div className="graph-columns">{
        cols.map(c=>
<div className="graph-col" key={c}>
<h4>{c}
</h4>{(by[c]||[]).slice(0,
            8).map((n:any)=>
<div className={`graph-node ${riskClass(Number(n.risk||0))}
                `} key={n.id}>
<b>{n.label}
</b>
<small>risk {Number(n.risk||0).toFixed(0)}
            · {n.status||'—'}
</small>
</div>)}
</div>)}
</div>
<div className="edge-list">{
        (graph.edges||[]).slice(0,40).map((e:any,i:number)=>
<div key={i}>
<span>{
            e.from}
</span>
<b>{e.relation}
</b>
<span>{e.to}
</span>
</div>)}
</div>
</div>}
function DecisionCard({out}:any){return <div className={`decision-card ${
            String(out.decision).toLowerCase()}`}>
<div className="decision-top">
<span className="pill">DECISION</span>
<strong>{
        out.decision}
</strong>
</div>
<div className="decision-score">
<span>Composite risk</span>
<b>{
        out.risk?.score??out.compositeScore??'—'}
</b>
</div>
<p>{out.reason}
</p>{
        out.matchedPolicies?.length>0&&<div className="chips">{out.matchedPolicies.map((p:any)=>
<span key={
                p.id}>{p.name} v{p.version}
</span>)}
</div>}
<div className="decision-meta">
<span>Request {
        String(out.requestId).slice(0,8)}…</span>
<span>Latency {out.latencyMs}
    ms</span>
</div>
</div>}
function ExplainModal({x,close}:any){return <div className="modal-back">
<div className="modal">
<button className="modal-close" onClick={
        close}>×</button>
<span className="pill">EXPLAINABLE DECISION</span>
<h2>{
        x.decision} · Risk {Number(x.compositeScore).toFixed(1)}
</h2>
<p>{x.explanation||x.reason}
</p>
<div className="evidence-list">{(x.evidence||[]).map((e:any)=>
<div className="evidence" key={
            e.id||e.signal}>
<div>
<b>{e.signal}
</b>
<span>{e.score} × {e.weight}
</span>
</div>
<p>{
            e.explanation}
</p>
</div>)}
</div>
</div>
</div>}
function HighRisk({rows,explain,incident}:any){return <table>
<thead>
<tr>
<th>Agent</th>
<th>Action</th>
<th>Resource</th>
<th>Risk</th>
<th>Decision</th>
<th>Actions</th>
</tr>
</thead>
<tbody>{
        rows.map((x:any)=>
<tr key={x.id}>
<td>{x.principalId}
</td>
<td className="mono">{
            x.action}
</td>
<td>{x.resourceType}/{x.resourceId}
</td>
<td>
<Risk value={
            x.compositeScore}/>
</td>
<td>
<Status value={x.decision}/>
</td>
<td>
<button onClick={
            ()=>explain(x)}>Explain</button>
<button onClick={()=>incident(x)}>Open incident</button>
</td>
</tr>)}
</tbody>
</table>}
function Timeline({rows,onExplain}:any){if(!rows?.length)return <Empty icon={
        Clock} text="No security decisions yet."/>;
        return <div className="timeline">{
        rows.map((x:any)=>
<div className="timeline-row" key={x.id||x.requestId}
        >
<span className={`timeline-dot ${String(x.decision).toLowerCase()}`}>
</span>
<div className="timeline-main">
<div>
<b>{
            x.principalId||'unknown'}
</b>
<span className="mono">{x.action}
</span>
</div>
<small>{
            x.resourceType}/{x.resourceId} · {time(x.createdAt)}
</small>
</div>
<Risk value={
            x.compositeScore||x.riskScore||0}/>{onExplain&&<button className="mini" onClick={
                ()=>onExplain(x)}>Explain</button>}
</div>)}
</div>}
function AssetList({rows}:any){return <div className="asset-list">{rows?.map((x:any)=>
<div className="asset" key={
            x.id}>
<div>
<b>{x.resourceId}
</b>
<span>{x.resourceType} · {x.dataClassification}
</span>
</div>
<Risk value={x.criticality}/>
</div>)}
</div>}
function PolicyTable({rows}:any){return <table>
<thead>
<tr>
<th>Name</th>
<th>Version</th>
<th>Effect</th>
<th>Status</th>
<th>Priority</th>
</tr>
</thead>
<tbody>{
        rows?.map((x:any)=>
<tr key={x.id}>
<td>
<b>{x.name}
</b>
</td>
<td>v{x.version}
</td>
<td>
<Status value={x.effect}/>
</td>
<td>
<Status value={x.status}/>
</td>
<td>{
            x.priority}
</td>
</tr>)}
</tbody>
</table>}
function ChangeTable({rows,onAction,onDiff}:any){return <table>
<thead>
<tr>
<th>Policy</th>
<th>Version</th>
<th>Status</th>
<th>Canary</th>
<th>Requested</th>
<th>Approval</th>
<th/>
</tr>
</thead>
<tbody>{
        rows?.map((x:any)=>
<tr key={x.id}>
<td>{x.policyName}
</td>
<td>v{x.version}
</td>
<td>
<Status value={x.status}/>
</td>
<td>{x.canaryPercent}%</td>
<td>{
            x.requestedBy||'—'}
</td>
<td>{x.approvedBy||'—'}
</td>
<td>
<button onClick={
            ()=>onDiff(x.id)}>Diff</button>{x.status==='PENDING'&&<button onClick={
                ()=>onAction(x.id,'approve')}>
<Check size={13}/> Approve</button>}{x.status==='APPROVED'&&<button onClick={
                ()=>onAction(x.id,'publish')}>
<Zap size={13}/> Publish</button>}
</td>
</tr>)}
</tbody>
</table>}
function ProfileTable({rows}:any){return <table>
<thead>
<tr>
<th>Agent</th>
<th>Peer</th>
<th>Max/min</th>
<th>Max amount</th>
<th>Step-up</th>
<th>Deny</th>
</tr>
</thead>
<tbody>{
        rows?.map((x:any)=>
<tr key={x.id}>
<td>
<b>{x.agentExternalId}
</b>
</td>
<td>{
            x.peerGroup}
</td>
<td>{x.maxActionsPerMinute}
</td>
<td>{x.maxAmount?`₩${
                money(Number(x.maxAmount))}`:'—'}
</td>
<td>{x.adaptiveStepUpScore}
</td>
<td>{
            x.adaptiveDenyScore}
</td>
</tr>)}
</tbody>
</table>}
function AnomalyTable({rows}:any){return <table>
<thead>
<tr>
<th>Time</th>
<th>Type</th>
<th>Severity</th>
<th>Score</th>
<th>Reason</th>
</tr>
</thead>
<tbody>{
        rows?.map((x:any)=>
<tr key={x.id}>
<td>{time(x.createdAt)}
</td>
<td className="mono">{
            x.anomalyType}
</td>
<td>
<Status value={x.severity}/>
</td>
<td>
<Risk value={
            x.score}/>
</td>
<td>{x.reason}
</td>
</tr>)}
</tbody>
</table>}
function AuditTable({rows}:any){return <table>
<thead>
<tr>
<th>Time</th>
<th>Action</th>
<th>Resource</th>
<th>Decision</th>
<th>Risk</th>
<th>Reason</th>
</tr>
</thead>
<tbody>{
        rows?.map((x:any)=>
<tr key={x.id}>
<td>{time(x.createdAt)}
</td>
<td className="mono">{
            x.action}
</td>
<td>{x.resourceType}/{x.resourceId}
</td>
<td>
<Status value={
            x.decision}/>
</td>
<td>{x.riskScore??'—'}
</td>
<td>{x.reason||'—'}
</td>
</tr>)}
</tbody>
</table>}
function IncidentTable({rows}:any){return <table>
<thead>
<tr>
<th>Severity</th>
<th>Status</th>
<th>Title</th>
<th>Agent</th>
<th>Time</th>
</tr>
</thead>
<tbody>{
        rows?.map((x:any)=>
<tr key={x.id}>
<td>
<Status value={x.severity}/>
</td>
<td>
<Status value={
            x.status}/>
</td>
<td>{x.title}
</td>
<td>{x.principalId}
</td>
<td>{time(x.createdAt)}
</td>
</tr>)}
</tbody>
</table>}
function Metric({label,value,icon:Icon,tone=''}:any){return <div className={
        `metric ${tone}`}>
<div className="metric-icon">
<Icon size={17}/>
</div>
<div>
<small>{
        label}
</small>
<b>{typeof value==='number'?value.toLocaleString():value}
</b>
</div>
</div>}
function Risk({value}:any){const n=Number(value)||0;
    return <span className={
        `risk ${riskClass(n)}`}>{n.toFixed(0)}
</span>}
function Status({value}:any){return <span className={`status ${String(value).toLowerCase().replace(/[^a-z0-9_]/g,
            '-')}`}>{value}
</span>}
function Panel({title,action,children}:any){return <section className="panel">
<div className="panel-head">
<h3>{
        title}
</h3>{action}
</div>{children}
</section>}
function Field({label,children}:any){return <label className="field">
<span>{label}
</span>{children}
</label>}
function Notice({text}:any){return text?<div className="notice">{text}
</div>:null}
function ErrorBanner({text}:any){return <div className="error-banner">
<AlertTriangle size={
        17}/>
<div>
<b>Request failed</b>
<span>{text}
</span>
</div>
</div>}
function Empty({icon:Icon,text}:any){return <div className="empty">
<Icon size={
        25}/>
<b>{text}
</b>
<small>Use the Setup Guide to load the demo environment.</small>
</div>}
function Quick({title,text,onClick}:any){return <button className="quick" onClick={
        onClick}>
<div>
<b>{title}
</b>
<span>{text}
</span>
</div>
<ChevronRight size={
        16}/>
</button>}

export default App;

function KubernetesPolicies(){
 const [rows,setRows]=React.useState<any[]>([]);
 const [error,setError]=React.useState('');
 const load=async()=>{try{setRows(await apiGet('/v1/kubernetes/policies'));
         setError('')}catch(e:any){setError(e.message)}};
         usePageRefresh(load);
 React.useEffect(()=>{
     load()},[]);
 return <div>
<div className="page-header">
<div>
<h2>Kubernetes ZeroTrust Policies</h2>
<p>Governed CRD policies reconciled by the ZeroTrustPolicy Operator.</p>
</div>
<button onClick={
     load}>Refresh</button>
</div>{error&&<ErrorBanner text={error}/>}
<Panel title="Policy reconciliation">
<div className="policy-flow">
<span>CRD</span>
<i>→</i>
<span>Identity</span>
<i>→</i>
<span>Attestation</span>
<i>→</i>
<span>Policy</span>
<i>→</i>
<span>Execution Contract</span>
<i>→</i>
<span className="final">Verification</span>
</div>
<div className="table-scroll">
<table>
<thead>
<tr>
<th>Cluster</th>
<th>Namespace</th>
<th>Resource</th>
<th>Desired</th>
<th>Observed</th>
</tr>
</thead>
<tbody>{
     rows.map((r:any)=>
<tr key={r.id}>
<td>{r.clusterName}
</td>
<td>{r.namespace}
</td>
<td>{r.resourceName}
</td>
<td>{r.desiredState}
</td>
<td>{r.observedState||'PENDING'}
</td>
</tr>)}
</tbody>
</table>
</div>
</Panel>
</div>
}

