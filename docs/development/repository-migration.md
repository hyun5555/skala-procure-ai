# 비공개 fork에서 독립 개인 저장소로 이전

확인일 2026-10-06. `hyun5555/skala-procure`는 비공개 원본 `SangMyeong5426/skala-procure`의 비공개 fork다.
GitHub는 fork 하나의 visibility 변경을 허용하지 않는다. UI의 Leave fork network도 공개 fork에만 적용되므로 현재 대상에는 사용할 수 없다.

공식 근거: [Fork 공개 범위](https://docs.github.com/en/pull-requests/reference/forks), [fork 분리](https://docs.github.com/en/pull-requests/how-tos/work-with-forks/detaching-a-fork).

## 선택한 이전 방식

기존 fork와 팀 원본을 보존하고 다른 이름의 독립 저장소 `hyun5555/skala-procure-ai`를 만든다.
기존 저장소 삭제·이력 재작성·mirror push는 필요하지 않다. 초기 공개 시 가져오는 범위는 검토한 최신 main의 조상 이력과 개인 기반 변경이다.
PR·Issue·설정 등 Git 이력 외 메타데이터는 복사되지 않는다. 기존 저장소에서 확인한다.

원본 팀·교육 템플릿의 출처와 고지문을 유지하며 새 LICENSE를 임의로 붙이지 않는다.
교육용 공개 설정값은 운영 자격증명이 아니다. 소스 공개와 실행 서비스의 외부 배포를 구분한다.

## 순서

1. 최신 원본 main과 사용자 fork main을 비교하고 포함할 코드·기록을 확인한다.
2. 작업 트리와 포함할 Git 이력의 비밀값·고객자료를 점검한다. 패턴 검사만으로 완전한 안전을 보장하지 않는다.
3. 개인 지침·설계·기록을 작업 브랜치에서 검증하고 관련 파일만 커밋한다.
4. 빈 독립 저장소를 만든 뒤 검토한 HEAD를 main으로 초기 push한다. 모든 refs를 보내는 mirror push를 사용하지 않는다.
5. 새 저장소의 visibility=PUBLIC, isFork=false와 main SHA를 확인한다.
6. 로컬 origin은 새 개인 저장소로 바꾸고, 기존 origin과 사용자 fork는 참조용 원격으로 보존한다.

## 명령 예시

아래는 이전 절차의 예시다. 존재하는 저장소를 덮어쓰지 않고, 이름과 인증 상태를 먼저 확인한다.

```bash
gh auth status
gh repo create hyun5555/skala-procure-ai --public --description 'AI-assisted procurement platform developed from the SKALA team project'
git remote add personal https://github.com/hyun5555/skala-procure-ai.git
git push personal HEAD:main
gh repo view hyun5555/skala-procure-ai --json nameWithOwner,visibility,isFork,defaultBranchRef
```

첫 main 생성만 pre-push hook이 허용한다. 이미 존재하는 main으로 직접 push하는 것은 계속 차단한다.
초기 push 뒤 개인 작업은 `codex/<작업명>` 브랜치와 PR로 진행한다. 원본 main에 개인 변경을 보내지 않는다.
실제 이전 결과·검증은 [기반 작업 기록](foundation-result.md)에 남긴다.
