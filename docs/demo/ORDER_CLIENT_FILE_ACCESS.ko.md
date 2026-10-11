# 주문 AI 클라이언트에서 ZT 파일 접근 테스트

기존 `ai-agent-client` 하나가 주문 도구와 ZT 파일 도구를 함께 사용합니다. 파일은 주문 MCP 서버로 복사하지 않습니다. 실제 demo 파일을 connector가 ZT에 동기화하며, 파일 요청은 ZT의 기존 JSON-RPC Retrieval API로 전달합니다. 별도의 Spring AI Streamable HTTP 서버를 새로 띄우는 구성은 아닙니다.

```text
finance의 Keycloak JWT → POST localhost:9999/ai
                                │
                      ai-agent-client / Ollama
                       ┌────────┴─────────┐
                       ▼                  ▼
                ZT 주문 MCP          ZT Retrieval MCP
                주문 credential     파일 credential + 사용자 위임
                       │                  │
                   주문 서버          원본 사용자 ACL ∩ ZT AI 정책
                                          ├─ payroll → DENY
                                          └─ general → ALLOW → 실제 파일
```

사용자의 JWT는 파일 위임 생성에만 사용합니다. 실제 AI 도구 호출은 `client:order-file-client-demo`로 인증하므로 사람이 읽는 요청으로 위장하지 않습니다. 파일 credential은 File Access Demo에 고정되고, 기존 주문 credential과 워크스페이스는 그대로 사용합니다. 같은 AI 애플리케이션의 두 연결에 서로 다른 범위의 credential을 사용합니다.

## 1. ZT 적용과 로컬 등록

저장소 루트 PowerShell:

```powershell
docker compose -f docker/docker-compose.yml -f docker/mcp-oidc.compose.yml up -d --build authorization-api dashboard
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\setup-file-access-demo.ps1 -EnableOrderFileClient
docker compose --env-file .local/file-access-demo/demo.env -f docker/docker-compose.yml -f docker/mcp-oidc.compose.yml -f docker/file-access-demo.compose.yml up -d --build file-demo-seed file-nfs file-connector
```

스크립트는 File Access Demo 및 finance/IT grant를 재사용하며 다음을 추가합니다.

- File Access Demo에 고정된 ZT 서비스 클라이언트 `order-file-client-demo`.
- 로컬 실행용 `.local/file-access-demo/order-client.env` 및 secret 재사용용 `order-client.json`. 이 경로는 Git에서 제외되며 secret은 터미널에 출력하지 않습니다.
- 파일 ALLOW 및 Payroll 하위 DENY 정책을 DRAFT로 생성합니다. 기존 정책·secret을 자동 교체하거나 Publish하지 않습니다.
- 데모 로그인 전용 Keycloak public client `zt-order-file-demo-login`. PowerShell/Postman을 위한 로컬 password grant만 사용합니다. 대시보드 PKCE 설정은 유지합니다. 일반 연동은 요청자의 기존 OIDC Bearer JWT를 받는 방식이며, 이 데모 password grant를 제품 인증 방식으로 사용하지 않습니다.

기존 서비스 클라이언트가 있는데 secret 파일을 잃었다면 기존 secret을 `-OrderFileClientSecret`로 제공하거나 새 `-OrderFileClientId`를 지정하세요. 기존 클라이언트는 폐기하거나 다른 공간으로 이동시키지 않습니다.

## 2. 정책 활성화 — DENY부터

`http://localhost:3000`에서 **demo-admin**으로 로그인하고 **File Access Demo → Policy Studio**를 엽니다.

1. `deny_order_payroll_order-file-client-demo_<workspace UUID>`의 DRAFT를 검토하고 **Validate → Publish**합니다.
2. 다음으로 `allow_order_files_order-file-client-demo_<workspace UUID>`를 검토하고 Publish합니다.
3. 두 정책 모두 ACTIVE인지 확인합니다. ALLOW만 먼저 게시하면 finance 위임으로 Payroll을 읽을 수 있으므로 반드시 DENY부터 게시합니다.

DENY의 주요 조건:

```go
principal.id == "client:order-file-client-demo" and
context.connectionId == "file-demo" and
resource.ancestors contains "file-demo-payroll"
```

READ/LIST/SEARCH/DOWNLOAD 모두에 적용되며 서버가 계산한 부모 관계로 하위 파일을 차단합니다. 정책은 이 파일용 AI subject를 대상으로 하므로 기존 Workflow AI 정책은 바꾸지 않습니다. source ACL은 사람이 가진 권한의 상한선입니다. 추가 ALLOW로 DENY를 덮을 수 없습니다.

## 3. 기존 주문 클라이언트 설정

수정한 로컬 소스 위치:

```text
C:\Users\User\_Develop\AI\ai-agent-client\ai-agent-client
```

IntelliJ Run Configuration → Environment variables에서 `.local/file-access-demo/order-client.env`의 네 값을 추가합니다.

```text
ZT_FILE_TOOLS_ENABLED=true
ZT_FILE_WORKSPACE_ID=<생성된 File Access Demo UUID>
ZT_FILE_AGENT_CLIENT_ID=order-file-client-demo
ZT_FILE_AGENT_SECRET=<파일용 서비스 secret>
```

기존 `ZT_ORDER_AGENT_CLIENT_ID`, `ZT_ORDER_AGENT_SECRET`, `ZT_WORKSPACE_ID`는 주문 연결용 값으로 유지합니다. 파일 workspace로 덮어쓰지 않습니다. 설정 후 ai-agent-client를 재시작하세요. 파일 secret이 누락된 채 기능을 켜면 시작 시 설정 오류를 표시합니다.

파일 도구는 기존 동적 `ToolCallback`에 추가합니다. 별도 `@Tool` 기반의 하드코딩 대신 ZT `tools/list`의 스키마를 사용하며 충돌 방지를 위해 모델에는 다음 이름으로 노출됩니다.

- `zt_file_readDocument`
- `zt_file_searchDocuments`
- `zt_file_listChannelMessages` — 폴더/채널의 허용된 자식 조회

모델은 token·secret·위임 sessionId·workspace 헤더를 도구 인자로 지정할 수 없습니다. 이 값은 서버 애플리케이션이 구성합니다. 모델 결과는 권한 증명이 아닙니다.

## 4. 같은 요청에서 차단과 허용 비교

저장소 루트에서 실행합니다. finance 비밀번호 입력 창이 뜹니다. 초기 데모 비밀번호는 `demo-finance`이며 변경했다면 현재 비밀번호를 사용하세요.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\invoke-order-file-demo.ps1 -Username finance
```

기본 질문은 Payroll 목록 조회 후 general의 company-handbook.txt 읽기입니다. 모델에는 두 작업을 모두 요청하며 파일명/ID를 만들어 답하지 않도록 지시합니다. 실제 도구 선택은 Ollama 결과이므로 `answer`만 보지 말고 `toolExecutions`를 확인하세요.

| 동작 | ZT 결과 | 확인할 증거 |
|---|---|---|
| finance 사람의 Payroll 직접 조회 | 원본 ACL상 허용 | HUMAN 요청은 AI 정책을 적용하지 않음 |
| 같은 finance가 위임한 AI의 Payroll 목록/읽기 | DENIED / DENY | results 비어 있음, matchedPolicies의 Payroll DENY |
| 같은 AI의 general 목록/읽기 | SUCCEEDED / ALLOW | 실제 파일 내용 및 `ZT_FILE_DEMO_GENERAL_2026` 참조 코드 |
| 주문 도구 | 기존 정책에 따름 | 기존 주문 callId/approvalId 및 실행 결과 |

파일 DENY 후 독립적인 general 조회는 계속할 수 있습니다. 두 결과가 함께 있으면 전체 상태는 `COMPLETED_WITH_DENIALS`입니다. 주문의 승인 대기·DENY, 전송 오류·UNKNOWN은 기존처럼 이후 도구 실행을 중단합니다. 차단된 내용을 검색이나 다른 경로로 다시 얻는 동작은 허용하지 않습니다.

다른 질문:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\invoke-order-file-demo.ps1 -Username finance -Question 'general 폴더에 있는 company-handbook.txt를 읽고 참조 코드를 알려줘'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\invoke-order-file-demo.ps1 -Username finance -Question 'payroll 폴더의 파일을 보여줘'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\invoke-order-file-demo.ps1 -Username it-user -Question 'general 폴더의 파일을 보여줘'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\invoke-order-file-demo.ps1 -Username finance -ToolsOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\invoke-order-file-demo.ps1 -Username finance -HumanPayrollControl
```

`-HumanPayrollControl`은 LLM을 거치지 않고 finance 사람의 JWT로 ZT에 직접 Payroll 목록을 요청하는 대조군입니다. 실제 반환에 따라 사람은 ALLOW인데 위임받은 AI는 DENY인 차이를 확인할 수 있습니다. 목록 API는 허용된 내용도 포함할 수 있으며 이 데모에는 가상 급여 자료만 사용합니다.

JWT 없이 `/ai`를 호출하면 기존 주문 도구만 사용합니다. 파일 도구를 제공하거나 사람을 임의로 선택하지 않습니다. Postman은 `POST http://localhost:9999/ai`에 finance의 `Authorization: Bearer <JWT>`와 JSON `{ "question": "..." }`을 보내면 동일합니다. 사람 JWT를 IDE 환경변수에 고정하지 않습니다.

## 5. 기록과 한계

파일 결과의 content.text JSON에는 `decision`, `results`, `matchedPolicies`, `auditEventId`가 있습니다. 파일 auditEventId는 주문 callId가 아니므로 주문 `/ai/calls/{id}/resume`에 넣지 않습니다. 파일은 승인 후 재개를 지원하지 않습니다.

ZT 관리자는 **File Access Demo → Audit Logs → FILE**에서 호출 신원 `client:order-file-client-demo`, 작업 `children/retrieve/search`, 반환 개수와 매칭 정책을 확인합니다. 사용자 위임은 별도의 delegate 감사 기록으로 남습니다. Retrieval Access의 전용 조회도 유지합니다. 정책 차단이면 파일 원문은 응답·모델 문맥으로 전달되지 않습니다. 통합 조회의 상세 규칙은 [감사·카드 상세 안내](AUDIT_AND_METRIC_DETAILS.ko.md)를 참고하세요.

general에는 finance/IT 그룹의 READ/SEARCH ACL이 있으며 Payroll은 finance, IT 폴더는 it 그룹 ACL을 유지합니다. 실제 파일은 NFS와 connector가 사용하는 볼륨에 있고 ZT에는 동기화된 조회 데이터가 있습니다. NFS wire protocol을 통해 읽는 테스트는 아닙니다.

위임은 요청별로 생성하고 끝날 때 폐기합니다. 폐기 요청이 실패하면 서버의 짧은 만료가 적용됩니다. 허용된 파일 내용은 현재 LLM 요청의 도구 결과에 포함됩니다. 이미 모델에 전달된 내용의 완전한 회수/삭제를 보장하지 않습니다. 클라이언트는 공유 사용자 채팅 기록을 새로 저장하지 않습니다. localhost 바인딩과 기존 주문 API의 로컬 데모 범위는 유지하며, 외부 서비스 노출용 로그인/권한 구성은 별도 작업입니다.

수정 후 빌드·실행·모델 호출 테스트는 자동으로 수행하지 않았습니다. 위 명령은 사용자가 실행할 적용·데모 절차입니다.
