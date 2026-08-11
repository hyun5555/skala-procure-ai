-- 온라인 강의 플랫폼 초기 DDL
-- Spring JPA ddl-auto: update 로도 생성되지만
-- 명시적 DDL로 테이블 선후 관계를 문서화

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    role        VARCHAR(20)     NOT NULL COMMENT 'STUDENT | INSTRUCTOR',
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 강사가 강의 개설 (instructor_id → users.id)
CREATE TABLE IF NOT EXISTS courses (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    title            VARCHAR(255)    NOT NULL,
    description      TEXT,
    category         VARCHAR(50)     NOT NULL COMMENT 'BACKEND|FRONTEND|DEVOPS|DATA_SCIENCE|MOBILE|SECURITY|DATABASE|OTHER',
    price            DECIMAL(10,2)   NOT NULL,
    instructor_id    BIGINT          NOT NULL,
    instructor_name  VARCHAR(100)             COMMENT '등록 시 user-service 에서 조회해 저장. 조회 실패 시 NULL',
    contract_end     DATE                     COMMENT '조달 계약 종료일. 지나면 목록에서 제외',
    enrollment_count INT             NOT NULL DEFAULT 0,
    -- 공급기업 누적 성과지표(QCD). 같은 공급기업의 모든 품목이 같은 값을 갖는다
    total_delivered_qty    BIGINT        NOT NULL DEFAULT 0,
    total_defect_qty       BIGINT        NOT NULL DEFAULT 0,
    evaluated_count        INT           NOT NULL DEFAULT 0,
    on_time_count          INT           NOT NULL DEFAULT 0,
    defect_rate            DECIMAL(5,2)            COMMENT 'Q — 누적 불량률(%). 평가 이력 없으면 NULL',
    on_time_rate           DECIMAL(5,2)            COMMENT 'D — OTD 납기 준수율(%). 평가 이력 없으면 NULL',
    total_estimated_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    total_actual_amount    DECIMAL(19,2) NOT NULL DEFAULT 0,
    cost_variance_rate     DECIMAL(7,2)            COMMENT 'C — PPV 견적 대비 증감률(%). 양수면 초과',
    status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | INACTIVE',
    created_at       DATETIME(6),
    updated_at       DATETIME(6),
    PRIMARY KEY (id),
    FOREIGN KEY (instructor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 수강생이 수강 신청 (user_id → users.id, course_id → courses.id)
CREATE TABLE IF NOT EXISTS enrollments (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    course_id       BIGINT          NOT NULL,
    quantity        BIGINT          NOT NULL,
    unit            VARCHAR(20)     NOT NULL,
    delivery_date   DATE            NOT NULL,
    delivery_place  VARCHAR(100)    NOT NULL,
    notes           VARCHAR(300),
    contact_name    VARCHAR(30)     NOT NULL,
    contact_phone   VARCHAR(30)     NOT NULL,
    estimated_total DECIMAL(19,2)   NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 결제대기 | SHIPPING 배송중 | DELIVERED 납품완료 | ACTIVE 레거시(SHIPPING 도입 전 결제분) | CANCELLED 취소',
    -- 공급성과 평가(QCD). 납품 후 구매기업이 입력한다. 평가 전에는 전부 NULL
    delivered_qty        BIGINT              COMMENT 'Quality — 납품수량',
    defect_qty           BIGINT              COMMENT 'Quality — 불량수량',
    defect_type          VARCHAR(100)        COMMENT 'Quality — 불량유형',
    defect_rate          DECIMAL(5,2)        COMMENT 'Quality — 불량률(%)',
    actual_delivery_date DATE                COMMENT 'Delivery — 실제 납품일',
    on_time              BOOLEAN             COMMENT 'Delivery — 납기 준수 여부. 판정 불가면 NULL',
    delay_days           INT                 COMMENT 'Delivery — 지연일수. 음수면 앞당겨 납품',
    actual_amount        DECIMAL(19,2)       COMMENT 'Cost — 실제 청구금액. 선택 입력',
    evaluated_at         DATETIME(6)         COMMENT '평가 등록 시각',
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_course (user_id, course_id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 수강 확정 후 결제 (user_id → users.id, course_id → courses.id)
CREATE TABLE IF NOT EXISTS payments (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    course_id       BIGINT          NOT NULL,
    amount          DECIMAL(19,2)   NOT NULL COMMENT '발주 견적(단가×수량). enrollments.estimated_total 과 같은 폭',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | COMPLETED | FAILED | CANCELLED',
    transaction_id  VARCHAR(255)    UNIQUE,
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
