# 개인 개발 기반 작업 결과

작성일: 2026-10-06. 작업 브랜치: `codex/personal-development-foundation`.
팀 main의 기준 커밋은 `980da07edbc709e79713a1897b07e73145280336`이다.

## 완료한 변경

팀 지침·제품 개요·제약·API·CI·hook 원문 10개를 manifest와 해시로 보존했다.
개인 `AGENTS.md`, Claude 진입점, 기여·PR 지침, 제품 방향, 호환성 문서를 정리했다.
Software Architect를 실제 실행해 기반 설계·ADR·백로그를 작성했다. 전역 Agency Agents 정의·모델·권한을 변경하지 않았다.
현재 런타임의 경로·포트 guard는 유지하고 팀 Sprint/Java 수정 제한을 제거했다. 최초 main 생성만 허용하도록 hook을 정리했다.

## 검증 기록

| 검사 | 실제 결과 |
| --- | --- |
| `python3 scripts/check-docs.py` | 기록 10개 해시·활성 Markdown 15개 경로 통과; URL·anchor 검사 아님 |
| `git diff --check` | 공백 오류 없음 |
| Python 문서 checker compile | 통과 |
| pre-push hook | 빈 main 생성 허용, 기존 main 갱신 차단, 작업 브랜치 허용의 세 경우 통과 |
| `npm run build` | 통과. Vite native config의 `__dirname` 관련 비차단 경고는 후속 정리 |
| 공개 이전 이력 패턴 검사 | 최신 팀 HEAD 조상 이력의 고유 blob 358개, private key·OpenAI/GitHub/AWS/Google/Slack 키 형태 6종에 해당하는 값 미발견 |

비밀값 검사는 패턴 기반이고 완전한 보안 검사·라이선스 검토를 의미하지 않는다. 교육용 고정값과 원본 고지는 유지했다.
문서 작업에서 전체 컨테이너 기동·주문 회귀·Java 테스트·실제 LLM 호출은 수행하지 않았다.

## 독립 저장소

새 대상은 https://github.com/hyun5555/skala-procure-ai 이다. 기존 비공개 fork와 원본을 삭제하지 않는다.
초기 main은 최신 팀 커밋의 Git 이력으로 생성하고 개인 기반 변경은 작업 브랜치·PR로 검증한다. 최종 공개 범위·fork 여부·main SHA·CI 결과는 GitHub API와 작업 결과에서 확인한다.
로컬 원격은 개인 origin, 팀 upstream, 기존 사용자 fork인 legacy-fork로 나눈다. 이력 재작성이나 mirror push는 하지 않는다.

## 발견과 판단

로컬 시작 커밋은 `7316979`로 원격보다 한 커밋 뒤였다. 원격 main을 fast-forward한 뒤 `980da07`을 기록 기준으로 사용했다.
처음 샌드박스 안의 gh auth status가 인증 실패로 보였으나 네트워크 접근 가능한 환경에서 정상 keyring 인증을 확인했다.
조건 점수와 QCD 표시의 문서·코드 차이는 설계에 원문으로 기록했으며 현재 산식을 변경하지 않았다.
독립 인증·게이트웨이, 주문 ID와 반복 발주, 새로운 LLM API는 설계·백로그이며 미구현이다.
