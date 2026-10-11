"""Run the separate test addon only in an explicitly disposable server fixture."""
from pathlib import Path
import argparse, json, os, queue, subprocess, threading, time, sys
if hasattr(sys.stdout,"reconfigure"):sys.stdout.reconfigure(encoding="utf-8",errors="replace")

parser = argparse.ArgumentParser()
parser.add_argument('server', type=Path)
parser.add_argument('report', type=Path)
parser.add_argument('--java', default='java')
parser.add_argument('--disposable', action='store_true', required=True)
args = parser.parse_args()
server, report = args.server.resolve(), args.report.resolve()
if not (server / 'fabric-server-launch.jar').is_file():
    raise SystemExit('Prepare an owned test fixture first.')
if report.exists():
    raise SystemExit('Choose a new report path to preserve earlier results.')
report.parent.mkdir(parents=True, exist_ok=True)
log_path = report.with_suffix('.log')
events = queue.Queue()
with log_path.open('x', encoding='utf-8') as log:
    process = subprocess.Popen([args.java, '-Xms512M', '-Xmx1536M',
        '-Dguishop.disposableReleaseTest=true', '-Dguishop.releaseReport=' + str(report),
        '-jar', 'fabric-server-launch.jar', 'nogui'], cwd=server,
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding='utf-8', errors='replace',
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    def read():
        for line in process.stdout:
            log.write(line); log.flush(); events.put(line)
    reader = threading.Thread(target=read, daemon=True)
    reader.start()
    passed = False
    try:
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline and process.poll() is None:
            try: line = events.get(timeout=1)
            except queue.Empty: continue
            if '[Release smoke] PASS:' in line:
                passed = True; break
            if '[Release smoke] FAIL:' in line: break
    finally:
        if process.poll() is None:
            process.stdin.write('stop\n'); process.stdin.flush()
            try: process.wait(timeout=50)
            except subprocess.TimeoutExpired:
                process.terminate(); process.wait(timeout=10)
        reader.join(timeout=5)
if not passed or process.returncode != 0 or not report.is_file():
    print(log_path.read_text(encoding='utf-8')[-18000:])
    raise SystemExit('Release smoke failed: ' + str(log_path))
result = json.loads(report.read_text(encoding='utf-8'))
if not result['passed']: raise SystemExit('Test reported failure.')
print(json.dumps(result))
