# 공통 워크스페이스 선택과 서버 인가

브라우저는 `http://localhost:3000` 하나를 사용합니다. 상단 Workspace 선택기는 `GET /v1/workspaces/accessible`에서 서버가 허용한 ACTIVE 공간만 표시합니다. File Access Demo와 Security Demo의 채팅·정책·승인·감사 요청은 같은 선택 범위를 사용합니다.

## 로그인과 권한

- 공통 Keycloak 브라우저 client의 JWT에는 `actor_type=HUMAN`, Boolean `workspace_selection=true`, tenant 및 MCP audience가 있어야 합니다. 이 claim은 선택 기능을 허용하며 임의 workspace 권한을 부여하지 않습니다.
- 일반 사용자의 추가 workspace 권한은 ZT `workspace_access_grants`의 실제 JWT `sub`로 확인합니다. 매 요청마다 ACTIVE 공간과 현재 enabled 권한을 검사합니다.
- 기존 signed `workspace_id`는 기본 범위의 호환 권한입니다. 별도 grant가 없어도 이 기본 공간은 사용할 수 있지만 명시적으로 disabled한 HUMAN grant가 있으면 차단합니다. 선택 기능이 없는 기존 JWT는 signed 기본 공간만 사용할 수 있습니다.
- 선택 기능이 있는 HUMAN ADMIN/PLATFORM은 해당 tenant의 ACTIVE 공간을 관리할 수 있습니다. 일반 사용자의 disabled grant가 tenant 관리자 역할을 제한하는 규칙은 아닙니다.
- connector와 service client 토큰은 고정된 workspace를 유지합니다. 브라우저 헤더를 바꿔 기계 토큰의 범위를 넓힐 수 없습니다.
- Workflow AI는 `workflow-chat-<선택 UUID>`라는 서버 지정 신원이며 같은 tenant의 ACTIVE AI identity와 ACTIVE 공간이 필요합니다. 명시적으로 disabled한 AI grant가 있으면 실행을 차단합니다. 기존 namespace별 AI identity는 추가 grant 없이 호환됩니다. 이 권한은 task/tool 위임·정책·원본 파일 ACL을 대체하지 않습니다.

설정 스크립트는 finance와 it-user의 실제 Keycloak sub에 File Access Demo 권한을 추가합니다. 둘 다 공간을 열 수 있어도 Payroll ACL은 finance만, IT 폴더 ACL은 it 그룹만 허용합니다.

## 범위 전환

진행 중인 HTTP 변경 요청이 있으면 선택기를 잠급니다. 전환은 기존 읽기 요청을 중단하고 전체 페이지를 다시 로드합니다. 초기 서버 인가가 끝나기 전에는 페이지 컴포넌트를 열지 않습니다. 늦게 도착한 이전 범위 응답도 표시하지 않습니다. 변경 요청은 자동으로 재시도하지 않습니다.

브라우저 저장소에는 tenant/사용자별 마지막 workspace UUID만 저장합니다. 서버는 이 선택값을 신뢰하지 않고 다시 검증합니다. Keycloak 세션 복원으로 다시 인증하며 토큰을 workspace 선택값으로 저장하지 않습니다.

Workflow·실행 계약·승인·감사·보안 결정 조회는 선택 범위를 사용합니다. 감사 기록의 workspace는 서버가 기록하며, 과거 데이터는 저장된 결정 또는 MCP 호출의 workspace를 확인할 수 있을 때만 보충합니다. 범위를 알 수 없는 과거 기록은 선택한 공간에 포함하지 않습니다. 신원·자산 같은 tenant-wide 인벤토리, 명시적 tenant-wide 정책 및 tenant 감사 체인 검증은 공유 기능입니다.

## 관리자 API

선택한 공간을 `X-Workspace-Id`에, tenant를 `X-Tenant-Id`에 보내고 관리자 JWT로 호출합니다. 경로의 workspace와 선택 헤더가 같아야 합니다.

```http
GET /v1/workspaces/accessible
GET /v1/workspaces/<workspace UUID>/access
PUT /v1/workspaces/<workspace UUID>/access
Content-Type: application/json

{"principalType":"HUMAN","principalId":"<실제 Keycloak sub>","enabled":true}
```

AI 권한의 principalType은 `AI_AGENT`, principalId는 `workflow-chat-<workspace UUID>`입니다. 차단하려면 같은 grant에 `enabled:false`를 보냅니다. 이 API는 workspace 접근 관리이며 파일 ACL이나 ALLOW 정책을 자동 변경하지 않습니다.

기존 환경에는 새 authorization-api와 dashboard를 적용한 뒤 `setup-file-access-demo.ps1`을 실행하고 로그아웃·재로그인하세요. 서버 시작 시 V11 migration이 grant 테이블과 감사 workspace 열을 추가합니다. 정책은 기존 활성 상태를 유지하며 새 DRAFT는 수동 Publish해야 합니다.
