# 통합 감사 조회와 숫자 카드 상세 화면

## 적용

저장소 루트에서:

```powershell
docker compose -f docker/docker-compose.yml -f docker/mcp-oidc.compose.yml up -d --build authorization-api dashboard
```

시작 시 V12 migration이 파일 감사 테이블에 위임 사용자·AI·세션 ID용 nullable 열을 추가합니다. 기존 감사 행은 UPDATE하지 않습니다. NFS/connector는 유지하며 이 명령의 orphan 경고만 보고 `--remove-orphans`를 사용하지 않습니다. 파일 connector나 외부 AI 클라이언트를 새로 등록할 필요는 없습니다.

## Audit Logs

상단에서 **File Access Demo**를 선택한 뒤 **Audit & Analysis → Audit Logs**를 엽니다. 관리자/감사 역할은 접근 가능한 공간의 기록을, 일반 사용자와 서비스 클라이언트는 자신에게 연결된 기록만 조회합니다. 다른 워크스페이스의 ID를 상세 API에 넣어도 조회하지 않습니다.

전체 조회 역할은 ADMIN/PLATFORM/AUDITOR/POLICY_MANAGER/SOC_ANALYST입니다. 기존 raw `/v1/audit` API도 이 역할로 제한하며 일반 사용자/서비스 클라이언트는 `/v1/audit/events`의 OWN_RECORDS 조회를 사용합니다. APPROVER나 AGENT_MANAGER 역할만으로 다른 사람의 전체 파일 감사 기록이 열리지는 않습니다.

| 종류 | 표시 자료 |
|---|---|
| MCP | 주문 등 MCP 호출의 현재 저장된 결정·승인·실행 상태 |
| FILE | 파일 조회·검색·목록·다운로드 및 위임 관련 기록 |
| WORKFLOW | 일반 응답/가상 이체 평가 및 저장된 Workflow 실행 기록 |
| DECISION | 그 밖의 정책 평가 감사 기록 |
| SYNC | connector의 리소스·사용자 동기화 및 삭제 기록 |

종류, ALLOW/STEP_UP/DENY, 정확한 호출자·사용자·AI subject를 필터로 선택합니다. 필터는 DB 조회에 적용한 후 페이지를 제한하므로 첫 페이지에서 없는 기록도 이후 페이지에서 찾을 수 있습니다. 최신 50개부터 표시하고 **Load older records**로 이어서 조회합니다. 자동 갱신은 15초마다 첫 페이지를 갱신하며, 과거 기록을 펼치면 자동 갱신을 멈춰 목록이 바뀌지 않게 합니다.

파일 테스트의 예:

1. Record type **FILE**.
2. Caller에 `client:order-file-client-demo` 입력.
3. Payroll의 DENY/반환 0개와 general의 ALLOW/반환 개수 확인.
4. **Inspect**에서 감사 ID, 사용자/AI, 매칭 정책, ACL version 등을 확인.

주문은 **MCP**, Workflow는 **WORKFLOW**로 선택합니다. File Access Demo에 주문 기록이 없는 것은 정상이며 주문 연결의 워크스페이스로 바꿔 조회해야 합니다. 공통 화면이라는 이유로 다른 공간의 로그를 합치지 않습니다. Retrieval Access에서도 **Open unified Audit Logs**로 파일 필터를 적용해 이동할 수 있습니다.

이전 파일 기록에는 위임 사용자와 세션이 없으므로 **Not recorded**로 표시합니다. 새 파일 요청부터 해당 정보를 저장합니다. 기존 감사 로그에서 workspace를 알 수 없는 행은 공간별 평가 목록에 포함하지 않습니다. 다만 저장된 MCP 호출이 자신의 decision_request_id로 연결한 과거 정책 감사는 해당 호출 상세의 근거로 조회합니다.

화면은 원문 파일, 질문 본문, 원본 도구 인자·결과, 토큰·secret을 반환하지 않는 투영입니다. 파일 감사 상세의 이름은 관리 역할에게 현재 등록된 대상 이름으로 표시하며 일반 계정은 기록된 대상 ID를 사용합니다. 과거 허용 기록을 조회한다고 지금 파일 내용에 접근할 권한이 생기지 않습니다.

**Verify tenant chain**은 기존 불변 정책 감사 체인에 대한 기능입니다. 통합 화면에 표시했다는 이유로 FILE/WORKFLOW 기록까지 암호학적으로 봉인되거나 원격 증명됐다고 주장하지 않습니다. MCP 행은 해당 호출의 최신 저장 상태이며, 모든 상태 전환을 별도 불변 이벤트로 재구성하는 기능은 아닙니다.

## 숫자 카드

숫자 카드는 마우스 클릭 또는 키보드 Tab → Enter/Space로 상세 화면을 엽니다. hover/focus와 이동 아이콘으로 클릭 가능함을 표시합니다. 진행 중인 HTTP 변경 작업이 있으면 상세 이동을 보류하며 실행 요청을 취소하거나 재시도하지 않습니다.

스크린샷의 Runtime Intelligence 카드:

| 카드 | 상세 목록 |
|---|---|
| Active sessions | 최근 런타임 세션 중 ACTIVE 상태 |
| Observed agents | 같은 세션 자료에서 관찰한 고유 agent ID |
| Recent anomalies | 최근 이상 징후 |
| High / critical | HIGH 또는 CRITICAL 심각도의 이상 징후만 |

런타임은 기존 tenant-wide inventory이므로 상세에도 **Shared tenant runtime inventory**를 표시합니다. 세션의 자료 범위는 최근 50개, 이상 징후 상세는 최대 500개입니다. 이 구조를 workspace별 감사 기록과 혼동하지 않습니다.

Overview/Decision Engine, 에이전트 리스크·예측, 제어 루프, 그래프, 사건·대응, 기업 설정, 데이터 플레인, 정책 What-if/Replay, 보고서 카드도 관련 자료를 상세로 표시합니다. DENY/STEP_UP, Critical, 고위험 확률, 승인 대기 등은 해당 조건을 적용합니다. 합계나 평균의 상세는 계산에 기여한 행이므로 상세 행 수가 집계값과 같은 단위는 아닐 수 있습니다.

이미 계산한 분석이나 선택된 agent 자료는 **SOURCE_SNAPSHOT**으로 구분합니다. 스냅샷에는 표시 가능한 필드만 남기며 브라우저 메모리에서만 유지합니다. 상세 페이지의 새로고침으로 분석을 재실행하거나 정책을 변경하지 않습니다. 페이지를 완전히 리로드하면 이 선택은 초기화되므로 원래 화면에서 카드를 다시 엽니다. 실시간 재조회가 가능한 런타임 상세에는 별도 Refresh가 있습니다.

**Inspect record**는 선택한 자료를 읽기 전용으로 표시하고, agent ID가 있는 행의 **Open agent**는 에이전트 화면으로 이동합니다. Navigation view는 메뉴 표시일 뿐이며 변경해도 서버 역할은 부여되지 않습니다.

## API

```http
GET /v1/audit/events?kind=FILE&decision=DENY&subject=client:order-file-client-demo&limit=50
GET /v1/audit/events/FILE/<audit UUID>
GET /v1/runtime/intelligence/records?view=ANOMALIES&severity=HIGH_CRITICAL
```

공통 인증 및 `X-Tenant-Id`, `X-Workspace-Id`를 사용합니다. 감사 목록 응답은 `items`, `nextCursor`, `visibility`를 반환합니다. 다음 페이지는 같은 필터와 `cursor`를 보냅니다. JSON-RPC 파일 도구의 auditEventId는 FILE 상세 ID이며 주문 resume API에 넣지 않습니다.

수정 후 빌드·테스트·실제 호출은 자동 실행하지 않았습니다.
