#!/bin/sh

protected_ref="refs/heads/main"

while read -r local_ref local_sha remote_ref remote_sha
do
  if [ "$remote_ref" = "$protected_ref" ]; then
    printf '%s\n' "ERROR: main 브랜치로 직접 push하는 것은 hook이 막습니다." >&2
    printf '%s\n' "작업 브랜치를 만들고 Pull Request로 병합하세요." >&2
    exit 1
  fi
done

exit 0
