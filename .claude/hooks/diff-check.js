// Stop 훅: 작업이 끝날 때 git diff --check 로 공백 문제를 확인한다.
// 문제가 있으면 한 번만 Claude 에게 돌려보내 확인하게 한다.
const fs = require('fs');
const { spawnSync } = require('child_process');

let input = {};
try {
  input = JSON.parse(fs.readFileSync(0, 'utf8') || '{}');
} catch {
  input = {};
}

// 이미 이 훅 때문에 다시 진행한 경우에는 반복하지 않는다.
if (input.stop_hook_active) {
  process.exit(0);
}

const cwd = process.env.CLAUDE_PROJECT_DIR || process.cwd();
const run = (extra) =>
  spawnSync('git', ['diff', ...extra, '--check'], { cwd, encoding: 'utf8' }).stdout || '';

const problems = (run([]) + run(['--cached'])).trim();

if (problems) {
  process.stdout.write(JSON.stringify({
    decision: 'block',
    reason:
      'git diff --check에서 공백 문제가 발견됐다. 이번 작업에서 바꾼 파일이면 고치고, ' +
      '사용자가 바꾼 파일이면 고치지 말고 알리기만 한다.\n' + problems.slice(0, 2000),
  }));
}

process.exit(0);
