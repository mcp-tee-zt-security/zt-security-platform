# Workflow Chat: 일반 대화 · 가상 송금 · 권한을 확인한 파일 조회

Ollama가 일반 질문에 답하거나 이체/파일 조회 제안을 작성합니다. 모델은 작업을 실행하지 않습니다. 이체는 기존 ZT 정책·리스크·승인·실행 계약을 사용하며 실행 대상은 PostgreSQL 가상 원장입니다. 파일 조회는 등록된 Retrieval 연결의 원본 사용자 ACL과 ACTIVE ZT AI 정책의 교집합으로 제한합니다. 실제 은행/결제 API, 환율 변환, 실계좌 ACL은 연결하지 않습니다.

## 시작

Ollama에서 `qwen3:8b`를 사용할 수 있도록 준비하고 저장소 루트에서 실행합니다.

```bash
docker compose -f docker/docker-compose.yml -f docker/mcp-oidc.compose.yml up -d --build keycloak authorization-api dashboard policy-data-plane-1 policy-data-plane-2
```

API 컨테이너는 `http://host.docker.internal:11434`의 Ollama를 사용합니다. 호스트 서비스가 컨테이너의 연결을 허용해야 합니다. 필요하면 Ollama 실행 환경에 `OLLAMA_HOST=0.0.0.0:11434`를 설정하고 재시작합니다. 로컬 방화벽은 이 데모에 필요한 연결만 허용하세요. 모델 주소는 서버 설정이며 사용자가 채팅으로 변경할 수 없습니다.

이 overlay에서는 개발 관리자 API key가 비활성화됩니다. 관리자 Postman 요청도 Keycloak JWT를 사용해야 합니다. 기존 AI 클라이언트의 등록된 ID/secret 인증은 유지됩니다. Rust 데이터 플레인은 별도의 로컬 데모 정책 읽기 키로 지정된 tenant/workspace의 `GET /v1/policy-bundles/fast`만 호출할 수 있습니다. 이 키로 정책 수정·승인·실행은 할 수 없습니다. `ZT_POLICY_BUNDLE_API_KEY`로 교체할 수 있습니다. Workflow/MCP의 실제 인가는 Java 정책 평가 파이프라인을 사용합니다.

기존 `zt-mcp-demo` realm은 재시작해도 계정/역할이 덮어써지지 않습니다. 새 데모 계정을 추가하려면 PowerShell에서 실행하세요.

```powershell
.\scripts\windows\update-workflow-demo-keycloak.ps1
```

스크립트는 로컬 Keycloak 관리 계정 `admin` / `admin`으로 기존 realm에 누락된 역할·계정·기본 scope를 추가합니다. 기존 사용자 삭제나 비밀번호 초기화는 하지 않습니다. 관리 계정이 다르면 `-AdminUsername`, `-AdminPassword`로 지정하세요. 아직 새 realm이 없다면 먼저 `docker compose -f docker/docker-compose.yml restart keycloak`으로 import한 뒤 실행합니다.

| 계정 | 로컬 데모 비밀번호 | 역할 |
|---|---|---|
| demo-admin | demo-admin | 데모 구성·정책 설치, 요청·승인·실행 관리 |
| requester | demo-request | 채팅 요청 및 인자 확인 |
| approver | demo-approve | 다른 사용자의 요청 승인·거절 |
| executor | demo-execute | 승인/허용된 계약 실행 및 결과 검증 |
| viewer | demo-view | 승인·실행 기록 조회 |

기존 계정의 비밀번호를 변경한 경우 그 값을 유지합니다. 화면의 Navigation view는 메뉴 표시만 바꾸며 서버 역할을 부여하지 않습니다.

## 최초 구성: 수동 등록

1. `http://localhost:3000`에서 `demo-admin`으로 로그인합니다.
2. 상단 공통 Workspace 선택기로 사용할 범위를 선택합니다. 기존 데모는 **Security Demo**, 파일 데모는 **File Access Demo**입니다. 접근이 허용된 공간만 표시되며 전환하면 페이지가 다시 로드되어 기존 채팅·파일 미리보기·승인 상세 상태가 초기화됩니다. 채팅 화면에는 자동 셋업·정책 게시 버튼이 없습니다.
3. AI identity의 externalId를 `workflow-chat-<workspace UUID>`, type을 `AI_AGENT`로 등록합니다. 예: `workflow-chat-88888888-8888-8888-8888-888888888801`. 이미 ACTIVE로 등록되어 있다면 재등록하지 않습니다.
4. 이체 테스트에는 `workflow.simulatedTransfer`라는 agent tool을 등록합니다. MCP upstream 도구와 별개인 로컬 가상 실행 도구입니다.
5. `GET /v1/workflow-chat/config`의 `policyTemplates`를 확인하고 Policy Studio에서 각각 **Validate → Save DRAFT → Publish**합니다. 자동으로 정책을 활성화하지 않습니다.

관리자 JWT와 `X-Tenant-Id`, `X-Workspace-Id` 헤더를 사용한 등록 API는 다음과 같습니다. 기본 데모 workspace 예시이며 다른 workspace에서는 externalId를 변경합니다.

```http
POST /v1/identities
Content-Type: application/json

{"externalId":"workflow-chat-88888888-8888-8888-8888-888888888801","type":"AI_AGENT","name":"Workflow Chat Agent","attributes":{}}
```

```http
POST /v1/agents/tools
Content-Type: application/json

{"name":"workflow.simulatedTransfer","description":"Local simulated transfer","riskLevel":"MEDIUM","metadata":"{}"}
```

워크스페이스별 AI identity와 가상 도구를 등록하고 다음 두 정책을 기존 Policy Studio에 활성화합니다.

- `simulation.transfer`에 대한 ALLOW
- 같은 요청의 `amount >= 1000`일 때 STEP_UP

이미 구성한 identity/tool/정책은 유지됩니다. 이후 정책 변경/해제는 Policy Studio에서 관리합니다. 메뉴가 보이지 않으면 Navigation view를 DEVOPS로 바꾸세요. 추가적인 위험 통제나 기존 DENY 정책이 적용되면 결과가 달라질 수 있습니다. `/setup` API는 이전 데모와의 호환을 위해 남아 있지만 수동 구성에서는 사용하지 않습니다.

## 일반 대화와 파일 요청

점수 공식·행동 비교·위협 신호·자연어 처리의 구체적인 범위는 [리스크·Agent 행동·런타임 위협 계산 상세](../security/RISK_BEHAVIOR_RUNTIME.ko.md)를 참고하세요.

모든 새 메시지는 응답을 화면에 반환하기 전에 실제 평가 기록을 만듭니다. 응답 상단에 다음 순서로 표시하고 마지막에 답변 또는 차단/승인 안내를 표시합니다.

1. 로그인한 요청자와 AI의 위임된 작업·도구 권한 확인
2. 기본/종합 리스크 점수 및 근거
3. 에이전트 행동 점수와 보고된 런타임 위협 신호
4. 해당 tenant/workspace의 매칭 정책과 파일 요청의 원본 ACL·AI 정책
5. ALLOW / STEP_UP / DENY 결정, 감사 ID 및 승인 상태

일반 대화는 `chat.respond` + `public_answer`에 한정된 내장 기본 허용 규칙 `builtin_public_conversation`을 평가 목록에 넣습니다. 외부 도구·파일 읽기·이체를 허용하는 정책은 아닙니다. 관리자가 작성한 매칭 DENY/STEP_UP이나 행동·위험 통제는 기본 허용보다 우선합니다. 내장 규칙은 DB의 Policy Studio 정책을 자동 게시하지 않으며 메타데이터에 표시됩니다. 감사 로그의 policy FK는 실제 DB 정책에만 연결합니다.

일반 대화에도 활성 Workflow AI identity가 필요하며, 서버가 사용자 요청에 대해 유효 기간이 있는 일반 응답 전용 task를 기록합니다. 외부 도구 위임은 없다고 표시합니다. 이체에는 등록된 `workflow.simulatedTransfer` 도구의 위임을 검사합니다. 파일 요청은 일반 응답 보안 게이트를 통과한 후 실제 source ACL·ACTIVE AI 정책을 별도로 검사합니다. 조회 불가/미구성은 fail closed로 처리하며 내용을 반환하지 않습니다.

날씨 같은 일반 질문에는 금융 거래 위험이 없지만, 기기 신뢰 미확인·행동·자산 등의 신호로 실제 종합 점수는 0이 아닐 수 있습니다. 점수를 0으로 꾸미지 않습니다. 현재 채팅에는 실시간 날씨 도구가 없으므로 현재 기온/예보를 만들어 답하지 않습니다. 일반 대화의 STEP_UP은 답변 반환을 보류하며 이체처럼 승인 후 답변을 재개하는 기능은 아직 없습니다. 런타임 위협 표시 역시 보고된 신호에 대한 엔진 결과이며 완전한 프롬프트 보안 검사를 증명하는 것은 아닙니다.

- `ZT가 뭐야?`: 보안 평가 후 허용되면 일반 답변을 표시합니다. 이체 필드가 없다는 오류나 이체 확인 버튼이 나오지 않습니다.
- `10000유로 우리은행으로 이체해줄 수 있어?`: 정중한 질문 형태도 이체 작업 요청입니다. 수취인 `우리은행`, 금액 `10000`, 통화 `EUR`의 가상 이체 제안을 만들고 전송 시 기존 ZT 정책·리스크 평가와 필요한 승인 생성을 진행합니다. 실제 가상 실행은 승인/허용 후 별도 실행 버튼으로 진행합니다. 은행 이름을 지정했다는 이유로 일반적인 실제 송금 불가 답변으로 끝내지 않습니다. `송금이 뭐야?` 또는 `1000유로 보내면 어떻게 돼?`처럼 설명을 요구하는 질문은 일반 대화입니다.
- `payroll 폴더에 있는 파일 하나 가져와`: 전송 시 보안 게이트와 현재 로그인 사용자의 ACL, `workflow-chat-<workspace UUID>` AI subject의 ZT 정책을 검사하고 허용된 파일 텍스트만 반환합니다. **Recheck ZT access / retrieve one file**은 다시 인가 후 조회합니다.
- 금액/수취인/통화가 빠진 이체 요청과 지원하지 않는 작업에는 추가 정보 또는 지원 범위를 안내합니다. 다중 작업 자동 실행과 임의 MCP 도구 선택은 이번 범위에 포함하지 않습니다.

파일 조회 준비는 자동으로 하지 않습니다. **Retrieval Access**에 현재 workspace의 연결을 등록하고, 인증된 source connector로 리소스·사용자·그룹·ACL을 동기화하고 최신 checkpoint를 유지해야 합니다. 소스 사용자 ID는 로그인 JWT의 실제 `sub`와 같아야 하며 그룹 권한은 JWT `groups`와 원본 디렉터리 그룹의 교집합입니다. `demo-admin`이라는 이름이나 ADMIN 역할만으로 Alice의 파일 권한이 부여되지 않습니다.

Stitch 전용 시뮬레이터와 메뉴는 제거했습니다. Workflow와 파일 데모는 `zt-mcp-demo`를 사용합니다. connector는 현재 API가 신뢰하는 issuer의 credential과 해당 workspace에 등록된 connector subject를 사용해야 합니다. 기존 다른 realm의 사용자나 source ACL을 자동으로 복사하지 않습니다. 현재 로컬 구성은 [실제 파일 접근 데모](FILE_ACCESS_NFS.ko.md)를 참고하세요.

Policy Studio에서 ACTIVE ALLOW가 필요하며 Payroll 하위 DENY는 계속 우선합니다. 기존 ALLOW가 `client:stitchy-simulator`에 한정되어 있다면 별도로 Workflow Chat AI subject를 허용하는 정책을 작성해야 합니다. 예를 들어 아래 ALLOW는 기본 workspace의 Workflow Chat만 해당 연결에 허용합니다. `connectionId`는 등록한 값으로 바꾸세요. Payroll DENY는 해제하지 않습니다.

```go
policy "allow_workflow_retrieval" {
  effect allow
  principal.type == "AI_AGENT"
  action in ["retrieval.read", "retrieval.list"]
  resource.type == "retrieval_resource"
  condition {
    principal.id == "workflow-chat-88888888-8888-8888-8888-888888888801" and
    context.connectionId == "stitch"
  }
}
```

제안의 폴더/파일 이름 또는 ID를 등록된 리소스와 정확히 매칭합니다. 동명이면 정확한 ID로 다시 요청해야 합니다. 폴더 아래에서 권한이 있는 파일 한 개의 등록된 텍스트를 반환하며, 바이너리 다운로드는 지원하지 않습니다. DENY 또는 STEP_UP은 내용을 반환하지 않습니다. Retrieval은 ACTIVE 정책만 사용하며 이체의 리스크/승인 재개 파이프라인과는 별개입니다.

파일 접근 기록은 **Retrieval Access의 감사 기록**에 `workflow.retrieve`와 Workflow AI subject로 남습니다. 파일 원문은 모델에게 전달하거나 서버 채팅 기록에 저장하지 않습니다. 현재 화면의 미리보기는 약 1분 후 또는 새로고침 시 사라지고 새 조회 때 다시 인가합니다. 이미 전달된 내용을 외부에서 복사한 경우까지 삭제를 보장하는 것은 아닙니다.

현재 메시지만 별도 호출로 분류하고, 일반 질문은 작업 JSON 형식 없이 자연어로 답합니다. 일반 대화 문맥은 현재 사용자·tenant·workspace의 최근 8개 일반 대화 쌍만 사용합니다. 과거 이체/파일 제안은 이 문맥에 포함하지 않습니다. 작업 제안 인자는 현재 메시지에서만 추출하며 이전 요청의 금액·수취인·폴더를 새 작업에 재사용하지 않습니다. 브라우저가 임의 과거 대화, AI subject 또는 실행 권한을 지정할 수 없습니다.

## 1,500 EUR: 승인 필요

1. 상단 **Sign out / switch user**로 로그아웃하고 `requester`로 로그인합니다.
2. Workflow Chat에 `Transfer 1500 EUR to ACME.`를 입력하고 오른쪽 **화살표 전송 버튼** 또는 Enter를 누릅니다. Shift+Enter는 줄바꿈입니다.
3. 전송 시 ZT가 정책·리스크를 평가합니다. 평가 목록과 수취인·금액·통화를 확인합니다. 잘못 추출했으면 승인·실행하지 말고 더 명확한 새 메시지를 보냅니다.
4. `STEP_UP`와 `PENDING` 승인을 확인합니다. 아직 실행하지 않습니다. 이전 미평가 제안에는 기존 확인 버튼이 남아 있을 수 있습니다.
5. `approver`로 전환 → **Approvals → Pending approvals**에서 WORKFLOW 요청을 검토하고 **Approve request**를 누릅니다. 자기 승인·권한 없는 승인·만료 승인·다른 workspace 승인은 서버에서 차단합니다.
6. `executor`로 전환합니다. Workflow Chat의 저장된 요청 또는 **Approvals → Workflow execution records → Inspect execution**에서 **Execute saved workflow**를 누릅니다.
7. 거래 ID, 수취인, 금액, `COMPLETED` 가상 거래 상태와 `SUCCEEDED` 실행 결과를 확인합니다.
8. **Verify execution result** 또는 **Verify transaction in simulator ledger**를 눌러 원장과 대조합니다. VERIFIED는 이 가상 원장과 값이 일치한다는 뜻이며 외부 금융 거래나 TEE 증명을 의미하지 않습니다.

## 999 EUR: 승인 없이 실행 가능

`requester`가 `Transfer 999 EUR to ACME.`를 보내면 즉시 평가합니다. 다른 위험 통제가 없으면 ALLOW와 READY 계약이 만들어집니다. 승인은 필요 없지만 실행 역할은 여전히 필요하므로 `executor`가 저장된 계약을 실행합니다. 자동으로 송금하지 않습니다.

## 상태와 기록

- 채팅은 일반 답변 또는 자연어→구조화된 제안이며 모델에는 실행 권한이 없습니다. 작업은 `simulation.transfer`와 등록된 파일 한 개 조회를 지원합니다. 임의 도구·다단계 계획은 구현하지 않습니다.
- 사람 신원은 JWT, AI 신원/tenant/workspace/위임 작업·도구는 서버가 결정합니다. 모델 결과에서 principal이나 승인 ID를 받아 사용하지 않습니다.
- 새 이체 메시지는 전송 시 평가하며 실행 시에는 저장된 제안을 참조합니다. 같은 제안 ID의 재평가는 기존 결정/계약을 반환하고, 같은 계약의 재실행은 기존 실행 결과를 반환합니다. 동일 문장을 새 메시지로 보내면 새 제안이므로 별도 요청입니다.
- 제안·위임 작업·승인·실행 계약은 약 15분 유효합니다. 승인 대기 중 정책 매칭이 바뀌면 기존 승인을 재사용하지 않고 새 요청/승인을 요구합니다.
- 통합 승인함은 Workflow와 MCP를 함께 표시합니다. MCP 승인 후에는 여전히 원래 AI 서비스 클라이언트가 `/resume`해야 합니다. 다른 사용자의 MCP 호출을 실행하는 버튼은 제공하지 않습니다.
- Workflow Chat은 자신의 요청 또는 검토 역할에 허용된 요청을 DB에서 불러옵니다. 상태는 15초마다 갱신하며 새로고침 버튼도 제공합니다.
- 요청자·AI 신원은 결정 기록, 승인자는 `approvals.decided_by`, 실행자는 실행 기록의 `executedBy`, 거래는 `workflow_simulated_transfers`에 남습니다. 읽기·쓰기 범위는 tenant/workspace로 제한됩니다.
- 정책에는 명시적인 tenant-wide 공유 정책도 포함됩니다. 신원·자산 등 tenant-wide 인벤토리는 공통 데이터입니다. 범위를 확인할 수 없는 과거 감사/결정 기록은 선택한 workspace 기록에 섞지 않습니다. 상세 규칙은 [공통 워크스페이스 범위](../security/WORKSPACE_SCOPE.ko.md)를 참고하세요.
- 공통 OIDC 모드에서는 개발 키로 자동 전환하지 않습니다. 기본 Compose만 사용하는 명시적 개발 모드의 키 인증은 유지됩니다. DPoP/mTLS 토큰 바인딩은 별도 TODO입니다.

## 설정 및 API

모델 어댑터는 [Ollama의 구조화 출력 API](https://github.com/ollama/ollama/blob/main/docs/capabilities/structured-outputs.mdx)를 사용합니다. 모델이 출력한 JSON은 서버에서 별도로 검증하며 실행 권한으로 취급하지 않습니다.

`ZT_WORKFLOW_CHAT_ENABLED`, `ZT_WORKFLOW_SIMULATION_ENABLED`, `ZT_WORKFLOW_REQUIRE_OIDC`는 기본 false이고 데모 overlay에서 true입니다. `ZT_WORKFLOW_CHAT_PROVIDER=ollama`, `ZT_WORKFLOW_CHAT_MODEL=qwen3:8b`, `ZT_WORKFLOW_OLLAMA_URL`로 모델을 설정합니다. 공급자 인터페이스는 분리했으며 현재 구현된 공급자는 Ollama 하나입니다.

| API | 용도 |
|---|---|
| GET /v1/session | 검증된 사용자 및 역할 |
| GET /v1/workflow-chat/config | 모델·AI·정책 템플릿 |
| POST /v1/workflow-chat/setup | 이전 자동 셋업 호환 API; 수동 구성에서는 사용하지 않음 |
| POST /v1/workflow-chat/messages | `{ "message": "..." }` → CHAT / TRANSFER / RETRIEVAL 답변·제안 |
| POST /v1/workflow-chat/proposals/{id}/submit | 저장된 제안 확인 후 정책 평가 |
| POST /v1/workflow-chat/proposals/{id}/retrieve | 원래 요청자의 파일 제안 확인, ACL + ZT AI 정책 검사 후 텍스트 반환 |
| GET /v1/workflow-chat/proposals | 접근 가능한 최근 제안·상태 |
| GET /v1/approval-inbox | Workflow + MCP 승인함 |
| POST /v1/approvals/{id}/decision?status=APPROVED | 독립 승인자의 결정 |
| POST /v1/execution/contracts/{id}/execute | 실행 권한자의 저장된 계약 실행 |
| POST /v1/execution/{id}/verify | 가상 원장 대조 |

이번 변경 후 빌드·테스트·실제 로그인/모델 요청은 자동 실행하지 않았습니다. 위 명령과 순서는 사용자가 실행할 데모 절차입니다.
