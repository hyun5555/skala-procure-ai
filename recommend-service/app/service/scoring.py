"""공급 신뢰도 점수 — QCD 100점 만점.

구매기업이 발주 관리 화면에서 기록한 **실제 거래 결과** 에서만 나온다.
사용자가 방금 입력한 조건(예산·일정·인증·지역)은 프론트엔드가 따로 채점한다.
산식은 docs/spec/api-spec.md 에 있다. 두 곳이 같은 것을 계산하면 반드시 어긋난다.

**지표 이름과 정의는 업계 표준, 배점과 곡선은 이 프로젝트의 MVP 설정이다.**

    표준        QCD 프레임 · PPM · OTD · PPV 정의
                OTD 목표선 95% · PPM 100/1,000/10,000 구간 · 로그 척도(Six Sigma DPMO)
    우리 설정   배점 50/30/20 · 곡선 형태 · 끝점(PPM 3%, OTD 80%, PPV +10%)

PPM 기준선은 산업 편차가 크다. 100 PPM 은 자동차·전자 기준이고 강관·주철관 같은
토목자재는 더 느슨한 것이 보통이다. 우리 기준이 엄격한 쪽이다.

    Q  PPM  Parts Per Million        50점   누적불량 ÷ 누적납품 × 1,000,000
    D  OTD  On-Time Delivery         30점   납기준수 건수 ÷ 평가 건수 × 100
    C  PPV  Purchase Price Variance  20점   (Σ실제청구 − Σ견적) ÷ Σ견적 × 100

검증 기간에는 평가가 1건이라도 있으면 실제 QCD 성과를 채운다. 평가가 전혀 없는
공급기업만 각 축에 **기본점수(만점의 절반)** 를 준다. 실제 공급기업 분포에서 뽑은
중앙값이 아니라 중립값이다 — 평가가 없을 때 좋다고도 나쁘다고도 할 수 없으므로
가운데에 둔다. 0점을 주면 신규 공급기업이 영원히 추천되지 않아 거래 이력을 쌓을
기회 자체가 사라진다.
overview.md 130행의 "신규 공급기업은 기본점수로 초기 평가한다" 가 이 뜻이다.
"""
import math
from decimal import Decimal
from typing import List, Optional

# 검증 기간에는 첫 평가부터 QCD 점수에 반영한다.
# 운영 기준으로 복귀할 때에는 이 값을 10으로 되돌린다.
MIN_EVALUATIONS = 1

MAX_QUALITY = 50
MAX_DELIVERY = 30
MAX_COST = 20

# Q — PPM 기준선
PPM_FULL_SCORE = 100        # world-class
PPM_ZERO_SCORE = 30_000     # 3%. 조달 대상으로 보기 어려운 지점

# D — OTD 기준선. 95% 를 업계 목표선으로 본다
OTD_TARGET = 95.0
OTD_TARGET_SCORE = 24.0     # 30점 중 24점
OTD_ZERO_SCORE = 80.0

# C — PPV 기준선
PPV_ZERO_SCORE = 10.0


def _clamp(value: float, low: float = 0.0, high: float = 1.0) -> float:
    return max(low, min(high, value))


def _to_float(value) -> Optional[float]:
    if value is None:
        return None
    return float(value) if isinstance(value, Decimal) else float(value)


def quality_score(defect_rate: Optional[float]) -> float:
    """PPM 을 로그 척도로 점수화한다.

    선형으로 두면 100 PPM 과 5,000 PPM 이 둘 다 '1% 미만' 으로 뭉개진다.
    실제로는 50배 차이이고 조달 담당자에게는 전혀 다른 값이다.
    구간별 등급이 업계에서 더 흔하지만 경계에서 점수가 뚝 떨어지는 절벽이 생긴다.
    """
    if defect_rate is None:
        return MAX_QUALITY / 2
    ppm = max(defect_rate * 10_000, 1.0)
    if ppm <= PPM_FULL_SCORE:
        return MAX_QUALITY
    ratio = (math.log10(PPM_ZERO_SCORE) - math.log10(ppm)) / (
        math.log10(PPM_ZERO_SCORE) - math.log10(PPM_FULL_SCORE)
    )
    return MAX_QUALITY * _clamp(ratio)


def delivery_score(on_time_rate: Optional[float]) -> float:
    """OTD 를 목표선 95% 에서 꺾는다.

    로그로 두면 98%(조달에서 우수한 값)가 3분의 2로 깎인다. 납기는 불량률처럼
    자릿수로 움직이지 않고 대부분 80~100% 사이에 몰린다.
    선형으로 두면 85%(6~7건 중 1건 지연)가 절반을 받아 너무 관대하다.
    목표선 아래를 위보다 가파르게 벌하는 것이 맞다.

        95~100%   24 → 30점   1.2점/%
        80~95%     0 → 24점   1.6점/%
    """
    if on_time_rate is None:
        return MAX_DELIVERY / 2
    if on_time_rate >= OTD_TARGET:
        over = (on_time_rate - OTD_TARGET) / (100.0 - OTD_TARGET)
        return OTD_TARGET_SCORE + (MAX_DELIVERY - OTD_TARGET_SCORE) * _clamp(over)
    under = (on_time_rate - OTD_ZERO_SCORE) / (OTD_TARGET - OTD_ZERO_SCORE)
    return OTD_TARGET_SCORE * _clamp(under)


def cost_score(cost_variance_rate: Optional[float]) -> float:
    """PPV 는 선형이다. 비용 초과는 자릿수로 움직이지 않고 보통 −10~+20% 안에 든다.

    절감(음수)에 가산점은 주지 않는다. 견적을 크게 밑돌면 좋은 것이 아니라
    견적이 부실했다는 신호일 수 있다.
    """
    if cost_variance_rate is None:
        return MAX_COST / 2
    overrun = max(cost_variance_rate, 0.0)
    return MAX_COST * _clamp((PPV_ZERO_SCORE - overrun) / PPV_ZERO_SCORE)


def score_course(course) -> dict:
    """품목 하나를 채점하고 근거를 함께 만든다."""
    evaluated = course.evaluatedCount or 0
    # 간편 품질 등록 화면은 Q(불량률)만 저장하고 납기/비용은 평가하지 않는다.
    # 이때 evaluatedCount 는 납기 평가 건수라 0일 수 있지만 defectRate 는 실제
    # 거래 결과다. 건수가 0이라는 이유로 실측 불량률까지 기본점수로 버리지 않는다.
    has_measured_performance = any(
        value is not None
        for value in (
            course.defectRate,
            course.onTimeRate,
            getattr(course, 'costVarianceRate', None),
        )
    )
    trusted = evaluated >= MIN_EVALUATIONS or has_measured_performance
    displayed_evaluated = max(evaluated, 1 if has_measured_performance else 0)

    defect_rate = _to_float(course.defectRate) if trusted else None
    on_time_rate = _to_float(course.onTimeRate) if trusted else None
    cost_variance = _to_float(getattr(course, 'costVarianceRate', None)) if trusted else None

    breakdown = {
        'quality': round(quality_score(defect_rate)),
        'delivery': round(delivery_score(on_time_rate)),
        'cost': round(cost_score(cost_variance)),
    }
    total = sum(breakdown.values())

    reasons: List[str] = []
    if trusted:
        # 세 지표를 같은 방식으로 방어한다. evaluatedCount 는 기준 이상인데 컬럼 하나가
        # NULL 인 상태는 정상 경로로 생기지 않지만, 백필이나 집계 중 실패로 컬럼이
        # 따로 놀 수 있다. 여기서 TypeError 가 나면 추천 응답 전체가 실패한다.
        if defect_rate is not None:
            ppm = int(round(defect_rate * 10_000))
            reasons.append(f'불량률 {defect_rate:.2f}% ({ppm:,} PPM)')
        if on_time_rate is not None:
            reasons.append(f'납기 준수율 {on_time_rate:.0f}%')
        if cost_variance is not None:
            word = '초과' if cost_variance > 0 else '절감'
            reasons.append(f'견적 대비 {abs(cost_variance):.1f}% {word}')
        reasons.append(f'평가 {displayed_evaluated}건 기준')
    else:
        reasons.append(f'거래 실적 부족 (평가 {evaluated}건 / {MIN_EVALUATIONS}건 필요)')
        reasons.append('QCD 는 기본점수로 대체')

    if course.enrollmentCount:
        reasons.append(f'누적 거래 {course.enrollmentCount}건')

    return {
        'score': total,
        'scoreBreakdown': breakdown,
        'reason': ' · '.join(reasons),
        'performanceTrusted': trusted,
    }
