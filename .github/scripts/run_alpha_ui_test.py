"""Run rendered GUI checks only against an explicitly disposable loopback server."""
import argparse
import os
import pathlib
import re
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('server_dir', type=pathlib.Path)
parser.add_argument('--java-home', type=pathlib.Path, required=True)
parser.add_argument('--gradle', type=pathlib.Path, required=True)
parser.add_argument('--beta-review-only', action='store_true', help='Run the focused Beta library/audit workflows against an existing fixture')
parser.add_argument('--player-client', action='store_true', help='Disable ClassicGUIShop on the client and test player trading')
parser.add_argument('--usefulfood-jar', type=pathlib.Path, help='Optional 26.3 Fabric fixture, installed on this disposable server too')
parser.add_argument('--travelersbackpack-jar', type=pathlib.Path, help='Optional Traveler backpack fixture; requires --forge-config-jar')
parser.add_argument('--forge-config-jar', type=pathlib.Path, help='Optional Forge Config API Port fixture')
parser.add_argument('--disposable', action='store_true', help='Confirm this server and its contents are test fixtures')
args = parser.parse_args()
if not args.disposable:
    parser.error('This harness modifies operator state and shop fixtures. --disposable is required.')
repo = pathlib.Path(__file__).resolve().parents[2]
server_dir = args.server_dir.resolve()
java_dir = args.java_home.resolve()
gradle = args.gradle.resolve()
properties = dict(line.split('=', 1) for line in (server_dir / 'server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
if properties.get('server-ip') != '127.0.0.1' or properties.get('server-port') != '25573' or properties.get('online-mode') != 'false':
    parser.error('Use a disposable offline test server bound to 127.0.0.1:25573.')
server_log = server_dir / ('player-ui-server.log' if args.player_client else 'admin-ui-server.log')
client_log = server_dir / ('player-ui-client.log' if args.player_client else 'admin-ui-client.log')
marker = '[Player UI test]' if args.player_client else '[Alpha UI test]'
java_name = 'java.exe' if os.name == 'nt' else 'java'
client_command = [str(gradle), '-PplayerUiTest=true' if args.player_client else '-PalphaUiTest=true', 'runClient', '--console=plain']
if args.beta_review_only: client_command.append('-PbetaReviewOnly=true')
if args.usefulfood_jar:
    client_command.extend(['-PusefulFoodTestJar=' + str(args.usefulfood_jar.resolve()),
                           '-PfullFabricApiTestJar=' + str(server_dir / 'mods' / 'fabric-api.jar')])
if args.travelersbackpack_jar:
    if not args.forge_config_jar:
        parser.error('--travelersbackpack-jar requires --forge-config-jar')
    client_command.extend(['-PtravelersBackpackTestJar=' + str(args.travelersbackpack_jar.resolve()),
                           '-PforgeConfigTestJar=' + str(args.forge_config_jar.resolve())])
    if not args.usefulfood_jar:
        client_command.append('-PfullFabricApiTestJar=' + str(server_dir / 'mods' / 'fabric-api.jar'))
if os.name == 'nt' and gradle.suffix.lower() in ('.bat', '.cmd'):
    client_command = ['cmd.exe', '/d', '/c', *client_command]
env = os.environ.copy()
env['JAVA_HOME'] = str(java_dir)
env['PATH'] = str(java_dir / 'bin') + os.pathsep + env['PATH']
client = None
with server_log.open('w', encoding='utf8') as server_output, client_log.open('w', encoding='utf8') as client_output:
    server = subprocess.Popen([str(java_dir / 'bin' / java_name), '-Xms512M', '-Xmx1536M', '-jar', 'fabric-server-launch.jar', 'nogui'],
                              cwd=server_dir, stdin=subprocess.PIPE, stdout=server_output, stderr=subprocess.STDOUT, text=True)
    success = False
    try:
        for _ in range(90):
            time.sleep(1)
            content = server_log.read_text(encoding='utf8', errors='replace')
            if '[ClassicGUIShop compatibility test] PASS:' in content:
                break
            if server.poll() is not None:
                raise RuntimeError('Test server exited before readiness. ' + content[-5000:])
        else:
            raise RuntimeError('Test server readiness timeout. ' + content[-5000:])
        server.stdin.write('difficulty peaceful\nwhitelist off\n'); server.stdin.flush()
        client = subprocess.Popen(client_command,
                                  cwd=repo, env=env, stdout=client_output, stderr=subprocess.STDOUT)
        granted = revoked = False
        seen = set()
        for _ in range(600):
            time.sleep(1)
            text = client_log.read_text(encoding='utf8', errors='replace')
            for line in text.splitlines():
                if marker in line and line not in seen:
                    print(line, flush=True); seen.add(line)
            player = re.search(r'\[Alpha UI test\] PLAYER: ([A-Za-z0-9_]+)', text)
            if player and '[Alpha UI test] DENIED_READY' in text and not granted:
                server.stdin.write(f'op {player[1]}\ngive {player[1]} minecraft:diamond 1\n'); server.stdin.flush(); granted = True
            if player and '[Alpha UI test] REVOKE_READY' in text and not revoked:
                server.stdin.write(f'deop {player[1]}\n'); server.stdin.flush(); revoked = True
            if marker + ' FAIL' in text:
                raise RuntimeError(text[-10000:])
            if client.poll() is not None:
                success = client.returncode == 0 and marker + ' PASS:' in text
                if not success:
                    raise RuntimeError('Client failed. ' + text[-12000:])
                break
        else:
            raise RuntimeError('Client test timeout. ' + text[-5000:])
    finally:
        if client is not None and client.poll() is None:
            subprocess.run(['taskkill', '/PID', str(client.pid), '/T', '/F'], capture_output=True) if os.name == 'nt' else client.terminate()
        if server.poll() is None:
            server.stdin.write('stop\n'); server.stdin.flush()
            try:
                server.wait(timeout=30)
            except subprocess.TimeoutExpired:
                server.terminate(); server.wait(timeout=10)
    if not success:
        raise SystemExit('Alpha UI test did not pass')
print('PASS: real Alpha client/server integration', flush=True)
