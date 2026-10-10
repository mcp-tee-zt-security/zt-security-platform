# Postman: ZT MCP / AI Client

## Import

1. Postman → Import → select `ZT-MCP-AI.postman_collection.json` and `ZT-Local.postman_environment.json`.
2. Select **ZT Local - Order AI** environment.
3. Change admin_api_key, tenant_id, workspace_id to match your deployment.

## 실행 순서

전체 Collection Run 대신 요청을 하나씩 Send 하세요. 등록·정책 활성화·실제 주문 취소 요청이 포함되어 있습니다.

1. **01 → List existing clients**. 신규인 경우만 **Create order-ai-client**. secret은 환경의 client_secret에 자동 저장됩니다. 기존 클라이언트는 보관한 secret을 직접 입력하세요.
2. client_secret 값을 IntelliJ AI Client Run Configuration의 **ZT_ORDER_AGENT_SECRET**에도 설정합니다. Postman 환경변수는 IDE로 자동 전달되지 않습니다.
3. 서버가 이미 등록되어 있으면 등록 요청을 생략합니다. **Discover upstream tools** → **List registered tools and bindings** → 필요한 **Register/update** 요청. 기존 binding을 수정하므로 schema/permissions/approval 설정을 먼저 확인하세요. 발견한 schema와 기존 tool UUID를 자동 저장합니다.
4. **02**에서 기존 정책을 확인합니다. 필요한 읽기/취소 DENY 정책을 Validate → Create DRAFT → Publish. 중복 생성하지 마세요.
5. **03 → Service identity**는 client:order-ai-client를 반환해야 합니다. tools/list → 조회 → 취소 요청 순서로 호출합니다. DENY 정책이 활성화되면 취소는 차단됩니다.
6. 승인 시나리오: 취소 DENY 정책을 변경하고 취소 ALLOW 정책을 준비한 후 대시보드 binding에서 Require independent approval을 활성화하세요. 취소 요청 → **04 Approve** → **03 Resume**. 관리자와 원래 서비스 호출자를 구분합니다.
7. **05**는 실제 AI Client입니다. IDE 환경변수를 설정한 뒤 재시작해야 합니다. 승인 뒤 AI Resume은 저장된 호출 하나만 재개합니다.

## 자동 저장

client_secret / client_record_id, 도구별 schema와 tool_id, 정책 id, call_id / approval_id를 응답에서 저장합니다. 선택한 환경에 기록됩니다. 승인 상세 API는 call_id, 결정 API는 approval_id를 사용합니다.

## 주소와 실행 효과

- ZT: localhost:8080 (직접 API, /api prefix 없음).
- AI Client: localhost:9999.
- Docker ZT에서 호스트 주문 MCP: host.docker.internal:9998/mcp.
- Evaluate ONLY는 정책 평가입니다. Execute / AI question / Resume은 허용되면 실제 외부 도구를 실행합니다.
- 실제 취소 결과는 upstream 주문 상태와 호출 횟수로 확인해야 합니다. Gateway 이력만으로 외부 실행 횟수를 입증하지 않습니다.
- UNKNOWN이나 응답 유실 시 재전송하지 말고 이력/주문 상태를 확인하세요. 매 Send는 새로운 RPC ID입니다.
- 입력 schema는 Discover 결과를 사용합니다. getOrders 예제 status=PENDING은 현재 주문 MCP 기준이며 schema가 다르면 요청을 수정하세요.
- 환경 파일은 secret 없이 제공합니다. secret 저장 후 환경을 공유하거나 Git에 커밋하지 마세요.

빌드, 테스트, 실제 API 실행은 수행하지 않았습니다.


## Demo order data

Folder 00 creates a PENDING order via POST localhost:9998/orders and saves its generated order_id. Each Send creates another order. Direct GET order status is available for checking the state after a ZT DENY. No direct cancellation request is included. A demo order ORD-FC0AE56B was created for customer ZT-DEMO-CUSTOMER, amount 10000. This is setup data, not an execution verification. Re-import the collection and update existing environment order_id/order_base_url if you already saved credentials; preserve your existing client_secret.

