-- 2026-08-11  payments.amount 자릿수 확장
--
-- 왜 필요한가
--   결제 금액이 99,000원 고정에서 발주 견적(단가 × 수량)으로 바뀌었다.
--   DECIMAL(10,2) 는 99,999,999.99 가 한계인데 이 데이터의 최고 단가가
--   25,247,100원이라 넉 대만 주문해도 넘친다.
--
-- 왜 손으로 돌려야 하는가
--   ddl-auto: update 는 없는 컬럼을 추가할 뿐 **기존 컬럼의 폭은 넓히지 않는다.**
--   서비스를 다시 빌드해도 이 문은 실행되지 않는다.
--
-- 안 돌리면 무슨 일이 생기는가
--   큰 발주에서 MariaDB 가 'Out of range value' 로 거부한다. 그런데 발주 행은
--   결제보다 먼저 독립 트랜잭션으로 커밋되므로 결제 없는 PENDING 발주만 남고,
--   화면은 결제 처리 중에서 멈춘다. 원인이 자기 DB 컬럼 폭이라는 것을 알아채기 어렵다.
--
-- 값이 사라지지 않는 확장이라 볼륨을 지울 필요는 없다.
--
-- 실행
--   docker compose exec -T mariadb mariadb -umanager -pSqlDba-1 lecture_db \
--     < scripts/migrations/2026-08-11-payment-amount-precision.sql

ALTER TABLE payments
    MODIFY amount DECIMAL(19,2) NOT NULL
    COMMENT '발주 견적(단가×수량). enrollments.estimated_total 과 같은 폭';

SELECT COLUMN_TYPE AS payments_amount
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'lecture_db' AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'amount';
