// PreToolUse 훅: 비밀값 파일 수정과 git push / git reset --hard 를 막는다.
// 사용법: node guard.js <보호할 상대 경로>...
const fs = require('fs');

const protectedPaths = process.argv.slice(2).map(normalize);

function normalize(p) {
  return String(p).replace(/\\/g, '/').toLowerCase();
}

function deny(reason) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: 'PreToolUse',
      permissionDecision: 'deny',
      permissionDecisionReason: reason,
    },
  }));
  process.exit(0);
}

let input = {};
try {
  input = JSON.parse(fs.readFileSync(0, 'utf8') || '{}');
} catch {
  process.exit(0);
}

const tool = input.tool_name || '';
const args = input.tool_input || {};

if (['Edit', 'Write', 'MultiEdit', 'NotebookEdit'].includes(tool)) {
  const file = normalize(args.file_path || args.notebook_path || '');
  const hit = protectedPaths.find((p) => file === p || file.endsWith('/' + p));
  if (hit) {
    deny(`${hit} 파일은 비밀값이 들어 있어 수정할 수 없다. 필요한 변경 내용을 사용자에게 알려 직접 수정하게 한다.`);
  }
}

if (tool === 'Bash' || tool === 'PowerShell') {
  const command = String(args.command || '');
  // git 뒤에 -C <경로>, -c <설정>, --옵션 이 붙은 경우도 잡는다.
  const gitPrefix = String.raw`\bgit(?:\s+(?:-[Cc]\s+\S+|--?[\w-]+(?:=\S+)?))*\s+`;
  if (new RegExp(gitPrefix + String.raw`push\b`).test(command)) {
    deny('git push는 훅으로 막혀 있다. 푸시는 사용자가 직접 한다.');
  }
  if (new RegExp(gitPrefix + String.raw`reset\b[^;&|\n]*--hard\b`).test(command)) {
    deny('git reset --hard는 훅으로 막혀 있다. 변경을 되돌려야 하면 사용자에게 먼저 확인한다.');
  }
}

process.exit(0);
