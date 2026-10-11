"""Generate auditable vanilla preset value caps with complete inventory budgets.

This developer-only LP combines native mechanics and exhaustive shipped-data
possibilities. Runtime stores final prices; neither SciPy nor an optimizer is
bundled with the mod. Unknown mechanics and resource farming remain excluded.
"""
import argparse, collections, json, pathlib, re
import numpy as np
from scipy.optimize import linprog
from scipy.sparse import coo_matrix

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('model',type=pathlib.Path)
parser.add_argument('native_facts',type=pathlib.Path)
parser.add_argument('--output',type=pathlib.Path,required=True)
args=parser.parse_args()
model=json.loads(args.model.read_text());native=json.loads(args.native_facts.read_text())
native_merchants=native.get('merchantActions',{}).get('normal',[a for a in native['actions'] if a['kind'].startswith('merchant')])
def canonical(key):
    return key if '|potion=' in key or '|enchantment=' in key else key.split('|',1)[0]
actions=[]
for a in native['actions']:
    if a['kind']=='farming-excluded' or a['kind'].startswith('merchant'):continue
    outputs=collections.Counter()
    for k,n in a['outputs'].items():outputs[canonical(k)]+=n
    actions.append({'source':a['source'],'kind':a['kind'],'inputs':[{'choices':sorted({canonical(k) for k in s['choices']}),'count':s['count']} for s in a['inputs']],'outputs':dict(outputs)})
groups={};active_blocks={x['item'] for x in native['details']['vanillaItemInventory'] if x['isBlockItem']}
for r in model['rules']:
    if r['kind']=='trade':continue
    if r.get('block') and r['block'] not in active_blocks:continue
    slots=json.loads(json.dumps(r['inputs']))
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
# An enchanted barter/loot output can pay for its base item AND its book
# bonus through /sellhand. Capture the complete cash-out, not just the base.
for a in native['actions']:
    if a['kind']=='farming-excluded' or a['kind'].startswith('merchant'):continue
    applied=[(key,count) for key,count in a['outputs'].items() if '|applied=' in key]
    if not applied:continue
    cash=collections.Counter()
    for key,count in a['outputs'].items():
        base=canonical(key)
        if '|applied=' in key:
            cash[sale_values[base]]+=count
            for enchant,level in re.findall(r'(minecraft:[a-z0-9_]+)@(\d+)',key):
                book=f'minecraft:enchanted_book|enchantment={enchant}@{level}'
                if book in sale_values:cash[sale_values[book]]+=count
        else:cash[indices[base]]+=count
    for s in a['inputs']:
        choices=tuple(sorted({canonical(k) for k in s['choices']}))
        variable=indices[choices[0]] if len(choices)==1 else aux[choices]
        cash[variable]-=s['count']
    rows.append(dict(cash))
# Enforce feasible vanilla payment-slot capacities, then runtime chooses the
# lowest safe whole payment within them. No fixed merchant terms are assumed.
def capacity(key):
    item=key.split('|')[0]
    limits=native['details'].get('itemStackLimits',{})
    return limits.get(item,1 if item.endswith(('_boat','_raft','_bucket')) or item=='minecraft:potion' else 64)
capacity_count=0
for a in native_merchants:
    row=collections.Counter()
    for key,count in a['outputs'].items():
        base=canonical(key);row[indices[base]]+=count
    slots=a['inputs'];first=canonical(slots[0]['choices'][0]);maximum=576 if first=='minecraft:emerald' else capacity(first)
    row[indices[first]]-=maximum
    if len(slots)>1:
        second=canonical(slots[1]['choices'][0]);row[indices[second]]-=capacity(second)
    elif maximum>1:row[indices[first]]-=maximum
    rows.append(dict(row));capacity_count+=1
    for key,count in a['outputs'].items():
        if '|applied=' not in key:continue
        base=canonical(key);cash=dict(row);cash[indices[base]]-=count;cash[sale_values[base]]=count
        for e in equipment.get(base,[]):
            if e in enchant_max:cash[enchant_max[e]]=cash.get(enchant_max[e],0)+count
        rows.append(cash)

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
        i=sale_values[key];minimum=.015*factor
        if key.split('|')[0].endswith(('_log','_stem','_wood','_hyphae')):minimum=price*(.1 if name=='hard' else .95 if name=='easy' else .3)/.95
        upper=min(price,price*(.1 if name=='hard' else .95 if name=='easy' else .3)/.95)
        if key=='minecraft:stick':minimum=min(upper,(.20 if name=='easy' else .15)/.95)
        if '|enchantment=' in key:minimum=max(minimum,int(key.rsplit('@',1)[1])/.95)
        ranges[i]=(minimum,upper);objective[i]=-1/price
    result=linprog(objective,A_ub=matrix,b_ub=np.zeros(len(rows)),bounds=ranges,method='highs',options={'primal_feasibility_tolerance':1e-9,'dual_feasibility_tolerance':1e-9})
    if not result.success:raise AssertionError((name,result.message))
    residual=float(np.max(matrix@result.x));assert residual<1e-7
    # Keep attained sell limits fixed, choose a clear currency value, then
    # minimize asset values to avoid unnecessary villager price inflation.
    for key,idx in sale_values.items():ranges[idx]=(max(0,result.x[idx]),max(0,result.x[idx]))
    currency_objective=np.zeros(variable_count);currency_objective[indices['minecraft:emerald']]=-1
    currency_result=linprog(currency_objective,A_ub=matrix,b_ub=np.zeros(len(rows)),bounds=ranges,method='highs')
    assert currency_result.success
    e=indices['minecraft:emerald'];ranges[e]=(currency_result.x[e],currency_result.x[e])
    value_objective=np.zeros(variable_count)
    for idx in indices.values():value_objective[idx]=1
    polished=linprog(value_objective,A_ub=matrix,b_ub=np.zeros(len(rows)),bounds=ranges,method='highs')
    assert polished.success;result=polished
    residual=float(np.max(matrix@result.x));assert residual<1e-7
    values={k:float(max(0,result.x[i])) for k,i in sale_values.items()}
    inventory={k:float(max(0,result.x[i])) for k,i in indices.items()}
    assert all(values[k]*.95>=.01 for k in buys)
    tables.append({'name':name,'buySeeds':buys,'values':values,'inventoryValues':inventory})
    print(name,'purchase keys',len(buys),'minimum positive',min(values[k] for k in buys),'max residual',residual,flush=True)
output={'version':2,'minecraft':'26.3','method':'Joint native conversion budgets and merchant payment capacities; preserved common-resource returns; runtime payments use these asset values; earned XP is separate gameplay income','constraints':len(rows),'variables':variable_count,'capacityConstraints':capacity_count,'tables':tables}
args.output.write_text(json.dumps(output,sort_keys=True,separators=(',',':'))+'\n')
print('Written',args.output,flush=True)
