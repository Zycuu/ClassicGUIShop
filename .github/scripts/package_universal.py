"""One installable Fabric JAR. Loader selects exactly one nested Minecraft-specific implementation."""
from pathlib import Path
import io,json,re,sys,zipfile

root=Path(__file__).resolve().parents[2]
profiles=json.loads((root/'ports/targets.json').read_text())
properties=dict(line.strip().split('=',1) for line in (root/'gradle.properties').read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
version=properties['mod_version']
destination=Path(sys.argv[1]).resolve()
targets=sys.argv[2:] or list(profiles)
entries=[]
def version_tuple(value):return tuple(map(int,value.split('.')))
def check_bundled_loaders(data,minimum):
    with zipfile.ZipFile(io.BytesIO(data)) as nested:
        item=json.loads(nested.read('fabric.mod.json'))
        requirement=item.get('depends',{}).get('fabricloader')
        if requirement is not None:
            match=re.fullmatch(r'>=(\d+(?:\.\d+)+)',requirement)
            if match is None:raise ValueError('Review unsupported Loader range: '+str(requirement))
            if version_tuple(match[1])>version_tuple(minimum):raise ValueError(item['id']+' requires Loader '+match[1]+' above profile minimum '+minimum)
        for entry in item.get('jars',[]):check_bundled_loaders(nested.read(entry['file']),minimum)

for target in targets:
    jar=root/'build'/target/'libs'/f'ClassicGUIShop-mc{target}-v{version}.jar'
    if not jar.is_file():raise FileNotFoundError(jar)
    with zipfile.ZipFile(jar) as child:
        metadata=json.loads(child.read('fabric.mod.json'))
        assert metadata['id']=='guishop' and metadata['depends']['minecraft']==target
        assert metadata['version']==version+'+mc'+target
        assert metadata['depends']['fabricloader']=='>='+profiles[target]['loaderMinimum']
        assert not any('StandaloneFontTest' in entry or 'compatibilityTest' in entry or '/testing/' in entry or 'UiTest' in entry or '/audit/' in entry or entry.startswith('data/guishop_release_test/') for entry in child.namelist())
    check_bundled_loaders(jar.read_bytes(),profiles[target]['loaderMinimum'])
    entries.append((f'META-INF/jars/guishop-mc{target}.jar',jar.read_bytes()))
minimum=min((profiles[target]['loaderMinimum'] for target in targets),key=lambda value:tuple(map(int,value.split('.'))))
metadata={'schemaVersion':1,'id':'classicguishop','version':version,'name':'ClassicGUIShop','description':'Vanilla server shop and optional client Adminshop. One JAR for the listed Minecraft versions.','authors':['Zycu'],'license':'All-Rights-Reserved','environment':'*','depends':{'fabricloader':'>='+minimum,'minecraft':targets,'java':'>=17','guishop':'*'},'jars':[{'file':name}for name,data in entries],'custom':{'guishop:supported-minecraft':targets}}
destination.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(destination,'x',compression=zipfile.ZIP_DEFLATED)as bundle:
    bundle.writestr('fabric.mod.json',json.dumps(metadata,indent=2))
    for name,data in entries:bundle.writestr(name,data)
print(destination)
