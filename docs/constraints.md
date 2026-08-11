# 검증된 제약

이 문서는 **바꿀 수 없는 것**과 **바꾸려면 대가가 있는 것**을 기록한다. 전부 기동 중인 컨테이너와 이미지 내부를 직접 확인한 결과이며, 추측이 아니다.

기획하다 막히면 여기를 먼저 본다. 대부분의 막힘은 아래 다섯 개 중 하나에 부딪힌 것이다.

## 1. 인프라 3종은 소스가 없다

`eureka-server`는 소스가 있지만 수정 대상이 아니고, `auth-server`와 `api-gateway`는 **소스 자체가 저장소에 없다.** `infra-images.tar`의 빌드된 이미지로만 제공된다.

```text
msa-lecture/
├── eureka-server/          소스 있음. 수정 대상 아님
├── auth-server/            없음 ← 이미지만 존재
├── api-gateway/            없음 ← 이미지만 존재
├── course-service/         수정 대상
├── enrollment-service/     수정 대상
├── payment-service/        수정 대상
├── recommend-service/      수정 대상
├── vue-frontend/           수정 대상
└── init-db/                수정 대상
```

"수정하지 말라"가 아니라 **수정할 방법이 없다.** 기획을 여기에 부딪히게 설계하면 시간을 잃는다.

## 2. API 경로가 게이트웨이에 고정되어 있다

api-gateway 기동 로그의 `RouteDefinition` 덤프에서 확인한 라우팅이다.

| 경로 | 대상 |
| --- | --- |
| `/api/users/**` | user-service |
| `/api/courses/**` | course-service |
| `/api/enrollments/**` | enrollment-service |
| `/api/payments/**` | payment-service |
| `/api/recommend/**` | recommend-service |
| `/oauth2/**` `/login` `/logout` `/userinfo` `/.well-known/**` | auth-server |

**catch-all 라우트가 없다.** 그래서 두 가지가 불가능하다.

- 기존 경로 이름을 바꾸는 것. `/api/courses` → `/api/products` 로 바꾸면 게이트웨이가 라우팅하지 못해 프론트에서 즉시 404가 된다.
- 신규 서비스를 새 경로로 노출하는 것. 새 기능은 기존 경로 하위에 붙인다. 예를 들어 품질검사 등록은 `PATCH /api/enrollments/{id}/quality` 처럼 만든다.

**도메인 치환은 화면에 보이는 말과 DB 컬럼 의미만 바꾸는 것이다.** 경로는 원본을 그대로 쓴다. 이 저장소에서 가장 흔한 실수다.

## 3. 프론트엔드 포트는 3000 이어야 한다

auth-server 이미지 내부 `BOOT-INF/classes/com/lecture/auth/config/AuthorizationServerConfig.class` 에 다음이 하드코딩되어 있다.

```text
web-client
http://localhost:3000/callback      redirect URI
http://localhost:3000/              post-logout redirect URI
```

jar 내부 `application.yml` 에 `spring.security.oauth2.authorizationserver.client.*` 속성이 **없다.** 그래서 `docker-compose.yml` 환경변수로도 덮어쓸 수 없다.

실제 확인 결과:

```text
redirect_uri=http://localhost:3000/callback → 302 (정상)
redirect_uri=http://localhost:3001/callback → 400 (거부)
```

포트를 3001로 바꾸면 화면은 뜨지만 **로그인이 거부되고, 라우터 가드가 걸린 화면 전부가 막힌다.** 랜딩과 로그인 화면만 남는다.

다른 프로젝트가 3000을 쓰고 있으면 **그 프로젝트를 옮긴다.** 이쪽은 선택의 여지가 없다.

`vite.config.js` 에 `strictPort: true` 가 있어서 3000이 점유되어 있으면 `Port 3000 is already in use` 로 기동 자체가 실패한다. 점유 프로세스는 이렇게 확인한다.

```bash
lsof -nP -iTCP:3000 -sTCP:LISTEN
```

## 4. 사용자 역할은 2종뿐이다

`STUDENT` 와 `INSTRUCTOR` 만 있다. JWT의 role 클레임을 auth-server가 발급하므로 **제3의 역할을 추가할 수 없다.**

이해관계자가 셋 이상인 도메인을 기획할 때는 **운영자를 Pain Point 기술에만 등장시키고 로그인 역할로는 만들지 않는다.** 두 역할에 팀 도메인 이름을 씌우는 방식으로 처리한다.

| 원본 | 조달 플랫폼 |
| --- | --- |
| `INSTRUCTOR` | 공급기업 |
| `STUDENT` | 구매기업 |

## 5. 한 사용자가 한 항목을 한 번만 신청할 수 있다

`init-db/01_init.sql` 과 `Enrollment` 엔티티 양쪽에 제약이 있다.

```sql
UNIQUE KEY uq_user_course (user_id, course_id)
```

재구매·반복 발주·재실행이 핵심인 도메인은 **두 번째 요청부터 DB 에러가 난다.** 기획 단계에서 "한 항목을 한 번" 구조로 정의해 회피하는 것이 기본이다. 정말 필요하면 `init-db` 와 엔티티의 제약을 함께 풀어야 하고, 이때 `docker compose down -v` 로 볼륨을 지워야 DDL이 다시 적용된다.

## 대가가 있는 것

바꿀 수는 있지만 자바 수정과 재빌드가 필요한 항목이다.

### 결제 금액이 고정되어 있다

`enrollment-service/src/main/java/com/lecture/enrollment/service/EnrollmentService.java` 에서 발주 시 결제 금액이 하드코딩되어 있다.

```java
paymentServiceClient.requestPayment(userId, courseId, BigDecimal.valueOf(99000));
```

주문마다 금액이 다른 도메인이면 결제 내역에 실제와 다른 값이 남는다. 선택지는 셋이다.

| 방법 | 자바 수정 | 결과 |
| --- | --- | --- |
| 카탈로그 단가를 조회해 넘긴다 | 필요 | 기획안대로 동작 |
| 시연용 단가를 99,000원에 맞춘다 | 없음 | 금액이 하나로 고정 |
| 화면에만 계산값을 표시한다 | 없음 | 결제 내역과 불일치. 발표에서 지적될 수 있다 |

**어느 쪽으로 갈지 Sprint1 Planning에서 정하고 이 문서에 기록한다.**

### 추천용 조달 조건은 발주와 별개다

`POST /api/enrollments` 는 수량·희망납품일·납품장소·요청사항·담당자 정보를 받아 저장한다. 예상 견적은 서버가 카탈로그 단가 × 수량으로 계산한다.

검색 화면의 총예산·최대 허용 불량률 같은 추천 조건은 발주 상세와 다른 입력이다. 현재는 프론트엔드 상태에서만 필터링하며, recommend-service 요청으로 전달하려면 추천 API 계약을 별도로 확장해야 한다.

### 엔티티에 없는 필드

`courses` 테이블에는 가공 가능 소재, 가공 방식, 최대 생산량, 평균 납기, 보유 인증에 해당하는 컬럼이 없다.

컬럼을 추가하기 전에 **`description` 자유 텍스트에 구분자로 담고 프론트엔드에서 파싱해 표로 표시**하는 방법을 먼저 검토한다. `description` 은 `TEXT` 라 길이 제약이 사실상 없다.

## 카테고리 enum 슬롯

백엔드 `Course.Category` 는 8개 값으로 고정되어 있다.

```text
BACKEND · FRONTEND · DEVOPS · DATA_SCIENCE · MOBILE · SECURITY · DATABASE · OTHER
```

**이 값들은 의미 없는 슬롯으로 취급한다.** 프론트엔드가 화면 라벨로 변환해서 보여주므로 도메인 값을 여기에 배정하면 된다.

라벨을 정의하는 곳이 네 군데이고, 서로 어긋나면 배지가 회색으로 떨어지거나 영문 enum이 화면에 그대로 노출된다.

| 파일 | 키 | 주의 |
| --- | --- | --- |
| `vue-frontend/src/store/course.js` `categories` | 화면 라벨 | 첫 항목 `'전체'` 는 필터 초기값이므로 남긴다 |
| `vue-frontend/src/store/course.js` `categoryLabelMap` | **백엔드 enum** | 8칸을 빠짐없이 채운다. 없는 키는 영문이 노출된다 |
| `vue-frontend/src/store/course.js` `categoryThumbnailMap` | 화면 라벨 | 위에서 정한 라벨과 철자까지 같아야 한다 |
| `vue-frontend/src/components/CourseCard.vue` `categoryConfig` | 화면 라벨 | 빠지면 회색 배지 + 썸네일 없음 |
| `vue-frontend/src/views/CourseCreateView.vue` `categoryOptions` | `label`은 문구, `value`는 **enum** | value 를 새 값으로 바꾸지 않는다 |

원본 상태에서 이미 어긋나 있다. `categoryLabelMap` 에 백엔드에 없는 `DATA` 와 `AI` 키가 있고, 실제로 저장되는 `DATA_SCIENCE` `MOBILE` `SECURITY` `DATABASE` `OTHER` 는 매핑이 없다. **8칸을 채우면 이 결함이 함께 해소된다.**

## Maven Central 429

같은 강의장 네트워크는 공용 IP를 쓴다. 여러 명이 동시에 Gradle 빌드를 돌리면 Maven Central이 그 IP를 차단한다.

```text
Could not GET 'https://repo.maven.apache.org/maven2/...'
Received status code 429 from server: Too Many Requests
```

**코드 문제가 아니다.** 이 저장소는 Google이 운영하는 Maven Central 미러로 우회하도록 설정되어 있다.

- `<서비스>/gradle/mirror-init.gradle` — 미러 저장소 설정
- 각 `Dockerfile` 의 gradlew 호출에 `-I gradle/mirror-init.gradle`

미러가 불필요해지면 두 곳을 함께 되돌린다. 한쪽만 지우면 빌드가 깨진다.

`--no-cache` 재빌드는 매번 의존성을 전부 다시 받게 만들어 429를 유발한다. **변경한 서비스만 다시 올린다.**

```bash
docker compose up -d --build course-service
```

## Swagger 접속 경로

게이트웨이 경유로는 문서를 볼 수 없다. 실습 가이드의 안내와 다르며, 직접 확인한 결과다.

| 대상 | 경로 | 결과 |
| --- | --- | --- |
| user / course / enrollment / payment | `:8081~8084/swagger-ui.html` | 정상 |
| 같은 서비스들의 OpenAPI JSON | `:8081~8084/api-docs` | 정상 |
| recommend | `:8085/docs` | 정상 (FastAPI) |
| 게이트웨이 경유 UI | `:8080/swagger-ui.html` | 401 |
| 게이트웨이 경유 JSON | `:8080/api-docs` | 200 이지만 `"paths":{}` 빈 문서 |

springdoc 경로가 커스터마이즈되어 있어 기본값 `/v3/api-docs` 가 아니라 **`/api-docs`** 다.

**문서 확인은 개별 포트, 실제 호출은 게이트웨이(8080)** 로 한다. Swagger의 Try it out은 개별 포트 기준으로 호출되므로 인증과 CORS 동작이 프론트엔드와 다르다.

## 미결 사항

사슬의 두 칸이 어긋났는데 즉시 해소하지 못한 항목을 여기에 적는다. **양쪽 원문을 인용한다.** 조용히 지나가지 않는다.

| 발견일 | 어긋난 두 칸 | 내용 | 상태 |
| --- | --- | --- | --- |
| 2026-08-10 | 기획안 ↔ enrollment-service | 기획안은 주문별 금액(480만원 등)을 전제하지만 코드는 99,000원 고정 | 미결 — Sprint1 Planning에서 결정 |
| 2026-08-10 | 기획안 ↔ recommend-service | 기획안 7.2의 `추천점수`·`추천 해석` 이 `RecommendResponse` 에 없음 | Sprint2 작업으로 계획 |
| 2026-08-10 | 기획안 ↔ API | 기획안 7.3의 품질검사 등록에 해당하는 엔드포인트가 없음 | Sprint2 작업으로 계획 |
| 2026-08-11 | SecurityConfig 주석 ↔ 실제 토큰 | 네 서비스가 전부 `permitAll` 이라 개별 포트로 인증을 우회할 수 있다. 주석 처리된 리소스 서버 설정을 켜면 issuer 불일치로 전면 401 | 미결 — Sprint1 범위 밖. 아래 참조 |

### 서비스 인증이 게이트웨이 한 곳에만 있다

발견일 2026-08-11. 개별 포트가 호스트에 열려 있어 **게이트웨이를 거치지 않으면 인증이 없다.**

```bash
curl http://localhost:8081/api/users/1
→ {"success":true,"message":"성공","data":{"id":1,"email":"student@lecture.com","name":"홍길동","role":"STUDENT"}}
```

토큰 없이 남의 계정 정보가 나온다. `/api/users/me` 는 게이트웨이가 넣어주는 `X-User-Id` 를 그대로 믿으므로 헤더를 직접 붙이면 아무 사용자로도 조회된다.

네 서비스의 현재 설정이다.

```text
user-service       SecurityConfig.java:36  .anyRequest().permitAll()
course-service     SecurityConfig.java:29  .anyRequest().permitAll()
enrollment-service SecurityConfig.java:28  .anyRequest().permitAll()
payment-service    SecurityConfig.java:28  .anyRequest().permitAll()
```

`user-service/src/main/java/com/lecture/user/config/SecurityConfig.java` 41~83행에 더 엄격한 설정이 통째로 주석 처리되어 있다. 원문이다.

```java
//             .authorizeHttpRequests(auth -> auth
//                 // 회원가입은 인증 불필요
//                 .requestMatchers("/api/users/register").permitAll()
//                 .requestMatchers(
//                     "/api-docs/**",
//                     "/swagger-ui/**",
//                     "/swagger-ui.html"
//                 ).permitAll()
//                 // 내부 서비스 호출 (Client Credentials)
//                 .requestMatchers("/api/users/internal/**").hasAuthority("SCOPE_service.read")
//                 // 나머지는 인증 필요
//                 .anyRequest().authenticated()
//             )
//             .oauth2ResourceServer(oauth2 -> oauth2
//                 .jwt(jwt -> {}) // application.yml jwk-set-uri 사용
//             );
```

**이 주석을 그대로 풀면 로그인 전체가 죽는다.** issuer 가 어긋나 있다.

`docker-compose.yml` 147행이 주입하는 값이다.

```yaml
- SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://auth-server:9000
```

실제 발급된 액세스 토큰의 페이로드다.

```json
{"sub":"18","aud":"web-client","nbf":1786408659,"role":"STUDENT","user_id":18,
 "scope":["read","openid","profile","write"],"iss":"http://localhost:8080",
 "name":"검증계정","exp":1786412259,"iat":1786408659,"email":"verify-92797@t.local"}
```

`iss` 가 게이트웨이 주소인 `http://localhost:8080` 이다. 게이트웨이를 경유해 발급받기 때문이며 auth-server 이미지 동작이라 바꿀 수 없다. 설정값과 다르므로 리소스 서버를 켜는 즉시 모든 요청이 401 이 된다.

`jwk-set-uri` 는 문제가 아니다. yml 의 `http://localhost:9000` 은 컨테이너 안에서 닿지 않지만 `docker-compose.yml` 146행이 `http://auth-server:9000/oauth2/jwks` 로 덮어쓴다. 컨테이너 내부에서 확인했다.

```text
wget http://localhost:9000/oauth2/jwks    → Connection refused
wget http://auth-server:9000/oauth2/jwks  → {"keys":[{"kty":"RSA",...
```

**확인하지 못한 것** — 게이트웨이가 하위 서비스로 `Authorization` 헤더를 전달하는지 모른다. 게이트웨이는 소스가 없고, 네 서비스가 전부 `permitAll` 이라 간접 확인도 되지 않는다. 전달하지 않는다면 issuer 를 맞춰도 `.anyRequest().authenticated()` 에서 전부 401 이 된다. 컨트롤러에 헤더 로깅을 임시로 넣고 한 번 호출하면 알 수 있다.

주석 블록이 보호하려는 `/api/users/internal/**` 는 **현재 호출하는 곳이 없다.** `course-service/src/main/resources/application.yml` 에 `user-service.url` 설정만 있고 실제 호출 코드는 없다.

Sprint1 은 한 흐름을 끝까지 동작시키는 것이므로 이 항목은 범위 밖으로 둔다. **모르고 넘어간 것이 아니라 알고 미룬 것이다.**
