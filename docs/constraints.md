# 현재 런타임의 제약과 전환 조건

기준: 팀 `main` 커밋 `980da07`, 개인 개발 전환일 2026-10-06.
이 문서는 기존 환경의 호환성 자료다. 팀의 작업 범위·Sprint·Java 수정 제한은 현재 규칙이 아니다. 개발 규칙은 [AGENTS.md](../AGENTS.md)를 따른다.
팀 실습 중의 관측과 원문은 [기록](history/team-project/constraints.legacy.md)에 보존했다. 당시 실행 검증을 이번에도 수행했다고 가정하지 않는다.

## 지금 유지하는 조건

| 대상 | 현재 상태 | 전환 조건 |
| --- | --- | --- |
| auth-server·api-gateway | 앱 소스 없이 `msa-lecture/*:1.0` 이미지로 제공 | 소스 확보 또는 대체 구현 후 로그인·권한·라우팅 통합 검증 |
| eureka-server | **앱 소스가 저장소에 있음** | 필요한 수정 가능; 팀 기록의 “인프라 3종 소스 없음”과 구분 |
| API 경로 | 고정 gateway의 `/api/users`, `/api/courses`, `/api/enrollments`, `/api/payments`, `/api/recommend` | 게이트웨이를 교체하기 전 신규 기능은 기존 prefix 하위; API rename은 호출자 동시 이전 |
| 포트·callback | Vue 3000, `localhost:3000/callback` 등록에 의존 | 환경별 인증 등록과 프록시·배포 설정 동시 변경 |
| JWT 역할 | STUDENT·INSTRUCTOR | 새 역할은 발급·검증·권한 모델을 함께 변경 |
| 반복 발주 | DB/엔티티 UNIQUE(user_id, course_id), 결제 이벤트도 같은 쌍 사용 | 주문 ID·멱등성·이벤트·데이터 백필 후 제약 해제 |
| 스펙 저장 | `description` 텍스트에서 조건 파싱 | 구조화 필드 추가·백필·기존 파서 호환·복구 계획 |
| 서비스 접근 | 게이트웨이 헤더를 신뢰하는 경로와 직접 포트 노출이 있음 | JWT·소유권·내부 API와 헤더 위조 방지 검증 후 외부 배포 |

게이트웨이 경유 신규 POST 허용과 JWT 실제 클레임은 구현 시 확인한다. 브라우저 인증의 `VITE_CLIENT_SECRET` 방식도 독립 배포 전에 대체해야 한다.
현재 경로·포트 guard는 이 런타임 계약 때문에 유지한다. 전환이 완료되면 문서와 guard를 함께 수정한다.

## 발주·데이터

현재 발주 상태는 PENDING → SHIPPING → DELIVERED다. ACTIVE는 이전 데이터 호환 값이고 CANCELLED에는 도달 API가 없다.
결제 금액은 등록 단가 × 수량의 서버 계산값이다. 과거 99,000원 고정은 해소됐으며 그 기록을 새 요구사항으로 사용하지 않는다.
품질·납기·비용은 과거 공급기업 성과다. 평가 문턱과 실제 관측 예외, 조건 점수의 문서·코드 차이는 [기반 설계](architecture/refactoring-foundation.md)에 기록한다.

`init-db`는 빈 DB의 최초 초기화에만 적용된다. 기존 볼륨에는 migration을 사용하며 `docker compose down -v`로 이전하지 않는다.
카탈로그 시드는 `scripts/generate-seed-sql.py`로 생성한다. 생성 SQL만 손으로 수정하지 않는다.
`infra-images.tar`는 저장소에 없고, 새 clone만으로 인증·게이트웨이 이미지를 확보할 수 없다. 이미지 확보와 공개 배포 가능성은 별개로 확인한다.

## 기존 환경에서 알려진 오탐

| 관측 | 팀 환경에서 확인한 설명 |
| --- | --- |
| user/course/enrollment의 `/actuator/health` 500 | 노출하지 않은 경로를 예외 응답으로 감쌈 |
| gateway/payment의 `/actuator/health` 404 | 해당 health 경로 없음 |
| ErrorHandlingDeserializer·ExceptionTranslationFilter 로그 | 설정 또는 클래스 이름만으로 오류 판정하지 않음 |
| Eureka NoProviderFoundException INFO | 검증 provider 관련 INFO; 레벨·문맥 확인 |
| recommend 기동 직후 Kafka NotCoordinatorForGroupError | 그룹 선출 중 일시 경고; 이후 join 여부 확인 |
| Java `/v3/api-docs` 오류 | 커스텀 경로는 `/api-docs` |
| gateway로 Swagger 조회 실패 | 문서는 개별 서비스 포트에서 조회 |

로그는 문자열 Error보다 실제 ERROR 레벨과 요청 실패를 확인한다. 독립화 후 위 관측은 새 환경에서 재검증한다.

## 프론트·빌드 관측

기존 axios baseURL은 상대 경로라 Vite 개발 프록시를 사용한다. `npm run build` 성공만으로 로그인·API 호출·배포가 검증된 것은 아니다.
401 응답의 자동 로그아웃이 기존 인터셉터에서 비활성화되어 화면이 비어 보일 수 있다. 토큰 상태와 API 응답을 먼저 확인한다.
Java 변경은 가능하다. 해당 서비스만 테스트·재빌드하며, 의존성 다운로드가 429이면 네트워크 제한과 캐시를 확인한다.
