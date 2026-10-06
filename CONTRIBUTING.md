# 개인 개발 방법

개발 규칙의 원본은 [`AGENTS.md`](AGENTS.md)다. 이 문서는 시작 절차만 안내한다.

1. [`readme.md`](readme.md)의 실행 조건과 [`docs/constraints.md`](docs/constraints.md)의 현행 호환성을 확인한다.
2. `./scripts/setup-git-hooks`를 실행한다. `main` 직접 push 방지는 계속 유지한다.
3. 개인 저장소를 `origin`으로 연결했는지 확인하고 `codex/<작업명>` 브랜치를 만든다.
4. [`docs/development/backlog.md`](docs/development/backlog.md)의 한 항목을 골라 구현·검증·문서를 함께 완료한다.
5. 개인 개발에서는 팀원 승인을 기다릴 필요가 없다. PR에는 동작 변화, 검증, 알려진 한계를 적는다.

복잡한 리팩토링의 기준은 [`기반 설계`](docs/architecture/refactoring-foundation.md), 에이전트 활용법은 [`역할별 작업 지침`](docs/development/agents.md)에 있다.
원격 공개 이전은 [`저장소 이전 가이드`](docs/development/repository-migration.md)를 따른다.

기존 팀 실습의 기여 지침은 [`기록`](docs/history/team-project/CONTRIBUTING.legacy.md)으로만 보존한다.
