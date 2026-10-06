#!/usr/bin/env python3
"""로컬 실습 거래 검증. 실행마다 새 소수 단가 품목·발주·결제를 한 건씩 추가한다."""

import argparse
import base64
import getpass
import json
import os
import time
from datetime import datetime, timedelta
from decimal import Decimal
from html.parser import HTMLParser
from http.cookiejar import CookieJar
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlencode, urljoin, urlparse
from urllib.request import HTTPCookieProcessor, Request, build_opener, urlopen
from zoneinfo import ZoneInfo

BASE = "http://localhost:8080"


class LoginForm(HTMLParser):
    def __init__(self):
        super().__init__()
        self.fields = {}
        self.action = "/login"

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "form":
            self.action = attrs.get("action", self.action)
        if tag == "input" and attrs.get("name"):
            self.fields[attrs["name"]] = attrs.get("value", "")


def request(method, path, body=None, token=None, expected=200):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if isinstance(body, dict):
        body = json.dumps(body, ensure_ascii=False)
    data = body.encode() if body is not None else None
    try:
        with urlopen(Request(BASE + path, data=data, headers=headers, method=method), timeout=10) as response:
            status, raw = response.status, response.read()
    except HTTPError as error:
        status, raw = error.code, error.read()
    assert status == expected, f"{method} {path}: expected {expected}, got {status}"
    return json.loads(raw, parse_float=Decimal) if raw else None


def login(email, password, config, role):
    opener = build_opener(HTTPCookieProcessor(CookieJar()))
    params = urlencode({"response_type": "code", "client_id": config["VITE_CLIENT_ID"],
                        "redirect_uri": config["VITE_REDIRECT_URI"], "scope": "openid profile read write"})
    with opener.open(BASE + "/oauth2/authorize?" + params, timeout=10) as response:
        form = LoginForm()
        form.feed(response.read().decode())
    form.fields.update(username=email, password=password)
    with opener.open(Request(urljoin(BASE, form.action), data=urlencode(form.fields).encode()), timeout=10) as response:
        code = parse_qs(urlparse(response.url).query).get("code", [None])[0]
    assert code, "OAuth authorization code missing"
    basic = base64.b64encode(f'{config["VITE_CLIENT_ID"]}:{config["VITE_CLIENT_SECRET"]}'.encode()).decode()
    body = urlencode({"grant_type": "authorization_code", "code": code, "redirect_uri": config["VITE_REDIRECT_URI"]})
    with urlopen(Request(BASE + "/oauth2/token", data=body.encode(), headers={
        "Content-Type": "application/x-www-form-urlencoded", "Authorization": "Basic " + basic}), timeout=10) as response:
        token = json.load(response)["access_token"]
    me = request("GET", "/api/users/me", token=token)["data"]
    assert me["email"] == email and me["role"] == role, "Authenticated account/role mismatch"
    payload = token.split(".")[1]
    claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    print(json.dumps({"role": role, "userId": me["id"], "issuer": claims["iss"],
                      "claimNames": sorted(claims)}, ensure_ascii=False))
    assert claims.get("user_id") == me["id"] and claims.get("role") == role, "JWT identity mismatch"
    return token, me["id"]


def check_order(course_id, expected_total, token, user_id):
    deadline = time.monotonic() + 30
    while True:
        orders = request("GET", "/api/enrollments/my", token=token)["data"]
        order = next(item for item in orders if item["courseId"] == course_id)
        if order["status"] == "SHIPPING":
            break
        assert time.monotonic() < deadline, "SHIPPING timeout; inspect payment/Kafka before retrying"
        time.sleep(1)
    assert order["userId"] == user_id
    assert order["orderRequest"]["quantity"] == 3 and order["orderRequest"]["unit"] == "개"
    assert order["orderRequest"]["estimatedTotal"] == expected_total
    payments = request("GET", f"/api/payments/user/{user_id}", token=token)["data"]
    matching = [item for item in payments if item["courseId"] == course_id]
    assert len(matching) == 1 and matching[0]["status"] == "COMPLETED"
    assert matching[0]["amount"] == expected_total
    course = request("GET", f"/api/courses/{course_id}", token=token)["data"]
    assert course["enrollmentCount"] == 1
    print(json.dumps({"courseId": course_id, "enrollmentId": order["id"],
                      "paymentId": matching[0]["paymentId"], "total": str(expected_total),
                      "paymentStatus": "COMPLETED", "orderStatus": "SHIPPING", "enrollmentCount": 1}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--supplier-email", required=True)
    parser.add_argument("--buyer-email", required=True)
    parser.add_argument("--screen-course-id", type=int, required=True, help="화면에서 이미 수량 3개로 발주한 정수 단가 품목")
    args = parser.parse_args()
    password = os.environ.get("RUNTIME_TEST_PASSWORD") or getpass.getpass("테스트 계정 공통 비밀번호: ")
    config = dict(line.split("=", 1) for line in (Path(__file__).resolve().parents[1] / "vue-frontend/.env").read_text().splitlines()
                  if line.startswith("VITE_") and "=" in line)
    assert config["VITE_REDIRECT_URI"] == "http://localhost:3000/callback"
    supplier_token, supplier_id = login(args.supplier_email, password, config, "INSTRUCTOR")
    buyer_token, buyer_id = login(args.buyer_email, password, config, "STUDENT")
    request("GET", "/api/users/me", expected=401)
    request("GET", "/api/users/me", token="invalid-runtime-test-token", expected=401)
    screen_course = request("GET", f"/api/courses/{args.screen_course_id}", token=buyer_token)["data"]
    assert screen_course["instructorId"] == supplier_id and screen_course["price"] == Decimal("1250")
    check_order(args.screen_course_id, Decimal("3750.00"), buyer_token, buyer_id)

    today = datetime.now(ZoneInfo("Asia/Seoul")).date()
    end = today + timedelta(days=365)
    title = f"런타임 소수 금액 검증 품목 {time.time_ns()}, Φ300mm"
    payload = {"title": title, "category": "BACKEND", "price": "1250.50", "contractEnd": str(end),
               "description": f"품명: 파형강관 | 규격: Φ300mm | 단위: 개 | 공급지역: 전지역 | 납품일수: 30일 | 인증정보: KS | 계약기간: {today}~{end}"}
    body = json.dumps(payload, ensure_ascii=False).replace('"price": "1250.50"', '"price": 1250.50')
    course = request("POST", "/api/courses", body, supplier_token, expected=201)["data"]
    course_id = course["id"]
    assert course["price"] == Decimal("1250.50") and course["contractEnd"] == str(end)
    catalog = request("GET", "/api/courses", token=buyer_token)["data"]
    assert {args.screen_course_id, course_id} <= {item["id"] for item in catalog}
    request("PUT", f"/api/courses/{course_id}", {"title": title, "category": "BACKEND", "price": 1250.5,
            "contractEnd": str(end)}, buyer_token, expected=403)
    request("POST", "/api/enrollments", {"courseId": course_id, "quantity": 3, "unit": "개",
            "deliveryDate": str(today + timedelta(days=30)), "deliveryPlace": "로컬 검증용 가상 납품장소",
            "contactName": "검증 담당자", "contactPhone": "010-0000-0000", "notes": "로컬 소수 금액 검증"}, buyer_token, expected=201)
    check_order(course_id, Decimal("3751.50"), buyer_token, buyer_id)
    print("PASS: OAuth identities, catalog, integer/decimal totals, SHIPPING, gateway auth and ownership")


if __name__ == "__main__":
    main()
