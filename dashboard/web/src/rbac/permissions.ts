export type DashboardRole='CISO'|'SOC_ANALYST'|'DEVOPS';
export const ROLE_PERMISSIONS:Record<DashboardRole,string[]>= {
 CISO:['copilot','overview','enterprise','operations','governance','intelligence','federation','audit','approvals','evidence'],
 SOC_ANALYST:['copilot','overview','intelligence','runtime','agents','approvals','audit','siem','incident','response','evidence','attackpaths'],
 DEVOPS:['copilot','overview','operations','policy','lifecycle','runtime','dataplane','k8s','agents','audit']
};
export const MENU_PERMISSION:Record<string,string>={Overview:'overview',
    Enterprise:'enterprise',Governance:'governance','Runtime Intelligence':'runtime',
    'Agents':'agents',Approvals:'approvals','Audit Logs':'audit',SIEM:'siem',
    'Incident Response':'incident','Response Center':'response','Compliance Evidence':'evidence',
    'Attack Paths':'attackpaths','Policy Studio':'policy',Lifecycle:'lifecycle',
    'Data Plane':'dataplane','Kubernetes Policies':'k8s',
    'Product Operations':'operations','Setup Guide':'overview','Command Center':'overview',
    'Continuous Agent Risk':'intelligence','Agent Risk Forecast':'intelligence',
    'Preventive Control Loop':'intelligence','Control Loop Verification':'intelligence',
    'Policy Auto-Tuning':'policy','AI Security Copilot':'copilot','Policy Tests':'policy','Execution Records':'approvals',
    'Policy What-if':'policy','Shadow Replay':'policy','Simulator':'policy',
    'MCP Gateway':'runtime','Runtime Gateway':'runtime','Agent Behavior':'agents','Security Graph':'intelligence',
    'Blast Radius':'attackpaths','Decision Engine':'policy'};
export function can(role:DashboardRole, menu:string){const p=MENU_PERMISSION[menu];
    return Boolean(p && ROLE_PERMISSIONS[role]?.includes(p))}
