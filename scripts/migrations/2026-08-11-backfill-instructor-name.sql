-- 2026-08-11  courses.instructor_name 백필
--
-- 왜 필요한가
--   공급기업 이름을 등록 시점에 courses 에 저장하기 시작했다. ddl-auto: update 가
--   컬럼은 추가해 주지만 **값은 채우지 않는다.** 이 변경 이전에 등록된 품목은
--   instructor_name 이 NULL 이고, 화면은 course.instructorName 이 비면
--   '공급기업' 으로 떨어진다. 이미 262건이 들어 있는 DB 에서는 전부 그렇게 보인다.
--
-- 왜 손으로 돌려야 하는가
--   서버는 등록 시점에만 조회한다. 조회 때마다 부르면 목록 한 번에 품목 수만큼
--   호출이 나가기 때문이다(262품목이면 262번).
--
-- 다시 시드하는 것으로도 되지만 그러면 발주·결제 이력이 함께 사라진다.
-- 이 문은 이름만 채운다.
--
-- 실행
--   docker compose exec -T mariadb mariadb -umanager -pSqlDba-1 lecture_db \
--     < scripts/migrations/2026-08-11-backfill-instructor-name.sql

UPDATE courses c
    JOIN users u ON u.id = c.instructor_id
SET c.instructor_name = u.name
WHERE c.instructor_name IS NULL;

SELECT COUNT(*) AS 전체, COUNT(instructor_name) AS 이름있음 FROM courses;
