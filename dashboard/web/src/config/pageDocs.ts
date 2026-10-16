type PageDocument = { path: string; title: string };

// Reuse repository documentation; update this map when a menu gains its own guide.
const architecture: PageDocument = {path:'docs/architecture/4.75_ARCHITECTURE.md',title:'Platform architecture'};
const security: PageDocument = {path:'docs/security/4.75_SECURITY_MODEL.md',title:'Security and governance model'};
const risk: PageDocument = {path:'docs/security/RISK_BEHAVIOR_RUNTIME.ko.md',title:'리스크·Agent 행동·런타임 위협 설명'};
const events: PageDocument = {path:'docs/api/COMMON_SECURITY_EVENTS_V1.md',title:'Common events, risk samples and forecast requirements'};
const audit: PageDocument = {path:'docs/demo/AUDIT_AND_METRIC_DETAILS.ko.md',title:'감사 기록과 숫자 카드 상세 안내'};
const operations: PageDocument = {path:'docs/operations/4.75_OPERATIONS.md',title:'Operations and failure handling'};
const simulation: PageDocument = {path:'docs/platform/4.75_SIMULATION.md',title:'Simulation and digital twin'};

export const PAGE_DOCUMENTS: Record<string,PageDocument> = {
 'Overview': architecture,
 'Command Center': security,
 'Enterprise': {path:'docs/enterprise/SSO_PREFLIGHT.md',title:'Enterprise SSO setup'},
 'Product Operations': operations,
 'Policy Studio': {path:'docs/policy/README.md',title:'Policy authoring and DSL'},
 'Lifecycle': {path:'docs/api/DASHBOARD_DEMO_WORKFLOW.ko.md',title:'정책 작성·검토·활성화 안내'},
 'Policy Tests': simulation,
 'Policy What-if': simulation,
 'Shadow Replay': simulation,
 'Simulator': simulation,
 'Runtime Intelligence': risk,
 'Continuous Agent Risk': risk,
 'Agent Risk Forecast': events,
 'Preventive Control Loop': events,
 'Control Loop Verification': events,
 'Policy Auto-Tuning': security,
 'MCP Gateway': {path:'docs/api/MCP_DASHBOARD_REGISTRATION.md',title:'MCP server and tool registration'},
 'Retrieval Access': {path:'docs/api/RETRIEVAL_CONTROL_PLANE_V2.md',title:'Retrieval connections, ACL and AI policies'},
 'Data Plane': {path:'docs/api/DATA_PLANE_BUNDLE_V2.md',title:'Rust data plane and policy bundles'},
 'Governance': security,
 'Workflow Chat': {path:'docs/demo/WORKFLOW_CHAT.ko.md',title:'Workflow Chat 사용·설정·승인 안내'},
 'Runtime Gateway': risk,
 'Agents': {path:'docs/api/SERVICE_AGENT_CONNECTIONS.md',title:'Service clients, AI identities and connections'},
 'Agent Behavior': risk,
 'Approvals': {path:'docs/demo/MCP_KEYCLOAK_APPROVAL.ko.md',title:'Keycloak 로그인과 독립 승인 안내'},
 'Execution Records': {path:'docs/api/MCP_GATEWAY_EXECUTION.md',title:'Governed execution and saved-call resume'},
 'Audit Logs': audit,
 'Metric Details': audit,
 'SIEM': {path:'docs/operations/4.75_OBSERVABILITY.md',title:'Observability and security telemetry'},
 'Compliance Evidence': {path:'docs/compliance/enterprise-compliance-mapping.md',title:'Compliance evidence mapping'},
 'Security Graph': {path:'docs/security/SECURITY_GRAPH.md',title:'Security Graph: nodes, relationships, risk and scope'},
 'Attack Paths': security,
 'Blast Radius': security,
 'Response Center': operations,
 'Incident Response': operations,
 'Decision Engine': risk,
 'Kubernetes Policies': {path:'docs/platform/4.75_KUBERNETES.md',title:'Kubernetes policy integration'},
 'Setup Guide': {path:'docs/INDEX.md',title:'Documentation index and setup guides'},
};

const base=(import.meta.env.VITE_DOCS_BASE_URL || 'https://github.com/mcp-tee-zt-security/zt-security-platform/blob/master').replace(/\/$/,'');
export function pageDocument(page:string){
 const document=PAGE_DOCUMENTS[page] || PAGE_DOCUMENTS['Setup Guide'];
 return {...document,url:base+'/'+document.path.split('/').map(encodeURIComponent).join('/')};
}
