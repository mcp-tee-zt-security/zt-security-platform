# 표준 Stitch 연동의 로컬 개발 절차

가상 Stitch 서버와 Stitchy 클라이언트를 함께 사용하려면 [로컬 시뮬레이션 가이드](../demo/STITCH_SIMULATION.ko.md)를 참고하세요. Prepare/위임/권한 변경/검색/삭제를 전용 데모 화면에서 실행할 수 있습니다. 아래 내용은 표준 API를 직접 호출하는 절차입니다.

실제 Stitch 시스템을 연결한 자료가 아닙니다. 합성 사용자/자료를 사용하지만 인증은 Keycloak이 서명한 실제 OIDC JWT로 진행합니다. 기존 Stitch Access PoC와 별도 API/테이블입니다.

## 준비

1. `docker/keycloak/stitch-contract-realm.json`은 로컬 전용 realm입니다. 이미 실행 중인 Keycloak은 새 파일을 자동으로 읽지 않을 수 있으므로, 로컬 환경에서 재시작하거나 관리자 콘솔로 새 realm을 가져옵니다. 기존 realm을 덮어쓰지 않습니다.
2. `docker/stitch-local-oidc.env.example`을 참고해 환경변수를 설정합니다.
3. 아래 명령으로 API/대시보드를 반영합니다. 여기서는 명령을 실행하지 않았습니다.

```powershell
docker compose --env-file docker/stitch-local-oidc.env.example -f docker/docker-compose.yml up -d --build authorization-api dashboard
```

새 realm을 읽으려면 로컬 Keycloak을 별도로 재시작해야 할 수 있습니다. 로컬 사용자 alice-demo와 공개된 데모 비밀번호는 합성 fixture만을 위한 값입니다. 운영 IdP에 가져오지 마세요. 실제 회사 로그인은 회사의 OIDC flow/claim mapper를 사용합니다.

## Postman 순서

`Stitch-Integration-v1.postman_collection.json`과 `Stitch-Integration-Local.postman_environment.json`을 Import합니다. 기존 order-ai-client의 secret을 새 환경의 client_secret에 직접 넣습니다.

1. 01에서 Human fixture token과 Connector service token을 발급받습니다. 응답 토큰은 해당 환경에 자동 저장됩니다.
2. 02에서 사용자 그룹을 먼저 provision하고, 일반 폴더/문서 → Payroll 폴더/파일/첨부파일 → 채널/메시지 순서로 sync합니다. 마지막 Read source sync checkpoint version → Commit complete source snapshot을 호출합니다. 이 완료 체크포인트 전에는 조회가 차단됩니다.
3. 03에서 사람의 Payroll 조회와 첨부파일 다운로드를 호출합니다. 같은 사람의 토큰으로 AI 위임 세션을 생성합니다.
4. 04에서 AI가 일반 자료를 조회하고 Payroll/첨부파일은 차단되는지 확인합니다. 검색은 허용된 chunk만 대상으로 합니다. 반복 lexical search는 권한을 다시 확인하는 ID 캐시를 사용합니다.
5. MCP는 위임 ID를 X-ZT-Delegation 헤더에 넣습니다. LLM tool arguments에 세션/신원/권한 정보를 넣지 않습니다.
6. 05에서 세션 폐기와 maintenance, pending deletion event를 확인합니다. 외부 수신자가 실제로 삭제한 뒤에만 ACK 요청을 보내세요.

리소스/subject sync는 sourceVersion을 증가시켜야 합니다. 동일 payload를 다시 보내면 409가 정상입니다. Collection 전체 Run은 권한 변경·삭제까지 실행하므로 개별 요청을 사용하세요.

## 대시보드

권한 변경 후에는 새 ACL 버전으로 source checkpoint를 다시 완료해야 합니다. 로컬 snapshot 유효기간은 600초이며 운영 기본값은 120초입니다. 기간이 지나면 source를 다시 확인·동기화하고 완료 체크포인트를 보내야 합니다. 토큰/위임 세션도 각각 만료되면 새로 발급합니다. source 중단/미완료를 계속 허용으로 처리하지 않습니다.

Agents & Connections → **Stitch Integration**. 실제 토큰 또는 서비스 자격 증명을 Apply and verify하고 요청 JSON을 실행합니다. 사람은 HUMAN JWT, source 동기화는 CONNECTOR/STITCH_SYNC JWT, AI는 전용 서비스 자격 증명 + 사람이 발급한 세션이 필요합니다. 기존 관리자 API key를 사람 로그인으로 사용하지 않습니다.

이 화면은 표준 API 콘솔입니다. 실제 Stitch 로그인 UI·폴더 설정 화면이 자동으로 연결된 것은 아닙니다. 요청별 schema와 연결 계약은 [STITCH_INTEGRATION_V1.md](STITCH_INTEGRATION_V1.md)를 확인하세요.

## 현재 완료 범위와 남은 연결

코드: 인증/위임, 사용자·그룹 ACL, 파일/첨부파일, PostgreSQL 검색과 RAG chunk, 권한 캐시 무효화, REST/MCP 어댑터, 보존·삭제 큐/확인 계약, 워커/SDK/배포 제한 템플릿.

남은 외부 연결: 실제 Stitch의 소스 권한 feed와 삭제 수신 API, 회사 IdP 설정, 실제 embedding 생성 모델, 배포 CNI/IP/TLS/DB 계정, provider/backup 삭제 정책. 아직 실제 연결·배포·빌드·테스트·실행 검증은 수행하지 않았습니다.
