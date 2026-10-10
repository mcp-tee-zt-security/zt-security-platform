# 대시보드 주문 데모 준비

## 화면의 대상과 신원

- 에이전트/호출자 선택은 비어 있는 상태로 시작합니다. 등록된 AI Agent와 Service Client는 구분하여 표시합니다.
- AI Agent identity는 업무·분석의 등록 대상입니다. Service Client는 secret으로 인증하는 프로그램의 신원입니다. 하나를 등록해도 다른 하나가 자동 생성되지는 않습니다.
- 분석에 서비스 호출자를 선택하면 `client:order-ai-client`라는 실제 subject를 조회합니다. 해당 분석에 수집된 데이터가 없을 수 있습니다. MCP 호출 이력과 runtime session 데이터는 동일한 저장 구조가 아닙니다.
- 시뮬레이션에서 선택한 신원은 가상 요청의 principal입니다. 실제 인증으로 바뀌지 않습니다.
- MCP Gateway의 실제 호출 신원은 Caller identity에서 인증한 사용자입니다.
- Tenant/Workspace 배너는 현재 빌드 설정을 표시합니다. 영역 전환 기능은 포함하지 않습니다. AI Agent identity 목록은 테넌트 범위이고, workspace-bound 서비스 클라이언트는 현재 workspace와 일치하는 항목만 표시합니다.

## 1. 클라이언트 준비

Product Operations에서 기존 클라이언트 목록을 확인하세요. 이미 `order-ai-client`가 있으면 재등록하지 않습니다.

신규 등록은 ID와 이름을 입력한 뒤 Create service client를 누릅니다. One-time secret을 복사해 IDE 환경변수 `ZT_ORDER_AGENT_SECRET`에 설정합니다. 화면을 떠나면 secret은 유지되지 않습니다. 대시보드 입력값은 소스나 localStorage에 저장하지 않습니다.

Create AI-agent identity는 분석용 identity를 별도로 생성합니다. 서비스 인증이나 실행 정책을 자동으로 만들지 않습니다.

## 2. 정책 준비

Policy Studio에서 Order reads 템플릿을 선택하고 Validate 후 Activate합니다. 취소 차단 장면은 Deny order cancellation 템플릿을 활성화합니다. 정책 목록에서 기존 이름과 활성 상태를 먼저 확인하세요.

Draft simulation은 별도 가상 평가입니다. 대상과 주문 시나리오를 선택하고 Order ID 및 Request JSON을 확인합니다. 실제 외부 호출 차단 데모는 MCP Gateway에서 진행합니다.

## 3. 도구와 호출 권한

MCP Gateway → Upstream servers에 주문 서버를 등록하고 도구를 발견합니다. Docker ZT에서 호스트 서버 주소는 `http://host.docker.internal:9998/mcp`입니다.

Tool registration에서 getOrders, getOrderStatus, cancelOrder의 allowed subjects에 `client:order-ai-client`를 한 줄로 추가합니다. 관리자 테스트도 필요하면 `api-key`를 별도 줄에 유지합니다. Binding 변경은 기존 대기 승인을 무효화합니다.

## 4. 실제 실행

Caller identity에서 Registered service client를 선택하고 Client ID 및 secret으로 Apply identity합니다.

Execute & inspect에서 도구를 선택합니다. 단순 필드는 schema에 따라 표시하며 JSON 직접 편집도 가능합니다.

1. getOrders: status=PENDING → Execute upstream tool.
2. cancelOrder: 실제 테스트 orderId → DENIED 확인.
3. getOrderStatus: 같은 orderId → PENDING 유지 확인.

Evaluate policy only는 Upstream을 호출하지 않습니다. ALLOW는 정책 결과이며, SUCCEEDED는 Upstream 응답 기록입니다. 실제 주문 상태는 도구 결과로 확인합니다. UNKNOWN 또는 응답 유실은 이력과 외부 상태를 먼저 확인하고 재전송하지 않습니다.

## 5. 승인 시나리오

취소 DENY 정책은 STEP_UP 정책보다 우선합니다. DENY 정책을 비활성화/대체한 뒤 승인 시나리오를 준비해야 합니다. Approve order cancellation 템플릿을 사용하거나, 취소 ALLOW 정책과 binding의 Require independent approval을 함께 설정하세요.

서비스 호출자의 취소 요청 → 독립 관리자 Caller identity로 전환 → MCP approvals에서 승인 → 원래 서비스 호출자로 전환 → 저장된 callId 재개.

승인 자체는 실행하지 않습니다. MCP approvals/Call history와 일반 workflow Approvals & Execution은 별도의 기록입니다. 화면 간 링크에서 해당 구분을 확인하세요.

## 갱신과 제한

상단 Refresh는 조회 데이터를 다시 불러옵니다. 정책 draft와 메모리 인증 정보는 그대로 유지합니다. Last API response는 마지막 성공 API 응답 시각이며 모든 데이터의 동시 갱신 완료를 의미하지 않습니다.

Runtime Gateway의 결제/환불은 명시적으로 선택하는 데모 요청입니다. 등록된 에이전트를 선택해도 해당 샘플 task/tool의 권한을 자동 부여하지 않습니다. 실제 MCP 실행에는 MCP Gateway를 사용합니다.

소스 변경 후 빌드·테스트·실제 실행 검증은 수행하지 않았습니다.
