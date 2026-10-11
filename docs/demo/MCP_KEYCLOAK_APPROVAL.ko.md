# AI 주문 요청과 Keycloak 사용자 승인

로컬 주문 데모에서 AI 클라이언트는 등록된 서비스 secret을 사용하고, 사람 승인자는 Keycloak 로그인으로 발급받은 JWT를 사용합니다. 현재 `mcp-oidc.compose.yml`은 대시보드 전체에 공통 OIDC 로그인을 적용하며 Workflow Chat과 통합 승인함도 활성화합니다. [Workflow Chat 데모](WORKFLOW_CHAT.ko.md)에서 새 계정 및 기존 realm 업데이트 방법을 확인하세요.

## 실행

저장소 루트에서 실행합니다. 아래 명령은 사용자가 수정본을 빌드하고 시작하는 명령입니다.

```bash
docker compose -f docker/docker-compose.yml -f docker/mcp-oidc.compose.yml up -d --build keycloak authorization-api dashboard policy-data-plane-1 policy-data-plane-2
```

Keycloak이 이미 실행 중이면 새 realm 파일 추가만으로는 위 `up` 명령이 Keycloak을 재시작하지 않을 수 있습니다. 새 `zt-mcp-demo` realm을 가져오도록 기존 컨테이너를 한 번 재시작하세요.

```bash
docker compose -f docker/docker-compose.yml restart keycloak
```

주문 데모 설정은 `zt-mcp-demo` issuer를 사용합니다. Stitch 시뮬레이션 설정은 `zt-stitch-contract` issuer를 사용하므로 두 overlay를 함께 적용하지 않습니다. 현재 API는 issuer 하나를 검증합니다. overlay 전환 때도 같은 파일 조합을 유지하세요.

Keycloak은 시작할 때 새 realm 파일을 가져옵니다. 이미 존재하는 realm은 덮어쓰지 않으므로 이후 계정·mapper 변경은 Keycloak 관리 콘솔에서 반영해야 합니다. 데이터 볼륨을 삭제할 필요가 없습니다.

`zt-dashboard`에는 `basic` client scope가 Default로 연결되어 있어야 합니다. Keycloak 26에서 JWT 사용자 ID(`sub`)는 이 scope의 mapper로 추가됩니다. 이전 설정을 가져온 경우 관리 콘솔에서 `zt-mcp-demo` → Clients → `zt-dashboard` → Client scopes → Add client scope → `basic` → Default로 추가하고 다시 로그인하세요.

## 승인 테스트

1. 기존 `ai-agent-client` 설정과 `createOrder` 도구 바인딩/활성 정책을 유지합니다. 조건부 STEP_UP은 `Require independent approval`을 해제하고 금액 정책으로 적용합니다.
2. `POST http://localhost:9999/ai`로 주문 생성 요청을 보냅니다. `PENDING_APPROVAL` 응답의 `callId`를 보관합니다.
3. `http://localhost:3000` → **MCP Gateway** → **Caller identity**를 펼치고 **Sign in with Keycloak**을 누릅니다.
4. 로컬 합성 계정 `approver` / `demo-approve`로 로그인합니다. Keycloak에서 비밀번호를 입력하며 ZT 화면에는 입력하지 않습니다.
5. 로그인 후 MCP Gateway로 돌아옵니다. **Keycloak user: approver · JWT authentication**과 서버가 검증한 Caller subject를 확인합니다.
6. **MCP approvals → Refresh → Review request**에서 저장된 인자를 확인하고 **Approve**합니다. 승인만으로 도구는 실행되지 않습니다.
7. `POST http://localhost:9999/ai/calls/{callId}/resume`을 호출합니다. 저장된 인자로 실행되며 LLM을 다시 호출하지 않습니다.
8. MCP approvals의 승인자 subject를 확인합니다. 승인자는 `approvals.decided_by`에 Keycloak 사용자 `sub`로 기록됩니다. Call history에서 다른 호출자의 실행 상태를 보려면 관리자 인증을 사용합니다. APPROVER 계정의 Call history는 자신의 호출만 표시합니다.

`viewer` / `demo-view`는 AUDITOR 계정이므로 승인 권한이 없습니다. Navigation view 변경은 서버 권한을 바꾸지 않습니다. 서비스 클라이언트에는 승인 권한을 부여하지 않습니다. 자기 승인도 기존 서버 검증으로 차단됩니다.

## 인증 동작

- Authorization Code + PKCE S256과 공식 `keycloak-js` SDK를 사용합니다. 브라우저 클라이언트에는 secret이 없습니다.
- 토큰은 SDK 메모리에만 보관하고 요청 전 만료를 확인해 갱신합니다. 로그인 후 MCP 페이지를 새로고침하면 `check-sso`로 기존 Keycloak 세션에서 새 토큰을 받습니다. 토큰을 localStorage/sessionStorage에 저장하지 않습니다. 세션이 유효하면 비밀번호를 다시 입력하지 않고 잠시 Keycloak으로 이동했다 돌아옵니다. 세션 만료·로그인 실패 시에는 다시 로그인해야 하며 개발 키로 자동 전환하지 않습니다. 명시적 로그아웃 직후에는 자동 SSO 복원을 하지 않습니다.
- OIDC 요청에는 `Authorization: Bearer`만 보내고 `X-API-Key`는 제거합니다. 서버도 bearer와 API key가 동시에 전달된 경우 API key 인증을 사용하지 않습니다.
- 서버는 JWT 서명·issuer·만료, MCP audience, tenant/workspace, `HUMAN` claim과 승인 역할을 검증합니다. 이 overlay는 `ZT_MCP_REQUIRE_OIDC_APPROVAL=true`로 MCP 승인·거절에 사람 OIDC 인증을 강제하므로 개발 master key로 승인할 수 없습니다.
- DPoP/mTLS 토큰 바인딩은 README의 미구현 TODO이며 이번 로그인에 포함되지 않습니다.

개발 서버는 `VITE_OIDC_ISSUER=http://localhost:8089/realms/zt-mcp-demo`, `VITE_OIDC_CLIENT_ID=zt-dashboard`를 사용합니다. Docker는 같은 값을 build args로 전달합니다. 다른 tenant/workspace를 사용하려면 realm claim mapper와 대시보드 설정을 함께 변경해야 합니다.

로컬 데모 계정/비밀번호입니다. 실제 배포의 계정·역할·TLS 설정을 대신하지 않습니다. 이번 변경 후 빌드·테스트·실제 로그인 검증은 실행하지 않았습니다.
