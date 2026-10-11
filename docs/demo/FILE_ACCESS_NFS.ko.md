# 실제 파일 기반 Workflow Chat 접근 테스트

finance 사용자에게 Payroll 목록을 허용하고 IT 사용자에게 차단하는 로컬 데모입니다. 파일 목록을 모델이 만들어 답하는 방식이 아니라 실제 파일을 ZT에 동기화하고 원본 ACL과 AI 정책을 평가합니다.

```text
실제 Linux 파일 볼륨 ── NFS-Ganesha: NFSv4 읽기 전용 export /files
          │
          └── file-connector: 실제 파일·ACL → ZT API로 동기화
                                              │
finance / IT → Keycloak 로그인 → Workflow Chat → 보안·리스크 평가
                                              → 원본 사용자 ACL ∩ ACTIVE AI 정책
                                              → 허용된 파일 목록 / DENY
```

이 데모의 connector는 NFS가 내보내는 **동일한 실제 볼륨을 직접 읽습니다**. 채팅 테스트가 NFS 프로토콜로 파일을 마운트해서 읽는 것은 아닙니다. NFS 서버 구성과 ZT의 파일 접근 통제를 함께 제공하지만 NFS wire protocol 검증은 별개입니다. NFS의 AUTH_SYS UID/GID와 웹 로그인 JWT는 다른 인증이며 finance/IT 접근 제어는 ZT에서 수행합니다. NFS 포트는 호스트에 열지 않습니다.

## 준비 및 실행

Docker Desktop의 Linux 컨테이너와 기존 Ollama 모델을 사용합니다. 저장소 루트의 PowerShell에서 먼저 기본 Workflow 서비스를 빌드합니다.

```powershell
docker compose -f docker/docker-compose.yml -f docker/mcp-oidc.compose.yml up -d --build keycloak authorization-api dashboard
```

기존 `zt-mcp-demo` realm이 없다면 Keycloak을 재시작해 import한 뒤 다음 명령을 실행하세요. 이미 있으면 설정 스크립트가 누락된 그룹·사용자를 추가합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\setup-file-access-demo.ps1
```

설정 스크립트는 다음을 수행합니다.

- 기존 사용자의 비밀번호를 초기화하지 않고 finance/IT 계정·그룹과 REQUESTER 역할을 추가합니다.
- **File Access Demo** 워크스페이스를 새로 만들거나 같은 slug의 기존 워크스페이스를 재사용합니다.
- 별도의 대시보드 OIDC client `zt-file-dashboard`와 최소 sync 권한의 connector client `zt-file-sync`를 구성합니다.
- 실제 Keycloak 사용자 `sub`와 그룹을 source 설정에 기록합니다. 사용자 이름을 임의 신원으로 사용하지 않습니다.
- 워크스페이스의 Workflow AI identity와 `file-demo` 연결을 등록합니다.
- `allow_file_demo_<workspace UUID>` 정책을 **DRAFT로만 저장**합니다. Publish는 자동 실행하지 않습니다.
- `.local/file-access-demo/demo.env`, `config.json`, 정책 DSL을 생성합니다. 이 폴더는 Git에서 제외되며 `config.json`은 로컬 데모 connector secret을 포함합니다.

설정에는 로컬 Keycloak `admin / admin`을 사용합니다. 계정이 다르면 `-AdminUsername`, `-AdminPassword`를 지정하세요. 스크립트는 임시 ADMIN service client로 ZT 등록 API를 호출한 후 그 client를 삭제합니다. ADMIN 토큰·임시 secret은 파일이나 화면에 기록하지 않습니다. 기존 Stitch 연결과 정책은 수정하지 않습니다.

파일 서비스를 시작합니다.

```powershell
docker compose --env-file .local/file-access-demo/demo.env -f docker/docker-compose.yml -f docker/mcp-oidc.compose.yml -f docker/file-access-demo.compose.yml up -d --build file-demo-seed file-nfs file-connector file-dashboard
```

초기 파일은 Linux named volume에 생성됩니다. Windows의 파일 시스템을 NFS VFS로 직접 export하지 않습니다. NFS 컨테이너에는 VFS 파일 핸들 접근에 필요한 `DAC_READ_SEARCH`만 추가하며 `privileged` 모드를 사용하지 않습니다. 기반 설명은 [NFS-Ganesha의 VFS 문서](https://github.com/nfs-ganesha/nfs-ganesha/wiki/VFS)를 참고하세요.

파일 connector는 30초마다 변경 파일·ACL·삭제를 동기화하고 checkpoint를 갱신합니다. 실패하면 이전 준비 상태는 유효 시간이 지나 만료되며, 불완전한 변경 중에는 ZT가 접근을 차단합니다.

## 정책을 수동으로 활성화

1. **http://localhost:3001**을 열어 `demo-admin / demo-admin`으로 로그인합니다.
2. 상단 workspace가 **File Access Demo**인지 확인합니다. 기존 **3000** 대시보드와 분리된 workspace입니다.
3. **Policies → Policy Studio**에서 `allow_file_demo_<workspace UUID>` DRAFT를 선택합니다. 메뉴가 없으면 Navigation view를 DEVOPS로 바꾸세요.
4. DSL의 AI subject와 `connectionId == "file-demo"`를 확인하고 Validate → Publish 합니다. 화면에서 reviewed DRAFT를 선택하는 흐름이면 그 단계를 따릅니다.
5. **Retrieval Access**에서 연결이 Enabled이고 `payroll`, `it`, 파일들이 동기화되어 있는지 확인합니다.

이 ALLOW는 AI에 파일 API 사용을 허용하지만 **사람의 원본 ACL을 우회하지 않습니다**. finance 권한은 source ACL의 `GROUP finance`, IT 권한은 `GROUP it`에 있습니다. AI 정책은 ZT Policy Studio에 있습니다. 정책을 DRAFT로 두면 finance도 default deny입니다.

## 계정을 바꿔 같은 요청 비교

| 로그인 사용자 | 처음 생성 시 비밀번호 | 그룹 | Payroll | IT |
|---|---|---|---|---|
| finance | demo-finance | finance | ALLOW | DENY |
| it-user | demo-it | it | DENY | ALLOW |

기존 계정의 비밀번호를 이전에 바꿨다면 기존 값을 사용하세요. 화면에서 **Sign out / switch user**를 눌러 계정을 바꿉니다.

finance 로그인 후 Workflow Chat:

```text
payroll 폴더에 있는 파일 리스트해
```

정책과 다른 위험 통제에 차단되지 않으면 `ALLOW`와 다음 실제 파일 이름이 표시됩니다.

```text
october-payroll.csv
payroll-notes.txt
```

목록 조회는 파일 내용 없이 이름·종류·리소스 ID·source version만 반환합니다. 모델이 파일 이름을 작성하지 않습니다.

IT 로그인 후 **같은 문장**을 보냅니다. 정상 예상 결과는 `DENY`이며 파일 이름과 내용을 반환하지 않습니다. 같은 AI subject라도 위임한 사람의 권한이 다르기 때문입니다. IT 사용자로 아래 요청은 허용되는 양성 대조입니다.

```text
it 폴더에 있는 파일 리스트해
```

예상 파일: `runbook.txt`. finance는 같은 IT 폴더 요청이 차단됩니다.

파일 내용 읽기는 별도 READ 작업입니다.

```text
payroll-notes.txt 파일 가져와
```

finance에만 허용됩니다. CSV/텍스트는 모두 가상의 데이터이며 실제 급여 정보가 아닙니다. 이 데모는 100개 이하 경로와 64KB 이하 UTF-8 파일을 지원합니다.

## 기록과 운영

- Workflow Chat의 5단계 평가에서 JWT 요청자·AI 위임·리스크·행동·정책·감사 ID를 확인합니다.
- 파일 감사 기록은 **Retrieval Access**의 현재 workspace 감사 기록에 `workflow.list` 또는 `workflow.retrieve`로 남습니다. 일반 채팅 게이트와 실제 파일 ACL 결정은 구분해서 표시합니다.
- ALLOW여도 동작·위협 통제가 강화되거나 매칭 DENY 정책이 있으면 결과가 달라집니다. source ACL이 차단한 접근은 ALLOW 정책 추가만으로 풀리지 않습니다.
- 파일 결과는 모델 문맥이나 서버 채팅 기록에 저장하지 않으며 화면 미리보기는 약 1분 후 사라집니다. 다시 조회하면 다시 인가합니다.
- seed는 기존 볼륨 파일을 덮어쓰지 않고 누락된 fixture 파일만 복사합니다. fixture에 새 파일을 추가한 후 seed 서비스를 다시 실행하면 새 파일을 복사합니다. NFS/connector 재시작은 파일을 복구하지 않지만, seed까지 다시 실행하면 삭제했던 기본 fixture 파일이 재생성됩니다. fixture에서 지운 파일이 볼륨에서도 자동 삭제되지는 않습니다.
- 계정·워크스페이스·정책 데이터는 기존 PostgreSQL/Keycloak 저장소에 남습니다. Compose를 내렸다고 데이터가 삭제되지는 않습니다.

문제가 있으면 사용자가 아래 로그를 확인할 수 있습니다.

```powershell
docker compose --env-file .local/file-access-demo/demo.env -f docker/docker-compose.yml -f docker/mcp-oidc.compose.yml -f docker/file-access-demo.compose.yml logs --tail 80 file-connector file-nfs
```

`workspace mismatch`: 생성된 env로 대시보드를 다시 빌드하고 로그아웃 후 재로그인합니다. `No matching allow policy`: 새 workspace의 DRAFT를 Publish합니다. `User is not provisioned`: 설정 스크립트를 다시 실행하고 connector의 다음 sync를 기다립니다. `source stale`: connector 인증·등록·볼륨 접근을 확인합니다.

작성 후 빌드·컨테이너 실행·로그인·NFS 접속 테스트는 자동으로 실행하지 않았습니다.
