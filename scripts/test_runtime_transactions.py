import importlib.util
import io
import unittest
from contextlib import redirect_stdout
from decimal import Decimal
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "runtime_transactions", Path(__file__).with_name("check-runtime-transactions.py")
)
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class ShippingDeadlineTest(unittest.TestCase):
    def test_shipping_must_be_observed_before_deadline(self):
        for status, elapsed in (("SHIPPING", 29), ("SHIPPING", 30),
                                ("SHIPPING", 31), ("PENDING", 31)):
            with self.subTest(status=status, elapsed=elapsed):
                now = 0
                responses = {
                    "/api/enrollments/my": {"data": [{
                        "id": 8, "courseId": 265, "userId": 57, "status": status,
                        "orderRequest": {"quantity": 3, "unit": "개",
                                         "estimatedTotal": Decimal("3751.50")},
                    }]},
                    "/api/payments/user/57": {"data": [{
                        "paymentId": 8, "courseId": 265, "status": "COMPLETED",
                        "amount": Decimal("3751.50"),
                    }]},
                    "/api/courses/265": {"data": {"enrollmentCount": 1}},
                }

                def respond(method, path, **kwargs):
                    nonlocal now
                    self.assertEqual(method, "GET")
                    now = elapsed
                    return responses[path]

                with patch.object(runtime, "request", side_effect=respond), \
                        patch.object(runtime.time, "monotonic", side_effect=lambda: now), \
                        patch.object(runtime.time, "sleep", side_effect=AssertionError("Unexpected extra poll")), \
                        redirect_stdout(io.StringIO()):
                    if elapsed < 30:
                        runtime.check_order(265, Decimal("3751.50"), "fake-token", 57)
                    else:
                        with self.assertRaisesRegex(AssertionError, "SHIPPING timeout"):
                            runtime.check_order(265, Decimal("3751.50"), "fake-token", 57)


if __name__ == "__main__":
    unittest.main()
