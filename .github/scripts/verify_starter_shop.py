from pathlib import Path
import subprocess,time,json,hashlib,shutil
import argparse,os
parser=argparse.ArgumentParser(description="Verify first-start Vanilla Normal setup on a disposable loopback fixture; originals are backed up, never deleted.")
parser.add_argument("server_dir",type=Path);parser.add_argument("--java-home",type=Path,required=True);parser.add_argument("--output-dir",type=Path,required=True);parser.add_argument("--disposable",action="store_true")
args=parser.parse_args()
if not args.disposable:parser.error("--disposable is required; this check changes fixture prices and empties its shop after backing up the original files")
server=args.server_dir.resolve();out=args.output_dir.resolve();java=args.java_home.resolve()/"bin"/("java.exe" if os.name=="nt" else "java");out.mkdir(parents=True,exist_ok=True)
properties=dict(line.split("=",1) for line in (server/"server.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
if properties.get("server-ip")!="127.0.0.1" or properties.get("online-mode")!="false":parser.error("Use a disposable offline server bound to loopback")
config_dir=server/"config/guishop"
if config_dir.exists() and any(config_dir.iterdir()):parser.error("This fixture already has saved data; use a fresh configuration for first-start verification")
names=['settings.json','shops.json','enchantments.json','default-shop-setup.json']
def hashes():return {n:hashlib.sha256((server/'config/guishop'/n).read_bytes()).hexdigest() for n in names}
def start(label):
 path=out/('beta3-'+label+'-server.log');log=path.open('x',encoding='utf-8');p=subprocess.Popen([str(java),'-Xms512M','-Xmx1536M','-jar','fabric-server-launch.jar','nogui'],cwd=server,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
 try:
  for i in range(120):
   time.sleep(.5);text=path.read_text(encoding='utf-8',errors='replace')
   if 'Economy audit checked' in text:return p,log,path
   if p.poll() is not None:raise RuntimeError(text[-8000:])
  raise RuntimeError('Readiness timeout: '+text[-8000:])
 except:
  if p.poll() is None:p.stdin.write('stop\n');p.stdin.flush();p.wait(timeout=30)
  log.close();raise

def stop(p,log):
 p.stdin.write('stop\n');p.stdin.flush();p.wait(timeout=30);log.close();assert p.returncode==0
p,log,path=start('fresh-normal-first-start')
try:
 data=json.loads((server/'config/guishop/shops.json').read_text());items=[i for c in data['categories'] for i in c['items']]
 assert len(data['categories'])>=10 and len(items)>1600
 assert all(i['item'].startswith('minecraft:') for i in items)
 assert any(i['item']=='minecraft:diamond' and i['buy']>0 for i in items)
 assert any(i['item']=='minecraft:enchanted_book' and i['buy']>0 for i in items)
 assert all(i['sell']<=i['buy']*.30+.000001 for i in items)
 state=json.loads((server/'config/guishop/default-shop-setup.json').read_text());assert state['status']=='applied' and state['preset']=='normal'
 assert 'Full Vanilla Normal starter shop saved:' in path.read_text(encoding='utf-8',errors='replace')
 print('PASS: fresh installation starts with Full Vanilla Normal, '+str(len(items))+' vanilla listings, books and 30% returns with safety caps',flush=True)
finally:stop(p,log)
before=hashes();p,log,path=start('fresh-normal-restart')
try:assert before==hashes();assert 'Full Vanilla Normal starter shop saved:' not in path.read_text(encoding='utf-8',errors='replace')
finally:stop(p,log)
assert before==hashes()
shops=server/'config/guishop/shops.json';shutil.copy2(shops,shops.with_name('shops-before-custom-price-test.json'))
data=json.loads(shops.read_text());target=next(i for c in data['categories'] for i in c['items'] if i['buy']>0 and i['sell']>0);target['buy']=9876.54;target['sell']=0;shops.write_text(json.dumps(data,indent=2),encoding='utf-8');custom=hashes();p,log,path=start('fresh-normal-custom-price')
try:assert custom==hashes()
finally:stop(p,log)
assert custom==hashes();shutil.copy2(shops,shops.with_name('shops-before-intentional-empty-test.json'));data['categories']=[];shops.write_text(json.dumps(data,indent=2),encoding='utf-8');empty=hashes();p,log,path=start('fresh-normal-intentional-empty')
try:assert empty==hashes();assert json.loads(shops.read_text())['categories']==[]
finally:stop(p,log)
assert empty==hashes()
(out/'beta3-starter-verification.json').write_text(json.dumps({'fresh_items':len(items),'default':'Full Vanilla Normal','sell_return':.30,'restart_before':before,'restart_after':before,'custom_prices_preserved':True,'intentional_empty_shop_preserved':True},indent=2),encoding='utf-8')
print('PASS: starter runs once; restart bytes, customized prices and an intentionally empty shop are preserved',flush=True)
