param(
    [ValidateSet('http://localhost:8089','http://127.0.0.1:8089')][string]$KeycloakUrl='http://localhost:8089',
    [ValidateSet('http://localhost:8080','http://127.0.0.1:8080')][string]$ApiUrl='http://localhost:8080',
    [string]$AdminUsername='admin',
    [string]$AdminPassword='admin'
)
# Explicit local provisioning only. Creates an isolated workspace and a DRAFT policy;
# never publishes policies, resets user passwords, or changes the existing Stitch connection.
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'update-workflow-demo-keycloak.ps1') -KeycloakUrl $KeycloakUrl -AdminUsername $AdminUsername -AdminPassword $AdminPassword
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$seed=Get-Content -LiteralPath (Join-Path $repoRoot 'docker/keycloak/zt-mcp-demo-realm.json') -Raw | ConvertFrom-Json
$tenantId='11111111-1111-1111-1111-111111111111'
$workspaceId='88888888-8888-8888-8888-888888888801'
$realmUrl="$KeycloakUrl/admin/realms/zt-mcp-demo"
$login=Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" -Body @{client_id='admin-cli';grant_type='password';username=$AdminUsername;password=$AdminPassword}
$kcHeaders=@{Authorization=('Bearer '+$login.access_token)}
function KcCall([string]$Path,[string]$Method='GET',$Body=$null){
    if($null -eq $Body){return Invoke-RestMethod -Method $Method -Uri "$realmUrl$Path" -Headers $kcHeaders}
    return Invoke-RestMethod -Method $Method -Uri "$realmUrl$Path" -Headers $kcHeaders -ContentType 'application/json' -Body (ConvertTo-Json -InputObject $Body -Depth 24)
}
function KcList([string]$Path){$reply=KcCall $Path;foreach($item in $reply){Write-Output $item}}
function ClaimMapper([string]$Name,[string]$Value){
    return @{name=$Name;protocol='openid-connect';protocolMapper='oidc-hardcoded-claim-mapper';config=@{'claim.name'=$Name;'claim.value'=$Value;'jsonType.label'='String';'access.token.claim'='true';'id.token.claim'='false'}}
}
function AudienceMapper([string]$Value){return @{name='audience';protocol='openid-connect';protocolMapper='oidc-audience-mapper';config=@{'included.custom.audience'=$Value;'access.token.claim'='true';'id.token.claim'='false'}}}
function GrantRole([string]$UserId,[string]$Name){
    $role=KcCall "/roles/$Name"
    KcCall "/users/$UserId/role-mappings/realm" 'POST' @($role) | Out-Null
}
function SaveMappers([string]$ClientId,$Mappers){
    foreach($mapper in $Mappers){
        $known=@(KcList "/clients/$ClientId/protocol-mappers/models" | Where-Object { $_.name -eq $mapper.name })
        if($known.Count -gt 1){throw "Duplicate protocol mapper: $($mapper.name)"}
        if($known.Count -eq 0){KcCall "/clients/$ClientId/protocol-mappers/models" 'POST' $mapper | Out-Null}
        else{
            $model=$mapper | ConvertTo-Json -Depth 24 | ConvertFrom-Json
            $model | Add-Member -NotePropertyName id -NotePropertyValue $known[0].id -Force
            KcCall "/clients/$ClientId/protocol-mappers/models/$($known[0].id)" 'PUT' $model | Out-Null
        }
    }
}
function SetClient($Client){
    $rows=@(KcList ("/clients?clientId="+[Uri]::EscapeDataString($Client.clientId)))
    if($rows.Count -eq 0){KcCall '/clients' 'POST' $Client | Out-Null;$rows=@(KcList ("/clients?clientId="+[Uri]::EscapeDataString($Client.clientId)))}
    if($rows.Count -ne 1){throw "Expected one client: $($Client.clientId)"}
    # Only these dedicated demo clients are managed here; the original dashboard is untouched.
    $representation=$Client | ConvertTo-Json -Depth 24 | ConvertFrom-Json
    $representation.PSObject.Properties.Remove('protocolMappers')
    KcCall "/clients/$($rows[0].id)" 'PUT' $representation | Out-Null
    SaveMappers $rows[0].id $Client.protocolMappers
    return $rows[0].id
}
function ServiceToken([string]$ClientId,[string]$Secret){
    $response=Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/zt-mcp-demo/protocol/openid-connect/token" -Body @{grant_type='client_credentials';client_id=$ClientId;client_secret=$Secret}
    return $response.access_token
}
function ZtCall([string]$Path,[string]$Method='GET',$Body=$null){
    $h=@{Authorization=('Bearer '+$ztToken);'X-Tenant-Id'=$tenantId;'X-Workspace-Id'=$workspaceId}
    if($null -eq $Body){return Invoke-RestMethod -Method $Method -Uri "$ApiUrl$Path" -Headers $h}
    return Invoke-RestMethod -Method $Method -Uri "$ApiUrl$Path" -Headers $h -ContentType 'application/json' -Body (ConvertTo-Json -InputObject $Body -Depth 24)
}
function ZtList([string]$Path){$reply=ZtCall $Path;foreach($item in $reply){Write-Output $item}}

$bootstrapName='zt-file-bootstrap-'+[Guid]::NewGuid().ToString('N')
$bootstrapSecret=[Guid]::NewGuid().ToString('N')
$bootstrapId=$null
try{
    # A short-lived admin service account lets the local setup use the same scoped ZT APIs.
    # It is deleted in finally, and its credentials/tokens are never printed or written to disk.
    $bootstrap=@{clientId=$bootstrapName;enabled=$true;protocol='openid-connect';publicClient=$false;secret=$bootstrapSecret;serviceAccountsEnabled=$true;standardFlowEnabled=$false;directAccessGrantsEnabled=$false;fullScopeAllowed=$true;defaultClientScopes=@('basic','roles');protocolMappers=@((ClaimMapper 'tenant_id' $tenantId),(ClaimMapper 'workspace_id' $workspaceId),(ClaimMapper 'actor_type' 'CONNECTOR'),(AudienceMapper 'zt-mcp-gateway'))}
    KcCall '/clients' 'POST' $bootstrap | Out-Null
    $created=@(KcList ("/clients?clientId=$bootstrapName"))
    if($created.Count -ne 1){throw 'Temporary bootstrap client creation failed'}
    $bootstrapId=$created[0].id
    $bootstrapUser=KcCall "/clients/$bootstrapId/service-account-user"
    GrantRole $bootstrapUser.id 'ADMIN'
    $ztToken=ServiceToken $bootstrapName $bootstrapSecret
    $workspaces=@(ZtList '/v1/workspaces' | Where-Object { $_.slug -eq 'file-access-demo' })
    if($workspaces.Count -eq 0){$createdWorkspace=ZtCall '/v1/workspaces' 'POST' @{name='File Access Demo';slug='file-access-demo'};$workspaceId=$createdWorkspace.id}
    elseif($workspaces.Count -eq 1){$workspaceId=$workspaces[0].id}
    else{throw 'Duplicate file-access-demo workspace'}
    # A JWT/header workspace match is required for all subsequent writes.
    $bootstrap.protocolMappers=@((ClaimMapper 'tenant_id' $tenantId),(ClaimMapper 'workspace_id' $workspaceId),(ClaimMapper 'actor_type' 'CONNECTOR'),(AudienceMapper 'zt-mcp-gateway'))
    SaveMappers $bootstrapId $bootstrap.protocolMappers
    $ztToken=ServiceToken $bootstrapName $bootstrapSecret

    $connectorSecret='local-file-sync-only'
    $connector=@{clientId='zt-file-sync';enabled=$true;protocol='openid-connect';publicClient=$false;secret=$connectorSecret;serviceAccountsEnabled=$true;standardFlowEnabled=$false;directAccessGrantsEnabled=$false;fullScopeAllowed=$true;defaultClientScopes=@('basic','roles');protocolMappers=@((ClaimMapper 'tenant_id' $tenantId),(ClaimMapper 'workspace_id' $workspaceId),(ClaimMapper 'actor_type' 'CONNECTOR'),(AudienceMapper 'zt-stitch-integration'))}
    $connectorId=SetClient $connector
    $connectorUser=KcCall "/clients/$connectorId/service-account-user"
    GrantRole $connectorUser.id 'RETRIEVAL_SYNC'
    $accessRows=@(ZtList "/v1/workspaces/$workspaceId/access")
    $subjects=@()
    foreach($username in @('finance','it-user')){
        $people=@(KcList "/users?username=$username&exact=true")
        if($people.Count -ne 1){throw "Expected exactly one $username user"}
        $groupName=if($username -eq 'it-user'){'it'}else{'finance'}
        $subjects+=@{subject=$people[0].id;groups=@($groupName)}
        if(-not ($accessRows | Where-Object { $_.principal_type -eq 'HUMAN' -and $_.principal_id -eq $people[0].id })){
            ZtCall "/v1/workspaces/$workspaceId/access" 'PUT' @{principalType='HUMAN';principalId=$people[0].id;enabled=$true} | Out-Null
        }
    }
    $aiSubject="workflow-chat-$workspaceId"
    $knownIdentities=@(ZtList '/v1/identities' | Where-Object { $_.externalId -eq $aiSubject })
    if($knownIdentities.Count -eq 0){ZtCall '/v1/identities' 'POST' @{externalId=$aiSubject;type='AI_AGENT';name='File Demo Workflow Agent';attributes=@{}} | Out-Null}
    elseif($knownIdentities[0].status -ne 'ACTIVE'){throw 'Existing file-demo AI identity is inactive; activate it manually'}
    if(-not ($accessRows | Where-Object { $_.principal_type -eq 'AI_AGENT' -and $_.principal_id -eq $aiSubject })){
        ZtCall "/v1/workspaces/$workspaceId/access" 'PUT' @{principalType='AI_AGENT';principalId=$aiSubject;enabled=$true} | Out-Null
    }
    ZtCall '/v1/retrieval/connections' 'POST' @{connectionId='file-demo';displayName='Local NFS demo files';connectorSubject=$connectorUser.id;enabled=$true} | Out-Null

    $policyName="allow_file_demo_$workspaceId"
    $dsl=@"
policy "$policyName" {
  effect allow
  principal.type == "AI_AGENT"
  action in ["retrieval.list", "retrieval.read"]
  resource.type == "retrieval_resource"
  condition {
    principal.id == "$aiSubject" and
    context.connectionId == "file-demo"
  }
}
"@
    $knownPolicies=@(ZtList '/v1/policies/all' | Where-Object { $_.name -eq $policyName })
    if($knownPolicies.Count -eq 0){ZtCall '/v1/policies' 'POST' @{name=$policyName;version=1;status='DRAFT';policyText=$dsl} | Out-Null}
    # Policy activation remains a manual review step in the common dashboard.
    $localDir=Join-Path $repoRoot '.local/file-access-demo'
    New-Item -ItemType Directory -Path $localDir -Force | Out-Null
    $configPath=Join-Path $localDir 'config.json'
    $utf8=New-Object System.Text.UTF8Encoding($false)
    $config=@{tenantId=$tenantId;workspaceId=$workspaceId;clientId='zt-file-sync';clientSecret=$connectorSecret;connectorSubject=$connectorUser.id;subjects=$subjects}
    [IO.File]::WriteAllText($configPath,(ConvertTo-Json -InputObject $config -Depth 12),$utf8)
    [IO.File]::WriteAllText((Join-Path $localDir 'demo.env'),("ZT_FILE_WORKSPACE_ID=$workspaceId`nZT_FILE_CONFIG_PATH="+$configPath.Replace([char]92,[char]47)+"`n"),$utf8)
    [IO.File]::WriteAllText((Join-Path $localDir 'allow-file-demo.policy'),$dsl,$utf8)
    # The common 3000 client replaces the old file-only login; keep users and source credentials.
    $retiredClients=@(KcList '/clients?clientId=zt-file-dashboard')
    foreach($retired in $retiredClients){KcCall "/clients/$($retired.id)" 'DELETE' | Out-Null}
    Write-Output "Workspace: File Access Demo ($workspaceId)"
    Write-Output "Connector subject: $($connectorUser.id)"
    Write-Output "Policy: $policyName (publish manually at localhost:3000 in File Access Demo)"
    Write-Output 'Local accounts: finance / demo-finance; it-user / demo-it (existing passwords are retained).'
    Write-Output 'Start the file-access-demo Compose overlay using .local/file-access-demo/demo.env.'
}finally{
    if($bootstrapId){KcCall "/clients/$bootstrapId" 'DELETE' | Out-Null}
    $ztToken=$null;$bootstrapSecret=$null;$login=$null
}
