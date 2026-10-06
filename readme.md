# AI 조달 개인 개발 프로젝트

SKALA 팀 프로젝트를 기반으로 자연어 구매 조건 입력, 공급업체 비교, 근거 설명 기능을 확장한다.
현재는 **개인 개발 지침·초기 리팩토링 설계 정리 단계**다. 신규 AI 기능은 아직 구현하지 않았다.

기존 팀 결과와 교육 템플릿의 출처를 유지한다. 팀 문서 원문은 [`docs/history/team-project/`](docs/history/team-project/README.md), 기준 커밋은 `980da07`이며 이후 변경을 개인 기여로 구분한다.

> The following practice code is intended for educational purposes only. For contact: audit@korea.ac.kr, Sungryel Lim Ph.D
>
> This practice code is not a completed commercial version but has been developed for educational purposes; supplementation is required depending on the deployment objective for use as a commercial service.

## 개발 안내

| 문서 | 용도 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | 개인 개발 에이전트 규칙의 원본 |
| [CLAUDE.md](CLAUDE.md) | Claude에서 같은 규칙을 읽는 진입점 |
| [제품 방향](docs/product/overview.md) | 현재 기반과 AI 확장 목표 |
| [리팩토링 기반 설계](docs/architecture/refactoring-foundation.md) | 현행 구조·API 제안·이전 조건·완료 기준 |
| [ADR-0001](docs/architecture/decisions/0001-personal-development.md) | 초기 구조를 유지하는 결정과 대안 |
| [개인 백로그](docs/development/backlog.md) | 다음에 구현할 작은 작업 |
| [에이전트 활용](docs/development/agents.md) | Agency Agents 역할별 범위와 요청 예시 |
| [런타임 제약](docs/constraints.md) | 경로·인증·DB 호환성과 알려진 오탐 |
| [기존 API](docs/spec/api-spec.md) | 기존 서비스 호출 참고; 점수 정책 차이는 설계에 기록 |
| [저장소 이전](docs/development/repository-migration.md) | private fork에서 독립 저장소로 이동 |
| [기반 작업 결과](docs/development/foundation-result.md) | 실제 검증·이전 결과와 남은 작업 |

## 현재 실행 구조

Vue·Pinia, Spring Boot 서비스, FastAPI 추천, MariaDB, Kafka, Eureka로 구성된다.
인증·게이트웨이는 앱 소스 없이 교육용 이미지에 의존한다. 결제는 실습용 성공 처리이며 실제 PG 연동이 아니다.
GitHub 소스 공개만으로 외부 배포 또는 깨끗한 환경의 완전한 실행이 가능해지는 것은 아니다.

| 구성 | 로컬 포트 |
| --- | --- |
| Vue | 3000 |
| API Gateway / Auth | 8080 / 9000 |
| user / course / enrollment / payment | 8081 / 8082 / 8083 / 8084 |
| recommend / Eureka | 8085 / 8761 |
| MariaDB / Kafka | 3379 / 9092 |

## 로컬 실행

먼저 Node.js, Python, Docker와 교육용 인증·게이트웨이 이미지를 준비한다.
프론트 도구의 Node 버전 조건은 사용 중인 Vite 패키지와 CI 설정을 확인한다.
`infra-images.tar`는 저장소에 없으며 강의 자료 배포 경로에서 확보해야 한다. 재배포 권한을 확인하지 않은 이미지를 공개 저장소에 올리지 않는다.

```bash
./scripts/setup-git-hooks
docker load -i infra-images.tar
docker compose up -d --build
```

첫 기동의 이미지 로드·DB·Kafka·인증 준비에 시간이 필요하다. `docker compose ps`와 서비스 로그로 상태를 확인한다.

```bash
cd vue-frontend
npm install
npm run dev
```

프론트는 http://localhost:3000 이다. 현재 인증 callback과 Vite 프록시 때문에 개발 포트는 3000을 사용한다.
빌드 산출물만 열어서는 기존 인증·API 흐름을 검증할 수 없다. 배포 설정 개선은 후속 작업이다.

## 데이터와 검증

새 DB에서는 `init-db/02_seed_catalog.sql`이 공급기업 45곳과 조달 품목 262건을 넣는다.
CSV를 수정하면 `python3 scripts/generate-seed-sql.py`로 SQL을 다시 생성한다. 계약 만료 필터 때문에 표시 수는 날짜에 따라 달라진다.
시드 공급기업 로그인 비밀번호는 기본으로 설정되지 않는다. 새 계정을 등록하거나 `init-db/03_seed_password.sh`의 환경변수 방식을 사용한다.
기존 볼륨에는 init SQL이 자동 재적용되지 않으므로 데이터 보존 migration을 사용한다.

```bash
python3 scripts/check-docs.py
git diff --check
```

프론트 검증은 `vue-frontend`에서 `npm run build`, 기능 변경은 해당 서비스 테스트와 영향받은 흐름의 회귀 검사로 확인한다.
Java·Python·인프라 수정이 가능하며 팀 Sprint 경계와 팀 승인 대기 조건은 적용하지 않는다.

## 설정·기록

커밋된 `.env`와 compose에는 교육용 고정 설정값이 있다. 운영 자격증명으로 재사용하지 않는다.
새 LLM 키는 서버의 환경변수 또는 gitignore된 로컬 설정으로 전달한다. `VITE_*`에 비밀키를 넣지 않는다.
기존 브라우저의 `VITE_CLIENT_SECRET` 토큰 교환 방식과 직접 서비스 접근은 독립 배포 전에 정리할 과제다.

`main` 직접 갱신을 막는 hook을 유지한다. 빈 독립 저장소의 첫 main 생성만 허용하고 이후 `codex/<작업명>` 브랜치와 PR로 진행한다.
원본·개인 기여의 공개 범위와 라이선스는 구분한다. 원본 허용 범위를 확인하지 않고 새 LICENSE를 붙이지 않는다.
