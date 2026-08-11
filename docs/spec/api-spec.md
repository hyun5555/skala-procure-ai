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
| GET | `/api/users/register?email=` | 이메일 중복 확인 | 공개 |
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

`role` 에 `STUDENT` `INSTRUCTOR` 가 아닌 값을 보내면 400 이다. 대소문자도 구분한다.

```json
// 400
{ "success": false, "message": "허용되지 않는 값입니다: ADMIN (가능한 값: [STUDENT, INSTRUCTOR])", "data": null }
```

### 이메일 중복 확인

가입 폼이 제출 전에 중복을 알려주기 위한 조회다. 아무것도 저장하지 않는다.

```json
// GET /api/users/register?email=student@lecture.com
{ "success": true, "message": "성공", "data": { "email": "student@lecture.com", "available": false } }
```

**경로가 `POST` 회원가입과 같은 것은 의도된 것이다.** 게이트웨이가 토큰 없이 통과시키는 경로는 `/api/users/register` 하나뿐이고, 가입 화면은 로그인 전이라 신규 경로를 만들면 401 에 막힌다. 게이트웨이는 이미지로만 제공되어 허용 규칙을 바꿀 수 없다. 확인 결과다.

```text
POST /api/users/register        → 통과
GET  /api/users/register        → 통과
POST /api/users/check-email     → 401
GET  /api/users/check-email     → 401
POST /api/users/anything        → 401
```

**최종 판정은 이 조회가 아니라 `POST` 응답이다.** 확인과 제출 사이에 다른 사람이 같은 이메일로 가입할 수 있다. 프론트엔드는 확인 실패를 가입 차단으로 쓰지 않는다.

## 2. 소재·가공 서비스 — course-service

| Method | URL | 조달 도메인 의미 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/courses` | 공급기업이 가공 서비스 등록 | INSTRUCTOR |
| PUT | `/api/courses/{id}` | 등록한 공급기업이 품목 수정 | INSTRUCTOR |
| PUT | `/api/courses/{id}/status` | 등록한 공급기업이 거래 가능 상태 변경 | INSTRUCTOR |
| GET | `/api/courses` | 전체 가공 서비스 목록 | 토큰 |
| GET | `/api/courses/my` | 내가 등록한 품목 목록 | INSTRUCTOR |
| GET | `/api/courses/{id}` | 가공 서비스 상세 | 토큰 |
| GET | `/api/courses/category/{category}` | 소재 계열별 조회 | 토큰 |
| GET | `/api/courses/internal/exists/{id}` | 발주 시 존재 확인 | service |
| GET | `/api/courses/internal/{id}` | 발주 목록 조립용 | service |
| POST | `/api/courses/internal/{id}/enrollment-count` | 거래 건수 증가 | service |
| POST | `/api/courses/internal/{id}/performance` | 공급기업 누적 성과지표 갱신 | service |
| GET | `/api/courses/internal/recommend` | 추천 후보 조회 | service |

```json
// POST /api/courses
{
  "title": "수도용덕타일주철관, Φ300mm×6m, 2종",
  "description": "공급업체소재지: 충청북도 영동군 | 기업구분: 중소기업 | 품명: 주철관 | 세부품명: 수도용덕타일주철관 | 물품식별번호: 10062465 | 단위: 본 | 공급지역: 전지역 | 납품일수: 30일 | 인증정보: KS, 소기업 | 우수제품여부: N | MAS여부: Y",
  "category": "SECURITY",
  "price": 620740,
  "contractEnd": "2027-12-31"
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
| PUT | `/api/enrollments/{id}/quality` | 납품 품질 등록·수정 | 토큰 |
| PATCH | `/api/enrollments/{id}/performance` | 공급성과 평가(QCD) 등록 | 토큰 |
| GET | `/api/enrollments/my` | 내 발주·주문 목록 | 토큰 |
| GET | `/api/enrollments/user/{userId}` | 특정 기업의 발주 목록 | 토큰 |
| GET | `/api/enrollments/internal/history/{userId}` | 추천용 거래 이력 | service |

```json
// POST /api/enrollments
{
  "courseId": 1,
  "quantity": 12,
  "unit": "본",
  "deliveryDate": "2026-09-10",
  "deliveryPlace": "서울특별시 강남구 현장 자재창고",
  "notes": "파렛트 포장 후 납품",
  "contactName": "홍길동",
  "contactPhone": "010-1234-5678"
}
```

`estimatedTotal` 은 클라이언트가 보내지 않는다. enrollment-service가 course-service에서 조회한 등록 단가에 수량을 곱해 계산·저장한다. **자동 결제도 이 값으로 청구된다.** 2026-08-11 이전에는 99,000원 고정이었다.

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
    "orderRequest": {
      "quantity": 12,
      "unit": "본",
      "deliveryDate": "2026-09-10",
      "deliveryPlace": "서울특별시 강남구 현장 자재창고",
      "notes": "파렛트 포장 후 납품",
      "contactName": "홍길동",
      "contactPhone": "010-1234-5678",
      "estimatedTotal": 7448880.00
    },
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

신규 발주의 상태 값은 `PENDING`(결제 대기) · `ACTIVE`(주문 확정) 두 개다.

### 품목 수정

```json
// PUT /api/courses/{id} — description 을 받지 않는다
{ "title": "수도용덕타일주철관, Φ300mm×6m, 2종", "category": "SECURITY", "price": 640000 }
```

**`description` 은 수정 대상이 아니다.** 조달 명세 16개 항목이 그 한 칸에 들어 있는데 등록 폼은 그중 다섯(`기업구분`·`세부품명`·`물품식별번호`·`납품장소`·`쇼핑몰등록일자`)을 수집하지 않는다. 등록 폼을 수정 화면으로 재사용하면 단가 하나만 고쳐도 그 항목들이 영구히 사라진다. **서버가 기존 값을 지킨다.**

| 상황 | 응답 |
| --- | --- |
| 남의 품목을 수정 | 403 `자신이 등록한 품목만 수정할 수 있습니다` |

### 발주 → 결제 → 확정 흐름

**프론트엔드가 호출하는 API는 `POST /api/enrollments` 하나뿐이다.** 나머지는 서버 내부에서 연쇄적으로 일어난다.

```text
1. 프론트엔드          POST /api/enrollments { courseId, quantity, unit, deliveryDate, ... }
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
    "amount": 38400.00,
    "status": "COMPLETED",
    "transactionId": "TXN-…",
    "createdAt": "2026-08-10T14:05:41"
  }]
}
```

**프론트엔드는 결제를 직접 생성하지 않는다.** 생성은 enrollment-service가 내부 API로 실행한다. 프론트는 `GET /api/payments/user/{userId}` 로 결과만 조회해 발주 관리 화면에 표시한다.

`amount` 는 발주의 `estimatedTotal`(단가 × 수량)과 같은 값이다. 99,000원 고정이던 시절의 사정은 [`../constraints.md`](../constraints.md)의 `결제 금액` 을 본다.

## 5. 공급업체 추천 — recommend-service

| Method | URL | 조달 도메인 의미 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/recommend/{userId}` | 거래 이력 기반 공급업체 추천 | 토큰 |

```json
// GET /api/recommend/3
{
  "userId": 3,
  "basedOnCategory": "SECURITY",
  "recommendedCourses": [{
    "id": 1, "title": "수도용덕타일주철관 …", "category": "SECURITY", "price": 620740,
    "instructorId": 2, "instructorName": "(주)신안주철", "enrollmentCount": 3,
    "defectRate": 0.50, "onTimeRate": 83.33, "costVarianceRate": 2.00, "evaluatedCount": 12,
    "score": 53,
    "scoreBreakdown": { "quality": 24, "delivery": 16, "cost": 13 },
    "reason": "불량률 0.50% (5,000 PPM) · 납기 준수율 83% · 견적 대비 2.0% 초과 · 평가 12건 기준 · KS · 우수제품 · MAS 등록",
    "performanceTrusted": true
  }],
  "message": "SECURITY 공급기업 5곳을 추천합니다. 그중 3곳은 평가 1건 이상의 실제 거래 성과가 반영되었습니다"
}
```

### 점수는 두 종류다

**지표 이름과 정의는 업계 표준이고, 배점과 곡선은 이 프로젝트의 MVP 설정이다.** 발표에서 이 구분을 지킨다.

| | 표준 |
| --- | --- |
| 인용 가능 | QCD 프레임 · PPM · OTD · PPV 정의 · OTD 목표선 95% · PPM 100/1,000/10,000 구간 · 로그 척도(Six Sigma DPMO) |
| 우리 설정 | 배점 50/30/20 · 곡선 형태 · 끝점(PPM 3%, OTD 80%, PPV +10%) · 검증 중 평가 문턱 1건 |

PPM 기준선은 산업 편차가 크다. 100 PPM 은 자동차·전자 기준이고 강관·주철관 같은 토목자재는 더 느슨한 것이 보통이다. **우리 기준이 엄격한 쪽이다.**

#### 점수 1 — 공급 신뢰도 (QCD) 100점 · 백엔드

구매기업이 발주 관리 화면에서 기록한 **실제 거래 결과** 에서만 나온다. `GET /api/recommend/{userId}` 응답의 `score` 다.

| 축 | 배점 | 지표 | 누적 산식 | 곡선 |
| --- | --- | --- | --- | --- |
| **Q** 품질 | 50 | PPM | `누적불량 ÷ 누적납품 × 1,000,000` | **로그**. 100 PPM 만점 / 30,000(3%) 0점 |
| **D** 납기 | 30 | OTD | `납기준수 ÷ 평가건수 × 100` | **꺾은선**. 95~100% → 24~30 / 80~95% → 0~24 |
| **C** 비용 | 20 | PPV | `(Σ실제청구 − Σ견적) ÷ Σ견적 × 100` | **선형**. 0% 이하 만점 / +10% 0점 |

```text
Q  0.01% (100 PPM) 50점 · 0.1% 29.8 · 0.5% 15.7 · 1% 9.6 · 3% 0
D  100% 30점 · 98% 27.6 · 95% 24 · 90% 16 · 85% 8 · 80% 0
C  0% 이하 20점 · +2% 16 · +5% 10 · +10% 0
```

**Q 를 로그로 두는 이유** — 선형이면 100 PPM(world-class)과 5,000 PPM(50배 나쁨)이 6점밖에 차이 나지 않는다. 조달 담당자가 가장 알고 싶어 하는 구간이 통째로 뭉개진다. 구간별 등급이 업계에서 더 흔하지만 경계에서 점수가 뚝 떨어지는 절벽이 생긴다.

**D 를 꺾은선으로 두는 이유** — 로그로 두면 98%(우수한 값)가 3분의 2로 깎인다. 납기는 자릿수로 움직이지 않고 80~100% 에 몰린다. 선형으로 두면 85%(6~7건 중 1건 지연)가 절반을 받아 관대하다. 목표선 아래를 위보다 1.33배 가파르게 벌한다.

**C 를 금액 가중으로 두는 이유** — 합계를 먼저 내고 나눈다. 건별 PPV 를 단순 평균하면 10만원짜리가 1억짜리와 같은 무게를 갖는다. 절감(음수)에 가산점은 주지 않는다. 견적을 크게 밑돌면 견적이 부실했다는 신호일 수 있다.

**검증용 평가 문턱** — 운영 기준은 평가 10건이지만 현재 검증 기간에는 1건으로 일시 완화했다. 따라서 평가가 1건이라도 있으면 실제 QCD 점수와 `performanceTrusted: true`를 사용한다. 평가가 0건이면 각 축에 **기본점수(만점의 절반, Q 25 / D 15 / C 10)** 를 주고 `performanceTrusted: false` 를 붙인다. 검증 종료 후 `recommend-service/app/service/scoring.py`의 `MIN_EVALUATIONS`를 10으로 되돌린다.

#### 점수 2 — 조건 적합도 100점 · 프론트엔드

사용자가 방금 입력한 값에서만 나온다. **백엔드는 이 값을 모른다.** 두 곳이 같은 것을 계산하면 반드시 어긋나므로 역할을 갈랐다.

| 축 | 배점 | 산식 |
| --- | --- | --- |
| 예산 여유 | 40 | `40 × (1 − 단가 × 수량 ÷ 예산)` |
| 일정 여유 | 20 | `20 × (요청 납기 − 납품일수) ÷ 요청 납기 ÷ 0.5`, 절반 이상 여유면 만점 |
| 인증 부합 | 20 | 요청 인증 전부 보유 20 / 일부 12 / 없음 0 |
| 지역 근접 | 20 | 거리 12 (같은 시·도 12 / 인접 8 / 그 외 4) + 운임조건 8 (무조건 전지역 8 / 운임 별도 3) |

**만점 구간을 두지 않는다.** "예산의 70% 이하면 만점" 같은 규칙은 어떤 데이터가 와도 상위권을 뭉갠다.

**미입력 축은 빼고 남은 가중치를 100으로 정규화한다.** 예산을 입력하지 않으면 나머지 60점을 100으로 편다.

**지역 근접에 운임조건을 묶는 이유** — 전수 84,180건에서 공급지역이 전부 "전지역" 이지만 **46.7% 가 "운임 별도"** 다. 납품은 되는데 구매자가 운임을 더 낸다. 단가만 비교하면 이 차이가 빠진다.

#### 최종

```text
종합 = 점수1 × 0.5 + 점수2 × 0.5
```

화면에는 **두 점수를 나란히** 표시한다. 합산 하나만 보이면 왜 1위인지 알 수 없고, 기획안이 정한 "점수만 보여주지 않고 판단 재료를 함께 표시한다" 에 어긋난다.

#### 1차 필터 — 통과/탈락, 점수 아님

**이진값은 필터, 연속값은 필터(하한) + 점수(여유).** 이진값에 점수를 주면 필터를 통과한 것들이 전부 만점이 되어 변별이 되지 않는다.

| 필터 | 판정 |
| --- | --- |
| 품명 · 세부품명 · 품목명 키워드 | 불일치 시 제외 |
| 공급지역 | 요청 지역이 **제외 조항**에 걸리면 제외 |
| **계약기간** | **종료일이 지났으면 제외** |
| 최대 납품일수 | 등록 납품일수 초과 시 제외 |
| 총예산 | 단가 × 수량 > 예산이면 제외 |
| 요청 인증 | 필수로 지정했는데 미보유면 제외 |
| 우수제품만 / MAS만 | 체크 시 미보유 제외 |

**종료일은 서버가 `description` 에서 뽑는다.** 클라이언트가 `contractEnd` 를 보내면 그것을 우선하지만, 안 보내도 `계약기간: 20240131~20250331` 에서 종료일을 파싱해 저장한다. 클라이언트에 맡기면 호출자마다 따로 고쳐야 하고 새 호출자가 잊으면 **만료 필터가 조용히 아무것도 거르지 않는다.** 형식이 다르면 `null` 로 두고 만료 판정을 포기한다 — 임의로 날짜를 지어내는 것보다 낫다.

**계약 만료는 백엔드가 이미 거른다.** `GET /api/courses` · `/category/{category}` · 추천 후보에서 종료일이 지난 품목을 제외한다. 단건 조회 `GET /api/courses/{id}` 는 거르지 않는다 — 이미 발주한 건의 상세를 볼 수 있어야 하고 발주 목록 조립에도 쓰인다.

**기업구분 필터는 실질 효과가 없다.** 전수에서 중소기업이 99.33% 다. 화면에 남기더라도 거의 걸러지지 않는다.

## 6. 납품 품질 등록

발주 관리 화면에서 주문 확정 건의 납품 수량과 불량 수량을 등록한다. 화면과 서버는 같은 식을 사용하며 서버 계산값을 최종값으로 저장한다.

```json
// PUT /api/enrollments/{id}/quality
{
  "deliveredQuantity": 200,
  "defectQuantity": 5,
  "defectType": "외관 불량"
}
```

```text
불량률(%) = 불량 수량 ÷ 납품 수량 × 100
200개 중 5개 불량 → 2.50%
```

소수 둘째 자리에서 반올림한다. 불량 수량은 납품 수량을 넘을 수 없으며, 불량이 0이면 불량 유형은 `해당 없음`이어야 한다. 같은 발주의 품질 정보는 다시 저장해 수정할 수 있다.

```json
// 200
{
  "success": true,
  "message": "성공",
  "data": {
    "deliveredQuantity": 200,
    "defectQuantity": 5,
    "defectType": "외관 불량",
    "defectRate": 2.50,
    "updatedAt": "2026-08-11T15:30:00"
  }
}
```

## 7. 공급성과 평가 — QCD

기획안의 핵심 차별점인 **거래 후 데이터를 다음 추천에 재활용하는 피드백 구조**가 여기서 닫힌다.

납품이 끝나면 구매기업이 **발주 관리 화면의 지난 발주**에 결과를 입력한다. 등록하면 그 공급기업의 누적 지표가 갱신되고 다음 추천에 실려 나간다.

```json
// PATCH /api/enrollments/{id}/performance
{
  "deliveredQty": 100,
  "defectQty": 2,
  "defectType": "치수 불량",
  "actualDeliveryDate": "2026-09-08",
  "actualAmount": 100000
}
```

| 필드 | 축 | 필수 | 설명 |
| --- | --- | --- | --- |
| `deliveredQty` | Quality | 필수 | 실제 납품수량. 1 이상 |
| `defectQty` | Quality | 필수 | 불량수량. 0 이상이며 납품수량을 넘을 수 없다 |
| `defectType` | Quality | 선택 | 불량유형. 불량이 없으면 비운다 |
| `actualDeliveryDate` | Delivery | 필수 | 실제 납품일. 발주 때 받은 희망 납품일과 비교한다 |
| `actualAmount` | Cost | 선택 | 실제 청구금액. 비우면 비용 축은 평가하지 않는다 |

**희망 납품일은 다시 받지 않는다.** 발주 시점에 이미 저장했으므로 실제 납품일 하나만 있으면 납기 준수가 계산된다.

```json
// 200 — 응답의 performance 블록
{
  "deliveredQty": 100, "defectQty": 2, "defectType": "치수 불량",
  "defectRate": 2.00,
  "deliveryDate": "2026-09-10", "actualDeliveryDate": "2026-09-08",
  "onTime": true, "delayDays": -2,
  "estimatedTotal": 100000.00, "actualAmount": 100000.00,
  "evaluatedAt": "2026-08-11T02:10:33"
}
```

`delayDays` 는 **음수면 앞당겨 납품한 것**이다. 계산은 서버가 한다. 프론트엔드에 맡기면 화면마다 반올림 자리와 부호 규칙이 달라진다.

### 누적은 공급기업 단위다

평가 한 건이 **그 공급기업의 모든 품목**에 반영된다. 기획안이 "공급업체 품질지표" 라고 정의했고, 품목마다 따로 쌓으면 신규 품목은 언제까지나 실적이 없는 상태로 남아 비교 대상이 되지 못한다.

`GET /api/courses` 와 `GET /api/recommend/{userId}` 응답에 실려 온다.

| 필드 | 뜻 |
| --- | --- |
| `defectRate` | 누적 불량수량 ÷ 누적 납품수량 × 100 |
| `onTimeRate` | 납기 준수 건수 ÷ 평가 건수 × 100 |
| `evaluatedCount` | 평가 건수 |

**`null` 과 `0.00` 은 다르다.** 앞은 아직 평가 이력이 없다는 뜻이고 뒤는 무결점이라는 뜻이다. 화면에서 뭉뚱그리면 신규 공급기업이 무결점 업체로 보인다.

비율을 매번 다시 계산하지 않고 원시 수량을 누적했다가 나눈다. 평균의 평균을 내면 발주 규모가 작은 건이 큰 건과 같은 무게를 갖게 되어 지표가 왜곡된다.

### 규칙

| 상황 | 응답 |
| --- | --- |
| 남의 발주를 평가 | 403 `자신의 발주만 평가할 수 있습니다` |
| `ACTIVE` 가 아닌 발주 | 400 `주문이 확정된 발주만 평가할 수 있습니다` |
| 이미 평가한 발주 | 400 `이미 평가한 발주입니다` |
| 불량수량 > 납품수량 | 400 `불량수량이 납품수량보다 많을 수 없습니다` |

**다시 평가할 수 없다.** 같은 발주를 두 번 등록하면 누적 지표에 두 번 반영되어 공급기업 성과가 왜곡된다. 수정이 필요하면 이전 값을 빼는 처리를 함께 넣은 수정 API 를 따로 만들어야 한다.

희망 납품일이 없는 옛 발주는 납기를 판정할 수 없다. 그 건은 `onTime` 을 `null` 로 두고 **납기 통계에서 제외**한다. 품질 통계에는 그대로 들어간다. 임의로 준수했다고 치면 지표가 거짓이 된다.

## Sprint별 호출 목록

| Sprint | 프론트엔드가 호출할 API | 백엔드 작업 |
| --- | --- | --- |
| **Sprint 1** | `POST /api/users/register`<br>`GET /api/users/me`<br>`POST /api/courses`<br>`GET /api/courses`<br>`GET /api/courses/{id}`<br>`GET /api/courses/category/{category}`<br>`POST /api/enrollments`<br>`GET /api/enrollments/my`<br>`GET /api/payments/user/{userId}`<br>`GET /api/recommend/{userId}` | enrollment-service 발주 상세 저장 |
| **Sprint 2** | `GET /api/courses/my`<br>`PUT /api/courses/{id}/status`<br>`PUT /api/courses/{id}`<br>`PUT /api/enrollments/{id}/quality`<br>`PATCH /api/enrollments/{id}/performance`<br>`GET /api/recommend/{userId}` (응답 확장) | 품목 관리 · 납품 품질 등록<br>공급성과 평가(QCD)<br>recommend-service 점수화 |
