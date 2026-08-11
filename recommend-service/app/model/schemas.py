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
    # course-service 가 품목 등록 시 채워 두는 공급기업 이름.
    # 이 필드를 선언하지 않으면 pydantic 이 걸러 내서 추천 카드의 업체명이 빈다.
    # 컬럼이 생기기 전에 등록된 품목은 None 이다.
    instructorName: Optional[str] = None
    enrollmentCount: int
    status: str
    createdAt: Optional[datetime] = None


class EnrollmentHistoryResponse(BaseModel):
    userId: int
    activeCourseIds: List[int]


class RecommendResponse(BaseModel):
    userId: int
    recommendedCourses: List[CourseResponse]
    basedOnCategory: Optional[CourseCategory] = None
    message: str


class ApiResponse(BaseModel):
    success: bool
    message: str
    data: Optional[dict] = None
