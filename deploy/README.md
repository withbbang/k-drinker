# 집 서버 배포 (Synology NAS)

웹·API·MySQL을 NAS의 Docker Compose로 실행하고, HTTPS는 DSM 역방향 프록시가 받는다. 이미지는 GitHub Actions가 빌드해 GHCR에 올리고, NAS는 받아서 실행만 한다 (`chat-app`, `lottery-picker`와 같은 방식). 같은 NAS에서 `lottery-picker`와 함께 돌리므로 프로젝트 이름·DB 이름·호스트 포트를 겹치지 않게 잡았다.

```
인스타그램(Meta)·휴대폰 ──HTTPS 443──▶ 공유기 ──▶ DSM 역방향 프록시(Let's Encrypt)
                                                    │ http://127.0.0.1:13001
                                                    ▼
                         web (127.0.0.1:13001 → 3000) ──/api/*──▶ api:8080 ──▶ mysql:3306
                                                     (Compose 내부 네트워크, 호스트 포트 없음)
                                                         api ──▶ media-data 볼륨 (/data/media)

master push ──▶ GitHub Actions ──build/push──▶ ghcr.io/withbbang/k-drinker-{api,web}:<SHA>
                      │ SSH (NAS_SSH_PORT)
                      ▼
                NAS: deploy/ 복사 → deploy/.env 에 IMAGE_TAG=<SHA> → compose pull · up -d
```

- 공유기에서 NAS로 넘기는 포트는 **80, 443, 배포용 SSH 포트(`NAS_SSH_PORT`)** 세 개다. 이미 `chat-app`·`lottery-picker`용으로 열려 있으면 그대로 쓴다. 13001, 3306, 8080, DSM 관리 포트(5000/5001)는 인터넷에 열지 않는다.
- web은 NAS의 루프백(`127.0.0.1:13001`)에만 묶인다. api, mysql은 호스트 포트가 아예 없다.
- 인스타그램은 사진을 **Meta 서버가 공개 URL로 직접 가져간다**. 그래서 `/api/v1/media/{token}.jpg`는 로그인 없이 열리고, 그 주소의 호스트가 `PUBLIC_BASE_URL`이다.
- 모든 컨테이너는 `restart: unless-stopped`, `TZ=Asia/Seoul`. web은 api가 healthy가 된 뒤에 뜬다.
- NAS에는 소스 코드가 가지 않는다. `deploy/` 폴더만 복사된다.

| 파일 | 역할 |
|---|---|
| `compose.yml` | mysql:8.4, api, web 구성 (프로젝트 이름 `k-drinker`, DB 이름 `kdrinker`). api·web은 GHCR 이미지 `:${IMAGE_TAG:-latest}`. api는 `media-data` 볼륨을 `/data/media`에 마운트 |
| `compose.build.yml` | 로컬 확인용으로 소스에서 빌드할 때만 겹치는 파일 (8장). NAS에서는 쓰지 않는다 |
| `.env.example` | 키 목록. 실제 값은 서버의 `deploy/.env`(Git 제외) |
| `backup.sh` | 일 1회 `mysqldump` → `backups/kdrinker-*.sql.gz`, 사진 볼륨 → `backups/kdrinker-media-*.tar.gz`. 각각 최근 14개 보관 |
| `restore-check.sh` | DB 덤프를 임시 MySQL에 복구해 운영 DB와 테이블별 행 수 비교 |
| `../.github/workflows/deploy.yml` | 빌드·푸시·NAS 배포 워크플로 |

NAS의 배치 (`/volume1/docker/k-drinker`, 워크플로 변수 `NAS_DEPLOY_DIR`로 바꿀 수 있다):

```
/volume1/docker/k-drinker/
├── deploy/          ← 워크플로가 매번 덮어쓴다 (Git에 있는 파일만)
│   ├── compose.yml, backup.sh, ...
│   └── .env         ← 직접 만든다. 워크플로는 IMAGE_TAG 줄만 고친다
└── backups/         ← backup.sh가 만든다. 워크플로는 건드리지 않는다
```

아래 명령은 NAS에서 이 폴더에 있다고 가정한다. DSM에서는 docker 명령에 `sudo`가 필요하다. 반복해서 쓰니 별칭을 둔다.

```bash
cd /volume1/docker/k-drinker
alias dc='sudo docker compose -f deploy/compose.yml --env-file deploy/.env'
```

---

## 1. 처음 배포

### 1-1. NAS 준비 확인 (chat-app·lottery-picker와 같은 배포 계정이면 이미 되어 있다)

`<배포계정>`은 chat-app의 `NAS_SSH_USER`와 같은 계정이다. 아래 세 가지를 확인만 하고, 안 되어 있는 것만 한다.

1. **SSH 키 로그인**: Mac에서 `ssh -p <NAS_SSH_PORT> <배포계정>@<NAS_HOST>`가 비밀번호 없이 들어가지는지.
2. **docker를 비밀번호 없이 sudo로**: Synology는 `docker.sock`이 `root:root 660`이라 sudo가 필요하다. 확인:
   ```bash
   sudo -n /usr/local/bin/docker ps      # 목록이 나오면 이미 되어 있다
   ```
   `a password is required`가 나오면 (visudo 없음):
   ```bash
   sudo sh -c 'echo "<배포계정> ALL=(root) NOPASSWD: /usr/local/bin/docker" > /etc/sudoers.d/<배포계정> && chmod 440 /etc/sudoers.d/<배포계정>'
   ```
3. **GHCR 로그인**: 워크플로가 처음 만든 패키지는 비공개로 시작한다. 같은 소유자(`withbbang`)의 이미지를 이미 받고 있으면 로그인이 되어 있다. 확인:
   ```bash
   sudo cat /root/.docker/config.json | grep ghcr    # "ghcr.io" 항목이 있으면 된다
   ```
   없으면 GitHub → Settings → Developer settings → Personal access tokens (classic)에서 `read:packages`만 준 토큰을 만들어 `sudo docker login ghcr.io -u withbbang` (비밀번호 자리에 토큰).

### 1-2. 배포 폴더와 `deploy/.env`

폴더는 배포 계정이 쓸 수 있어야 한다 (워크플로가 `deploy/`를 풀고 `.env`의 `IMAGE_TAG`를 고친다).

```bash
# NAS
sudo mkdir -p /volume1/docker/k-drinker/deploy
sudo chown <배포계정> /volume1/docker/k-drinker
sudo chown -R <배포계정> /volume1/docker/k-drinker/deploy
```

`.env`의 틀은 Mac에서 예시 파일을 그대로 보낸다.

```bash
# Mac, 저장소 루트
scp -O -P <NAS_SSH_PORT> deploy/.env.example <배포계정>@<NAS_HOST>:/volume1/docker/k-drinker/deploy/.env
```

```bash
# NAS
cd /volume1/docker/k-drinker
chmod 600 deploy/.env
vi deploy/.env
```

| 키 | 값 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | 새로 만든 긴 비밀번호 (백업·복구에만 씀) |
| `DB_USERNAME` | `kdrinker` 그대로 (root 불가) |
| `DB_PASSWORD` | 새로 만든 긴 비밀번호 |
| `DB_URL` | 비워 둔다 (`jdbc:mysql://mysql:3306/kdrinker`) |
| `TOTP_SECRET` | 1-4에서 채운다. 지금은 비워 둔다 |
| `PUBLIC_BASE_URL` | web의 공개 HTTPS 주소. 예: `https://kdrinker.example.com` (끝 슬래시 없음, 2장의 도메인과 같아야 한다). 비어 있으면 compose와 워크플로가 배포를 막는다 |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` | P2/P3에서 사용. 지금은 비워 둔다 |
| `ANTHROPIC_API_KEY` | 분석(라벨 인식·양조장 조사·캡션 생성)에 필수. 비워 두면 기동은 되지만 모든 게시물이 "분석 서버 응답에 문제가 있었습니다." 사유로 실패한다 |
| `INSTAGRAM_ACCESS_TOKEN`, `INSTAGRAM_USER_ID` | P2/P3에서 사용. 지금은 비워 둔다 |
| `IMAGE_TAG` | 비워 둔다. 워크플로가 배포할 때마다 커밋 SHA를 써 넣는다 |

- 비밀번호는 영문·숫자로만 만든다 (따옴표, `$`, 공백은 Compose 치환과 충돌한다). 예: `openssl rand -hex 24`.
- `MYSQL_*`, `DB_PASSWORD`는 **mysql 볼륨을 처음 만들 때만** 적용된다. 나중에 `.env`만 바꾸면 DB 비밀번호는 그대로라 api가 접속에 실패한다 (9장 참고).
- `SPRING_PROFILES_ACTIVE=prod`, `MEDIA_DIR=/data/media`는 compose에 고정되어 있다. 프로파일이 빠지면 앱이 `local`(H2, 인증 꺼짐)로 뜨므로 지우지 않는다.

### 1-3. GitHub 설정

이 저장소(`withbbang/k-drinker`)의 Settings → Secrets and variables → Actions → **Secrets 탭 → Repository secrets**에 네 개를 등록한다. 값은 chat-app·lottery-picker에 넣은 것과 같다. GitHub은 등록된 시크릿을 다시 보여 주지 않으므로 원래 값(키 파일 등)에서 다시 입력한다.

| 이름 | 값 |
|---|---|
| `NAS_HOST` | NAS 공인 주소 (도메인 또는 IP) |
| `NAS_SSH_PORT` | 배포용 SSH 포트 |
| `NAS_SSH_USER` | `<배포계정>` |
| `NAS_SSH_KEY` | 배포용 개인키 전체 (`-----BEGIN ... END ...-----` 포함) |

```bash
gh secret set NAS_HOST
gh secret set NAS_SSH_PORT
gh secret set NAS_SSH_USER
gh secret set NAS_SSH_KEY < ~/.ssh/<배포용 개인키>
```

- Environment secrets에 넣으면 이 워크플로가 읽지 못한다. 비어 있으면 deploy 잡이 **이름만** 출력하고 멈춘다.
- 배포 폴더를 바꿀 때만 Variables 탭에 `NAS_DEPLOY_DIR`를 둔다 (기본 `/volume1/docker/k-drinker`).
- GHCR 패키지 `k-drinker-api`, `k-drinker-web`은 첫 빌드 뒤 비공개로 생긴다. NAS가 받을 수 있으면(1-1의 3번) 공개로 바꿀 필요 없다.

### 1-4. 첫 실행과 OTP 등록

`TOTP_SECRET`이 비어 있으면 api가 기동하지 않으므로, 워크플로는 `.env`의 `TOTP_SECRET`·`PUBLIC_BASE_URL`이 비어 있으면 **배포 전에 멈춘다**. 첫 실행은 이미지를 GHCR에 올리고 `deploy/`를 NAS에 복사한 뒤 여기서 멈추는 것이 정상이다.

이 배포 구성을 `master`에 처음 푸시하는 순간에도 워크플로가 자동으로 한 번 돈다. 시크릿이나 `.env`가 아직 없으면 그 실행은 해당 확인 단계에서 실패하고 NAS는 건드리지 않으니 무시해도 된다.

1. GitHub → Actions → **deploy** → Run workflow (브랜치 `master`). build 잡 2개가 성공하고, deploy 잡이 `TOTP_SECRET 이 비어 있습니다`로 실패한다.
2. NAS에서 방금 올라간 api 이미지로 일회성 시크릿 생성기(`com.kdrinker.auth.TotpSetup`)를 실행한다. DB에는 접속하지 않는다. `PUBLIC_BASE_URL`이 아직 비어 있어도 `dc run`이 막히므로 먼저 1-2에서 채워 둔다.
   ```bash
   cd /volume1/docker/k-drinker
   dc pull api                 # IMAGE_TAG가 비어 있으면 :latest
   dc run --rm --no-deps --entrypoint java api -cp app.jar com.kdrinker.auth.TotpSetup
   ```
   출력:
   ```
   Picked up JAVA_TOOL_OPTIONS: ...        ← JVM 안내 문구, 무시
   TOTP_SECRET=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
   otpauth://totp/k-drinker?secret=XXXX...&issuer=k-drinker
   ```
3. `TOTP_SECRET=...` 줄의 값을 `deploy/.env`의 `TOTP_SECRET`에 넣는다.
4. OTP 앱(Google Authenticator 등)에 등록한다. **설정 키 직접 입력**: 계정 이름 `k-drinker`, 키는 위 시크릿, 유형 "시간 기준". QR로 하려면 Mac에서 `brew install qrencode` 후 `qrencode -t ansiutf8 'otpauth://...'`. 시크릿을 온라인 QR 생성 사이트에 넣지 않는다.
5. 출력이 남은 터미널은 닫는다. 시크릿은 `deploy/.env`와 OTP 앱에만 있어야 한다.
6. GitHub Actions의 실패한 실행에서 **Re-run failed jobs**. 이번에는 pull → up까지 간다.

- 시크릿을 잃어버렸거나 바꿀 때는 2~5 후 `dc up -d`를 하면 된다. 시크릿이 바뀌면 기동 시 이전 세션과 잠금이 모두 지워진다.

### 1-5. 기동 확인

첫 배포는 HDD에서 mysql 초기화(수 분) → api 기동 → web 순서라 deploy 잡이 몇 분 걸린다. 끝나면 NAS에서:

```bash
dc ps                              # mysql, api가 (healthy), web이 Up
curl -s localhost:13001/api/v1/health    # {"success":true,"data":{"status":"UP"}}
```

---

## 2. HTTPS 연결 (DSM 역방향 프록시)

새 서브도메인을 하나 더 만들어 `127.0.0.1:13001`로 연결한다. 이미 `lottery-picker`용 80/443 포워딩과 DSM 보호(방화벽·2단계 인증 등)가 되어 있으면 포트·보호 작업은 다시 할 필요 없고, 2-1·2-3·2-4만 새로 한다. 처음 NAS를 인터넷에 여는 경우의 포워딩·DSM 보호 절차는 `lottery-picker`의 `deploy/README.md` 2-2, 2-7을 따른다 (80·443·`NAS_SSH_PORT`만 열고, DSM 관리 포트는 닫고, 관리자 2단계 인증·자동 차단·SSH 키 로그인만 허용).

아래에서 `<도메인>`은 `PUBLIC_BASE_URL`의 호스트 부분이다 (예: `kdrinker.example.com`).

### 2-1. DNS

도메인 관리 화면에서 새 서브도메인 A 레코드 `<도메인>` → 집의 고정 공인 IP (기존 서브도메인과 같은 IP). 확인:

```bash
dig +short <도메인>     # 공인 IP가 나와야 한다
```

### 2-2. 공유기 포트 포워딩

TCP 80 → NAS:80 (Let's Encrypt 발급·갱신), TCP 443 → NAS:443, TCP `NAS_SSH_PORT` → NAS:`NAS_SSH_PORT`. 기존 서비스가 이미 쓰고 있으면 추가할 것이 없다.

### 2-3. 인증서

DSM 제어판 → 보안 → 인증서 → 추가 → 새 인증서 추가 → **Let's Encrypt에서 인증서 얻기**

- 도메인 이름 `<도메인>`, 이메일 입력. 기본 인증서로 지정하지 않아도 된다.
- 발급되면 DSM이 만료 전에 자동 갱신한다 (그래서 80 포워딩을 유지한다).

### 2-4. 역방향 프록시 규칙 (새 서브도메인 → 127.0.0.1:13001)

DSM 제어판 → 로그인 포털 → 고급 → 역방향 프록시 → 생성

| 항목 | 값 |
|---|---|
| 설명 | `k-drinker` |
| 소스 프로토콜 / 호스트 이름 / 포트 | HTTPS / `<도메인>` / 443 |
| HSTS 활성화 | 켜도 된다 (선택) |
| 대상 프로토콜 / 호스트 이름 / 포트 | HTTP / `127.0.0.1` / `13001` |

- **사용자 지정 머리글은 추가하지 않는다.** 이 앱은 WebSocket을 쓰지 않는다.
- **업로드 크기**: 사진은 한 번에 최대 210MB(multipart)가 이 프록시를 지난다. DSM nginx 역방향 프록시는 기본 요청 본문 제한이 없지만, 업로드가 413으로 막히면 이 규칙의 사용자 지정 머리글이 아니라 DSM 쪽 `client_max_body_size`(DSM 버전별로 다름)를 확인한다.

그다음 제어판 → 보안 → 인증서 → **설정**에서 `<도메인>` 역방향 프록시 항목의 인증서를 2-3에서 발급한 것으로 바꾼다.

### 2-5. 세션 쿠키와 프록시

- 로그인 쿠키 `KD_SESSION`은 `Secure; HttpOnly; SameSite=Strict`로 내려간다. `Secure`는 설정(`app.auth.cookie-secure: true`)으로 항상 켜져 있어, 프록시 뒤의 api가 평문 HTTP를 받아도 브라우저는 HTTPS 주소에서 받으므로 정상 저장한다.
- 반대로 `http://`로 접속하면 쿠키가 저장·전송되지 않아 로그인이 유지되지 않는다. 항상 `https://<도메인>`으로 들어간다.

### 2-6. 확인

- 휴대폰 **와이파이를 끄고** LTE/5G로 `https://<도메인>/api/v1/health`가 `{"success":true,...}`로 열리는지 확인한다 (자물쇠 표시). 로그인 화면도 열려야 한다.
- 바깥 망에서 `http://<공인 IP>:13001`, `:3306`, `:8080`, `:5000`, `:5001`이 **열리지 않아야** 한다.

### 2-7. 외부 이미지 URL 확인 (인스타그램이 사진을 가져갈 수 있는지)

인스타그램 게시는 Meta 서버가 `PUBLIC_BASE_URL` 기준의 공개 이미지 URL에 접속해 사진을 가져가는 방식이다. 로그인 없이, 외부 망에서, HTTPS 인증서가 유효한 상태로 열려야 한다.

1. 로그인한 상태에서 사진을 올린다 (웹 화면 또는 `POST /api/v1/posts`). 응답/게시물 정보에서 렌더링된 이미지 토큰을 확인한다. NAS에서는 `dc exec api ls /data/media/rendered`로도 파일명(`{token}.jpg`)을 볼 수 있다.
2. **와이파이를 끈 휴대폰(LTE/5G)** 브라우저에서 `https://<도메인>/api/v1/media/{token}.jpg`를 연다. 사진이 보이면 된다 (로그인 쿠키가 없는 새 시크릿 탭이면 더 확실하다).
3. 같은 주소가 `PUBLIC_BASE_URL`로 만든 URL과 일치하는지 본다. 이미지가 4:5로 변환된 JPEG이고 `Content-Type: image/jpeg`여야 한다.
4. 안 열리면: 공유기 443 포워딩, 인증서(만료·도메인 불일치), 역방향 프록시 대상(`127.0.0.1:13001`), `PUBLIC_BASE_URL` 오타(끝 슬래시·`http`)를 순서대로 본다. 사진이 `/data/media`에 없으면 api 로그(`dc logs --tail 100 api`)를 본다.

---

## 3. 다시 배포 (업데이트)

평소 흐름(피처 → `dev` → `master` 병합, `git push origin dev master`)대로 `master`에 푸시하면 끝이다. `apps/**`, `deploy/**`, 워크플로 파일이 바뀐 푸시만 배포한다. 그 안의 `.md`만 바뀐 푸시는 배포하지 않는다. 수동 실행(Run workflow)은 `master`에서만 돈다.

- 진행은 GitHub → Actions → deploy에서 본다. 끝나면 NAS에서 `dc ps`로 확인.
- 다시 돌리고 싶을 때: Actions → deploy → **Run workflow** (`master`).
- DB 스키마는 api 기동 시 Flyway가 올린다. 큰 변경 전에는 NAS에서 `sudo bash deploy/backup.sh`를 먼저 돌린다.

### 되돌리기

배포된 커밋 SHA는 `deploy/.env`의 `IMAGE_TAG`에 있고, 이전 SHA는 GitHub Actions 실행 목록이나 `git log master`에서 찾는다.

```bash
vi deploy/.env             # IMAGE_TAG=<되돌릴 커밋 SHA 전체>
dc up -d                   # 없는 이미지는 받아 와서 재생성
```

- 다음 `master` 배포가 `IMAGE_TAG`를 다시 덮어쓴다.
- Flyway 마이그레이션은 앞으로만 간다. 스키마를 바꾼 커밋보다 이전으로 되돌리면 api가 기동하지 않을 수 있다. 그때는 4장의 덤프로 DB를 되돌린다.

### 오래된 이미지 정리

```bash
sudo docker images 'ghcr.io/withbbang/k-drinker-*'
sudo docker rmi ghcr.io/withbbang/k-drinker-api:<오래된 SHA> ghcr.io/withbbang/k-drinker-web:<오래된 SHA>
```

---

## 4. 백업

`deploy/backup.sh`, `deploy/restore-check.sh`는 따로 보낼 필요 없다. 배포 워크플로가 `deploy/`를 복사할 때 같이 NAS에 들어간다. `backups/`는 워크플로가 건드리지 않는다.

`deploy/backup.sh`가 하는 일:

- DB: `docker compose exec`로 mysql 컨테이너 안에서 `mysqldump --single-transaction` → gzip → `backups/kdrinker-YYYYMMDD-HHMMSS.sql.gz`. 비밀번호는 명령행에 싣지 않는다.
- **사진**: `media-data` 볼륨의 `original/`(원본)·`rendered/`(4:5 변환본)을 api 컨테이너 안에서 tar로 묶어 `backups/kdrinker-media-YYYYMMDD-HHMMSS.tar.gz`로 저장한다. DB 덤프와 같은 시각 이름이라 짝을 찾기 쉽다.
- DB 덤프와 사진 tar를 **각각 최근 14개**만 남기고 오래된 것은 지운다. 끝까지 기록된 파일만 남긴다(DB는 `-- Dump completed`, tar는 목록 읽기 검증). 실패하면 0이 아닌 코드로 끝나고 반쯤 쓴 파일은 지운다.
- 폴더는 root 전용(700), 파일은 600이다. 보거나 읽을 때는 `sudo`를 붙인다.
- api 컨테이너가 떠 있어야 한다 (사진 tar를 그 안에서 만든다).

수동 실행:

```bash
sudo bash deploy/backup.sh
sudo ls -l backups/
```

### 매일 자동 실행 (DSM 작업 스케줄러)

DSM 제어판 → 작업 스케줄러 → 생성 → 예약된 작업 → 사용자 정의 스크립트

| 탭 | 값 |
|---|---|
| 일반 | 작업 `k-drinker backup`, 사용자 **root** |
| 일정 | 매일, 04:45 (lottery-picker 04:30 백업과 겹치지 않게) |
| 작업 설정 | 사용자 정의 스크립트: `bash /volume1/docker/k-drinker/deploy/backup.sh` |

- 보관 위치·개수는 환경변수로 바꿀 수 있다: `BACKUP_DIR=/volume1/backup/k-drinker KEEP=30 bash .../backup.sh`.
- 사진은 다시 만들 수 없는 원본이다. `backups/`를 Hyper Backup이나 다른 기기로 한 번 더 복사하는 것을 권한다.

### 복구 확인 (DB를 빈 DB에 복구해 행 수 비교)

```bash
sudo bash deploy/backup.sh
sudo bash deploy/restore-check.sh            # 인자가 없으면 backups/의 가장 최근 DB 덤프
```

특정 덤프를 확인하려면 `sudo ls backups/`로 이름을 본 뒤 `sudo bash deploy/restore-check.sh backups/kdrinker-<시각>.sql.gz`. 덤프와 확인 사이에 게시물이 올라오면 그 테이블만 다를 수 있다. 다르면 바로 다시 `backup.sh` → `restore-check.sh`.

### 실제로 운영 DB·사진을 되돌릴 때

```bash
sudo bash deploy/backup.sh                       # 현재 상태도 남겨 둔다
sudo ls backups/                                 # 되돌릴 파일 이름 확인
sudo -v
dc stop web
# DB
sudo gzip -dc backups/kdrinker-<복구할 시각>.sql.gz \
  | dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"'
# 사진: api 컨테이너 안에서 풀어 app 사용자 소유로 들어가게 한다
sudo cat backups/kdrinker-media-<복구할 시각>.tar.gz | dc exec -T api tar -xzf - -C /data/media
dc start web
```

- DB 덤프에는 테이블별 `DROP TABLE IF EXISTS`가 들어 있어 같은 DB에 그대로 덮어쓴다.
- 사진 tar는 기존 파일 위에 덮어 풀기만 한다(지우지 않는다). DB와 사진은 같은 시각의 파일을 짝으로 쓴다.

---

## 5. 재부팅과 1주 운영

- DSM 패키지 센터에서 Container Manager가 실행 중이면, 재부팅 후 Docker가 올라오면서 `unless-stopped` 컨테이너가 자동으로 다시 뜬다. `dc stop`으로 멈춘 컨테이너는 재부팅 후에도 멈춘 상태로 남는다.
- 재부팅 때는 Docker가 컨테이너를 한꺼번에 다시 띄우므로 `depends_on`의 `service_healthy`가 지켜지지 않는다. api가 mysql보다 먼저 준비되면 DB 접속에 실패해 종료하고 재시작 정책으로 다시 뜬다. **재부팅 직후 api가 한두 번 재시작되는 것은 정상이다.** 몇 분 뒤 `dc ps`에서 api가 `(healthy)`면 된다. 계속 재시작되면 `dc logs --tail 100 api`.
- 재부팅 후 확인: `dc ps`, `curl -s localhost:13001/api/v1/health`, 휴대폰에서 HTTPS 접속.

---

## 6. 자주 쓰는 명령

```bash
dc ps                      # 상태 (health 포함)
dc logs -f --tail 200 api  # api 로그
dc restart api             # .env를 바꾼 뒤에는 restart 말고 `dc up -d` (환경변수는 컨테이너 재생성 때 반영)
dc down                    # 정지·삭제 (볼륨은 남는다)
sudo docker stats --no-stream   # 메모리 사용량
```

**`dc down -v`는 DB 볼륨과 사진 볼륨(`media-data`)까지 지운다. 운영에서는 쓰지 않는다.**

---

## 7. 메모리

NAS 여유 메모리(약 4 GiB, lottery-picker와 공유)를 고려해 상한을 두었다.

| 서비스 | 설정 | 로컬 측정 |
|---|---|---|
| mysql | `innodb-buffer-pool-size=128M`, `performance-schema=OFF`, `mem_limit 768m` | 약 275 MiB |
| api | `-Xmx512m -XX:+UseSerialGC`, `mem_limit 768m` | 기동 직후 약 330 MiB, 190MB 업로드 처리 중 약 565 MiB |
| web | `mem_limit 768m` | 평소 약 60 MiB, 190MB 업로드 중 약 270 MiB |

- web이 평소보다 큰 상한을 가진 이유: `/api/*` 프록시(Next rewrites)가 업로드 본문을 메모리에 버퍼링한다. `apps/web/next.config.ts`의 `experimental.proxyClientMaxBodySize: "250mb"`가 이 한도다 (기본 10MB에서 잘려 업로드가 실패한다).
- 세 서비스가 동시에 상한까지 쓰는 일은 드물지만, lottery-picker와 합쳐 NAS 메모리가 모자라면 `dc stats`로 확인한다.

---

## 8. 로컬에서 같은 구성 확인 (개발용)

Mac에서 확인할 때는 프로젝트 이름과 `.env`를 따로 쓴다. GHCR 이미지 대신 소스에서 빌드하도록 `compose.build.yml`을 겹친다.

```bash
# 저장소 밖의 임시 파일에 키를 채운다 (더미 값):
#   MYSQL_ROOT_PASSWORD, DB_USERNAME=kdrinker, DB_PASSWORD, PUBLIC_BASE_URL=https://kdrinker.example.test,
#   TOTP_SECRET=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ (RFC 6238 테스트 값)
alias kdc='docker compose -p kd-deploy-test -f deploy/compose.yml -f deploy/compose.build.yml --env-file <임시 env>'
kdc up -d --build
curl -s localhost:13001/api/v1/health
kdc down -v --rmi local
```

`backup.sh`, `restore-check.sh`도 `ENV_FILE=<임시 env> COMPOSE_PROJECT=kd-deploy-test BACKUP_DIR=<임시 폴더>`로 같은 스택에 돌릴 수 있다.

---

## 9. DB 비밀번호를 바꿀 때

`.env`만 바꾸면 이미 만들어진 DB 계정은 그대로다. DB에서 먼저 바꾼 뒤 `.env`를 맞춘다.

```bash
dc exec mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
# mysql> ALTER USER 'kdrinker'@'%' IDENTIFIED BY '<새 비밀번호>';
# root는: ALTER USER 'root'@'%' IDENTIFIED BY '...'; ALTER USER 'root'@'localhost' IDENTIFIED BY '...';
sudo vi deploy/.env        # DB_PASSWORD / MYSQL_ROOT_PASSWORD 수정
dc up -d                   # 바뀐 환경변수로 컨테이너 재생성
```
