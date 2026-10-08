# MCP Gateway: real tool execution

이번 단계는 실제 MCP tools/call 실행 통제입니다. Gateway는 인증된 신원으로 전체 정책·행동·위협 평가를 수행하고, 허용되거나 독립적인 사람의 승인을 받은 요청만 관리자 등록 upstream에 전달합니다. 원격 증명, TEE 실행, 암호학적 감사 봉인은 이 단계에 포함되지 않습니다.

## 실행 흐름

인증 → scope/도구 ACL → 인자 제한 → 전체 정책 평가 → 필요 시 승인 대기 → 실행 상태 커밋 → upstream 초기화·실행 → 응답 필터 → 완료 기록 및 outbox.

- DENY는 실행하지 않습니다. 지원되지 않는 결정도 실행하지 않습니다.
- STEP_UP 또는 requireApproval=true이면 호출 인자를 저장하고 PENDING_APPROVAL로 반환합니다.
- 승인 재개는 같은 tenant/workspace와 같은 인증 주체만 할 수 있습니다. 호출자가 인자를 교체하거나 승인 ID를 지정할 수 없습니다.
- 승인 후 현재 정책을 다시 평가합니다. 현재 DENY는 기존 승인을 우회하지 않습니다. 일치한 정책 ID/버전/effect가 달라지면 새 승인이 필요합니다.
- 도구 설정·이름·위험도·upstream 주소가 변경되면 기존 대기 호출은 재개되지 않습니다.
- PostgreSQL 행 잠금으로 같은 승인의 동시 재개를 직렬화합니다. 네트워크 호출 전에 EXECUTING 상태를 커밋합니다.
- 실제 호출 중 데이터베이스 트랜잭션을 유지하지 않습니다.

## 인증과 범위

JWT는 Spring의 서명·유효 시간·issuer 검증을 거치며 MCP 경로에서 추가로 aud=zt-mcp-gateway와 tenant_id를 확인합니다. workspace_id가 있으면 요청 헤더와 일치해야 합니다. 사용자 신원은 JWT sub입니다.

등록된 서비스 클라이언트는 X-Client-Id와 X-API-Key를 사용하며 신원은 client:<client-id>입니다. 공유 개발 master key의 신원은 api-key이며 개별 에이전트를 구별하지 못합니다. 운영은 개별 클라이언트/JWT를 사용해야 합니다.

body.agent, arguments.agent, 전달된 인증 헤더, upstream 세션 ID는 신원을 변경하지 못합니다. upstream에는 별도의 서버용 토큰만 사용하며 호출자의 API 키·Bearer·tenant 헤더를 전달하지 않습니다.

도구 바인딩은 workspace별로 정확히 구분됩니다. workspace 없는 바인딩은 workspace 없는 호출에서만 사용됩니다. 도구 ACL의 allowedSubjects도 통과해야 합니다. 정책 생성·발행·롤백은 PLATFORM/ADMIN/POLICY_MANAGER 역할로 제한했습니다.

Origin이 있는 MCP 요청은 정확한 허용 목록과 일치해야 합니다. 다른 도메인의 브라우저 요청은 거부합니다. Origin 없는 서비스 호출도 반드시 인증돼야 합니다.

## 1. upstream 설정

upstream 서버 주소는 서버 설정에만 등록합니다. 호출 body나 도구 바인딩에 URL을 받을 수 없습니다. HTTPS가 기본이고 개발용 HTTP는 명시적으로 켜야 합니다.

```yaml
zt:
  security:
    mcp:
      servers:
        payments:
          endpoint: https://your-mcp-server.example/mcp
          bearer-token-env: PAYMENTS_MCP_TOKEN
```

PAYMENTS_MCP_TOKEN 환경 변수에는 이 upstream에만 사용하는 토큰을 설정합니다. bearer-token-env를 생략하면 인증 헤더 없이 호출하므로, 이는 별도로 보호된 개발 서버 등에만 사용합니다. 호출자의 토큰으로 자동 대체하지 않습니다.

Docker 예제: [docker-compose.mcp.example.yml](../../docker/docker-compose.mcp.example.yml). 예제 주소를 실제 주소로 교체하고 토큰을 컨테이너 환경에 전달하세요. 예제 파일만 추가하면 기본 Compose에는 적용되지 않습니다.

```powershell
docker compose -f docker/docker-compose.yml -f docker/docker-compose.mcp.example.yml up -d --build authorization-api dashboard
```

localhost는 authorization-api 컨테이너 자신입니다. Windows 호스트의 개발 서버는 host.docker.internal 같은 실제 접근 가능한 주소를 사용하고 ZT_MCP_ALLOW_HTTP=true를 명시하세요.

설정된 주소를 신뢰 대상으로 취급합니다. 운영에서는 Gateway만 upstream 자격 증명을 보유하고, Gateway를 우회하는 직접 접근을 네트워크·upstream 인증으로 제한해야 합니다. 고정 주소와 redirect 금지만으로 고객 네트워크의 모든 SSRF/우회 경로를 해결했다고 간주하지 않습니다.

## 2. 도구 실행 바인딩 등록

먼저 기존 POST /v1/agents/tools로 도구를 생성하거나 기존 toolId를 사용합니다. PLATFORM/ADMIN 신원으로 다음을 등록합니다. X-Tenant-Id와 사용할 X-Workspace-Id를 함께 보내세요.

PUT /v1/mcp/tools/<tool-id>/binding

```json
{
  "serverId": "payments",
  "upstreamTool": "payment.transfer",
  "allowedSubjects": ["client:payment-agent"],
  "inputSchema": {
    "type": "object",
    "properties": {
      "amount": {"type": "integer", "minimum": 1, "maximum": 500000},
      "destination": {"type": "string", "enum": ["internal"], "maxLength": 32}
    },
    "required": ["amount", "destination"],
    "additionalProperties": false
  },
  "redactResultPaths": ["/structuredContent/accountNumber"],
  "redactTextLiterals": [],
  "requireApproval": true
}
```

allowedSubjects의 client:payment-agent는 등록된 API client ID의 예입니다. JWT는 실제 sub 값을 넣습니다. 공유 개발 키로 조회하려면 api-key를 명시적으로 추가해야 합니다. 기본적으로 기존 데모 도구에 실행 바인딩/권한을 자동 추가하지 않습니다.

인자 규칙은 JSON Schema의 제한된 부분집합입니다: object/properties/required/additionalProperties=false, array/items/minItems/maxItems, string/minLength/maxLength, number·integer/minimum/maximum, boolean, scalar enum, description. $ref, oneOf, pattern 등 지원하지 않는 키는 등록을 거부합니다. 일반적인 전체 JSON Schema 구현이라고 가정하면 안 됩니다.

기존 정책에도 mcp.tool.call, AI_AGENT, mcp_tool의 명시적 허용이 있어야 합니다. 도구 등록만으로 정책 ALLOW가 생성되지는 않습니다. Policy Studio에서 다음처럼 해당 도구를 제한하는 정책을 작성할 수 있습니다.

```text
policy "mcp_internal_payment" {
  effect allow
  principal.type == "AI_AGENT"
  action == "mcp.tool.call"
  resource.type == "mcp_tool"
  condition {
    context.mcp.tool == "payment.transfer"
    and context.amount <= 500000
    and context.destination == "internal"
  }
}
```

실제 정책·위험·행동 결과에 따라 이 allow 정책이 있어도 DENY/STEP_UP가 될 수 있습니다. 인자는 일반 context와 mcp.arguments에 제공하고, mcp.actor/mcp.tool/tool_id/위험도 등 Gateway 파생 속성은 서버에서 덮어씁니다. caller가 주장한 값과 인증·서버 파생 속성을 혼동하지 마세요.

## 3. 호출과 승인

POST /v1/mcp/json-rpc (application/json)

```json
{
  "jsonrpc": "2.0",
  "id": "a-new-uuid-for-this-operation",
  "method": "tools/call",
  "params": {
    "name": "payment.transfer",
    "arguments": {"amount": 100, "destination": "internal"}
  }
}
```

응답 result._meta["zt.security"]에 callId, requestId, decision, status, approvalId, 완료 auditEventId가 있습니다. PENDING_APPROVAL이면 독립적인 APPROVER/ADMIN/PLATFORM 신원이 기존 POST /v1/approvals/<approval-id>/approve로 승인합니다. 요청자 본인의 승인은 거부합니다. 공유 master key 한 개로 요청·승인을 모두 할 수 없습니다.

원래 호출자는 POST /v1/mcp/calls/<call-id>/resume로 저장된 호출을 재개합니다. 이 경로에는 대체 인자를 받지 않습니다. GET /v1/mcp/calls/<call-id>는 자신의 호출 상태와 해시를 보여주며 원문 인자는 반환하지 않습니다.

POST /v1/mcp/authorize는 평가만 수행하고 upstream을 호출하지 않습니다. ALLOW preview는 NOT_EXECUTED이며 실제 실행하려면 새 tools/call 요청이 필요합니다. 승인 대기 preview는 저장된 callId로 승인 후 재개할 수 있습니다. legacy agent 값은 신원에 사용하지 않고, legacy context는 도구 인자로만 취급합니다.

Gateway 메뉴에서도 JSON 인자 입력, Evaluate only, Execute tool, Resume approved call을 제공합니다. tools/list에는 현재 workspace에서 바인딩됐고 호출자에게 허용된 도구만 나타납니다.

## 재전송과 장애

실행 요청의 JSON-RPC id는 이 Gateway에서 호출자·tenant/workspace별 영속적인 중복 실행 방지 키로 사용됩니다. 각 새 작업은 새 UUID를 사용하세요. 같은 ID와 같은 도구/인자의 재전송은 기존 callId를 반환하며 다시 dispatch하지 않습니다. 다른 인자로 같은 ID를 재사용하면 거부합니다. 완료 결과 원문은 재전송 응답에 보관·재생하지 않습니다.

이 방식은 Gateway의 중복 dispatch를 막으며 외부 도구의 exactly-once 실행을 보장하지 않습니다. upstream 자체의 멱등성·조회 기능이 있다면 추가로 활용해야 합니다.

| 상태 | 의미 |
| --- | --- |
| DENIED | 정책/승인 거부, 실행 없음 |
| PENDING_APPROVAL | 승인 대기, 실행 없음 |
| EXECUTING | 실행 시도 예약을 커밋함; 완료 여부는 아직 확정하지 않음 |
| SUCCEEDED | 정상 upstream 결과를 수신·필터링하고 완료 기록 커밋 |
| TOOL_ERROR | upstream 도구가 isError=true를 반환 |
| NOT_EXECUTED | preview 또는 초기화·자격 증명·capacity 오류로 도구 호출 안 함 |
| UNKNOWN | 도구 호출 이후 타임아웃/전송/응답 필터 오류로 실행 결과 불확정 |

UNKNOWN에서는 새 ID로 자동 재호출하지 마세요. Gateway가 실행 예약 후 중단되거나 완료 저장이 실패하면 EXECUTING이 남을 수 있습니다. 이 경우에도 자동 재개하지 않으며 upstream 기록으로 실행 여부를 확인해야 합니다. 로그를 저장하지 못한 결과를 audited success로 반환하지 않습니다.

## 전송·응답 지원 범위

upstream은 Streamable HTTP로 initialize → notifications/initialized → tools/call을 수행합니다. session ID와 합의된 protocol version을 해당 호출 세션에만 유지하며 가능하면 DELETE로 종료합니다. 2025-11-25/2025-06-18을 지원합니다.

JSON 응답과 제한된 크기·시간 내 종료되는 POST SSE 응답을 읽습니다. stdio, 구형 HTTP+SSE transport, 연결 재개, 서버 주도 sampling/elicitation, resources/prompts, 지속적인 서버 push는 이번 단계에 포함되지 않습니다. 수신 /json-rpc는 인증된 stateless JSON-RPC 어댑터이며 완전한 MCP OAuth discovery 서버 구현은 아닙니다.

응답은 text content와 object structuredContent만 지원합니다. 이미지·오디오·embedded resource 등은 반환하지 않습니다. redactResultPaths는 결과 전체에 대한 JSON Pointer로 필드를 제거하고, redactTextLiterals는 문자열 값의 정확한 부분 문자열을 한 번만 치환합니다. 정규식/의미 분석 DLP나 프롬프트 인젝션 완전 방어라고 주장하지 않습니다. upstream _meta와 임의의 추가 필드는 전달하지 않습니다.

## 설정과 기록

| 변수 | 기본값 |
| --- | --- |
| ZT_MCP_AUDIENCE | zt-mcp-gateway |
| ZT_MCP_TIMEOUT_SECONDS | 각 HTTP 요청 15초 |
| ZT_MCP_MAX_REQUEST_BYTES | 65536 |
| ZT_MCP_MAX_RESPONSE_BYTES | 2097152 |
| ZT_MCP_APPROVAL_TTL_SECONDS | 900 |
| ZT_MCP_MAX_CONCURRENT_CALLS | replica당 32 |
| ZT_MCP_ALLOW_HTTP | false |
| ZT_MCP_ALLOWED_ORIGINS | http://localhost:3000,http://127.0.0.1:3000 |

V4 migration은 mcp_tool_bindings와 mcp_invocations를 추가하며 강제 RLS를 적용합니다. 호출 인자는 승인 후 동일 작업을 실행하기 위해 tenant/workspace별 DB에 저장됩니다. 기존 전체 평가 감사 경로에도 인자가 포함될 수 있으므로 민감한 프롬프트나 토큰을 인자로 보내기 전에 저장·보관 정책을 정해야 합니다.

SecurityEventOutbox에는 준비·실행 예약·거부·재승인·완료 이벤트를 넣습니다. 이벤트에는 requestId/callId, 인자·바인딩·필터된 결과의 해시와 상태를 기록합니다. 이번 단계의 이벤트는 TEE 봉인이나 변조 불가능한 저장을 구현한 것은 아닙니다.

빌드·테스트·컨테이너 실행 검증은 수행하지 않았습니다. 인증 audience/tenant, 인자 제한, 승인 후 현재 DENY, 재전송 차단, 실제 HTTP 초기화/SSE, 필터, 불확정 결과·완료 저장 실패에 관한 테스트 소스만 추가했습니다.

전송과 lifecycle의 기준: [MCP Streamable HTTP](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports), [MCP Lifecycle](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle).
