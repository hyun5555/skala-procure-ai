"""카탈로그의 필수 구매 조건 판정. 날짜·데이터를 주입하며 외부 서비스를 호출하지 않는다."""

import re
import unicodedata
from datetime import date
from decimal import Decimal, InvalidOperation, localcontext

from app.model.schemas import (
    MAX_MONEY, SUPPORTED_UNITS, MatchCheck, MatchCriteria, MatchResult,
)

# 시드 생성기와 등록 화면의 category → 품명 대응이다.
PRODUCT_BY_CATEGORY = {
    "BACKEND": "파형강관", "FRONTEND": "파형강관이음관", "DEVOPS": "피복강관",
    "DATA_SCIENCE": "피복강관이음", "MOBILE": "스틸파일", "SECURITY": "주철관",
    "DATABASE": "주철제관이음", "OTHER": "기타 관류",
}
ALIASES = {
    "product": ("품명",), "itemName": ("품목명",), "specification": ("규격",),
    "unit": ("단위",), "delivery": ("납품일수", "평균납기"),
    "certifications": ("인증정보", "인증"), "contract": ("계약기간",),
}
NUMBER = r"[0-9]+(?:\.[0-9]+)?"
DIAMETER = rf"φ{NUMBER}(?:(?:mm|m)?x{NUMBER})*(?:mm|m)"
SPECIFICATION = re.compile(
    rf"(?:{DIAMETER}|{NUMBER}a)(?:,(?:{DIAMETER}|{NUMBER}(?:mm|m|종|rs)))*"
)


def parse_capability(description: str | None) -> dict[str, list[tuple[str, str]]]:
    fields = {}
    if not isinstance(description, str):
        return fields
    for part in description.split("|"):
        key, separator, value = part.partition(":")
        if not separator:
            continue
        key = key.strip()
        for field, aliases in ALIASES.items():
            if key in aliases:
                fields.setdefault(field, []).append((key, value.strip()))
                break
    return fields


def _text(value):
    return re.sub(r"\s+", "", unicodedata.normalize("NFKC", value)) or None


def _read(fields, field, normalize):
    entries = fields.get(field, [])
    source = "description." + "/".join(dict.fromkeys(key for key, _ in entries)) if entries else "description." + ALIASES[field][0]
    raw = [value for _, value in entries]
    values = [normalize(value) for value in raw]
    value = values[0] if values and values[0] is not None and all(item == values[0] for item in values) else None
    return value, (raw[0] if len(raw) == 1 else raw or None), source


def _specification(value):
    if not isinstance(value, str) or len(value) > 200:
        return None
    value = _text(value)
    if not value:
        return None
    value = value.lower().translate(str.maketrans({"ø": "φ", "⌀": "φ", "×": "x", "✕": "x"}))
    if not SPECIFICATION.fullmatch(value):
        return None
    return value if all(Decimal(number) > 0 for number in re.findall(NUMBER, value)) else None


def _name_specification(value):
    if not isinstance(value, str):
        return None
    parts = value.split(",")
    for index, part in enumerate(parts):
        prefix = (_text(part) or "").lower()
        if prefix.startswith(("φ", "ø", "⌀")) or re.fullmatch(rf"{NUMBER}a", prefix):
            # 첫 치수 이후의 모호한 등급을 건너뛰고 뒤쪽 치수만 채택하지 않는다.
            return _specification(",".join(parts[index:]))
    return None


def _unit(value):
    return {"M": "m", "KG": "kg"}.get(value, value) if value in SUPPORTED_UNITS else None


def _days(value):
    matched = re.fullmatch(r"([0-9]{1,19})\s*일?", value)
    return int(matched[1]) if matched and int(matched[1]) > 0 else None


def _certifications(value):
    values = [item.strip() for item in value.split(",")]
    if not all(values):
        return None
    absent = [item for item in values if _text(item) == "해당없음"]
    if absent:
        return () if len(values) == len(absent) else None
    return tuple(sorted(set(values)))


def _date(value):
    if type(value) is date:
        return value
    if isinstance(value, str) and re.fullmatch(r"[0-9]{8}", value):
        value = f"{value[:4]}-{value[4:6]}-{value[6:]}"
    if isinstance(value, str) and re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value):
        try:
            return date.fromisoformat(value)
        except ValueError:
            pass
    return None


def _period(value):
    parts = unicodedata.normalize("NFKC", value).split("~")
    if len(parts) == 2:
        start, end = (_date(part.strip()) for part in parts)
        if start is not None and end is not None and start <= end:
            return start, end
    return None


def _price(value):
    if type(value) not in (int, str, Decimal):
        return None
    if isinstance(value, str) and (len(value) > 20 or not re.fullmatch(r"[0-9]+(?:\.[0-9]{1,2})?", value)):
        return None
    try:
        amount = Decimal(value)
        if not amount.is_finite() or not 0 <= amount <= MAX_MONEY:
            return None
        with localcontext() as context:
            context.prec = 40
            return amount if amount == amount.quantize(Decimal("0.01")) else None
    except InvalidOperation:
        return None


def _match_course(criteria, course, today):
    fields = parse_capability(course.get("description"))
    checks = []

    def check(field, status, code, requested, actual, source, message):
        checks.append(MatchCheck(field=field, status=status, code=code, requested=requested,
                                 actual=actual, source=source, message=message))

    product, raw, source = _read(fields, "product", _text)
    category = course.get("category")
    category_product = PRODUCT_BY_CATEGORY.get(category) if isinstance(category, str) else None
    if raw is None:
        product = _text(category_product) if category_product else None
        raw, source = category_product, "category"
    if product is None or (category_product and product != _text(category_product)):
        check("product", "unknown", "PRODUCT_UNKNOWN", criteria.product, raw, source, "품명을 확인할 수 없거나 분류와 충돌합니다.")
    else:
        passed = product == _text(criteria.product)
        check("product", "passed" if passed else "failed", "PRODUCT_MATCH" if passed else "PRODUCT_MISMATCH", criteria.product, raw, source, "품명이 일치합니다." if passed else "요청한 품명과 다릅니다.")

    specification, raw, source = _read(fields, "specification", _specification)
    name, name_raw, name_source = _read(fields, "itemName", lambda value: value or None)
    if name_raw is None:
        name, name_raw, name_source = course.get("title"), course.get("title"), "title"
    name_spec = _name_specification(name)
    name_conflict = name_raw is not None and name is None
    if raw is None:
        specification, raw, source = name_spec, name_raw, name_source
    elif name_spec is not None and specification != name_spec:
        specification = None
        source += "," + name_source
    requested_spec = _specification(criteria.specification)
    if requested_spec is None or specification is None or name_conflict:
        check("specification", "unknown", "SPECIFICATION_UNKNOWN", criteria.specification, raw, source, "전체 규격이 누락·상충하거나 지원하지 않는 형식입니다.")
    else:
        passed = specification == requested_spec
        check("specification", "passed" if passed else "failed", "SPECIFICATION_MATCH" if passed else "SPECIFICATION_MISMATCH", criteria.specification, raw, source, "전체 규격이 일치합니다." if passed else "길이·두께·등급을 포함한 전체 규격이 다릅니다.")

    unit, raw, source = _read(fields, "unit", _unit)
    unit_matches = unit is not None and unit == _unit(criteria.unit)
    check("unit", "unknown" if unit is None else "passed" if unit_matches else "failed",
          "UNIT_UNKNOWN" if unit is None else "UNIT_MATCH" if unit_matches else "UNIT_MISMATCH",
          criteria.unit, raw, source, "단위 일치 여부를 확인합니다. 개·EA·본 사이의 환산은 하지 않습니다.")

    price_raw = course.get("price")
    price = _price(price_raw)
    price_actual = str(price_raw) if isinstance(price_raw, (Decimal, float)) else price_raw
    check("price", "passed" if price is not None else "unknown", "PRICE_VALID" if price is not None else "PRICE_UNKNOWN", "등록 단가 × 요청 수량", price_actual, "price", "소수 정밀도가 유효한 단가만 사용하며 float 단가는 확정하지 않습니다.")
    subtotal = None
    if price is not None and unit_matches:
        with localcontext() as context:
            context.prec = 40
            total = price * criteria.quantity
            if total <= MAX_MONEY:
                subtotal = format(total.quantize(Decimal("0.01")), "f")
        if subtotal is None:
            check("quantity", "unknown", "SUBTOTAL_OVERFLOW", criteria.quantity, str(total), "price,quantity", "요청 수량의 소계가 거래 금액 저장 범위를 초과합니다.")

    status = course.get("status")
    check("status", "passed" if status == "ACTIVE" else "failed" if status == "INACTIVE" else "unknown",
          "STATUS_ACTIVE" if status == "ACTIVE" else "STATUS_INACTIVE" if status == "INACTIVE" else "STATUS_UNKNOWN",
          "ACTIVE", status, "status", "활성 품목만 충족으로 판정합니다.")

    period, period_raw, source = _read(fields, "contract", _period)
    column_end = course.get("contractEnd")
    start = period[0] if period else None
    end = _date(column_end) if column_end is not None else period[1] if period else None
    actual = {"contractEnd": column_end, "contractPeriod": period_raw}
    invalid = (column_end is not None and end is None) or (start is not None and end is not None and start > end)
    if invalid:
        result, code = "unknown", "CONTRACT_INVALID"
    elif end is not None and end < today:
        result, code = "failed", "CONTRACT_EXPIRED"
    elif start is not None and start > today:
        result, code = "failed", "CONTRACT_NOT_STARTED"
    elif start is None or end is None:
        result, code = "unknown", "CONTRACT_UNKNOWN"
    else:
        result, code = "passed", "CONTRACT_VALID"
    check("contract", result, code, today.isoformat(), actual, "contractEnd," + source, "유효한 종료일 컬럼을 우선하고 계약 시작일·종료일을 함께 확인합니다.")

    if criteria.maxDeliveryDays is not None or criteria.requestedDeliveryDate is not None:
        limit = criteria.maxDeliveryDays if criteria.maxDeliveryDays is not None else (criteria.requestedDeliveryDate - today).days
        days, raw, source = _read(fields, "delivery", _days)
        passed = days is not None and days <= limit
        check("delivery", "unknown" if days is None else "passed" if passed else "failed",
              "DELIVERY_UNKNOWN" if days is None else "DELIVERY_WITHIN_LIMIT" if passed else "DELIVERY_TOO_LATE",
              limit, raw, source, "등록 납품일수가 요청 기한 이내인지 확인합니다.")

    if criteria.requiredCertifications:
        certificates, raw, source = _read(fields, "certifications", _certifications)
        passed = certificates is not None and set(criteria.requiredCertifications).issubset(certificates)
        check("certifications", "unknown" if certificates is None else "passed" if passed else "failed",
              "CERTIFICATIONS_UNKNOWN" if certificates is None else "CERTIFICATIONS_MATCH" if passed else "CERTIFICATIONS_MISSING",
              criteria.requiredCertifications, raw, source, "등록된 인증 이름을 정확히 비교하며 인증 진위를 보증하지 않습니다.")

    if criteria.budget is not None:
        if subtotal is None:
            result, code = "unknown", "SUBTOTAL_UNKNOWN"
        elif Decimal(subtotal) > Decimal(criteria.budget):
            result, code = "failed", "BUDGET_EXCEEDED"
        elif criteria.budgetScope == "total":
            result, code = "unknown", "TOTAL_COST_UNKNOWN"
        else:
            result, code = "passed", "WITHIN_BUDGET"
        check("budget", result, code, {"amount": criteria.budget, "scope": criteria.budgetScope}, subtotal, "price,quantity", "소계만 계산하며 운임·세금을 임의로 더하거나 총액 충족을 추정하지 않습니다.")

    statuses = {item.status for item in checks}
    eligibility = "ineligible" if "failed" in statuses else "needsReview" if "unknown" in statuses else "eligible"
    evidence = {key: course.get(key) for key in ("title", "description", "category", "status", "contractEnd")}
    evidence["price"] = price_actual
    return MatchResult(courseId=course["id"], supplierId=course["instructorId"], eligibility=eligibility,
                       itemSubtotal=subtotal, costCompleteness="itemSubtotalOnly" if subtotal is not None else "unknown",
                       checks=checks, evidence=evidence)


def match_courses(
    criteria: MatchCriteria | dict, courses: list[dict], today: date,
) -> list[MatchResult]:
    """요청은 오류로 거부하고, 후보 정보의 누락·오류는 판정 근거로 반환한다."""
    if type(today) is not date:
        raise ValueError("today는 시각 없는 명시적 date여야 합니다")
    data = criteria.model_dump() if isinstance(criteria, MatchCriteria) else criteria
    criteria = MatchCriteria.model_validate(data, context={"today": today})
    ids = []
    for course in courses:
        if any(type(course.get(key)) is not int or course[key] <= 0 for key in ("id", "instructorId")):
            raise ValueError("카탈로그 id·instructorId는 양의 정수여야 합니다")
        ids.append(course["id"])
    if len(ids) != len(set(ids)):
        raise ValueError("카탈로그 품목 ID가 중복됐습니다")
    return [_match_course(criteria, course, today) for course in sorted(courses, key=lambda item: item["id"])]
