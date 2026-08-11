#!/usr/bin/env python3
"""init-db/Data.csv 를 init-db/02_seed_catalog.sql 로 바꾼다.

왜 SQL 인가
    MariaDB 엔트리포인트는 첫 초기화 때 /docker-entrypoint-initdb.d 의 .sql 과
    .sh 만 실행한다. .csv 는 무시한다. Data.csv 가 그 디렉터리에 마운트되어
    있는데도 적재된 적이 없는 이유가 이것이다.

    SQL 로 만들어 두면 `docker compose up` 만으로 카탈로그가 채워진다.
    새 PC 에서 clone 한 팀원도, `down -v` 로 볼륨을 지운 사람도 명령을 따로
    외울 필요가 없다.

seed-catalog.py 와의 차이
    seed-catalog.py 는 게이트웨이 API 를 호출한다. 실제 사용자와 같은 경로라
    비밀번호가 정상 해시되어 시드 공급기업으로 로그인할 수 있다. 대신 스택이
    떠 있어야 하고 사람이 실행해야 한다.

    이 스크립트가 만든 SQL 은 DB 가 처음 만들어질 때 알아서 돈다. 대신
    비밀번호를 해시할 방법이 SQL 에 없다. CLAUDE.md 가 비밀번호 커밋을
    금지하므로 시드 계정은 **로그인할 수 없는 상태**로 넣는다. 품목의
    instructor_id 가 가리킬 대상이자 공급기업 이름의 출처로만 쓴다.

    시드 공급기업으로 로그인해야 하면 init-db/03_seed_password.sh 를 본다.

사용법
    python3 scripts/generate-seed-sql.py

    Data.csv 를 갱신했으면 다시 돌려 02_seed_catalog.sql 을 함께 커밋한다.
    생성물을 손으로 고치지 않는다 — 다음 생성 때 사라진다.
"""
import csv
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSV_PATH = os.path.join(ROOT, 'init-db', 'Data.csv')
OUT_PATH = os.path.join(ROOT, 'init-db', '02_seed_catalog.sql')

# 아래 두 표는 scripts/seed-catalog.py 와 **같아야 한다.** 한쪽만 고치면
# 두 적재 경로가 서로 다른 카테고리를 넣어 화면 필터가 어긋난다.
CATEGORY_BY_PRODUCT = {
    '파형강관': 'BACKEND',
    '파형강관이음관': 'FRONTEND',
    '피복강관': 'DEVOPS',
    '피복강관이음': 'DATA_SCIENCE',
    '스틸파일': 'MOBILE',
    '주철관': 'SECURITY',
    '주철제관이음': 'DATABASE',
    '커플링식관이음': 'OTHER',
    '융복합관류': 'OTHER',
}

PRODUCT_LABEL = {
    '커플링식관이음': '기타 관류',
    '융복합관류': '기타 관류',
}

SUPPLIER_EMAIL = 'supplier{index:02d}@procurix.local'
BUYER_EMAIL = 'buyer@procurix.local'
BUYER_NAME = '대한건설 구매팀'


def q(value):
    """SQL 문자열 리터럴. 홑따옴표와 역슬래시를 이스케이프한다."""
    text = '' if value is None else str(value)
    return "'" + text.replace('\\', '\\\\').replace("'", "''") + "'"


def build_description(row):
    """vue-frontend/src/utils/procurement.js 의 parseCapability 가 읽는 형식.

    scripts/seed-catalog.py 의 build_description 과 같은 결과를 내야 한다.
    형식이 어긋나면 상세 화면의 스펙 표가 빈칸이 된다.
    """
    return ' | '.join([
        f"공급업체명: {row['공급업체명']}",
        f"공급업체소재지: {row['공급업체소재지']}",
        f"기업구분: {row['기업구분']}",
        f"품명: {PRODUCT_LABEL.get(row['품명'], row['품명'])}",
        f"세부품명: {row['세부품명']}",
        f"품목명: {row['품목명']}",
        f"물품식별번호: {row['물품식별번호']}",
        f"단위: {row['단위']}",
        f"공급지역: {row['공급지역']}",
        f"납품일수: {row['납품일수']}일",
        f"납품장소: {row['납품장소']}",
        f"인도조건: {row['인도조건']}",
        f"인증정보: {row['인증정보'].strip() or '해당 없음'}",
        f"우수제품여부: {row['우수제품여부']}",
        f"MAS여부: {row['MAS여부']}",
        f"계약기간: {row['계약기간']}",
        f"쇼핑몰등록일자: {row['쇼핑몰등록일자']}",
    ])


def contract_end(row):
    """'20260311~20260413' 에서 종료일을 뽑는다.

    형식이 다르면 NULL 로 둔다. 날짜를 지어내면 만료 필터가 거짓말을 한다.
    api-spec.md 의 '형식이 다르면 null 로 두고 만료 판정을 포기한다' 와 같다.
    """
    period = (row.get('계약기간') or '').strip()
    if '~' not in period:
        return 'NULL'
    end = period.split('~')[-1].strip()
    if len(end) != 8 or not end.isdigit():
        return 'NULL'
    return q(f'{end[:4]}-{end[4:6]}-{end[6:]}')


def locked_hash():
    """로그인할 수 없는 자리채움 해시.

    형식은 BCrypt($2a$10$ + 53자)라 user-service 가 읽어도 예외가 나지 않고,
    본문은 어떤 비밀번호의 해시도 아니라 무엇을 넣어도 검증에 실패한다.
    커밋되는 것은 비밀번호가 아니다.

    **고정값이다.** 실행할 때마다 난수를 만들면 Data.csv 를 고치지 않아도
    02_seed_catalog.sql 의 45줄이 매번 바뀌어 diff 를 읽을 수 없다.
    계정별로 달라야 할 이유도 없다 — 어느 것으로도 로그인되지 않는다.
    """
    # 본문은 정확히 53자여야 한다. Spring 의 BCryptPasswordEncoder 는
    # \A\$2(a|y|b)?\$(\d\d)\$[./0-9A-Za-z]{53} 로 형식을 먼저 본다. 길이가
    # 어긋나면 matches() 가 "does not look like BCrypt" 경고를 남긴다.
    # 예외는 아니지만 로그에 잡음이 쌓이고 진짜 문제를 가린다.
    body = 'SeedAccountNoLoginPlaceholderHashDoNotUseThisAsPassword'[:53]
    assert len(body) == 53, f'자리채움 해시 본문이 53자가 아니다: {len(body)}'
    return f'$2a$10${body}'


def main():
    if not os.path.exists(CSV_PATH):
        print(f'CSV 를 찾을 수 없다: {CSV_PATH}', file=sys.stderr)
        return 1

    with open(CSV_PATH, encoding='utf-8-sig') as handle:
        rows = list(csv.DictReader(handle))

    unknown = {r['품명'] for r in rows} - set(CATEGORY_BY_PRODUCT)
    if unknown:
        print(f'CATEGORY_BY_PRODUCT 에 없는 품명: {sorted(unknown)}', file=sys.stderr)
        print('seed-catalog.py 와 함께 표를 채운 뒤 다시 실행한다.', file=sys.stderr)
        return 1

    # 등장 순서를 유지해야 재생성 때 supplierNN 번호가 흔들리지 않는다.
    suppliers = list(dict.fromkeys(r['공급업체명'] for r in rows))
    email_of = {name: SUPPLIER_EMAIL.format(index=i) for i, name in enumerate(suppliers, start=1)}

    out = []
    w = out.append
    w('-- 이 파일은 생성물이다. 손으로 고치지 않는다.')
    w('-- 생성: python3 scripts/generate-seed-sql.py  (원본 init-db/Data.csv)')
    w('--')
    w('-- DB 가 처음 만들어질 때 한 번 실행된다. 이미 데이터가 있는 볼륨에서는')
    w('-- 엔트리포인트가 이 디렉터리를 아예 보지 않으므로 다시 돌지 않는다.')
    w('-- 그래도 손으로 흘려 넣는 경우를 대비해 전부 중복을 건너뛰도록 썼다.')
    w(f'-- 공급기업 {len(suppliers)}곳 · 품목 {len(rows)}건')
    w('')
    w('-- 시드 공급기업 계정. 비밀번호는 원문을 알 수 없는 해시라 로그인되지 않는다.')
    w('-- 품목의 instructor_id 가 가리킬 대상이자 공급기업 이름의 출처다.')
    w('-- 로그인이 필요하면 init-db/03_seed_password.sh 를 본다.')

    for name in suppliers:
        w(
            'INSERT IGNORE INTO users (email, password, name, role, created_at, updated_at) '
            f'VALUES ({q(email_of[name])}, {q(locked_hash())}, {q(name)}, \'INSTRUCTOR\', NOW(6), NOW(6));'
        )

    w('')
    w('-- 구매기업 계정도 같은 이유로 로그인되지 않는다. 화면에서 직접 가입해 쓴다.')
    w(
        'INSERT IGNORE INTO users (email, password, name, role, created_at, updated_at) '
        f'VALUES ({q(BUYER_EMAIL)}, {q(locked_hash())}, {q(BUYER_NAME)}, \'STUDENT\', NOW(6), NOW(6));'
    )

    w('')
    w('-- 조달 품목. instructor_id 는 이메일로 찾는다. AUTO_INCREMENT 값을 가정하면')
    w('-- 기존 데이터가 있는 DB 에서 엉뚱한 공급기업에 품목이 붙는다.')
    w('--')
    w('-- 중복 방지를 파생 테이블 NOT EXISTS 로 감싼 이유 — MariaDB 는 INSERT ... SELECT 의')
    w('-- 서브쿼리에서 대상 테이블(courses)을 직접 참조하지 못한다(에러 1093). 한 단계')
    w('-- 감싸면 통과한다. courses(instructor_id, title) 에 UNIQUE 를 두고 INSERT IGNORE')
    w('-- 로 가면 의도가 더 분명하지만, 그건 init-db 스키마 변경이라 별도 작업이다.')
    w('--')
    w('-- 누적 성과지표 컬럼을 전부 0 으로 적어 넣는다. 기본값에 기대지 않는다 —')
    w('-- 실행 중인 DB 의 courses 는 01_init.sql 이 아니라 Hibernate ddl-auto 가')
    w('-- 만든 것이라 evaluated_count 같은 NOT NULL 컬럼에 DEFAULT 가 없다.')
    w('-- 비워 두면 INSERT 가 1364 로 거부되고, 금액 컬럼은 NULL 로 들어가')
    w('-- 첫 성과 평가에서 NPE 가 난다(Course.applyPerformance 에 널 가드가 없다).')

    for row in rows:
        name = row['공급업체명']
        email = email_of[name]
        w(
            'INSERT INTO courses '
            '(title, description, category, price, instructor_id, instructor_name, '
            'contract_end, enrollment_count, status, '
            'total_delivered_qty, total_defect_qty, evaluated_count, on_time_count, '
            'total_estimated_amount, total_actual_amount, created_at, updated_at)\n'
            f'SELECT {q(row["품목명"])}, {q(build_description(row))}, '
            f'{q(CATEGORY_BY_PRODUCT[row["품명"]])}, {row["단가"] or 0}, '
            f'u.id, {q(name)}, {contract_end(row)}, 0, \'ACTIVE\', '
            '0, 0, 0, 0, 0, 0, NOW(6), NOW(6)\n'
            f'  FROM users u WHERE u.email = {q(email)}\n'
            '   AND NOT EXISTS (SELECT 1 FROM (SELECT 1) t WHERE EXISTS '
            f'(SELECT 1 FROM courses c WHERE c.title = {q(row["품목명"])} AND c.instructor_id = u.id));'
        )

    # 적재 결과를 스스로 알린다.
    #
    # 품목 INSERT 는 이메일로 공급기업을 찾는데, 그 계정이 없으면 **오류 없이 0행**이
    # 들어간다. 계정 INSERT 가 IGNORE 라 같은 이메일이 다른 이름으로 이미 있어도
    # 조용히 넘어간다. 실패가 눈에 띄지 않는 조합이라 건수를 찍어 준다.
    w('')
    w('-- 적재 결과 확인. 기대값과 다르면 공급기업 계정이 없거나 이미 들어가 있는 것이다.')
    w(f"SELECT '시드 공급기업' AS 항목, COUNT(*) AS 건수, {len(suppliers)} AS 기대값"
      "  FROM users WHERE email LIKE 'supplier%@procurix.local'")
    w("UNION ALL")
    w(f"SELECT '시드 품목', COUNT(*), {len(rows)}"
      "  FROM courses c JOIN users u ON u.id = c.instructor_id"
      " WHERE u.email LIKE 'supplier%@procurix.local';")

    with open(OUT_PATH, 'w', encoding='utf-8') as handle:
        handle.write('\n'.join(out) + '\n')

    print(f'{OUT_PATH} 생성')
    print(f'  공급기업 {len(suppliers)}곳 · 품목 {len(rows)}건')
    return 0


if __name__ == '__main__':
    sys.exit(main())
