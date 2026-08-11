import logging
from collections import Counter
from typing import List, Optional

from app.client.course_client import course_client
from app.client.enrollment_client import enrollment_client
from app.model.schemas import (
    CourseCategory,
    CourseResponse,
    RecommendResponse,
    ScoredCourse,
)
from app.service import scoring

logger = logging.getLogger(__name__)


class RecommendService:
    """
    규칙 기반 강의 추천 서비스

    추천 규칙:
    1. 사용자의 수강 중인 강의 카테고리 분석
    2. 가장 많이 수강한 카테고리 선택 (최빈 카테고리)
    3. 해당 카테고리에서 미수강 강의 조회
    4. 수강생 수 기준 내림차순 정렬하여 반환
    5. 수강 이력 없으면 전체 강의 중 인기순 반환
    """

    MAX_RECOMMEND_COUNT = 5   # 최대 추천 건수
    CANDIDATE_POOL_SIZE = 30  # 채점 후보 수. 전체를 채점하면 262건을 훑는다

    async def get_recommendations(self, user_id: int) -> RecommendResponse:
        logger.info(f"[RecommendService] 추천 시작 - userId: {user_id}")

        # 1. 수강 이력 조회
        history = await enrollment_client.get_enrollment_history(user_id)
        active_course_ids = history.activeCourseIds

        # 2. 수강 이력 없는 신규 사용자 처리
        if not active_course_ids:
            return await self._recommend_for_new_user(user_id)

        # 3. 수강한 강의의 카테고리 분석 → 최빈 카테고리 선택
        dominant_category = await self._find_dominant_category(active_course_ids)
        if not dominant_category:
            return await self._recommend_for_new_user(user_id)

        # 4. 최빈 카테고리 기반 미수강 강의 조회
        recommended = await course_client.get_recommend_courses(
            category=dominant_category,
            exclude_ids=active_course_ids
        )

        # 5. 점수화 후 상위 N건
        scored = self._score_and_rank(recommended)

        trusted = sum(1 for c in scored if c.performanceTrusted)
        logger.info(f"[RecommendService] 추천 완료 - userId: {user_id}, "
                    f"category: {dominant_category}, count: {len(scored)}, "
                    f"성과 반영 {trusted}건")

        return RecommendResponse(
            userId=user_id,
            recommendedCourses=scored,
            basedOnCategory=dominant_category,
            message=self._summary(scored, dominant_category)
        )

    async def _find_dominant_category(
        self, course_ids: List[int]
    ) -> Optional[CourseCategory]:
        """
        수강한 강의들의 카테고리 분석 → 최빈 카테고리 반환
        Course Service에서 각 강의 정보를 조회하여 카테고리 집계
        """
        all_courses = await course_client.get_all_courses()
        course_map = {c.id: c for c in all_courses}

        categories = [
            course_map[cid].category
            for cid in course_ids
            if cid in course_map
        ]

        if not categories:
            return None

        # Counter로 최빈 카테고리 선택
        most_common = Counter(categories).most_common(1)
        return most_common[0][0] if most_common else None

    async def _recommend_for_new_user(self, user_id: int) -> RecommendResponse:
        """
        신규 사용자: 수강생 수 기준 전체 인기 강의 추천
        """
        logger.info(f"[RecommendService] 신규 사용자 추천 - userId: {user_id}")

        all_courses = await course_client.get_all_courses()
        # 전체를 다 채점하면 262건을 훑게 되므로 거래건수 상위만 후보로 좁힌 뒤 채점한다
        candidates = sorted(
            all_courses,
            key=lambda c: c.enrollmentCount,
            reverse=True
        )[:self.CANDIDATE_POOL_SIZE]
        scored = self._score_and_rank(candidates)

        return RecommendResponse(
            userId=user_id,
            recommendedCourses=scored,
            basedOnCategory=None,
            message=self._summary(scored, None)
        )

    def _score_and_rank(self, courses: List[CourseResponse]) -> List[ScoredCourse]:
        """점수를 매겨 상위 N건을 돌려준다.

        동점이면 거래건수가 많은 쪽을 앞에 둔다. 실적이 같아 보일 때
        실제로 더 많이 거래된 업체를 먼저 보여주는 것이 자연스럽다.
        """
        scored = [
            ScoredCourse(**course.model_dump(), **scoring.score_course(course))
            for course in courses
        ]
        scored.sort(key=lambda c: (c.score, c.enrollmentCount), reverse=True)
        return scored[:self.MAX_RECOMMEND_COUNT]

    @staticmethod
    def _summary(scored: List[ScoredCourse], category: Optional[CourseCategory]) -> str:
        """추천 근거를 한 줄로 요약한다. 점수만 보여주지 않는다는 기획안 원칙이다."""
        if not scored:
            return "조건에 맞는 공급기업을 찾지 못했습니다"
        trusted = sum(1 for c in scored if c.performanceTrusted)
        head = f"{category.value} " if category else ""
        if trusted:
            return (f"{head}공급기업 {len(scored)}곳을 추천합니다. "
                    f"그중 {trusted}곳은 평가 {scoring.MIN_EVALUATIONS}건 이상의 "
                    f"실제 거래 성과가 반영되었습니다")
        return (f"{head}공급기업 {len(scored)}곳을 추천합니다. "
                f"평가 {scoring.MIN_EVALUATIONS}건 이상 쌓인 곳이 아직 없어 "
                f"조달 등록 정보와 거래 실적으로 평가했습니다")


recommend_service = RecommendService()
