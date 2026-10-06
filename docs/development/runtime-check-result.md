# 로컬 실행 환경·거래 흐름 검증 결과

검증일: 2026-10-06. 기반 커밋: `19e56a2`. 작업 브랜치: `codex/runtime-transaction-check`.
현재 macOS 개발 컴퓨터의 Docker `29.6.2`·`aarch64`, Node `26.7.0`, Python `3.11.15`에서 확인했다. [실행 설계](../architecture/runtime-and-criteria-design.md)의 작업 1 결과이며 새 조건 필터·AI 구현은 포함하지 않는다.

## 데이터 보존과 기동

기존 `msa-lecture_mariadb_data`, `msa-lecture_kafka_data` 볼륨을 유지했다. 관련 컨테이너가 중지된 상태에서 두 볼륨을 읽기 전용으로 마운트해 압축 백업을 만들고, 전체 압축 항목을 읽어 무결성을 확인했다. DB 시작 후 업무 서비스 시작 전에 `lecture_db` 논리 백업도 확보했다. 복구 실행은 하지 않았다.

백업은 Git에서 제외한 `.local/runtime-check/20261006/`에 있다. `mariadb-volume.tar.gz`, `kafka-volume.tar.gz`, `lecture-db-before.sql`과 백업 크기·SHA-256 manifest를 보관한다. 원본 데이터와 백업은 커밋하지 않는다.

기존 스키마의 `courses.contract_end`, 발주 수량·단위·납품·담당자·`estimated_total` 컬럼, 결제 `DECIMAL(19,2)`를 확인했다. 현재 사례에 수동 마이그레이션은 필요하지 않았다. 기존 시드를 다시 넣거나 볼륨을 삭제하지 않았다.

| 테이블 | 기존 행 수 | 검증 후 행 수 | 기존 행 비교 |
| --- | --- | --- | --- |
| users | 55 | 57 | ID 55 이하 덤프 SHA-256 동일 |
| courses | 264 | 266 | ID 264 이하 덤프 SHA-256 동일 |
| enrollments | 6 | 8 | ID 7 이하 덤프 SHA-256 동일 |
| payments | 6 | 8 | ID 7 이하 덤프 SHA-256 동일 |

기존 발주·결제 ID에 빈 번호가 있어 최대 ID와 행 수가 다르다. 새 테스트 계정 2개, 품목 2개, 발주·결제 각 2개만 추가했다. 비교는 기본 키 순서의 데이터 덤프로 수행했으며 기존 행의 값이 바뀌지 않았다.

인증·게이트웨이는 기존 arm64 이미지를 사용하고, 소스가 있는 6개 서비스를 현재 코드로 빌드했다.

```sh
docker compose -p msa-lecture -f docker-compose.yml build
docker compose -p msa-lecture -f docker-compose.yml up -d --no-build
docker compose -p msa-lecture -f docker-compose.yml ps --all
```

컨테이너 10개 기동을 확인했다. DB·Kafka·Eureka·인증 health와 실제 게이트웨이 회원가입·업무 API 응답을 대조했다. 추천은 `http://localhost:8085/health`가 200이었다. 기동 직후 게이트웨이 회원가입 경로는 서비스 등록·캐시 갱신 중 503이었고, 이후 200으로 정상화됐다. 상태가 Running이라는 이유만으로 준비 완료로 판정하지 않았다.

## 실제 거래 검증

데모 버튼을 사용하지 않았다. 브라우저 회원가입으로 공급기업 ID `56`·구매기업 ID `57`을 생성하고, 인증 서버의 로그인 화면 → callback → 토큰 교환 → 사용자 조회 → 공급·구매 화면 복귀를 각각 확인했다. 공급기업 로그아웃 후 구매기업으로 계정 전환도 성공했다.

| 사례 | 등록·발주 경로 | 품목 / 발주 / 결제 ID | 단가 × 수량 | 서버 견적·결제 금액 | 최종 결과 |
| --- | --- | --- | --- | --- | --- |
| 정수 금액 | 브라우저 등록·조회·발주 | 265 / 8 / 8 | 1250 × 3개 | 3750.00 | COMPLETED / SHIPPING / 거래건수 1 |
| 소수 금액 | 인증된 게이트웨이 API 등록·발주 | 266 / 9 / 9 | 1250.50 × 3개 | 3751.50 | COMPLETED / SHIPPING / 거래건수 1 |

두 품목 모두 파형강관(`BACKEND`), `Φ300mm`, 단위 `개`, 30일 납품·KS, 계약 2026-10-06~2027-10-06을 사용했다. 등록 단가·계약·품목 목록 포함 여부를 API에서 확인했다. 발주는 미래 납품일과 가상 장소·담당자·연락처만 사용했다. API·DB·발주 관리 화면의 금액과 상태를 대조했다. 발주 관리 화면은 소수 금액을 `3,751.5원`으로 표시하며 서버·DB 값 `3751.50`과 같은 금액이다.

발주 상태는 최초 PENDING일 수 있고 Kafka 완료 이벤트가 SHIPPING으로 바꾼다. 정수 사례의 최초 화면에는 결제 완료와 결제 대기가 함께 보였고, 페이지 재조회 후 배송중으로 표시됐다. 현재 화면에는 자동 상태 재조회가 없다. 소수 사례는 검증 스크립트가 1초 간격·최대 30초로 SHIPPING을 확인했다. 이후 브라우저 재조회에서도 두 발주가 배송중이었다. 발주 POST를 자동 재시도하지 않았다.

## 인증·로그 점검

| 검사 | 실제 결과 |
| --- | --- |
| JWT 신원 | `user_id` 56/57, `role` INSTRUCTOR/STUDENT가 `/api/users/me`와 일치 |
| issuer | `http://localhost:8080` |
| 무토큰 보호 경로 | `GET /api/users/me` → 401 |
| 유효하지 않은 토큰 | 같은 경로 → 401 |
| 타인 품목 수정 | 구매기업이 공급기업 품목 266을 PUT → 403 |
| 정상 인증 로그 | auth store의 토큰 응답·사용자 응답 console 로그 0개 |

auth store의 두 원문 응답 로그를 제거했고, 사용자 조회·callback 실패 시 요청 헤더를 포함할 수 있는 오류 객체를 콘솔에 넘기지 않게 했다. 토큰·비밀번호는 결과 문서에 저장하지 않았다. 위 검사는 현재 게이트웨이 경유 사례다. 직접 서비스 포트·내부 API·신규 추천 API의 전체 권한 검증은 [백로그](backlog.md)의 P0-2에 남아 있다.

## 재실행과 검증

[검증 스크립트](../../scripts/check-runtime-transactions.py)는 Python 표준 라이브러리만 사용한다. 먼저 새 테스트 계정 두 개를 같은 테스트 비밀번호로 화면 가입시킨 뒤, 공급기업이 정수 단가 1250·단위 개의 품목을 등록하고 구매기업이 수량 3으로 발주한다. 아래 이메일과 ID는 해당 실행의 값으로 바꾼다. 비밀번호는 프롬프트에서 입력하며, 자동 실행에서는 `RUNTIME_TEST_PASSWORD` 환경변수를 사용할 수 있다.

```sh
python3 scripts/check-runtime-transactions.py \
  --supplier-email supplier-test@example.test \
  --buyer-email buyer-test@example.test \
  --screen-course-id 265
```

실행마다 별도의 소수 단가 품목·발주·실습 결제를 한 건씩 추가한다. 기존 정수 발주는 조회만 한다. OAuth 로그인·JWT 매핑·401/403·카탈로그·금액·SHIPPING·거래건수 검사를 수행하고 토큰·비밀번호를 출력하지 않는다. 이 스크립트는 거래 데이터를 만들므로 일반 CI에서 자동 실행하지 않는다.

실행 검증 스크립트와 `npm run build`가 통과했다. Vite의 기존 `__dirname` 비차단 경고는 남아 있다. Java 소스는 변경하지 않았고 이번 작업에서 Java 단위 테스트는 실행하지 않았다. 실제 PG 결제, 다른 컴퓨터의 새 DB 기동, 백업 복구, 품질 등록·DELIVERED는 이번 완료 범위가 아니다. 현재 로컬 서버는 검증 후 기동 상태로 유지한다.
