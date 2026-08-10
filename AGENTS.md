# 작업 규칙

이 문서는 이 저장소에서 작업하는 사람과 자동화 에이전트가 따라야 할 규칙을 정의한다.

이 저장소는 SKALA `Agile 방법론 및 MSA 개발` 실습의 팀 프로젝트다. 교수님이 제공한 온라인 교육 플랫폼 MSA 템플릿을 **산업 조달 플랫폼 도메인으로 치환**해 개발한다. 템플릿을 처음부터 다시 만드는 것이 목적이 아니다.

## 이 문서는 두 이름으로 존재한다

[`AGENTS.md`](AGENTS.md)와 [`CLAUDE.md`](CLAUDE.md)는 **같은 문서다.** 내용이 한 글자도 다르지 않아야 한다.

이름이 둘인 이유는 도구마다 읽는 파일이 다르기 때문이다. 한쪽에만 규칙을 두면 다른 도구로 작업하는 사람은 그 규칙을 보지 못한다.

### 두 파일을 갱신하는 규칙

- **한쪽을 고치면 다른 쪽도 같은 PR에서 똑같이 고친다.** 한쪽만 바꾼 상태로 두지 않는다.
- 두 파일이 다르면 **어느 쪽이 맞는지 임의로 정하지 않고 묻는다.** 물을 때는 양쪽의 현재 내용을 원문으로 인용한다.

**이 규칙은 CI가 강제한다.** `Checks` 워크플로의 `Rules — AGENTS.md and CLAUDE.md must be identical` 단계가 두 파일을 `diff`로 비교하고 다르면 실패한다.

검사를 고치거나 없애려면 이 절도 함께 고친다. 검사만 지우면 규칙이 문서에만 남고, 규칙만 지우면 검사가 이유 없이 PR을 막는다.

## 수정 금지 구역

**이것을 먼저 읽는다.** 아래 항목은 취향이나 합의의 문제가 아니라 **바꾸면 즉시 깨지는 것**들이다. 전부 기동 중인 컨테이너와 이미지 내부를 직접 확인해 검증했다. 근거는 [`docs/constraints.md`](docs/constraints.md)에 있다.

| 대상 | 상태 | 어기면 |
| --- | --- | --- |
| `eureka-server` / `auth-server` / `api-gateway` | **소스가 없다.** `infra-images.tar`의 빌드된 이미지뿐 | 수정할 방법 자체가 없다 |
| API 경로 `/api/users` `/api/courses` `/api/enrollments` `/api/payments` `/api/recommend` | 게이트웨이 라우팅에 고정. catch-all 없음 | 프론트에서 즉시 404 |
| 프론트엔드 포트 `3000` | auth-server에 `localhost:3000/callback` 하드코딩 | 로그인이 400으로 거부 |
| 사용자 역할 `STUDENT` / `INSTRUCTOR` 2종 | auth-server가 JWT role 클레임 발급 | 제3의 역할을 만들 수 없다 |
| `enrollments` 유일 제약 `UNIQUE (user_id, course_id)` | init-db와 엔티티 양쪽에 있음 | 같은 사용자가 같은 항목을 두 번 신청하면 DB 에러 |

**도메인을 치환할 때 API 경로를 함께 바꾸지 않는다.** 화면에 보이는 말과 DB 컬럼 의미만 바꾼다. `/api/courses`를 `/api/products`로 바꾸는 것이 이 저장소에서 가장 흔한 자살골이다.

수정 대상은 `course-service`, `enrollment-service`, `payment-service`, `recommend-service`, `vue-frontend`, `init-db` 여섯 개다.

## 실습 범위 원칙

실습 가이드가 명시한 원칙을 그대로 따른다.

> 제공된 백엔드 코드를 한 줄씩 읽으며 이해하려는 시도는 이번 실습 범위를 벗어난 것이며, 이해가 안 되는 것이 정상이다.

따라서 다음을 지킨다.

- **인프라의 내부 동작을 설명하려 들지 않는다.** Eureka 등록 방식, JWK 서명 검증, Kafka Producer/Consumer 코드는 "몰라도 되는 것"으로 분류된 항목이다. 질문이 그쪽으로 향하면 범위를 벗어났다고 알린다.
- **API는 Swagger로 확인한다.** 컨트롤러 소스를 통째로 읽는 대신 Method·경로·Request·Response를 확인하고 그대로 호출한다. 접속 경로는 [`docs/spec/api-spec.md`](docs/spec/api-spec.md)에 있다.
- 필요한 것보다 넓게 읽지 않는다. 한 화면을 만들려면 그 화면이 호출하는 엔드포인트만 알면 된다.

## 명세와 코드의 사슬

**기준이 되는 문서 하나를 두지 않는다.** 이어진 사슬을 맞춰 나간다.

```text
docs/product/overview.md      이해관계자의 Pain Point와 AI 솔루션이 무엇인가
       ↕
vue-frontend/src              화면이 무엇을 하고 무엇을 요청하는가
       ↕
docs/spec/api-spec.md         API에서 무엇을 주고받는가
       ↕
각 서비스 src                 그 요청을 어떻게 처리하는가
       ↕
init-db/01_init.sql           그 데이터를 어떻게 저장하는가
```

**붙어 있는 두 칸은 항상 같은 것을 말해야 한다.** 한 칸을 바꾸면 양옆을 확인한다. 떨어진 칸끼리는 직접 비교하지 않는다 — 화면 코드와 스키마를 바로 맞추지 않고 사이의 API 명세를 거친다. 그래야 어긋난 지점이 한 곳으로 좁혀진다.

**명세끼리 직접 대조하지 않는다.** 두 명세만 놓고 보면 파생 값을 빠진 것으로 오해한다. 화면이 "예상 총액"을 보여주는데 API가 단가와 수량만 주는 것은 정상이다. **계산해서 만드는 값인지 읽어오는 값인지는 코드에만 있다.**

### 사슬에는 방향이 없다

기획이 코드를 낳는 것도 아니고 코드가 기획을 정하는 것도 아니다. **한 번에 전부 맞추려 하지 않는다.** 작업하다 어긋난 곳을 발견하면 그때 맞추고, 지금 건드리지 않는 칸의 불일치는 기록만 하고 넘어간다.

### 어긋나면 반드시 묻는다

붙어 있는 두 칸이 다른 것을 말하고 있으면 **어느 쪽이 사실인지 임의로 정하지 않는다.** 기획이 의도이고 코드가 뒤처진 것인지, 코드가 현실이고 기획이 낡은 것인지는 그때그때 다르다. 한쪽을 조용히 다른 쪽에 맞추면 결정이 기록되지 않고, 다음 사람은 그것이 합의된 것인지 누군가 임의로 정한 것인지 알 수 없다.

물을 때는 다음을 갖춘다.

- 사슬의 **어느 두 칸**이 어긋났는지
- **양쪽의 현재 상태**를 원문으로 인용해서. 요약하지 않는다
- 어느 쪽에 맞추면 각각 무엇을 고쳐야 하는지

**즉시 물을 수 없으면** [`docs/constraints.md`](docs/constraints.md)의 미결 사항에 양쪽 원문과 함께 기록하고 넘어간다. 조용히 지나가지 않는다.

### 아직 구현되지 않은 것은 급한 것이 아니다

| | 급한 것인가 |
| --- | --- |
| 기획안과 API 명세가 서로 다른 것을 말한다 | **그렇다.** 어느 쪽으로 만들지 정해지지 않았다 |
| 코드가 API 명세와 다르게 동작한다 | **그렇다.** 지금 잘못 돌고 있다 |
| 기획안에 있고 코드에 없다 | **아니다.** 아직 안 만든 것이다 |

미구현 항목은 남은 작업 목록이지 결함이 아니다. 이것을 급한 것으로 올리면 **진짜 급한 것이 묻힌다.**

## Sprint 규칙

Agile 실습이므로 Sprint 경계를 지키는 것 자체가 평가 대상이다.

- **Sprint1은 한 흐름을 끝까지 동작시키는 것이다.** 여러 서비스를 조금씩 건드리지 않는다. 등록 → 조회 → 신청 → 결제 → 확정이 끊김 없이 이어지는 것이 완료 기준이다.
- **Sprint1에서 손대지 않기로 한 것을 미리 건드리지 않는다.** `payment-service`, Kafka, `recommend-service`는 Sprint1에서 원본 그대로 통과시킨다.
- Sprint 범위를 넘는 작업이 필요해 보이면 **범위를 넓히지 않고 묻는다.** 다음 Sprint 백로그로 옮기는 것이 기본이다.
- Sprint 경계를 바꿨으면 [`docs/product/overview.md`](docs/product/overview.md)의 Sprint 구분도 같은 PR에서 갱신한다.

## 작업 원칙

- 저장소에 없는 제품 요구사항이나 기술 스택을 임의로 확정하지 않는다.
- 변경 범위를 작게 유지하고, 서로 다른 목적의 변경은 별도 브랜치와 PR로 분리한다.
- 기존 사용자 변경사항을 덮어쓰거나 삭제하지 않는다.
- 비밀키, 토큰, 비밀번호, 개인 식별 정보를 커밋하지 않는다. **LLM API 키는 어떤 경우에도 커밋하지 않는다** — `docker-compose.yml`의 `environment`로 주입하거나 gitignore된 파일에 둔다.
- 변경한 서비스만 다시 올린다. 전체 `--no-cache` 재빌드는 시간만 버린다.
- 자바를 수정하지 않고 프론트엔드만으로 달성할 수 있으면 그 방법을 먼저 검토한다. 이유는 아래 `백엔드 최소 수정` 절에 있다.

### 백엔드 최소 수정

이 저장소는 **자바 수정을 최소화하는 방향**을 기본값으로 한다. 두 가지 이유가 있다.

첫째, 실습 가이드가 "이미 만들어진 MSA 구조를 가져다 쓰는 능력"을 목표로 제시한다. 둘째, 자바를 고치면 Gradle 재빌드가 필요하고, 같은 강의장 네트워크에서 여러 명이 동시에 빌드하면 Maven Central이 공용 IP를 `429 Too Many Requests`로 차단한다.

도메인 치환은 대부분 프론트엔드 라벨 맵 교체로 끝난다. 백엔드 enum 값은 **의미 없는 슬롯**으로 취급하고 화면 라벨만 바꾼다.

| 파일 | 정의하는 것 |
| --- | --- |
| `vue-frontend/src/store/course.js` | 필터 목록, 백엔드 enum → 화면 라벨 변환, 카테고리별 썸네일 |
| `vue-frontend/src/components/CourseCard.vue` | 배지 색상과 썸네일. 키가 **화면 라벨**이다 |
| `vue-frontend/src/views/CourseCreateView.vue` | 등록 드롭다운. `label`은 화면 문구, `value`는 **백엔드 enum이므로 바꾸지 않는다** |
| `vue-frontend/src/views/EnrollmentView.vue` | 상태 라벨 |

네 곳의 라벨이 서로 어긋나면 배지가 회색으로 떨어지거나 영문 enum이 화면에 노출된다. **한 곳을 바꾸면 나머지 세 곳을 함께 확인한다.**

엔티티에 없는 필드가 필요하면 컬럼을 추가하기 전에 **`description` 자유 텍스트에 담고 프론트엔드에서 파싱하는 방법**을 먼저 검토한다. `description`은 `TEXT`라 길이 제약이 사실상 없다.

## Git 브랜치 규칙

- `main`은 동작하는 상태를 유지하는 보호 브랜치다.
- 저장소 초기화 이후 `main`에 직접 커밋하거나 push하지 않는다.
- 모든 변경은 최신 `main`에서 만든 짧은 수명의 작업 브랜치에서 진행한다.
- 장기 유지하는 `develop`, `frontend`, `backend` 브랜치는 만들지 않는다.
- 브랜치 이름은 영문 소문자와 하이픈을 사용한다.

허용하는 브랜치 접두사:

- `feat/<name>`: 기능
- `fix/<name>`: 버그 수정
- `refactor/<name>`: 동작 변경 없는 구조 개선
- `docs/<name>`: 문서
- `test/<name>`: 테스트
- `chore/<name>`: 설정 및 유지보수
- `ci/<name>`: CI/CD
- `hotfix/<name>`: 긴급 수정

예: `feat/supplier-catalog-labels`, `fix/enrollment-amount`, `docs/api-spec`

저장소를 clone한 직후 다음 명령으로 공유 Git hook을 활성화한다.

```bash
./scripts/setup-git-hooks
```

**실행하지 않으면 hook이 동작하지 않는다.** 이 hook이 실수로 `main`에 직접 push하는 것을 막는다.

## 커밋 규칙

- Conventional Commits 형식을 사용한다: `<type>(<optional-scope>): <summary>`.
- 제목은 명령형으로 간결하게 쓰고 마침표를 붙이지 않는다.
- 하나의 커밋에는 하나의 논리적 변경만 포함한다.
- 관련 없는 파일을 `git add -A`로 함께 stage하지 않는다.

허용하는 커밋 타입: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `ci`, `build`, `perf`, `revert`

스코프는 서비스 디렉터리 이름을 쓴다: `web`, `course`, `enrollment`, `payment`, `recommend`, `db`, `infra`

예:

```text
feat(web): map category labels to supplier domain
fix(enrollment): use catalog unit price instead of fixed amount
docs: add verified constraints
chore: configure repository
```

## Pull Request 규칙

- 모든 변경은 PR을 통해 `main`에 반영한다.
- PR에는 목적, 주요 변경, 검증 방법, 영향 범위를 기록한다.
- 프론트엔드 변경과 백엔드 변경은 가능한 한 별도 PR로 분리한다.
- 모든 리뷰 대화를 해결한 후 merge한다.
- `PR Policy` 검사가 실패한 PR은 merge하지 않는다.
- merge는 Squash merge만 사용하고 merge 후 작업 브랜치를 삭제한다.

### PR 본문 작성

merge는 Squash merge만 사용하므로 **작업 브랜치의 개별 커밋 메시지는 `main`에 남지 않는다.** PR 본문이 squash 커밋 메시지가 되며, 이것이 `main`에 남는 유일한 기록이다.

**다음은 생략하지 않고 반드시 PR 본문에 남긴다.**

- 시도했다가 되돌린 접근과 되돌린 이유
- 작업 중 발견해서 고친 결함. 무엇이 잘못됐고, 어떻게 드러났고, 왜 그렇게 됐는지를 함께 적는다
- 검증하지 못한 항목과 그 이유

**실패와 실수를 지우지 않는다.** 성공한 결과만 남은 기록은 왜 그 결과에 도달했는지 설명하지 못한다. 되돌린 접근을 적어두지 않으면 다음 사람이 같은 것을 다시 시도한다.

이 기록은 발표 회고의 재료이기도 하다. Sprint Retrospective에서 "무엇이 잘 안 됐는가"를 말해야 하는데, 그때 PR 본문이 유일한 근거가 된다.

### 승인 규칙

- 리뷰 가능한 팀원이 있으면 최소 1명의 승인을 받는다.
- 승인은 마지막으로 변경을 push한 사람 외의 팀원에게 받는다.
- 리뷰어를 구할 수 없으면 작성자 자체 검증으로 대신하고, 그 사유를 PR에 남긴 뒤 merge한다.
- Sprint 시간 내에 리뷰가 막히면 merge를 기다리며 멈추지 않는다. 사유를 남기고 진행한 뒤 회고에서 다룬다.

## 안전 규칙

- force push, 커밋 이력 재작성, 보호 브랜치 삭제를 하지 않는다.
- `git reset --hard`, 대규모 파일 삭제 등 복구가 어려운 명령은 명시적 승인 없이 실행하지 않는다.
- 다른 작업자의 커밋을 임의로 amend, rebase 또는 revert하지 않는다.
- `docker compose down -v`는 **DB 볼륨을 지운다.** 스키마를 바꿔 재적용해야 할 때만 쓰고, 실행 전에 알린다.
- `infra-images.tar`를 커밋하지 않는다. 343MB로 GitHub 파일 크기 한도를 넘는다.

## 알려진 오탐

시간을 낭비하기 쉬운 항목들이다. **아래는 장애가 아니다.**

| 관측 | 실제 |
| --- | --- |
| `user`/`course`/`enrollment`의 `/actuator/health`가 500 | 이 서비스들은 actuator를 노출하지 않는다. 없는 경로를 `GlobalExceptionHandler`가 자기 응답 포맷으로 감싼 것이다 |
| `gateway`/`payment`의 `/actuator/health`가 404 | 같은 이유. compose에도 이 서비스들엔 healthcheck가 없다 |
| 로그에 `ErrorHandlingDeserializer` | Kafka 설정 덤프에 나오는 클래스 이름이다 |
| 로그에 `ExceptionTranslationFilter` | Spring Security 필터 체인 이름이다 |
| eureka 로그의 `NoProviderFoundException` | Bean Validation provider가 없다는 INFO 로그. Eureka엔 필요 없다 |
| recommend 기동 직후 Kafka `NotCoordinatorForGroupError` | 그룹 코디네이터 선출 중 나오는 일시적 WARNING. 곧 `Successfully joined group`이 뜬다 |
| `:8082/v3/api-docs`가 500 | springdoc 경로가 `/api-docs`로 커스터마이즈되어 있다. `/v3/api-docs`는 없는 경로다 |
| 게이트웨이로 Swagger 접속이 401 또는 빈 문서 | 게이트웨이에 Swagger 라우팅이 없다. 개별 서비스 포트로 접속한다 |

로그에서 `ERROR` 레벨을 찾을 때는 문자열 `Error`가 아니라 **로그 레벨 `ERROR`** 로 필터한다. 클래스 이름에 걸려 오탐이 쏟아진다.

## 작업 시 참고

- 무엇을 만드는지는 [`docs/product/overview.md`](docs/product/overview.md)에서 확인한다.
- 무엇을 호출하는지는 [`docs/spec/api-spec.md`](docs/spec/api-spec.md)에서 확인한다.
- 무엇을 바꿀 수 없는지는 [`docs/constraints.md`](docs/constraints.md)에서 확인한다.
- 기획안에서 **미결정**으로 표시한 항목을 임의로 확정하지 않는다.
