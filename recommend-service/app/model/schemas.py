from pydantic import BaseModel
from typing import List, Optional
from enum import Enum
from decimal import Decimal
from datetime import datetime


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
