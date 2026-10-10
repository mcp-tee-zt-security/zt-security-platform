import React from 'react';
export default function RecordLinks({mcp=false}:{mcp?:boolean}){
 const go=(page:string)=>window.dispatchEvent(new CustomEvent('zt-navigate',{detail:{page}}));
 return <div className="workflow-context"><p>{mcp?'This view contains MCP tool calls and their approvals.':'This view contains governed workflow approvals and execution contracts.'} The two record types have separate histories.</p><button onClick={()=>go(mcp?'Approvals':'MCP Gateway')}>{mcp?'Open workflow approvals':'Open MCP calls & approvals'}</button></div>;
}
