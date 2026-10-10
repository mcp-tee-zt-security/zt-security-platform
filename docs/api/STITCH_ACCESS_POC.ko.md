# Stitch AI 접근 통제 PoC

## 반영 및 시작

```powershell
docker compose -f docker/docker-compose.yml up -d --build authorization-api dashboard
```

로컬 Compose는 `ZT_STITCH_POC_ENABLED=true`를 기본값으로 사용합니다. API를 IDE로 실행할 때는 이 환경변수를 직접 설정합니다. 기본 application.yml에서는 비활성화됩니다. Flyway V6가 영역·문서·호출 이력 테이블을 추가하며 데이터는 관리자 Initialize 버튼을 누를 때 생성됩니다.

대시보드 → Agents & Connections → **Stitch Access PoC**. CISO / SOC_ANALYST / DEVOPS 메뉴에서 접근할 수 있습니다. 메뉴 노출은 실제 서버 권한을 바꾸지 않습니다.

## 5분 데모

1. **Initialize synthetic fixtures**를 누릅니다. 이미 있는 데이터와 설정은 유지됩니다.
2. 기본 설정을 확인합니다: General ALLOW, Payroll DENY, Payroll archive 로컬 ALLOW / 상속 DENY, General channel ALLOW, Payroll channel DENY.
3. Caller를 **Configured administrator — human demo access**로 적용합니다.
4. `readDocument` → `payroll-2026` 실행: ALLOW, 합성 급여 내용 반환.
5. **Registered AI service client**로 변경합니다. 기존 `order-ai-client`와 발급된 secret으로 Apply identity합니다. AI subject는 `client:order-ai-client`입니다. 별도의 AI identity나 MCP binding 등록은 이 PoC endpoint에 필요하지 않습니다.
6. 같은 `payroll-2026` 조회: DENY, results=[], contentReads=0.
7. `handbook` 조회: ALLOW. `payroll-archive-2025` 조회: 부모 금지 상속으로 DENY.
8. `searchDocuments` → `payroll`: handbook/general-message만 반환됩니다. Payroll 제목·내용·미리보기·숨겨진 결과 수는 AI 응답에 포함되지 않습니다. `Alice` 검색은 AI에게 빈 결과입니다.
9. `listChannelMessages` → `payroll-channel`: DENY. `general-channel`: ALLOW.
10. AI 접근 설정을 Payroll ALLOW로 바꾸고 같은 AI 조회를 실행하면 허용됩니다. 데모 종료 전에 DENY로 복원하세요. 이미 반환한 데이터는 회수되지 않습니다.

호출 이력은 화면 하단에 표시됩니다. 관리자에서는 workspace 전체, 서비스 호출자에서는 자신의 기록만 보입니다. 이 이력은 기존 MCP/workflow/암호화 봉인 감사 로그와 별도이며 문서 내용과 검색어를 저장하지 않습니다.

## 인증과 관리자 화면

리소스 설정·관리자 카탈로그는 대시보드의 설정된 관리자 자격 증명으로 조회합니다. 실행 영역은 Apply identity로 적용한 자격 증명을 사용합니다. 서비스 클라이언트를 고른 상태에서도 관리자 카탈로그 제목이 보이는 것은 로그인된 관리자의 관리 UI입니다. 이 카탈로그 API 자체는 서비스 클라이언트에게 허용되지 않습니다.

HUMAN 장면은 인증된 관리자의 합성 사용자 ACL입니다. 실제 Stitch 사용자의 SSO와 folder ACL을 구현한 것은 아닙니다. 실제 JWT 연결 시 신뢰된 issuer의 `actor_type=HUMAN/AI_AGENT`, `aud=zt-stitch-poc`(또는 ZT_STITCH_POC_AUDIENCE 설정값), 일치하는 `tenant_id`, `workspace_id`가 필요합니다. SERVICE_CLIENT / AI_AGENT 역할은 HUMAN으로 바뀌지 않습니다. 요청 body의 principal, actorType, agentAccess는 인증과 권한을 바꾸지 못합니다.

## 서버에서의 통제

문서 ID → 서버 저장소의 소속 영역 → 부모 영역까지 설정 계산 → ZT PolicyEvaluator 평가 → ALLOW일 때만 본문 반환.

이 PoC는 필수 기본 guard 정책을 메모리에서 구성하고, 해당 tenant/workspace의 활성 정책과 함께 평가합니다. 운영 정책 테이블에 자동 생성하지 않습니다. DENY는 ALLOW보다 우선하며 STEP_UP도 본문 반환을 막습니다. PoC에는 STEP_UP 승인/재개 기능이 없습니다. MCP의 full ActionService/risk/approval 파이프라인과는 별도 retrieval adapter입니다.

영역 설정은 조회 트랜잭션 동안 잠금으로 유지되어, 동시에 금지 설정이 변경될 때 하나의 요청 안에서 판정과 조회가 엇갈리지 않도록 합니다. 합성 PoC 작업은 workspace 단위 트랜잭션 잠금으로 직렬 처리하며 운영 검색 처리량을 목표로 하지 않습니다. 금지 설정 변경은 진행 중인 조회가 완료된 뒤 적용될 수 있습니다. 이미 전송한 내용은 회수되지 않습니다.

검색은 먼저 metadata로 허용 document ID 목록을 만든 뒤 그 ID만 대상으로 제목·본문 검색 SQL을 수행합니다. 외부 검색 인덱스·벡터 DB·캐시는 사용하지 않습니다. `contentReads`는 허용된 content query에서 애플리케이션으로 반환된 행 수이며 물리적 DB 읽기나 외부 MCP 호출 횟수는 아닙니다.

## 실제 Stitch 연결에서 남은 부분

- 실제 사용자/AI 세션 인증과 folder/channel ACL 연결.
- 파일·하위 폴더·첨부파일·다중 권한 모델 연결.
- 실제 검색/RAG 인덱스와 캐시의 권한 필터·권한 변경 처리.
- Stitchy의 retrieval API 또는 MCP tool 실행 경로에 이 adapter 연결.
- 직접 DB/API 우회 경로와 외부 연결의 접근 제한.
- 기존에 AI로 전달된 내용에 대한 보존·삭제 정책.

현재 데이터는 합성 샘플만 사용합니다. 실제 Stitch, Ollama/LLM, TEE, 외부 MCP 서버를 연결하거나 호출하지 않습니다. 위 변경에 대해 빌드·테스트·실행 검증을 수행하지 않았습니다.

## Postman

`docs/postman/Stitch-Access-PoC.postman_collection.json`을 가져오고 기존 **ZT Local - Order AI** 환경을 선택합니다. 해당 환경의 client_secret에 기존 secret이 있어야 합니다. 컬렉션에는 인자 조작 요청과 설정 변경·복원 요청이 포함됩니다. 전체 Run보다 요청을 하나씩 Send하세요.
