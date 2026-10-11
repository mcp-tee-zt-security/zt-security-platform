param(
    [ValidateSet('http://localhost:8089','http://127.0.0.1:8089')][string]$KeycloakUrl='http://localhost:8089',
    [string]$AdminUsername='admin',
    [string]$AdminPassword='admin'
)
# Updates local demo roles/users and client scopes. Does not delete existing accounts or reset their passwords.
$ErrorActionPreference='Stop'
$seedPath=Join-Path $PSScriptRoot '../../docker/keycloak/zt-mcp-demo-realm.json'
$seed=Get-Content -LiteralPath $seedPath -Raw | ConvertFrom-Json
$login=Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" -Body @{client_id='admin-cli';grant_type='password';username=$AdminUsername;password=$AdminPassword}
$headers=@{Authorization=('Bearer '+$login.access_token)}
$realmUrl="$KeycloakUrl/admin/realms/$($seed.realm)"
function Get-KeycloakList {
    param([string]$Uri)
    # Windows PowerShell 5.1 emits a REST JSON array as one pipeline object.
    # Enumerate the response explicitly before filtering/counting individual records.
    $response=Invoke-RestMethod -Uri $Uri -Headers $headers
    foreach($item in $response){ Write-Output $item }
}
foreach($role in $seed.roles.realm){
    $known=@(Get-KeycloakList -Uri "$realmUrl/roles")
    if(-not ($known | Where-Object { $_.name -eq $role.name })){
        Invoke-RestMethod -Method Post -Uri "$realmUrl/roles" -Headers $headers -ContentType 'application/json' -Body ($role | ConvertTo-Json) | Out-Null
    }
}
$demoGroups=@{}
foreach($group in $seed.groups){
    $knownGroups=@(Get-KeycloakList -Uri "$realmUrl/groups")
    $found=@($knownGroups | Where-Object { $_.name -eq $group.name })
    if($found.Count -eq 0){
        Invoke-RestMethod -Method Post -Uri "$realmUrl/groups" -Headers $headers -ContentType 'application/json' -Body ($group | ConvertTo-Json) | Out-Null
        $found=@(Get-KeycloakList -Uri "$realmUrl/groups" | Where-Object { $_.name -eq $group.name })
    }
    if($found.Count -ne 1){throw "Expected one demo group: $($group.name)"}
    $demoGroups[$group.name]=$found[0].id
}
foreach($user in $seed.users){
    $users=@(Get-KeycloakList -Uri "$realmUrl/users?username=$([Uri]::EscapeDataString($user.username))&exact=true")
    if($users.Count -eq 0){
        $body=$user | Select-Object username,enabled,firstName,lastName,email,emailVerified,requiredActions,credentials
        Invoke-RestMethod -Method Post -Uri "$realmUrl/users" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 10) | Out-Null
        $users=@(Get-KeycloakList -Uri "$realmUrl/users?username=$([Uri]::EscapeDataString($user.username))&exact=true")
    }
    if($users.Count -ne 1){throw "Expected exactly one demo user: $($user.username)"}
    $assigned=@(Get-KeycloakList -Uri "$realmUrl/users/$($users[0].id)/role-mappings/realm")
    foreach($name in $user.realmRoles){
        if(-not ($assigned | Where-Object { $_.name -eq $name })){
            $role=Invoke-RestMethod -Uri "$realmUrl/roles/$name" -Headers $headers
            $roleBody=ConvertTo-Json -InputObject @($role) -Depth 10
            Invoke-RestMethod -Method Post -Uri "$realmUrl/users/$($users[0].id)/role-mappings/realm" -Headers $headers -ContentType 'application/json' -Body $roleBody | Out-Null
        }
    }
    foreach($path in $user.groups){
        $groupName=$path.TrimStart('/')
        if(-not $demoGroups.ContainsKey($groupName)){throw "Missing demo group: $groupName"}
        Invoke-RestMethod -Method Put -Uri "$realmUrl/users/$($users[0].id)/groups/$($demoGroups[$groupName])" -Headers $headers | Out-Null
    }
    Write-Output "Demo user ready: $($user.username)"
}
$clients=@(Get-KeycloakList -Uri "$realmUrl/clients?clientId=zt-dashboard")
if($clients.Count -ne 1){throw 'zt-dashboard client missing; start Keycloak with the realm import first'}
$scopes=@(Get-KeycloakList -Uri "$realmUrl/client-scopes")
foreach($mapper in $seed.clients[0].protocolMappers){
    $knownMappers=@(Get-KeycloakList -Uri "$realmUrl/clients/$($clients[0].id)/protocol-mappers/models")
    if(-not ($knownMappers | Where-Object { $_.name -eq $mapper.name })){
        Invoke-RestMethod -Method Post -Uri "$realmUrl/clients/$($clients[0].id)/protocol-mappers/models" -Headers $headers -ContentType 'application/json' -Body ($mapper | ConvertTo-Json -Depth 12) | Out-Null
    }
}
foreach($scopeName in $seed.clients[0].defaultClientScopes){
    $scope=$scopes | Where-Object { $_.name -eq $scopeName }
    if(-not $scope){throw "Missing realm client scope: $scopeName"}
    Invoke-RestMethod -Method Put -Uri "$realmUrl/clients/$($clients[0].id)/default-client-scopes/$($scope.id)" -Headers $headers | Out-Null
}
Write-Output 'Local Workflow demo provisioning complete. Sign out and sign in again for new roles.'
