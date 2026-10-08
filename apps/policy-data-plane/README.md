# Policy data plane

Rust 서비스는 배포된 정책의 보수적인 fast path를 담당합니다. 전체 승인·행동 분석·감사 흐름은 authorization-api에서 수행합니다. 응답의 `evaluation_scope`는 `POLICY_ONLY`이며 `DEFER`를 받은 호출자는 전체 평가를 호출해야 합니다.

## 소스 구조

| 파일 | 책임 |
| --- | --- |
| main.rs | 설정 로드, 서버 시작, 종료 신호 |
| config.rs | 시작 시 환경 변수 파싱과 유효성 확인 |
| models.rs | 요청·응답·번들 DTO |
| canonical.rs | Java와 공유하는 v2 서명 바이트 형식 |
| bundle.rs | 범위·시간·해시·서명 확인, 정책 사전 컴파일 |
| state.rs | 공유 스냅샷, 원자적 교체, readiness |
| refresh.rs | 제한된 다운로드·재시도, 선택적 로컬 캐시 |
| condition.rs | 단일 context 비교식 처리 |
| evaluator.rs | 정책 매칭과 DENY/STEP_UP/ALLOW/DEFER 결정 |
| evidence.rs | 요청 원문과 결과를 연결하는 Ed25519 증거 |
| identity.rs | 전달된 workload identity 표시 |
| attestation.rs | 문서 상태와 강제 적용 여부 |
| metrics.rs | 카운터와 지연 시간 히스토그램 |
| routes.rs | HTTP 경로, 입력 제한, 선택적 API 키 인증 |

요청은 Arc로 공유하는 불변 스냅샷 하나를 사용합니다. 서명 확인과 조건 컴파일은 갱신 시 수행합니다. 실패한 갱신은 현재 번들을 교체하지 않습니다. 더 오래된 타임스탬프의 번들을 거부하며 동일 번들 재전달은 monotonic age를 초기화하지 않습니다.

## 정책 결정

1. 일치하는 지원 정책의 DENY가 최우선입니다.
2. 적용될 수 있지만 처리할 수 없는 정책이 있으면 DEFER입니다.
3. 그다음 STEP_UP, ALLOW 순서입니다.
4. 일치하는 지원 정책이 없으면 DEFER입니다.

Rust 조건 처리기는 단일 숫자 비교와 문자열·불리언 동등 비교를 지원합니다. 복합식, 타입 불일치, 지원하지 않는 속성은 DEFER로 연결됩니다. Java는 principal/resource/action 선택자가 정확히 하나씩 있는 정책만 fast path로 표시합니다. 조건이 있는 Java 정책은 계속 전체 평가로 넘깁니다. condition의 Java AST 설명 문자열은 revision에 반영하며 fast path의 실행식으로 사용하지 않습니다.

## 설정

환경 변수는 시작 시 한 번 로드합니다. 잘못된 숫자, 모드, UUID, 헤더 값, Base64 키는 시작 오류를 반환합니다. 키와 전체 설정은 로그에 출력하지 않습니다.

| 변수 | 기본값 / 설명 |
| --- | --- |
| ZT_BIND_ADDR | 0.0.0.0:8091 |
| ZT_CONTROL_PLANE_URL | http://localhost:8080 |
| ZT_TENANT_ID | 11111111-1111-1111-1111-111111111111 |
| ZT_WORKSPACE_ID | 미설정이면 tenant-wide 정책만. Compose는 데모 워크스페이스 사용 |
| ZT_API_KEY | control plane 호출용, 개발 기본값 dev-master-key |
| ZT_POLICY_REFRESH_SECONDS | 5, 1..3600 |
| ZT_CONTROL_PLANE_CONNECT_TIMEOUT_SECONDS | 1, 1..120 |
| ZT_CONTROL_PLANE_TIMEOUT_SECONDS | 3, 1..300 |
| ZT_BUNDLE_MAX_AGE_SECONDS | 60, 1..86400 |
| ZT_BUNDLE_MAX_FUTURE_SKEW_SECONDS | 5, 0..300 |
| ZT_MAX_BUNDLE_BYTES | 4194304, 다운로드 및 캐시 읽기 제한 |
| ZT_MAX_REQUEST_BYTES | 65536, 평가 요청 본문 제한 |
| ZT_FAIL_MODE | FAIL_CLOSED; FAIL_OPEN은 가용성 오류에만 적용 |
| ZT_POLICY_PUBLIC_KEY_B64 | Ed25519 raw 32-byte 공개키, Base64 |
| ZT_POLICY_PUBLIC_KEY_ID | 선택적 서명 키 ID 고정 |
| ZT_REQUIRE_SIGNED_BUNDLE | false; true면 공개키 필수 |
| ZT_ALLOW_UNSIGNED_BUNDLES | 공개키 없는 개발 설정에서만 기본 true |
| ZT_POLICY_BUNDLE_PIN | 선택적 sha256:... 정책 revision 고정 |
| ZT_BUNDLE_CACHE_PATH | 미설정이면 비활성화. Compose에서 /var/cache/zt/bundle.json 사용 가능 |
| ZT_DP_API_KEY | 이 서비스의 수신 요청용 키. 설정하면 인증 기본 활성화 |
| ZT_DP_REQUIRE_API_KEY | 수신 키 존재 여부에 따라 기본값 결정 |
| ZT_CORS_ALLOWED_ORIGINS | 기본 *; 예: http://localhost:3000,http://localhost:5173 |
| ZT_EVIDENCE_SIGNING_PRIVATE_KEY_B64 | Ed25519 raw 32-byte seed, Base64 |
| ZT_EVIDENCE_SIGNING_KEY_ID | zt-dp-evidence-ed25519 |
| ZT_ATTESTATION_MODE | DISABLED, DOCUMENT_HASH, EXTERNAL_VERIFIED, REQUIRED |
| ZT_ATTESTATION_PROVIDER | NONE, 표시용 |
| ZT_NITRO_ATTESTATION_DOCUMENT_B64 | 선택적 문서, 최대 1 MiB |
| ZT_NITRO_EXPECTED_PCR3, ZT_NITRO_EXPECTED_PCR8 | 기대값 표시용 |
| ZT_ATTESTATION_NONCE, ZT_ATTESTATION_KMS_KEY_ID | 기대값 표시용 |

공개키와 unsigned acceptance는 함께 사용할 수 없습니다. unsigned 개발 번들은 `UNSIGNED_DEVELOPMENT`, `bundle_signature_valid=false`로 표시합니다. 운영에서는 Java의 서명 개인키와 Rust의 공개키를 함께 설정해야 합니다.

수신 인증을 켜면 /health, /ready를 제외한 경로에 X-Api-Key가 필요합니다. 기존 대시보드 직접 조회에는 키가 없으므로 인증을 켜려면 호출 측에도 키를 전달하는 프록시 구성이 필요합니다. 호출자가 scope 헤더를 보내면 이 인스턴스의 scope와 일치해야 합니다.

## 경로와 장애 처리

| 경로 | 용도 |
| --- | --- |
| GET /health | 프로세스 liveness |
| GET /ready | 준비 상태와 사유. 준비 안 됨은 HTTP 503 |
| GET /metrics | Prometheus 카운터·누적 지연 시간 히스토그램 |
| GET /v1/fast/stats | 기존 snake_case 통계 및 refresh/readiness/signature_state |
| GET /v1/fast/policy-bundle | 마지막 검증된 번들, 없으면 503 |
| GET /v1/fast/identity | 전달된 identity 주장 표시 |
| GET /v1/fast/attestation | attestation 상태 |
| POST /v1/fast/evaluate | application/json 정책 평가 |

BUNDLE_UNAVAILABLE, BUNDLE_STALE은 가용성 오류입니다. FAIL_OPEN을 명시하면 이 경우 ALLOW와 degraded=true를 반환합니다. 검증 실패 후 신뢰할 수 있는 번들이 없거나 만료되면 NO_TRUSTED_BUNDLE 또는 STALE_AFTER_INTEGRITY_REJECTION으로 거부합니다. 이후 네트워크 오류가 검증 실패 기록을 지우지 않습니다. 아직 유효한 마지막 정상 번들은 계속 사용하며 정상 업데이트 수락 시 실패 상태를 해제합니다.

attestation 외부 검증기는 아직 없습니다. EXTERNAL_VERIFIED 설정만으로 VERIFIED를 반환하지 않습니다. DOCUMENT_HASH는 문서 해시만 표시하며 DISABLED 이외 모드는 readiness를 막습니다. X-Forwarded-Client-Cert URI도 전달된 주장으로 표시하고 identity_authenticated=false입니다.

## 캐시와 증거

캐시는 수락된 번들만 같은 디렉터리의 임시 파일에 쓰고 rename합니다. 시작 시에도 scope·서명·해시·유효 기간을 확인하며 캐시로 유효 기간을 연장하지 않습니다. Docker Linux 파일시스템의 rename 교체를 대상으로 합니다. 교체를 허용하지 않는 파일시스템에서는 cache_error를 기록하고 메모리 번들은 유지합니다. replica별 별도 캐시 볼륨을 사용합니다. 형식이나 scope 변경 시 오래된 캐시는 거부되며 정상 발급 번들을 받아야 준비 상태가 됩니다.

증거 서명 키를 설정하면 signed_payload가 포함됩니다. 그 UTF-8 원문에 대한 Ed25519 서명을 검증해야 하며 JSON을 재직렬화하면 안 됩니다. request_hash는 실제 수신 HTTP 본문 바이트의 SHA-256입니다. 서명 내용에는 결정·정책·scope·번들·degraded 상태·identity 인증 여부·attestation 상태·시간이 포함됩니다.

## 적용

Java와 Rust 번들 v2는 함께 적용해야 합니다. v1 번들은 수락되지 않으며 기존 ZT_POLICY_BUNDLE_PIN=3.2.0 같은 값도 새 content revision으로 변경해야 합니다.

프로젝트 루트에서 다음 명령으로 함께 재빌드할 수 있습니다.

```powershell
docker compose -f docker/docker-compose.yml up -d --build authorization-api policy-data-plane-1 policy-data-plane-2 policy-data-plane
```

이 작업에서는 빌드·테스트·실행 검증을 수행하지 않았습니다. Rust와 Java에 변조, scope, 시간, 갱신 거부, 보수적 평가, UTF-8 canonical 형식 테스트 소스를 추가했습니다.

프로토콜: [DATA_PLANE_BUNDLE_V2.md](../../docs/api/DATA_PLANE_BUNDLE_V2.md).
