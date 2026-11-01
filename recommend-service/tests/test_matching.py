import copy
import json
import unittest
from datetime import date, datetime
from decimal import Decimal, localcontext

from pydantic import ValidationError

from app.model.schemas import CourseResponse, MatchCriteria
from app.service.matching import match_courses, parse_capability


TODAY = date(2026, 10, 22)
CRITERIA = {
    "product": "주철관", "specification": "Φ300mm×6m, 2종",
    "quantity": 3, "unit": "본", "budget": "5000.00",
    "budgetScope": "itemSubtotal", "maxDeliveryDays": 30,
    "requiredCertifications": ["KS"],
}
FIELDS = {
    "품명": "주철관", "규격": "Φ300mm×6m, 2종", "단위": "본",
    "납품일수": "30일", "인증정보": "KS, 환경표지",
    "계약기간": "2026-01-01~2026-12-31",
}


def course(**changes):
    item = {
        "id": 101, "instructorId": 7, "title": "테스트 주철관",
        "category": "SECURITY", "price": "1250.50", "status": "ACTIVE",
        "contractEnd": "2026-12-31", "enrollmentCount": 0,
        "description": " | ".join(f"{key}: {value}" for key, value in FIELDS.items()),
    }
    item.update(changes)
    return item


def description(**changes):
    fields = {**FIELDS, **changes}
    return " | ".join(f"{key}: {value}" for key, value in fields.items() if value is not None)


class MatchingTests(unittest.TestCase):
    def match(self, item=None, **criteria):
        return match_courses({**CRITERIA, **criteria}, [item or course()], TODAY)[0]

    def check(self, result, field):
        return next(check for check in result.checks if check.field == field)

    def test_complete_candidate_and_decimal_result(self):
        result = self.match()
        self.assertEqual(result.eligibility, "eligible")
        self.assertEqual(result.itemSubtotal, "3751.50")
        self.assertEqual(result.costCompleteness, "itemSubtotalOnly")
        self.assertEqual((result.courseId, result.supplierId), (101, 7))
        self.assertTrue(all(check.status == "passed" for check in result.checks))
        self.assertEqual(json.loads(result.model_dump_json())["itemSubtotal"], "3751.50")

    def test_budget_equality_and_one_cent_boundary(self):
        for budget, expected in [("3751.50", "eligible"), ("3751.49", "ineligible")]:
            with self.subTest(budget=budget):
                self.assertEqual(self.match(budget=budget).eligibility, expected)

    def test_total_cost_is_unknown_unless_subtotal_already_exceeds_budget(self):
        result = self.match(budgetScope="total")
        self.assertEqual(result.eligibility, "needsReview")
        self.assertEqual(self.check(result, "budget").code, "TOTAL_COST_UNKNOWN")
        self.assertEqual(result.costCompleteness, "itemSubtotalOnly")
        self.assertEqual(self.match(budget="3751.49", budgetScope="total").eligibility, "ineligible")

    def test_unrequested_optional_checks_are_absent(self):
        result = self.match(budget=None, budgetScope=None, maxDeliveryDays=None, requiredCertifications=[])
        self.assertEqual(result.eligibility, "eligible")
        self.assertFalse({"budget", "delivery", "certifications"} & {check.field for check in result.checks})

    def test_entire_specification_compares_length_thickness_and_grade(self):
        for actual in ["Φ300mm×4m, 2종", "Φ300mm×6m, 3종", "Φ300mm", "Φ300mm, 1.6mm, 6m"]:
            with self.subTest(actual=actual):
                result = self.match(course(description=description(규격=actual)))
                self.assertEqual(result.eligibility, "ineligible")
                self.assertEqual(self.check(result, "specification").code, "SPECIFICATION_MISMATCH")
        result = self.match(course(description=description(규격="Φ300mm, 1.8mm, 6m")), specification="Φ300mm, 1.6mm, 6m")
        self.assertEqual(self.check(result, "specification").status, "failed")

    def test_specification_glyphs_and_supported_seed_suffixes(self):
        for specification in ["Φ1219×22mm", "Φ300mm, 1.6mm, 6m", "Φ300mm×6m, 2종", "100A, 6m", "Φ300mm"]:
            with self.subTest(specification=specification):
                item = course(description=description(규격=None, 품목명=f"주철관, 제조사, 모델A300, {specification}"))
                self.assertEqual(self.match(item, specification=specification).eligibility, "eligible")
        item = course(description=description(규격=" ⌀300 mm x 6 m , 2종 "))
        self.assertEqual(self.match(item).eligibility, "eligible")

    def test_existing_seed_item_and_compact_contract_period(self):
        # init-db/Data.csv의 스틸파일 행과 시드 생성기의 description 형식.
        title = "강관파일, 웰텍, WEL-GRPF-SP-1219-22, Φ1219×22mm"
        item = course(
            title=title, category="MOBILE", contractEnd="2026-04-13",
            description=" | ".join([
                "품명: 스틸파일", f"품목명: {title}", "단위: KG", "납품일수: 90일",
                "인증정보: 우수조달물품, G-PASS기업, G-PASS기업(B등급)",
                "계약기간: 20260311~20260413",
            ]),
        )
        criteria = {**CRITERIA, "product": "스틸파일", "specification": "Φ1219×22mm", "unit": "kg", "maxDeliveryDays": 90, "requiredCertifications": ["우수조달물품"]}
        result = match_courses(criteria, [item], date(2026, 4, 1))[0]
        self.assertEqual(result.eligibility, "eligible")
        item["contractEnd"] = None
        self.assertEqual(match_courses(criteria, [item], date(2026, 4, 1))[0].eligibility, "eligible")

    def test_frontend_registration_description(self):
        item = course(category="BACKEND", price="1250", description=" | ".join([
            "공급업체소재지: 서울", '공급지역코드: ["11"]', "품명: 파형강관",
            "품목명: 화면 등록 품목", "규격: Φ300mm", "단위: m", "공급지역: 전국",
            "납품일수: 30일", "인도조건: 현장도착도", "인증정보: 해당 없음",
            "우수제품여부: N", "MAS여부: N", "계약기간: 2026-01-01~2026-12-31",
        ]))
        result = self.match(item, product="파형강관", specification="Φ300mm", unit="m", requiredCertifications=[])
        self.assertEqual(result.eligibility, "eligible")
        self.assertEqual(result.itemSubtotal, "3750.00")

    def test_unknown_specification_is_not_guessed_from_numbers(self):
        for actual in [None, "직경 300, 길이 6", "Φ300mm 이상", "Φ0mm", "모델300"]:
            with self.subTest(actual=actual):
                item = course(title="모델300 제조사600", description=description(규격=actual))
                self.assertEqual(self.check(self.match(item), "specification").status, "unknown")
        self.assertEqual(self.match(specification="직경 약 300").eligibility, "needsReview")
        item = course(description=description(규격=None, 품목명="주철관, Φ300mm, 2종 이상, Φ300mm"))
        result = self.match(item, specification="Φ300mm")
        self.assertEqual(self.check(result, "specification").status, "unknown")

    def test_specification_sources_must_not_conflict(self):
        item = course(description=description(품목명="주철관, Φ300mm×4m, 2종"))
        self.assertEqual(self.check(self.match(item), "specification").status, "unknown")
        item = course(title="주철관, Φ300mm×4m, 2종")
        self.assertEqual(self.check(self.match(item), "specification").status, "unknown")

    def test_product_fallback_and_category_conflict(self):
        self.assertEqual(self.match(course(description=description(품명=None))).eligibility, "eligible")
        result = self.match(course(description=description(품명="피복강관")))
        self.assertEqual(self.check(result, "product").status, "unknown")
        result = self.match(course(category="MOBILE", description=description(품명="스틸파일")))
        self.assertEqual(self.check(result, "product").status, "failed")
        result = self.match(course(category="INVALID", description=description(품명=None)))
        self.assertEqual(self.check(result, "product").status, "unknown")

    def test_units_only_normalize_known_case_variants(self):
        for requested, actual, expected in [("m", "M", "passed"), ("kg", "KG", "passed"), ("개", "EA", "failed"), ("m", "본", "failed"), ("본", None, "unknown")]:
            with self.subTest(requested=requested, actual=actual):
                result = self.match(course(description=description(단위=actual)), unit=requested)
                self.assertEqual(self.check(result, "unit").status, expected)
                self.assertEqual(result.itemSubtotal is None, expected != "passed")

    def test_delivery_boundaries_aliases_and_invalid_values(self):
        for value, status in [("30", "passed"), ("30일", "passed"), ("31일", "failed"), (None, "unknown"), ("30~60일", "unknown"), ("약 30일", "unknown"), ("0", "unknown"), ("-1", "unknown")]:
            with self.subTest(value=value):
                result = self.match(course(description=description(납품일수=None, 평균납기=value)))
                self.assertEqual(self.check(result, "delivery").status, status)

    def test_delivery_date_uses_injected_today(self):
        result = self.match(maxDeliveryDays=None, requestedDeliveryDate="2026-11-21")
        self.assertEqual(self.check(result, "delivery").requested, 30)
        self.assertEqual(result.eligibility, "eligible")
        result = self.match(maxDeliveryDays=None, requestedDeliveryDate=TODAY)
        self.assertEqual(self.check(result, "delivery").status, "failed")
        self.assertEqual(self.match(requestedDeliveryDate="2026-11-21").eligibility, "eligible")

    def test_certification_exact_names_and_explicit_absence(self):
        for value, status in [("KS", "passed"), ("KSQ", "failed"), ("해당 없음", "failed"), (None, "unknown"), ("KS, 해당 없음", "unknown"), ("KS,", "unknown")]:
            with self.subTest(value=value):
                result = self.match(course(description=description(인증정보=None, 인증=value)))
                self.assertEqual(self.check(result, "certifications").status, status)

    def test_duplicate_aliases_preserve_conflict_evidence(self):
        for extra, field in [("평균납기: 31일", "delivery"), ("인증: KSQ", "certifications"), ("규격: Φ200mm", "specification"), ("단위: m", "unit"), ("품명: 피복강관", "product"), ("계약기간: 2026-01-01~2027-01-01", "contract")]:
            with self.subTest(extra=extra):
                result = self.match(course(description=description() + " | " + extra))
                check = self.check(result, field)
                self.assertEqual(check.status, "unknown")
                self.assertIn(extra, result.evidence["description"])
        result = self.match(course(description=description() + " | 평균납기: 30 | 인증: 환경표지, KS"))
        self.assertEqual(result.eligibility, "eligible")

    def test_parser_uses_first_colon_and_keeps_original_values(self):
        parsed = parse_capability(" 무시 | 품명: 주철관 | 품목명: 모델: A300 | 평균납기: 30일 | 인증: KS ")
        self.assertEqual(parsed["itemName"], [("품목명", "모델: A300")])
        self.assertEqual(parsed["delivery"], [("평균납기", "30일")])
        self.assertEqual(parse_capability(None), {})

    def test_contract_dates_and_column_precedence(self):
        cases = [
            ({"contractEnd": "2026-10-21"}, "failed", "CONTRACT_EXPIRED"),
            ({"contractEnd": TODAY}, "passed", "CONTRACT_VALID"),
            ({"contractEnd": None}, "passed", "CONTRACT_VALID"),
            ({"contractEnd": "2026-02-30"}, "unknown", "CONTRACT_INVALID"),
            ({"description": description(계약기간="2026-11-01~2026-12-31")}, "failed", "CONTRACT_NOT_STARTED"),
            ({"description": description(계약기간=None)}, "unknown", "CONTRACT_UNKNOWN"),
            ({"description": description(계약기간="2026-12-31~2026-01-01")}, "unknown", "CONTRACT_UNKNOWN"),
            ({"description": description(계약기간="2026-02-30~2026-12-31")}, "unknown", "CONTRACT_UNKNOWN"),
            ({"contractEnd": "2025-12-31"}, "unknown", "CONTRACT_INVALID"),
            ({"description": description(계약기간="2026-01-01~2026-10-21")}, "passed", "CONTRACT_VALID"),
        ]
        for changes, status, code in cases:
            with self.subTest(changes=changes):
                check = self.check(self.match(course(**changes)), "contract")
                self.assertEqual((check.status, check.code), (status, code))

    def test_status_and_failure_precedence_keep_all_reasons(self):
        self.assertEqual(self.match(course(status="INACTIVE")).eligibility, "ineligible")
        self.assertEqual(self.match(course(status="UNKNOWN")).eligibility, "needsReview")
        result = self.match(course(description=description(규격="Φ200mm", 납품일수=None)))
        self.assertEqual(result.eligibility, "ineligible")
        self.assertEqual(self.check(result, "specification").status, "failed")
        self.assertEqual(self.check(result, "delivery").status, "unknown")

    def test_price_zero_and_decimal_types(self):
        for price, subtotal in [(0, "0.00"), (1250, "3750.00"), (Decimal("1250.50"), "3751.50"), ("0.10", "0.30")]:
            with self.subTest(price=price):
                self.assertEqual(self.match(course(price=price)).itemSubtotal, subtotal)
        with localcontext() as context:
            context.prec = 6
            result = self.match(course(price="99999999999999999.99"), quantity=1, budget="99999999999999999.99")
        self.assertEqual(result.itemSubtotal, "99999999999999999.99")
        self.assertEqual(result.eligibility, "eligible")

    def test_bad_prices_are_unknown_and_json_serializable(self):
        for price in [None, True, -1, "-1", "1.001", "NaN", "Infinity", "1e3", "100000000000000000.00", 1250.50, float("nan"), Decimal("NaN"), Decimal("Infinity")]:
            with self.subTest(price=price):
                result = self.match(course(price=price))
                self.assertEqual(result.eligibility, "needsReview")
                self.assertEqual(self.check(result, "price").status, "unknown")
                self.assertIsNone(result.itemSubtotal)
                json.dumps(result.model_dump(mode="json"), allow_nan=False)

    def test_subtotal_overflow_is_not_rounded_into_budget(self):
        for price, quantity in [("99999999999999999.99", 2), ("0.02", 9223372036854775807)]:
            with self.subTest(price=price, quantity=quantity):
                result = self.match(course(price=price), quantity=quantity)
                self.assertIsNone(result.itemSubtotal)
                self.assertEqual(self.check(result, "quantity").code, "SUBTOTAL_OVERFLOW")
                self.assertEqual(result.eligibility, "needsReview")

    def test_empty_stable_order_repeat_and_no_input_mutation(self):
        criteria = copy.deepcopy(CRITERIA)
        items = [course(id=103), course(id=101), course(id=102)]
        original = copy.deepcopy(items)
        expected = match_courses(criteria, items, TODAY)
        self.assertEqual([result.courseId for result in expected], [101, 102, 103])
        self.assertEqual(match_courses(criteria, list(reversed(items)), TODAY), expected)
        self.assertEqual(match_courses(criteria, items, TODAY), expected)
        self.assertEqual(match_courses(criteria, [], TODAY), [])
        self.assertEqual(criteria, CRITERIA)
        self.assertEqual(items, original)

    def test_course_response_preserves_contract_end_and_decimal(self):
        item = CourseResponse.model_validate(course()).model_dump()
        result = self.match(item)
        self.assertEqual(result.eligibility, "eligible")
        self.assertEqual(result.evidence["contractEnd"], date(2026, 12, 31))


class CriteriaValidationTests(unittest.TestCase):
    def test_valid_trimming_deduplication_and_upper_quantity(self):
        criteria = MatchCriteria.model_validate({**CRITERIA, "product": " 주철관 ", "quantity": 9223372036854775807, "requiredCertifications": [" KS ", "KS", "환경표지"]})
        self.assertEqual(criteria.product, "주철관")
        self.assertEqual(criteria.requiredCertifications, ["KS", "환경표지"])

    def test_invalid_required_fields_and_unsupported_conditions(self):
        invalid = [("quantity", value) for value in [0, -1, True, 1.5, "3", 9223372036854775808]]
        invalid += [("product", value) for value in ["", " ", "전체", "가" * 101, None]]
        invalid += [("specification", value) for value in ["", " ", "전체", "가" * 201]]
        invalid += [("unit", value) for value in ["", "톤", "ea", None]]
        invalid += [("maxDeliveryDays", value) for value in [0, -1, True, 1.5, "30"]]
        invalid += [("requiredCertifications", value) for value in [[""], [" "], ["가" * 101], [str(i) for i in range(21)], [1]]]
        invalid += [("region", "서울"), ("mas", True), ("supplierId", 7)]
        for field, value in invalid:
            with self.subTest(field=field, value=value), self.assertRaises(ValidationError):
                match_courses({**CRITERIA, field: value}, [], TODAY)
        for field in ["product", "specification", "quantity", "unit"]:
            data = {**CRITERIA}
            data.pop(field)
            with self.subTest(missing=field), self.assertRaises(ValidationError):
                match_courses(data, [], TODAY)

    def test_invalid_budgets_and_scope_pairs(self):
        for budget in ["0", "-1", "NaN", "Infinity", "1e3", "1.001", "100000000000000000.00", 5000, 1.5, True, "", "1,000"]:
            with self.subTest(budget=budget), self.assertRaises(ValidationError):
                match_courses({**CRITERIA, "budget": budget}, [], TODAY)
        for changes in [{"budgetScope": None}, {"budget": None}, {"budgetScope": "unknown"}]:
            with self.subTest(changes=changes), self.assertRaises(ValidationError):
                match_courses({**CRITERIA, **changes}, [], TODAY)

    def test_invalid_or_conflicting_dates_even_with_typed_criteria(self):
        for value in ["2026-10-21", "2026-02-30", "20261022", "2026-10-22T00:00:00", 1792623600, datetime(2026, 10, 22), "2026-11-20"]:
            with self.subTest(value=value), self.assertRaises(ValidationError):
                match_courses({**CRITERIA, "requestedDeliveryDate": value}, [], TODAY)
        typed = MatchCriteria.model_validate({**CRITERIA, "requestedDeliveryDate": "2026-10-21"})
        with self.assertRaises(ValidationError):
            match_courses(typed, [], TODAY)
        with self.assertRaises(ValueError):
            match_courses(CRITERIA, [], datetime(2026, 10, 22))

    def test_invalid_catalog_identity_is_an_error_not_an_eligible_candidate(self):
        for changes in [{"id": True}, {"id": "101"}, {"id": 0}, {"instructorId": None}]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                match_courses(CRITERIA, [course(**changes)], TODAY)
        with self.assertRaises(ValueError):
            match_courses(CRITERIA, [course(), course()], TODAY)


if __name__ == "__main__":
    unittest.main()
