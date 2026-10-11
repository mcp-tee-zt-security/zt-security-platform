param(
    [ValidateSet('finance','it-user')][string]$Username='finance',
    [string]$Question='List the payroll folder, then read company-handbook.txt in the general folder. Report each ZT decision separately.',
    [ValidateSet('http://localhost:9999','http://127.0.0.1:9999')][string]$ClientUrl='http://localhost:9999',
    [switch]$ToolsOnly,
    [switch]$HumanPayrollControl
)
# Credentials and human JWT remain in this process, never in an env file or transcript output.
$ErrorActionPreference='Stop'
$credential=Get-Credential -UserName $Username -Message 'Local Keycloak demo login (finance: demo-finance; it-user: demo-it unless changed)'
if(-not $credential){throw 'Login cancelled'}
$token=$null;$password=$null
try{
    $password=$credential.GetNetworkCredential().Password
    $token=Invoke-RestMethod -Method Post -Uri 'http://localhost:8089/realms/zt-mcp-demo/protocol/openid-connect/token' -Body @{grant_type='password';client_id='zt-order-file-demo-login';username=$credential.UserName;password=$password}
    $headers=@{Authorization=('Bearer '+$token.access_token)}
    if($HumanPayrollControl){
        if($ToolsOnly){throw 'Choose HumanPayrollControl or ToolsOnly'}
        $taskConfigPath=Join-Path $PSScriptRoot '../../.local/file-access-demo/config.json'
        $taskConfig=Get-Content -LiteralPath $taskConfigPath -Raw | ConvertFrom-Json
        $headers['X-Tenant-Id']=$taskConfig.tenantId;$headers['X-Workspace-Id']=$taskConfig.workspaceId
        Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/v1/integrations/retrieval/children' -Headers $headers -ContentType 'application/json' -Body '{"resourceId":"file-demo-payroll"}' | ConvertTo-Json -Depth 24
        $taskConfig=$null
    }
    elseif($ToolsOnly){Invoke-RestMethod -Method Get -Uri "$ClientUrl/ai/tools" -Headers $headers | ConvertTo-Json -Depth 24}
    else{Invoke-RestMethod -Method Post -Uri "$ClientUrl/ai" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject @{question=$Question}))) | ConvertTo-Json -Depth 24}
}finally{$password=$null;$token=$null;$headers=$null;$credential=$null}
