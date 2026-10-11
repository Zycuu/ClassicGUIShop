"""Export native conversion facts from an explicitly disposable loopback server."""
from pathlib import Path
import argparse,os,queue,subprocess,threading,time

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('server_dir',type=Path)
parser.add_argument('--java-home',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--disposable',action='store_true')
parser.add_argument('--blueprints',action='store_true')
parser.add_argument('--native-trades',action='store_true',help='Audit presets with protection Off, without persisting a settings change')
args=parser.parse_args()
if not args.disposable:parser.error('--disposable is required; the addon exercises native world/inventory mechanics')
server=args.server_dir.resolve();output=args.output.resolve();log_path=output.with_suffix('.log')
if output.exists() or log_path.exists():parser.error('Preserve existing audit output; choose a new path')
properties=dict(line.split('=',1) for line in (server/'server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
if properties.get('server-ip')!='127.0.0.1' or properties.get('online-mode')!='false':parser.error('Use a disposable offline server bound to loopback')
output.parent.mkdir(parents=True,exist_ok=True)
java=args.java_home.resolve()/'bin'/('java.exe' if os.name=='nt' else 'java')
events=queue.Queue();log=log_path.open('x',encoding='utf-8')
property_name='guishop.tradeBlueprintsOutput' if args.blueprints else 'guishop.secondAuditOutput'
vm_args=['-Dguishop.auditNativeTrades=true'] if args.native_trades else []
process=subprocess.Popen([str(java),'-Xms512M','-Xmx1536M','-Dguishop.disposableNativeAudit=true','-Dguishop.expectPatchedPresets=true',*vm_args,f'-D{property_name}={output}','-jar','fabric-server-launch.jar','nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
def read():
    for line in process.stdout:log.write(line);log.flush();events.put(line)
thread=threading.Thread(target=read,daemon=True);thread.start();passed=False
try:
    deadline=time.monotonic()+360
    while time.monotonic()<deadline:
        try:line=events.get(timeout=1)
        except queue.Empty:
            if process.poll() is not None:break
            continue
        if '[Second audit]' in line:print(line.rstrip(),flush=True)
        if '[Second audit] PASS:' in line:passed=True;break
        if '[Second audit] FAIL' in line or 'Exception in server tick loop' in line:break
finally:
    if process.poll() is None:
        process.stdin.write('stop\n');process.stdin.flush()
        try:process.wait(timeout=40)
        except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=10)
    thread.join(timeout=3);log.close()
if not passed or process.returncode!=0:raise SystemExit('Native export failed; inspect '+str(log_path))
print('PASS: '+str(output),flush=True)
