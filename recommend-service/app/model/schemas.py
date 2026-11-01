import re
from pydantic import BaseModel, ConfigDict, Field, ValidationInfo, field_validator, model_validator
from typing import Any, List, Literal, Optional
from enum import Enum
from decimal import Decimal
from datetime import date, datetime

MAX_QUANTITY = 9223372036854775807
MAX_MONEY = Decimal("99999999999999999.99")
SUPPORTED_UNITS = {"m", "M", "kg", "KG", "개", "EA", "본", "조", "식"}


class MatchCriteria(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    product: str = Field(min_length=1, max_length=100)
    specification: str = Field(min_length=1, max_length=200)
    quantity: int = Field(strict=True, gt=0, le=MAX_QUANTITY)
    unit: str = Field(min_length=1, max_length=20)
    budget: Optional[str] = Field(default=None, max_length=20)
    budgetScope: Optional[Literal["itemSubtotal", "total"]] = None
    maxDeliveryDays: Optional[int] = Field(default=None, strict=True, gt=0)
    requestedDeliveryDate: Optional[date] = None
    requiredCertifications: List[str] = Field(default_factory=list, max_length=20)

    @field_validator("product", "specification")
    @classmethod
    def concrete_condition(cls, value):
        if value in {"전체", "전체 규격"}:
            raise ValueError("전체가 아닌 구체적인 품명·규격을 입력해야 합니다")
        return value

    @field_validator("unit")
    @classmethod
    def supported_unit(cls, value):
        if value not in SUPPORTED_UNITS:
            raise ValueError("지원하지 않는 단위입니다")
        return value

    @field_validator("budget", mode="before")
    @classmethod
    def decimal_budget(cls, value):
        if value is None:
            return None
        if not isinstance(value, str) or not re.fullmatch(r"[0-9]+(?:\.[0-9]{1,2})?", value.strip()):
            raise ValueError("예산은 소수 둘째 자리 이내의 금액 문자열이어야 합니다")
        value = value.strip()
        if len(value) > 20 or not 0 < Decimal(value) <= MAX_MONEY:
            raise ValueError("예산이 DECIMAL(19,2)의 양수 범위를 벗어났습니다")
        return value

    @field_validator("requestedDeliveryDate", mode="before")
    @classmethod
    def iso_date(cls, value):
        if value is None or type(value) is date:
            return value
        if not isinstance(value, str) or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value):
            raise ValueError("납품일은 ISO 날짜여야 합니다")
        return date.fromisoformat(value)

    @field_validator("requiredCertifications")
    @classmethod
    def certification_names(cls, values):
        if any(not value.strip() or len(value.strip()) > 100 for value in values):
            raise ValueError("인증 이름은 1~100자여야 합니다")
        return list(dict.fromkeys(value.strip() for value in values))

    @model_validator(mode="after")
    def consistent_conditions(self, info: ValidationInfo):
        if (self.budget is None) != (self.budgetScope is None):
            raise ValueError("예산과 예산 범위를 함께 지정해야 합니다")
        today = (info.context or {}).get("today")
        if today is not None and self.requestedDeliveryDate is not None:
            days = (self.requestedDeliveryDate - today).days
            if days < 0:
                raise ValueError("납품일은 기준일 이후여야 합니다")
            if self.maxDeliveryDays is not None and days != self.maxDeliveryDays:
                raise ValueError("납품일과 최대 납품일수가 모순됩니다")
        return self


class MatchCheck(BaseModel):
    field: str
    status: Literal["passed", "failed", "unknown"]
    code: str
    requested: Any
    actual: Any
    source: str
    message: str


class MatchResult(BaseModel):
    courseId: int
    supplierId: int
    eligibility: Literal["eligible", "ineligible", "needsReview"]
    itemSubtotal: Optional[str]
    costCompleteness: Literal["itemSubtotalOnly", "unknown"]
    checks: List[MatchCheck]
    evidence: dict[str, Any]


class CourseCategory(str, Enum):
    BACKEND = "BACKEND"
    FRONTEND = "FRONTEND"
    DEVOPS = "DEVOPS"
    DATA_SCIENCE = "DATA_SCIENCE"
    MOBILE = "MOBILE"
    SECURITY = "SECURITY"
    DATABASE = "DATABASE"
    OTHER = "OTHER"


class CourseResponse(BaseModel):
    id: int
    title: str
    description: Optional[str] = None
    category: CourseCategory
    price: Decimal
    instructorId: int
    # 선언하지 않은 필드는 pydantic 이 걸러 낸다. 추천 카드에서 값이 비면 여기부터 본다.
    # course-service 가 품목 등록 시 채워 두는 공급기업 이름
    instructorName: Optional[str] = None
    contractEnd: Optional[date] = None
    # 공급기업 누적 성과지표(QCD). 평가 이력이 없으면 None 이다
    defectRate: Optional[Decimal] = None
    onTimeRate: Optional[Decimal] = None
    costVarianceRate: Optional[Decimal] = None
    evaluatedCount: Optional[int] = None
    enrollmentCount: int
    status: str
    createdAt: Optional[datetime] = None


class EnrollmentHistoryResponse(BaseModel):
    userId: int
    activeCourseIds: List[int]


class ScoreBreakdown(BaseModel):
    """QCD 세 축. 합이 100이다.

    조달 등록(인증·우수제품·MAS)과 거래 실적은 여기 없다. 앞은 사용자가 요청했을 때
    판단할 값이라 조건 적합도로 옮겼고, 뒤는 performanceTrusted 플래그가 같은 역할을
    한다. 축을 줄이면서 이 스키마를 함께 고치지 않아 추천 응답 전체가 500 이 된 적이
    있다 — pydantic 이 없는 필드를 필수로 요구했다.
    """
    quality: int
    delivery: int
    cost: int


class ScoredCourse(CourseResponse):
    """추천 응답 전용. 점수와 근거를 붙인 품목이다."""
    score: int
    scoreBreakdown: ScoreBreakdown
    reason: str
    # 평가 건수가 기준에 못 미쳐 성과 항목을 평균값으로 대체했으면 False
    performanceTrusted: bool


class RecommendResponse(BaseModel):
    userId: int
    recommendedCourses: List[ScoredCourse]
    basedOnCategory: Optional[CourseCategory] = None
    message: str


class ApiResponse(BaseModel):
    success: bool
    message: str
    data: Optional[dict] = None
