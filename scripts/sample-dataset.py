#!/usr/bin/env python3
"""조달 원본 CSV에서 init-db/Data.csv 를 다시 만든다.

원본은 84,180행 33MB다. 저장소는 이력 재작성과 force push 를 금지하므로
한 번 커밋하면 뺄 수 없다. 그래서 원본은 커밋하지 않고 추린 것만 둔다.

추출 규칙 — 품명 × 공급업체 조합마다 최저가·중간가·최고가 3건.
조합을 다 도는 방식이라 공급업체·품명·소재지가 하나도 빠지지 않고,
최저·최고를 함께 잡아 단가 분포도 원본 그대로 유지된다.

사용법:
    python3 scripts/sample-dataset.py <원본CSV경로>

원본은 강의 자료 배포 경로에서 받는다. 파일명 예: 프로젝트용_산업자재_공급업체_정리.csv
"""
import collections
import csv
import os
import sys

OUT = os.path.join(os.path.dirname(__file__), '..', 'init-db', 'Data.csv')


def price(row):
    return int(row['단가']) if row['단가'].strip().isdigit() else 0


def main(src):
    with open(src, encoding='utf-8-sig', newline='') as f:
        reader = csv.DictReader(f)
        columns = reader.fieldnames
        rows = list(reader)

    buckets = collections.defaultdict(list)
    for row in rows:
        buckets[(row['품명'], row['공급업체명'])].append(row)

    picked, seen = [], set()
    for key in sorted(buckets):
        group = sorted(buckets[key], key=price)
        for row in (group[0], group[len(group) // 2], group[-1]):
            # 업체명을 키에 넣는다. 물품식별번호만 쓰면 업체가 통째로 빠질 수 있다.
            ident = (row['공급업체명'], row['물품식별번호'])
            if ident not in seen:
                seen.add(ident)
                picked.append(row)

    picked.sort(key=lambda r: (r['품명'], r['공급업체명'], price(r)))

    with open(OUT, 'w', encoding='utf-8-sig', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=columns)
        writer.writeheader()
        writer.writerows(picked)

    print(f'{len(rows):,}행 → {len(picked):,}행  ({os.path.getsize(OUT):,} bytes)')
    for col in ('공급업체명', '품명', '세부품명', '공급업체소재지', '단위'):
        kept, total = len({r[col] for r in picked}), len({r[col] for r in rows})
        print(f'  {col:<10s} {kept:>3} / {total:>3}' + ('' if kept == total else '   ← 누락'))


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
