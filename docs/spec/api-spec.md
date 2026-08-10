# API 명세

프론트엔드가 호출하는 계약이다. 실제 구현된 컨트롤러와 DTO에서 추출했다.

**경로는 원본을 그대로 쓴다.** 게이트웨이 라우팅이 빌드된 이미지에 고정되어 있고 catch-all 라우트가 없다. 이유는 [`../constraints.md`](../constraints.md)에 있다.

## 공통 규약

| 항목 | 값 |
| --- | --- |
| 호출 주소 | `http://localhost:8080` — **반드시 게이트웨이로.** 개별 포트 직접 호출은 CORS 오류 |
| 인증 헤더 | `Authorization: Bearer <access_token>` — 모든 `/api/**` 필수. 없으면 401 |
| 공통 응답 | `{ "success": boolean, "message": string, "data": T }` |
| 역할 | 공급기업 = `INSTRUCTOR`, 구매기업 = `STUDENT` |

`X-User-Id` · `X-User-Email` · `X-User-Role` 헤더는 **프론트엔드가 보내는 것이 아니다.** 게이트웨이가 JWT에서 추출해 하위 서비스로 전달한다. 명세에는 적되 `fetch` 코드에는 넣지 않는다.

`/internal/**` 로 끝나는 엔드포인트는 서비스 간 호출용이다. 공통 응답 래퍼 없이 원시 객체를 반환하며 **프론트엔드는 호출하지 않는다.**

## Swagger

| 서비스 | UI | OpenAPI JSON |
| --- | --- | --- |
| user-service | `:8081/swagger-ui.html` | `:8081/api-docs` |
| course-service | `:8082/swagger-ui.html` | `:8082/api-docs` |
| enrollment-service | `:8083/swagger-ui.html` | `:8083/api-docs` |
| payment-service | `:8084/swagger-ui.html` | `:8084/api-docs` |
| recommend-service | `:8085/docs` | `:8085/openapi.json` |

게이트웨이 경유(`:8080`)로는 볼 수 없다. UI는 401, JSON은 빈 문서다. **문서는 개별 포트, 호출은 게이트웨이.**

## 1. 기업 회원 — user-service

| Method | URL | 조달 도메인 의미 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/users/register` | 기업 회원가입 | 공개 |
| GET | `/api/users/me` | 로그인한 내 기업 정보 | 토큰 |
| GET | `/api/users/{id}` | 특정 기업 정보 | 토큰 |
| GET | `/api/users/internal/{id}` | 서비스 간 호출 | service |

```json
// POST /api/users/register
{
  "email": "buyer@daehan-auto.com",
  "password": "Passw0rd!",
  "name": "대한자동차부품",
  "role": "STUDENT"
}
```

```json
// 201
{
  "success": true,
  "message": "성공",
  "data": { "id": 3, "email": "buyer@daehan-auto.com", "name": "대한자동차부품", "role": "STUDENT" }
}
```

공급기업으로 가입하려면 `"role": "INSTRUCTOR"` 로 보낸다. 가공 서비스 등록은 이 역할만 가능하다.

## 2. 소재·가공 서비스 — course-service

| Method | URL | 조달 도메인 의미 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/courses` | 공급기업이 가공 서비스 등록 | INSTRUCTOR |
| GET | `/api/courses` | 전체 가공 서비스 목록 | 토큰 |
| GET | `/api/courses/{id}` | 가공 서비스 상세 | 토큰 |
| GET | `/api/courses/category/{category}` | 소재 계열별 조회 | 토큰 |
| GET | `/api/courses/internal/exists/{id}` | 발주 시 존재 확인 | service |
| GET | `/api/courses/internal/{id}` | 발주 목록 조립용 | service |
| POST | `/api/courses/internal/{id}/enrollment-count` | 거래 건수 증가 | service |
| GET | `/api/courses/internal/recommend` | 추천 후보 조회 | service |

```json
// POST /api/courses
{
  "title": "수도용덕타일주철관, Φ300mm×6m, 2종",
  "description": "공급업체소재지: 충청북도 영동군 | 기업구분: 중소기업 | 품명: 주철관 | 세부품명: 수도용덕타일주철관 | 물품식별번호: 10062465 | 단위: 본 | 공급지역: 전지역 | 납품일수: 30일 | 인증정보: KS, 소기업 | 우수제품여부: N | MAS여부: Y",
  "category": "SECURITY",
  "price": 620740
}
```

```json
// 201
{
  "success": true,
  "message": "성공",
  "data": {
    "id": 1,
    "title": "SUS304 CNC 정밀가공",
    "description": "가공방식: CNC, MCT | …",
    "category": "BACKEND",
    "price": 4800,
    "instructorId": 2,
    "enrollmentCount": 0,
    "status": "ACTIVE",
    "createdAt": "2026-08-10T14:02:11"
  }
}
```

`category` 는 8칸 고정 enum 중 하나다. 조달 품명군을 슬롯에 배정하고 **화면 라벨만 프론트엔드에서 바꾼다.**

`description` 에 담는 스펙 정보는 컬럼이 없어서 자유 텍스트로 넣는 것이다. 구분자를 팀에서 하나로 정하고 프론트엔드에서 파싱해 표로 표시한다.

`enrollmentCount` 는 거래 성사 시 자동으로 1 증가한다. 기획안의 **누적 거래건수**에 그대로 대응한다.

## 3. 견적·발주 — enrollment-service

| Method | URL | 조달 도메인 의미 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/enrollments` | 발주 요청. 생성 시 `PENDING` | 토큰 |
| GET | `/api/enrollments/my` | 내 발주·주문 목록 | 토큰 |
| GET | `/api/enrollments/user/{userId}` | 특정 기업의 발주 목록 | 토큰 |
| GET | `/api/enrollments/internal/history/{userId}` | 추천용 거래 이력 | service |

```json
// POST /api/enrollments
{ "courseId": 1 }
```

이게 전부다. 수량·예산·희망납기 같은 조달조건 필드는 없다. 대응 방법은 [`../constraints.md`](../constraints.md)의 `조달 조건 입력 필드가 없다` 를 본다.

```json
// GET /api/enrollments/my — course 객체가 붙어서 온다. 별도 조회가 필요 없다.
{
  "success": true,
  "message": "성공",
  "data": [{
    "id": 10,
    "userId": 3,
    "courseId": 1,
    "status": "ACTIVE",
    "createdAt": "2026-08-10T14:05:40",
    "course": {
      "id": 1,
      "title": "SUS304 CNC 정밀가공",
      "description": "가공방식: CNC, MCT | …",
      "category": "BACKEND",
      "price": 4800,
      "thumbnail": null,
      "instructorName": "대한정밀",
      "enrollmentCount": 1
    }
  }]
}
```

상태 값은 `PENDING`(결제 대기) · `ACTIVE`(주문 확정) · `CANCELLED`(취소) 세 개다.

### 발주 → 결제 → 확정 흐름

**프론트엔드가 호출하는 API는 `POST /api/enrollments` 하나뿐이다.** 나머지는 서버 내부에서 연쇄적으로 일어난다.

```text
1. 프론트엔드          POST /api/enrollments { courseId }
2. enrollment-service  → course-service /internal/exists/{id}   서비스 존재 확인
3. enrollment-service  주문 레코드 생성 (PENDING)
4. enrollment-service  → payment-service /internal/request      결제 자동 요청
5. payment-service     Kafka payment.completed 발행
6. enrollment-service  이벤트 수신 → ACTIVE 전환 + 거래건수 증가
7. enrollment-service  Kafka enrollment.completed 발행 → recommend-service
```

**이 배선은 이미 구현되어 동작한다.** Sprint2의 "결제 및 주문 확정"은 새로 만들 것이 아니라 로그로 증명하고 확장하는 항목이다.

## 4. 주문 결제 — payment-service

| Method | URL | 조달 도메인 의미 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/payments/{id}` | 결제 단건 조회 | 토큰 |
| GET | `/api/payments/user/{userId}` | 기업 결제 내역 | 토큰 |
| POST | `/api/payments/internal/request` | 결제 실행. enrollment-service만 호출 | service |

```json
// GET /api/payments/user/3
{
  "success": true,
  "message": "성공",
  "data": [{
    "paymentId": 7,
    "userId": 3,
    "courseId": 1,
    "amount": 99000,
    "status": "COMPLETED",
    "transactionId": "TXN-…",
    "createdAt": "2026-08-10T14:05:41"
  }]
}
```

**프론트엔드는 결제를 직접 호출하지 않는다.** 결제를 생성하는 공개 엔드포인트가 없고, `vue-frontend/src/api/` 에 `payment.js` 가 아예 없다. 의도된 구조다.

`amount` 가 99,000원 고정인 이유와 대응 방법은 [`../constraints.md`](../constraints.md)의 `결제 금액이 고정되어 있다` 를 본다.

## 5. 공급업체 추천 — recommend-service

| Method | URL | 조달 도메인 의미 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/recommend/{userId}` | 거래 이력 기반 공급업체 추천 | 토큰 |

```json
// GET /api/recommend/3 — 현재 구현
{
  "userId": 3,
  "recommendedCourses": [{
    "id": 1,
    "title": "SUS304 CNC 정밀가공",
    "description": "가공방식: CNC, MCT | …",
    "category": "BACKEND",
    "price": 4800,
    "instructorId": 2,
    "enrollmentCount": 1,
    "status": "ACTIVE",
    "createdAt": "2026-08-10T14:02:11"
  }],
  "basedOnCategory": "BACKEND",
  "message": "…"
}
```

현재 추천 규칙은 `recommend-service/app/service/recommend_service.py` 에 있다.

- 거래 이력 있음: 최빈 카테고리의 미거래 서비스를 거래건수 순으로
- 거래 이력 없음: 전체 인기 서비스

### Sprint2 확장 계획

기획안 7.2의 `추천점수` 와 `추천 해석` 이 현재 응답에 없다. **recommend-service는 파이썬이라 이 확장이 가장 가볍다.** 수정 대상은 `app/model/schemas.py` 와 `app/service/recommend_service.py` 두 파일이다.

```json
{
  "userId": 3,
  "recommendedCourses": [{
    "id": 1,
    "score": 95,
    "reason": "불량률 0.7%, 납기 준수율 97%, 예산 충족",
    "scoreBreakdown": { "spec": 30, "quality": 24, "price": 17, "leadTime": 14, "onTimeRate": 10 }
  }],
  "basedOnCategory": "BACKEND",
  "message": "조건 충족 업체 3곳"
}
```

기획안 6번의 배점표를 `scoreBreakdown` 으로 노출하면 발표에서 추천 근거를 설명하기 좋다.

`httpx` 가 이미 설치되어 있어 **의존성 추가 없이** LLM 호출을 붙일 수 있다. API 키는 `docker-compose.yml` 의 `environment` 로 주입하고 **커밋하지 않는다.**

## 6. 없는 엔드포인트 — Sprint2 신규

기획안 7.3의 품질검사 등록에 대응하는 엔드포인트가 없다. 게이트웨이에 새 경로를 만들 수 없으므로 기존 경로 하위에 붙인다.

| Method | URL | 용도 |
| --- | --- | --- |
| PATCH | `/api/enrollments/{id}/quality` | 납품수량·불량수량·불량유형 등록, 불량률 계산 |

enrollment-service 자바 수정이 필요하다. 기획안의 핵심 차별점인 "거래 후 품질 데이터를 다음 추천에 재활용"의 출발점이므로, **이 하나만은 자바 수정을 감수한다.** 불량률 계산 이후의 추천 반영은 파이썬 쪽에서 이어받는다.

## Sprint별 호출 목록

| Sprint | 프론트엔드가 호출할 API | 백엔드 작업 |
| --- | --- | --- |
| **Sprint 1** | `POST /api/users/register`<br>`GET /api/users/me`<br>`POST /api/courses`<br>`GET /api/courses`<br>`GET /api/courses/{id}`<br>`GET /api/courses/category/{category}`<br>`POST /api/enrollments`<br>`GET /api/enrollments/my`<br>`GET /api/payments/user/{userId}`<br>`GET /api/recommend/{userId}` | 없음 (자바 0줄) |
| **Sprint 2** | `PATCH /api/enrollments/{id}/quality` (신규)<br>`GET /api/recommend/{userId}` (응답 확장) | enrollment-service 품질검사<br>recommend-service 점수화 |
