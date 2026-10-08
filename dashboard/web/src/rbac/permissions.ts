export type DashboardRole='CISO'|'SOC_ANALYST'|'DEVOPS';
export const ROLE_PERMISSIONS:Record<DashboardRole,string[]>= {
 CISO:['overview','enterprise','governance','intelligence','federation','audit'],
 SOC_ANALYST:['overview','runtime','agents','approvals','audit','siem','incident','response','evidence','attackpaths'],
 DEVOPS:['overview','policy','lifecycle','runtime','dataplane','k8s','agents','audit']
};
export const MENU_PERMISSION:Record<string,string>={Overview:'overview',
    Enterprise:'enterprise',Governance:'governance','Runtime Intelligence':'runtime',
    'Agents':'agents',Approvals:'approvals','Audit Logs':'audit',SIEM:'siem',
    'Incident Response':'incident','Response Center':'response','Compliance Evidence':'evidence',
    'Attack Paths':'attackpaths','Policy Studio':'policy',Lifecycle:'lifecycle',
    'Data Plane':'dataplane','Kubernetes Policies':'k8s'};
export function can(role:DashboardRole, menu:string){const p=MENU_PERMISSION[menu];
    return !p || ROLE_PERMISSIONS[role].includes(p)}
