# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

공통 지침은 Codex와 함께 쓰는 `AGENTS.md`에서 관리한다. 내용은 그 파일에 추가하고, 이 파일에는 Claude 전용 내용만 둔다.

@AGENTS.md

## 서브에이전트 사용
- 코드 점검(버그, 규칙 위반, 성능 문제)은 `code-inspector` 서브에이전트에게 맡긴다. 기능 구현이나 수정이 끝나면 점검을 요청받지 않아도 `code-inspector`로 점검한다.
- `code-inspector`가 보고한 버그, 규칙 위반, 성능 문제는 사용자에게 알리고, 수정은 사용자가 요청할 때 진행한다.
- 버그 수정, 문제 해결, 속도·효율 개선은 `code-fixer` 서브에이전트에게 맡긴다. `code-inspector` 보고서를 바탕으로 수정할 때는 보고서 내용을 그대로 `code-fixer`에게 전달한다.
- `code-fixer`에게는 보고서와 함께 사용자가 고른 항목을 지정해서 전달한다.
- `code-fixer`가 끝나도 바로 재점검하지 않는다. 사용자가 요청한 수정을 모두 마친 뒤 처리 결과를 모아 `code-inspector`에게 한 번에 전달해 재점검한다.
- 파일 한두 곳의 몇 줄짜리 수정과 주석·문구 수정은 메인 Claude가 직접 고치고, 관련 테스트와 컴파일만 실행한다. 이때는 `code-fixer`와 `code-inspector`를 쓰지 않으며, 이 항목이 위의 맡기기·점검 규칙보다 우선한다. 단, 아래 보안 점검 대상을 바꾸는 수정은 크기와 상관없이 작은 수정으로 보지 않는다.
- `code-inspector`는 Sonnet 모델로 실행한다. 에이전트 정의를 불러오지 못해 범용 에이전트로 대신 실행할 때도 `model: sonnet`을 지정한다.
- 인증·토큰, 회원·계정 복구, 소셜 로그인, 파일 업로드, `SecurityConfig`·`WebConfig`를 바꿨으면 `code-inspector`와 함께 `security-reviewer`로 보안 점검한다. 보고된 취약점은 사용자에게 알리고, 수정은 사용자가 요청할 때 보고서를 `code-fixer`에게 전달해 진행한 뒤 `security-reviewer`로 다시 점검한다.
