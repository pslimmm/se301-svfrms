"""Enforce bundle coverage and complete branch coverage of newly changed decisions."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, capture_output=True, text=True, check=True).stdout


def changed_lines(base, path):
    diff = git('diff', '--unified=0', base, '--', path)
    lines = set()
    for start, count in re.findall(r'^@@ .*?\+(\d+)(?:,(\d+))? @@', diff, re.M):
        lines.update(range(int(start), int(start) + int(count or 1)))
    return lines


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--xml', type=Path, required=True)
    parser.add_argument('--base', required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'target/coverage-summary.json')
    args = parser.parse_args()
    git('cat-file', '-e', args.base + '^{commit}')
    report = ET.parse(args.xml).getroot()
    totals = {}
    for c in report.findall('counter'):
        if c.get('type') in ['LINE', 'BRANCH']:
            missed, covered = int(c.get('missed')), int(c.get('covered'))
            totals[c.get('type').lower()] = dict(covered=covered, missed=missed,
                                                 ratio=covered / (covered + missed) if covered + missed else 1)
    changed = set(git('diff', '--name-only', args.base, '--', 'src/main/java').splitlines())
    changed.update(git('ls-files', '--others', '--exclude-standard', 'src/main/java').splitlines())
    violations, residual, decisions = [], [], []
    for package in report.findall('package'):
        for source in package.findall('sourcefile'):
            path = 'src/main/java/' + package.get('name') + '/' + source.get('name')
            is_new = subprocess.run(['git', 'cat-file', '-e', args.base + ':' + path], cwd=ROOT,
                                    capture_output=True).returncode != 0
            delta = changed_lines(args.base, path) if path in changed and not is_new else set()
            for line in source.findall('line'):
                missed, covered = int(line.get('mb')), int(line.get('cb'))
                if not missed and not covered:
                    continue
                record = dict(path=path, line=int(line.get('nr')), covered=covered, missed=missed)
                if path in changed and (is_new or record['line'] in delta):
                    decisions.append(record)
                    if missed:
                        violations.append(record)
                elif missed:
                    residual.append(record)
    outcome = dict(base=args.base, bundle=totals, changed_decisions=decisions,
                   uncovered_changed_decisions=violations, inherited_uncovered_decisions=residual,
                   scope='100% of new/modified branch decisions; all inherited branches remain in bundle denominator')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(outcome, indent=2) + '\n')
    print(json.dumps(dict(bundle=totals, changed_decisions=len(decisions), violations=violations,
                         inherited_uncovered_decisions=len(residual)), indent=2))
    if totals['line']['ratio'] < .90 or totals['branch']['ratio'] < .85 or violations:
        raise SystemExit('Coverage gate failed')


if __name__ == '__main__':
    main()
