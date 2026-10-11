"""Generate auditable vanilla preset value caps with complete inventory budgets.

This developer-only LP combines native mechanics and exhaustive shipped-data
possibilities. Runtime stores final prices; neither SciPy nor an optimizer is
bundled with the mod. Unknown mechanics and resource farming remain excluded.
"""
import argparse, collections, hashlib, json, pathlib
import numpy as np
from scipy.optimize import linprog
from scipy.sparse import coo_matrix

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('model',type=pathlib.Path)
parser.add_argument('native_facts',type=pathlib.Path)
parser.add_argument('trade_profile',type=pathlib.Path)
parser.add_argument('--output',type=pathlib.Path,required=True)
args=parser.parse_args()
model=json.loads(args.model.read_text());native=json.loads(args.native_facts.read_text());profile=json.loads(args.trade_profile.read_text())
def canonical(key):
    return key if '|potion=' in key or '|enchantment=' in key else key.split('|',1)[0]
actions=[]
for a in native['actions']:
    if a['kind']=='farming-excluded':continue
    outputs=collections.Counter()
    for k,n in a['outputs'].items():outputs[canonical(k)]+=n
    actions.append({'source':a['source'],'kind':a['kind'],'inputs':[{'choices':sorted({canonical(k) for k in s['choices']}),'count':s['count']} for s in a['inputs']],'outputs':dict(outputs)})
groups={};active_blocks={x['item'] for x in native['details']['vanillaItemInventory'] if x['isBlockItem']}
for r in model['rules']:
    if r.get('block') and r['block'] not in active_blocks:continue
    slots=json.loads(json.dumps(r['inputs']))
    if r['kind']=='trade':
        definition=r['name'].removeprefix('trade/')
        if definition in profile['profile']:slots[0]['count']=max(slots[0]['count'],profile['profile'][definition]['minimum'])
    if 'share' in r:
        key=(r['kind'],json.dumps(slots,sort_keys=True),tuple(sorted(r.get('catalysts',[]))))
        a=groups.setdefault(key,{'source':r['name'],'kind':r['kind'],'inputs':slots,'outputs':{}})
        a['outputs'][r['output']]=max(a['outputs'].get(r['output'],0),r['count'])
    else:actions.append({'source':r['name'],'kind':r['kind'],'inputs':slots,'outputs':{r['output']:r['count']}})
actions.extend(groups.values())
keys=sorted({k for a in actions for k in a['outputs']}|{k for a in actions for s in a['inputs'] for k in s['choices']}|{canonical(row['key']) for entries in native['prices'].values() for row in entries})
indices={k:i for i,k in enumerate(keys)};rows=[];aux={};variable_count=len(keys)
sale_values={}
for key in keys:
    sale_values[key]=variable_count;variable_count+=1
    rows.append({sale_values[key]:1,indices[key]:-1})
equipment=native['details']['vanillaEquipmentEnchantments']
enchant_max={}
for enchant,maximum in model['enchantments'].items():
    levels=[f'minecraft:enchanted_book|enchantment={enchant}@{level}' for level in range(1,maximum+1)]
    levels=[k for k in levels if k in indices]
    if not levels:continue
    enchant_max[enchant]=variable_count;variable_count+=1
    for k in levels:rows.append({sale_values[k]:1,enchant_max[enchant]:-1})
enchanted_trades={a['source'].removeprefix('minecraft:') for a in native['actions'] if a['kind']=='merchant' and any('|applied=' in k for k in a['outputs'])}
for a in actions:
    row=collections.Counter({indices[k]:n for k,n in a['outputs'].items()})
    for s in a['inputs']:
        choices=tuple(sorted(set(s['choices'])))
        if len(choices)==1:i=indices[choices[0]]
        else:
            if choices not in aux:
                aux[choices]=variable_count;variable_count+=1
                for k in choices:rows.append({aux[choices]:1,indices[k]:-1})
            i=aux[choices]
        row[i]-=s['count']
    rows.append(dict(row))
    # /sellhand can add exact book sell values to an enchanted item's base
    # payout. Bound the most conservative combination of supported vanilla
    # enchantments, across every level, by the cheapest native trade payment.
    if a['kind']=='trade' and a['source'].removeprefix('trade/') in enchanted_trades:
        bonus=dict(row)
        for item,count in a['outputs'].items():
            bonus[indices[item]]=bonus.get(indices[item],0)-count
            bonus[sale_values[item]]=bonus.get(sale_values[item],0)+count
            for enchant in equipment.get(item,[]):
                if enchant in enchant_max:bonus[enchant_max[enchant]]=bonus.get(enchant_max[enchant],0)+count
        rows.append(bonus)
# XP farming is intended gameplay. Even with free XP and the cheapest one-
# lapis table roll, enchanting purchased equipment must not create cash profit.
for enchantments in equipment.values():
    budget=collections.Counter({indices['minecraft:lapis_lazuli']:-1})
    for enchant in enchantments:
        if enchant in enchant_max:budget[enchant_max[enchant]]+=1
    if len(budget)>1:rows.append(dict(budget))
rr,cc,vv=[],[],[]
for ri,row in enumerate(rows):
    for ci,value in row.items():
        if value:rr.append(ri);cc.append(ci);vv.append(value)
matrix=coo_matrix((vv,(rr,cc)),shape=(len(rows),variable_count)).tocsr()
tables=[]
for name in ('normal','hard','easy','basics'):
    factor=3 if name=='hard' else .75 if name=='easy' else 1
    buys={}
    for entry in native['prices'][name]:
        key=canonical(entry['key'])
        if entry['buy']>0:buys[key]=min(buys.get(key,float('inf')),entry['buy'])
    ranges=[(0,None) for _ in range(variable_count)];objective=np.zeros(variable_count)
    for key,price in buys.items():
        if key not in indices:raise AssertionError(('Unmodeled purchase',name,key))
        i=indices[key];ranges[i]=(0,price)
        i=sale_values[key];ranges[i]=(.015*factor,price);objective[i]=-1/price
    result=linprog(objective,A_ub=matrix,b_ub=np.zeros(len(rows)),bounds=ranges,method='highs',options={'primal_feasibility_tolerance':1e-9,'dual_feasibility_tolerance':1e-9})
    if not result.success:raise AssertionError((name,result.message))
    residual=float(np.max(matrix@result.x));assert residual<1e-7
    values={k:float(max(0,result.x[i])) for k,i in sale_values.items()}
    inventory={k:float(max(0,result.x[i])) for k,i in indices.items()}
    assert all(values[k]*.95>=.01 for k in buys)
    tables.append({'name':name,'buySeeds':buys,'values':values,'inventoryValues':inventory})
    print(name,'purchase keys',len(buys),'minimum positive',min(values[k] for k in buys),'max residual',residual,flush=True)
fingerprint=hashlib.sha256(json.dumps(profile['profile'],sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest()
output={'version':1,'minecraft':'26.3','tradeProfileSha256':fingerprint,
        'method':'Joint native and exhaustive inventory constraints; positive common-item floors; maximum normalized purchasable-item value',
        'constraints':len(rows),'variables':variable_count,'tables':tables}
args.output.write_text(json.dumps(output,sort_keys=True,separators=(',',':'))+'\n')
print('Written',args.output,flush=True)
