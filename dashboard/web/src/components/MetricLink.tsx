import React from 'react';
import {hasPendingMutation} from '../api/client';
export type MetricSelection={sourcePage:string;label:string;records?:any[];subject?:string};
const FIELDS=new Set('id name title description agent agentExternalId externalId subject type kind status decision reason severity score compositeScore riskScore riskState riskDrift riskDirection currentScore forecastScore highProbability confidence recommendation eventCount createdAt updatedAt startedAt lastSeenAt activeSessions task anomalyType source target resourceId resourceType action operation verification responseId profileId evidenceCount externalRef allowed blocked risk value version priority effect policies bundleVersion p50_us p95_us p99_us p999_us max_us sampleCount latencyUs requestedBy approvedBy executedBy from to before after changed controlId met engine workloadIdentity bundleHash bundlePin bundle_signature_valid reachableResources criticalPaths blockedPaths classification criticality paymentExposure'.split(' '));
export function metricRows(rows:any[]):any[]{return rows.slice(0,500).map(row=>typeof row==='object'&&row!==null?Object.fromEntries(Object.entries(row).filter(([key,value])=>FIELDS.has(key)&&(value==null||['string','number','boolean'].includes(typeof value)))):{value:String(row)});}
export default function MetricLink({label,records,subject,children,className='metric'}:{label:string;records?:any[];subject?:string;children:React.ReactNode;className?:string}){
 const open=()=>{if(hasPendingMutation()){window.dispatchEvent(new CustomEvent('zt-navigation-blocked',{detail:{message:'Wait for the current action to finish before opening details.'}}));return;}
  const sourcePage=new URLSearchParams(window.location.search).get('zt_page')||'Overview';
  window.dispatchEvent(new CustomEvent('zt-metric-open',{detail:{sourcePage,label,subject,...(records!==undefined?{records:metricRows(records)}:{})}}));
 };
 return <button type="button" className={className+' metric-link'} onClick={open} aria-label={'View '+label+' details'} title={'View '+label+' details'}>{children}<span className="metric-link-arrow" aria-hidden="true">↗</span></button>;
}
