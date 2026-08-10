# AI 기반 품질 데이터 활용 산업용 자재·가공업체 매칭 및 조달 플랫폼

SKALA `Agile 방법론 및 MSA 개발` 실습 팀 프로젝트. 교수님이 제공한 온라인 교육 플랫폼 MSA 템플릿을 **산업 조달 플랫폼 도메인으로 치환**해 개발한다.

> The following practice code is intended for educational purposes only. For contact: audit@korea.ac.kr, Sungryel Lim Ph.D
>
> This practice code is not a completed commercial version but has been developed for educational purposes; supplementation is required depending on the deployment objective for use as a commercial service.

## 먼저 읽을 문서

| 문서 | 내용 |
| --- | --- |
| [`AGENTS.md`](AGENTS.md) = [`CLAUDE.md`](CLAUDE.md) | 작업 규칙. **수정 금지 구역이 여기 있다** |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | 처음 합류할 때 한 번 읽는 최소한 |
| [`docs/product/overview.md`](docs/product/overview.md) | 무엇을 만드는가 — Pain Point, AI 역할, Sprint 구분 |
| [`docs/spec/api-spec.md`](docs/spec/api-spec.md) | 무엇을 호출하는가 |
| [`docs/constraints.md`](docs/constraints.md) | 무엇을 바꿀 수 없는가 |

**기획하다 막히면 `docs/constraints.md` 를 먼저 본다.** 대부분의 막힘은 거기 적힌 다섯 개 제약 중 하나에 부딪힌 것이다.

## 처음 한 번만

```bash
./scripts/setup-git-hooks
```

`main` 에 직접 push하는 것을 막는 hook과 커밋 템플릿을 활성화한다. **실행하지 않으면 동작하지 않는다.**

## 실행

### 1. 인프라 이미지 로드

`infra-images.tar` 는 343MB로 GitHub 파일 크기 한도를 넘어 **저장소에 없다.** 강의 자료 배포 경로에서 직접 받아 저장소 루트에 둔다.

```bash
docker load -i infra-images.tar
docker images    # msa-lecture/auth-server:1.0, msa-lecture/api-gateway:1.0 확인
```

### 2. 백엔드 기동

```bash
docker compose build
docker compose up -d
```

`--no-cache` 를 붙이지 않는다. 매번 의존성을 전부 다시 받아 Maven Central이 공용 IP를 `429` 로 차단한다. 이유는 [`docs/constraints.md`](docs/constraints.md)의 `Maven Central 429` 를 본다.

기동 순서는 `depends_on` 이 처리한다.

```text
MariaDB / Kafka → Eureka → Auth Server → API Gateway + 4개 서비스 → Recommend Service
```

`auth-server` 의 `start_period` 가 120초라 **첫 기동에 2~3분 걸린다.** 로그가 조용해도 정상이다.

### 3. 기동 확인

```bash
docker compose ps
docker compose logs -f                    # 전체
docker compose logs -f enrollment-service # 개별
```

<http://localhost:8761> 에서 7개 서비스 등록을 확인한다 — `API-GATEWAY`, `AUTH-SERVER`, `USER-SERVICE`, `COURSE-SERVICE`, `ENROLLMENT-SERVICE`, `PAYMENT-SERVICE`, `RECOMMEND-SERVICE`.

로그에서 오류를 찾을 때는 문자열 `Error` 가 아니라 **로그 레벨 `ERROR`** 로 필터한다. `ErrorHandlingDeserializer` 같은 클래스 이름에 걸려 오탐이 쏟아진다. 헷갈리기 쉬운 항목은 [`AGENTS.md`](AGENTS.md)의 `알려진 오탐` 표에 정리해 두었다.

### 4. 프론트엔드

```bash
cd vue-frontend
npm install
npm run dev
```

<http://localhost:3000> 으로 접속한다.

**포트 3000을 반드시 유지한다.** auth-server에 `localhost:3000/callback` 이 하드코딩되어 있어 다른 포트에서는 로그인이 400으로 거부된다. 다른 프로젝트가 3000을 쓰고 있으면 그 프로젝트를 옮긴다.

`vite.config.js` 에 `strictPort: true` 가 있어 3000이 점유되어 있으면 기동 자체가 실패한다.

```bash
lsof -nP -iTCP:3000 -sTCP:LISTEN   # 점유 프로세스 확인
```

### 시드 계정

`init-db/01_init.sql` 이 두 계정을 만든다. 비밀번호는 해시로 저장되어 있어 강의 자료의 값을 쓴다.

| 이메일 | 역할 | 조달 플랫폼에서 |
| --- | --- | --- |
| `instructor@lecture.com` | `INSTRUCTOR` | 공급기업 |
| `student@lecture.com` | `STUDENT` | 구매기업 |

`courses` 테이블은 비어 있다. **공급기업 계정으로 가공 서비스를 먼저 등록해야** 발주 흐름을 볼 수 있다.

## 개발

### 변경한 서비스만 다시 올린다

```bash
docker compose up -d --build course-service
```

전체 재빌드는 시간만 버린다. 프론트엔드는 dev 서버가 저장 시 자동 반영한다.

### 스키마를 바꿨을 때

```bash
docker compose down -v && docker compose up -d
```

`-v` 는 **DB 볼륨을 지운다.** `init-db/01_init.sql` 을 다시 적용해야 할 때만 쓴다.

### 서비스 구성

| 서비스 | 포트 | 역할 | 수정 |
| --- | --- | --- | --- |
| mariadb | 3379 → 3306 | 데이터 저장 | `init-db/` 만 |
| kafka | 9092 | 이벤트 버스 | 안 함 |
| eureka-server | 8761 | 서비스 등록·탐색 | 안 함 |
| auth-server | 9000 | 인증·토큰 발급 | **소스 없음** |
| api-gateway | 8080 | 단일 진입점·라우팅·인증 | **소스 없음** |
| user-service | 8081 | 기업 회원 | 안 함 |
| course-service | 8082 | 소재·가공 서비스 | 함 |
| enrollment-service | 8083 | 견적·발주·주문 | 함 |
| payment-service | 8084 | 주문 결제 | Sprint2 |
| recommend-service | 8085 | 공급업체 추천 (FastAPI) | Sprint2 |
| vue-frontend | 3000 | 화면 | 함 |

### Swagger

문서는 개별 포트로, **실제 호출은 게이트웨이(8080)** 로 한다. 게이트웨이 경유로는 문서를 볼 수 없다.

| 서비스 | 경로 |
| --- | --- |
| user / course / enrollment / payment | `:8081~8084/swagger-ui.html` |
| recommend | `:8085/docs` |

springdoc 경로가 커스터마이즈되어 있어 OpenAPI JSON은 `/v3/api-docs` 가 아니라 **`/api-docs`** 다.

## 전체 종료

```bash
docker compose down       # 컨테이너만
docker compose down -v    # DB 볼륨까지 (데이터 사라짐)
```

## 커밋된 `.env` 에 대해

`vue-frontend/.env` 와 `recommend-service/.env` 는 커밋되어 있다. 담긴 값이 **강의에서 고정한 공개값**이고, auth-server가 클라이언트 정보를 하드코딩하고 있어 팀원 전원이 동일한 값을 써야 로그인이 동작하기 때문이다.

**실제 비밀값은 다르게 다룬다.** Sprint2에서 LLM API 키를 쓰게 되면 `docker-compose.yml` 의 `environment` 로 주입하거나 gitignore된 파일에 두고, **어떤 경우에도 커밋하지 않는다.**
