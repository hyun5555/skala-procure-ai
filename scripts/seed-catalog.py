#!/usr/bin/env python3
"""init-db/Data.csv 를 실제 계정과 조달 품목으로 등록한다.

데모 모드를 끄면 화면을 채우던 demo.js 상수가 사라진다. 그때 보여 줄 데이터를
DB 에 넣는 것이 이 스크립트다. 자바도 DDL 도 건드리지 않고 API 만 호출하므로
`docker compose down -v` 가 필요 없고 기존 데이터도 지우지 않는다.

하는 일
    1. CSV 의 공급업체마다 INSTRUCTOR 계정을 만든다
    2. 각 공급업체로 로그인해 액세스 토큰을 받는다
    3. 그 토큰으로 자기 품목을 POST /api/courses 로 등록한다
    4. 구매기업(STUDENT) 계정을 하나 만든다

전부 게이트웨이(8080)를 거친다. 개별 포트로 직접 넣으면 더 간단하지만
그건 서비스가 permitAll 이라 뚫리는 것이고(docs/constraints.md 참조),
그 구멍이 막히는 순간 스크립트가 깨진다. 실제 사용자와 같은 경로로 넣는다.

사용법
    SEED_PASSWORD='고른비밀번호' python3 scripts/seed-catalog.py
    SEED_PASSWORD='...' python3 scripts/seed-catalog.py --force   # 이미 품목이 있어도 추가

비밀번호를 코드에 두지 않는 이유는 CLAUDE.md 가 비밀번호 커밋을 금지하기
때문이다. 팀에서 하나로 정해 각자 환경변수로 넣는다. 재실행할 때 같은 값을
써야 기존 계정으로 로그인된다.

이미 있는 계정은 건너뛰고 로그인만 한다. 품목에는 중복 제약이 없어서
그냥 다시 실행하면 같은 품목이 두 번 쌓인다. 그래서 품목이 하나라도 있으면
--force 없이는 멈춘다.
"""
import argparse
import csv
import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

GATEWAY = os.environ.get('SEED_GATEWAY', 'http://localhost:8080')
CLIENT_ID = 'web-client'
CLIENT_SECRET = 'web-secret'
REDIRECT_URI = 'http://localhost:3000/callback'

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSV_PATH = os.path.join(ROOT, 'init-db', 'Data.csv')

# 백엔드 enum 은 의미 없는 슬롯이다. 품명을 슬롯에 배정하고 화면 라벨은
# vue-frontend/src/store/course.js 의 categoryLabelMap 이 붙인다.
# 한쪽만 바꾸면 영문 enum 이 화면에 노출되므로 함께 확인한다.
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

# 백엔드 enum 8칸에 품명 9종을 넣느라 OTHER 를 둘이 나눠 쓴다. 화면 드롭다운은
# 그 둘을 '기타 관류' 하나로 보여주므로, description 의 품명도 같은 라벨로 적어야
# 필터가 걸린다. vue-frontend/src/utils/procurement.js 의 productOptions 와 맞춘다.
PRODUCT_LABEL = {
    '커플링식관이음': '기타 관류',
    '융복합관류': '기타 관류',
}

BUYER = {'email': 'buyer@procurix.local', 'name': '대한건설 구매팀', 'role': 'STUDENT'}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """인가 코드는 Location 헤더에 실려 온다. 따라가면 코드를 놓친다."""

    def redirect_request(self, *args):
        return None


def request(url, data=None, headers=None, opener=None, form=False):
    """(status, body, location) 를 돌려준다. 4xx·3xx 도 예외로 만들지 않는다."""
    body = None
    headers = dict(headers or {})
    if data is not None:
        if form:
            body = urllib.parse.urlencode(data).encode()
            headers['Content-Type'] = 'application/x-www-form-urlencoded'
        else:
            body = json.dumps(data).encode()
            headers['Content-Type'] = 'application/json'

    req = urllib.request.Request(url, data=body, headers=headers)
    send = (opener or urllib.request.build_opener()).open
    try:
        with send(req) as res:
            return res.status, res.read().decode(), res.headers.get('Location')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(), e.headers.get('Location')


def register(email, name, role, password):
    """계정을 만든다. 이미 있으면 True 를 그대로 돌려준다."""
    status, body, _ = request(
        f'{GATEWAY}/api/users/register',
        {'email': email, 'password': password, 'name': name, 'role': role},
    )
    if status == 201:
        return True
    if status == 400 and '이미 사용 중인 이메일' in body:
        return True
    print(f'  !! 계정 생성 실패 {email} — {status} {body}', file=sys.stderr)
    return False


def login(email, password):
    """OAuth2 Authorization Code Flow 를 돌려 액세스 토큰을 받는다."""
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(jar), NoRedirect
    )

    status, _, _ = request(
        f'{GATEWAY}/login',
        {'username': email, 'password': password},
        opener=opener,
        form=True,
    )
    if status != 302:
        print(f'  !! 로그인 실패 {email} — {status}', file=sys.stderr)
        return None

    params = urllib.parse.urlencode({
        'response_type': 'code',
        'client_id': CLIENT_ID,
        'redirect_uri': REDIRECT_URI,
        'scope': 'openid profile read write',
    })
    _, _, location = request(f'{GATEWAY}/oauth2/authorize?{params}', opener=opener)
    if not location or 'code=' not in location:
        print(f'  !! 인가 코드 없음 {email} — {location}', file=sys.stderr)
        return None
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location).query)['code'][0]

    import base64
    basic = base64.b64encode(f'{CLIENT_ID}:{CLIENT_SECRET}'.encode()).decode()
    status, body, _ = request(
        f'{GATEWAY}/oauth2/token',
        {'grant_type': 'authorization_code', 'code': code, 'redirect_uri': REDIRECT_URI},
        headers={'Authorization': f'Basic {basic}'},
        form=True,
    )
    if status != 200:
        print(f'  !! 토큰 교환 실패 {email} — {status} {body}', file=sys.stderr)
        return None
    return json.loads(body).get('access_token')


def build_description(row):
    """vue-frontend/src/utils/procurement.js 의 parseCapability 가 읽는 형식.

    ' | ' 로 나누고 첫 ':' 로 키와 값을 가른다. 키 이름이 specAliases 와
    어긋나면 상세 화면의 해당 칸이 빈다.

    공급업체명을 맨 앞에 넣는다. CourseResponse 에 instructorName 이 없던 시절의
    유일한 전달 경로였다.

    **course-service 가 instructorName 을 채우기 시작하면 그쪽이 우선이다.**
    여기 값은 등록 시점에 박히므로 공급기업이 이름을 바꿔도 따라가지 않는다.
    화면은 course.instructorName 을 먼저 보고 없을 때만 이 값을 쓴다.
    """
    return ' | '.join([
        f"공급업체명: {row['공급업체명']}",
        f"공급업체소재지: {row['공급업체소재지']}",
        f"기업구분: {row['기업구분']}",
        # 화면 드롭다운(productOptions)에 없는 품명은 그 라벨로 바꿔 적는다.
        # OTHER 슬롯을 나눠 쓰는 커플링식관이음·융복합관류가 그렇다. 원본 그대로 두면
        # 드롭다운에서 '기타 관류' 를 골라도 필터가 원본값과 비교해 0건이 나온다.
        # 이 파일이 라벨을 정하는 여섯 번째 자리다 — constraints.md 의 표를 함께 본다.
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


def existing_course_count(token):
    status, body, _ = request(
        f'{GATEWAY}/api/courses', headers={'Authorization': f'Bearer {token}'}
    )
    if status != 200:
        return None
    payload = json.loads(body)
    data = payload.get('data', payload)
    return len(data) if isinstance(data, list) else None


def main():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument('--force', action='store_true')
    parser.add_argument('-h', '--help', action='store_true')
    args = parser.parse_args()
    if args.help:
        sys.exit(__doc__)

    password = os.environ.get('SEED_PASSWORD')
    if not password:
        sys.exit('SEED_PASSWORD 환경변수가 필요하다. 자세한 것은 --help 를 본다.')
    if len(password) < 8:
        sys.exit('비밀번호는 8자 이상이어야 한다. user-service 가 400 으로 거부한다.')

    with open(CSV_PATH, encoding='utf-8-sig', newline='') as f:
        rows = list(csv.DictReader(f))

    unknown = {r['품명'] for r in rows} - set(CATEGORY_BY_PRODUCT)
    if unknown:
        sys.exit(f'카테고리 슬롯에 없는 품명이 있다: {sorted(unknown)}')

    # 단가 파싱 실패를 0원으로 흘려보내지 않는다. 0원 품목은 예산 조건을 항상
    # 통과하고 가격 점수도 만점이라 추천 상위로 올라온다. 쉼표나 소수점이 섞인
    # CSV 가 들어오면 여기서 멈춘다.
    bad_price = [r['품목명'] for r in rows if not r['단가'].strip().isdigit()]
    if bad_price:
        sys.exit(f'단가를 숫자로 읽을 수 없는 행이 {len(bad_price)}건 있다: {bad_price[:3]}')

    suppliers = sorted({r['공급업체명'] for r in rows})
    print(f'{CSV_PATH}\n  {len(rows)}품목 · 공급업체 {len(suppliers)}곳\n')

    # 1. 구매기업 — 품목 조회에 토큰이 필요하므로 먼저 만든다
    print('구매기업 계정')
    if not register(BUYER['email'], BUYER['name'], BUYER['role'], password):
        sys.exit(1)
    buyer_token = login(BUYER['email'], password)
    if not buyer_token:
        sys.exit(1)
    print(f"  {BUYER['email']}  준비됨")

    # 2. 이미 등록된 품목이 있으면 멈춘다. 품목에는 중복 제약이 없다
    count = existing_course_count(buyer_token)
    # 모르면 멈춘다. None 과 0 을 같이 거짓으로 다루면 게이트웨이 오류·토큰 만료·응답
    # 형식 변화에서 "품목이 없다" 로 읽혀 262건을 그대로 더 넣는다. 품목에는 중복
    # 제약이 없어 복구하려면 524건 중 뒤쪽을 골라 지워야 한다.
    if count is None:
        sys.exit('\n현재 품목 수를 확인할 수 없다. 게이트웨이 상태를 확인하고 다시 실행한다.')
    if count and not args.force:
        sys.exit(
            f'\n이미 {count}개 품목이 등록되어 있다. 그대로 실행하면 중복으로 쌓인다.\n'
            '정말 더 넣으려면 --force 를 준다.'
        )
    if count:
        print(f'\n  이미 {count}개가 있지만 --force 라 이어서 넣는다')

    # 3. 공급기업 계정과 품목
    by_supplier = {}
    for row in rows:
        by_supplier.setdefault(row['공급업체명'], []).append(row)

    print(f'\n공급기업 {len(suppliers)}곳')
    created, failed = 0, 0
    for index, supplier in enumerate(suppliers, start=1):
        email = f'supplier{index:02d}@procurix.local'
        if not register(email, supplier, 'INSTRUCTOR', password):
            failed += len(by_supplier[supplier])
            continue
        token = login(email, password)
        if not token:
            failed += len(by_supplier[supplier])
            continue

        ok = 0
        for row in by_supplier[supplier]:
            status, body, _ = request(
                f'{GATEWAY}/api/courses',
                {
                    'title': row['품목명'],
                    'description': build_description(row),
                    'category': CATEGORY_BY_PRODUCT[row['품명']],
                    'price': int(row['단가']) if row['단가'].strip().isdigit() else 0,
                },
                headers={'Authorization': f'Bearer {token}'},
            )
            if status == 201:
                ok += 1
            else:
                failed += 1
                print(f"  !! 품목 등록 실패 {row['품목명'][:30]} — {status} {body[:120]}",
                      file=sys.stderr)
        created += ok
        print(f'  [{index:2d}/{len(suppliers)}] {email}  {supplier[:22]:<24s} {ok:>3}품목')

    total = existing_course_count(buyer_token)
    print(f'\n등록 {created}  실패 {failed}  현재 품목 수 {total}')
    print(f"\n로그인 계정 — 비밀번호는 SEED_PASSWORD 로 준 값\n"
          f"  구매기업  {BUYER['email']}\n"
          f"  공급기업  supplier01@procurix.local ~ supplier{len(suppliers):02d}@procurix.local")
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
