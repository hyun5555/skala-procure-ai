#!/bin/bash
# 시드 계정에 로그인 가능한 비밀번호를 넣는다. 선택 사항이다.
#
# 02_seed_catalog.sql 은 시드 공급기업·구매기업 계정을 넣되 비밀번호는 원문을
# 알 수 없는 해시로 둔다. SQL 에는 BCrypt 해시 함수가 없고, CLAUDE.md 가
# 비밀번호 커밋을 금지하기 때문이다.
#
# 시드 계정으로 직접 로그인하고 싶으면 BCrypt 해시를 환경변수로 넘긴다.
# docker-compose.yml 의 mariadb 서비스에 아래를 넣거나(값은 gitignore 된
# .env 에 두고 참조), 그냥 화면에서 계정을 새로 만들어 쓴다.
#
#     environment:
#       SEED_PASSWORD_HASH: ${SEED_PASSWORD_HASH}
#
# 해시 만들기 (원문은 팀에서 정하고 저장소에 넣지 않는다):
#     python3 -c "import bcrypt,os;print(bcrypt.hashpw(os.environ['P'].encode(),bcrypt.gensalt(10,prefix=b'2a')).decode())"
#
# 값이 없으면 아무것도 하지 않는다. 기본 동작은 '로그인 불가'다.
set -e

if [ -z "$SEED_PASSWORD_HASH" ]; then
    echo "[03_seed_password] SEED_PASSWORD_HASH 없음 — 시드 계정은 로그인 불가 상태로 둔다"
    exit 0
fi

case "$SEED_PASSWORD_HASH" in
    '$2a$'*|'$2b$'*|'$2y$'*) ;;
    *)
        echo "[03_seed_password] BCrypt 해시가 아니다. 원문 비밀번호를 넣지 않았는지 확인한다" >&2
        exit 1
        ;;
esac

mariadb --protocol=socket -uroot -p"${MARIADB_ROOT_PASSWORD:-$MYSQL_ROOT_PASSWORD}" \
    "${MARIADB_DATABASE:-$MYSQL_DATABASE}" <<SQL
UPDATE users
   SET password = '${SEED_PASSWORD_HASH}'
 WHERE email LIKE 'supplier%@procurix.local'
    OR email = 'buyer@procurix.local';
SQL

echo "[03_seed_password] 시드 계정 비밀번호 설정 완료"
