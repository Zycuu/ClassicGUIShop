"""One installable Fabric JAR. Loader selects exactly one nested Minecraft-specific implementation."""
from pathlib import Path
import io,json,sys,zipfile

root=Path(__file__).resolve().parents[2]
profiles=json.loads((root/'ports/targets.json').read_text())
destination=Path(sys.argv[1]).resolve()
targets=sys.argv[2:] or list(profiles)
entries=[]
for target in targets:
    jar=root/'build'/target/'libs'/f'ClassicGUIShop-mc{target}-v1.4.0.jar'
    if not jar.is_file():raise FileNotFoundError(jar)
    with zipfile.ZipFile(jar) as child:
        metadata=json.loads(child.read('fabric.mod.json'))
        assert metadata['id']=='guishop' and metadata['depends']['minecraft']==target
        assert metadata['version']=='1.4.0+mc'+target
        assert not any('StandaloneFontTest' in entry or 'compatibilityTest' in entry or '/testing/' in entry or 'UiTest' in entry or '/audit/' in entry for entry in child.namelist())
    entries.append((f'META-INF/jars/guishop-mc{target}.jar',jar.read_bytes()))
metadata={'schemaVersion':1,'id':'classicguishop','version':'1.4.0','name':'ClassicGUIShop','description':'Vanilla server shop and optional client Adminshop. One JAR for the listed Minecraft versions.','authors':['Zycu'],'license':'All-Rights-Reserved','environment':'*','depends':{'fabricloader':'>=0.19.5','minecraft':targets,'java':'>=17','guishop':'*'},'jars':[{'file':name}for name,data in entries],'custom':{'guishop:supported-minecraft':targets}}
destination.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(destination,'x',compression=zipfile.ZIP_DEFLATED)as bundle:
    bundle.writestr('fabric.mod.json',json.dumps(metadata,indent=2))
    for name,data in entries:bundle.writestr(name,data)
print(destination)
