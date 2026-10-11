"""Derive a small, reversible vanilla merchant minimum-price profile.

Developer tooling only. A positive emerald value must survive every modeled
conversion, including complete native output vectors. Keep ordinary prices
where possible, then restore discounts down to the lowest safe whole count.
SciPy is not a runtime dependency. Rebuild and rerun both preset audits after
changing this profile; this is not a promise about arbitrary custom mechanics.
"""
import argparse, collections, hashlib, json, math, pathlib, zipfile
import numpy as np
from scipy.optimize import linprog
from scipy.sparse import coo_matrix

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('minecraft_jar', type=pathlib.Path)
parser.add_argument('model', type=pathlib.Path)
parser.add_argument('native_facts', type=pathlib.Path)
parser.add_argument('--blueprints', type=pathlib.Path, required=True, help='Native VillagerTrade.CODEC definitions from the separate audit addon')
parser.add_argument('--output', type=pathlib.Path, required=True)
args = parser.parse_args()
jar = zipfile.ZipFile(args.minecraft_jar)
defs = {n[30:-5]: json.loads(jar.read(n)) for n in jar.namelist()
        if n.startswith('data/minecraft/villager_trade/') and n.endswith('.json')}
model = json.loads(args.model.read_text())
native = json.loads(args.native_facts.read_text())
blueprints = json.loads(args.blueprints.read_text())
assert set(blueprints)==set(defs),'Native blueprint coverage does not match the inspected vanilla definitions'

def minimum(name, d):
    base = d['wants'].get('count', 1)
    return base if name.startswith('wandering_trader/') else max(1, base - math.floor(150*d.get('reputation_discount', 0)) - max(1, math.floor(.550000011920929*base)))

floors = {name: d['wants'].get('count', 1) for name, d in defs.items()}
# Unstackable water bottles and boats cannot accept larger input stacks.
# Raise the few supplying trades instead. Later minimization lowers every
# count that is not required by a real vanilla exchange/crafting chain.
floors['librarian/3/emerald_glass'] = 4
floors['wandering_trader/emerald_sand'] = 8
floors['wandering_trader/emerald_red_sand'] = 4
floors['mason/1/clay_ball_emerald'] = 32
for name in defs:
    if name.startswith('wandering_trader/emerald_') and name.endswith('_log'):
        floors[name] = 7

seed_harvests={'native/break/minecraft:'+s for s in ('melon_seeds','pumpkin_seeds','wheat_seeds','beetroot_seeds','torchflower_seeds','pitcher_pod')}
actions = [a for a in native['actions'] if a['kind'] != 'farming-excluded' and a['source'] not in seed_harvests]
# The first model supplies exhaustive modifier possibilities; the native
# audit supplies actual remainder vectors and independently sampled outputs.
groups = {}
active_blocks={x['item'] for x in native['details']['vanillaItemInventory'] if x['isBlockItem']}
for rule in model['rules']:
    if rule.get('block') and rule['block'] not in active_blocks:continue
    if 'share' in rule:
        vector = json.dumps(rule['inputs'], sort_keys=True)
        key = (rule['kind'], vector, tuple(sorted(rule.get('catalysts', []))))
        a = groups.setdefault(key, {'source':rule['name'], 'kind':rule['kind'], 'inputs':rule['inputs'], 'outputs':{}})
        a['outputs'][rule['output']] = max(a['outputs'].get(rule['output'], 0), rule['count'])
    else:
        actions.append({'source':rule['name'], 'kind':rule['kind'], 'inputs':rule['inputs'], 'outputs':{rule['output']:rule['count']}})
actions.extend(groups.values())
keys = sorted({k for a in actions for k in a['outputs']} | {k for a in actions for s in a['inputs'] for k in s['choices']})
indices = {k:i for i,k in enumerate(keys)}
aux = {}
rows = []
row_sources={}
merchant_rows = collections.defaultdict(list)
ranges = [(0, None) for _ in keys]
# Preserve a positive cent-scale outlet for ordinary purchasable items even
# in Easy. Actual Normal buy prices bound the anchor, so a cheap finite input
# cannot make emeralds so inexpensive that common resources round down to 0.
for entry in native['prices']['normal']:
    key=entry['key']
    if key not in indices or not entry['buy']>0:continue
    i=indices[key];previous=ranges[i][1]
    ranges[i]=(.015,entry['buy'] if previous is None else min(previous,entry['buy']))
ranges[indices['minecraft:emerald']] = (.5, .5)

for a in actions:
    row = collections.Counter({indices[k]:v for k,v in a['outputs'].items()})
    name = a['source'].removeprefix('trade/').removeprefix('minecraft:')
    for si, slot in enumerate(a['inputs']):
        choices = tuple(sorted(set(slot['choices'])))
        if len(choices) == 1:
            i = indices[choices[0]]
        else:
            if choices not in aux:
                aux[choices] = len(ranges); ranges.append((0, None))
                for k in choices: rows.append({aux[choices]:1, indices[k]:-1})
            i = aux[choices]
        count = slot['count']
        if si == 0 and name in defs and a['kind'] in ('trade', 'merchant', 'merchant-map-conservative'):
            merchant_rows[name].append((len(rows), i, count, row.get(i, 0)))
            count = max(count, floors[name])
        row[i] -= count
    row_sources[len(rows)]=a['source'];rows.append(dict(row))

def solve():
    for name, entries in merchant_rows.items():
        for ri, column, actual, output in entries:
            rows[ri][column] = output - max(actual, floors[name])
    rr, cc, vv = [], [], []
    for ri, row in enumerate(rows):
        for ci, value in row.items():
            if value: rr.append(ri); cc.append(ci); vv.append(value)
    matrix = coo_matrix((vv, (rr, cc)), shape=(len(rows), len(ranges))).tocsr()
    return linprog(np.zeros(len(ranges)), A_ub=matrix, b_ub=np.zeros(len(rows)), bounds=ranges, method='highs',
                   options={'primal_feasibility_tolerance':1e-9, 'dual_feasibility_tolerance':1e-9})

result = solve()
if not result.success:
    diagnostic_actions=json.loads(json.dumps(actions))
    for a in diagnostic_actions:
        name=a['source'].removeprefix('trade/').removeprefix('minecraft:')
        if name in defs and a['kind'] in ('trade','merchant','merchant-map-conservative'):
            a['inputs'][0]['count']=max(a['inputs'][0]['count'],floors[name])
    args.output.with_name('trade-loop-diagnostic-v1.json').write_text(json.dumps({'actions':diagnostic_actions,'prices':{'normal':[{'key':'minecraft:emerald','buy':1,'sell':1}]}}))
    # An elastic diagnosis names the remaining incompatible native offers.
    rr,cc,vv=[],[],[]
    for ri,row in enumerate(rows):
        for ci,value in row.items():
            if value:rr.append(ri);cc.append(ci);vv.append(value)
    labels={ri:name for name,entries in merchant_rows.items() for ri,_,_,_ in entries}
    extra=list(labels)
    for j,ri in enumerate(extra):rr.append(ri);cc.append(len(ranges)+j);vv.append(-1)
    matrix=coo_matrix((vv,(rr,cc)),shape=(len(rows),len(ranges)+len(extra))).tocsr()
    diagnostic=linprog(np.r_[np.zeros(len(ranges)),np.ones(len(extra))],A_ub=matrix,b_ub=np.zeros(len(rows)),bounds=ranges+[(0,None)]*len(extra),method='highs')
    if diagnostic.success:
        for j,ri in enumerate(extra):
            if diagnostic.x[len(ranges)+j]>.000001:
                print('Conflict',labels[ri],floors[labels[ri]],[(keys[c] if c<len(keys) else 'choices',v,float(diagnostic.x[c])) for c,v in rows[ri].items()],flush=True)
    else:
        rr,cc,vv=[],[],[]
        for ri,row in enumerate(rows):
            for ci,value in row.items():
                if value:rr.append(ri);cc.append(ci);vv.append(value)
            rr.append(ri);cc.append(len(ranges)+ri);vv.append(-1)
        matrix=coo_matrix((vv,(rr,cc)),shape=(len(rows),len(ranges)+len(rows))).tocsr()
        diagnostic=linprog(np.r_[np.zeros(len(ranges)),np.ones(len(rows))],A_ub=matrix,b_ub=np.zeros(len(rows)),bounds=ranges+[(0,None)]*len(rows),method='highs')
        if diagnostic.success:
            for ri,row in enumerate(rows):
                if diagnostic.x[len(ranges)+ri]>.000001:print('Conversion conflict',row_sources.get(ri,'choices'),[(keys[c] if c<len(keys) else 'choices',v,float(diagnostic.x[c])) for c,v in row.items()],flush=True)
    raise AssertionError('Initial vanilla protection is infeasible: '+result.message)
print('Initial profile is feasible', flush=True)
# Prefer preserving ordinary costs. Each unchanged deep-discount minimum is
# tested against the entire graph, not just the goods offered by one NPC.
order = sorted(defs, key=lambda n: (floors[n] <= defs[n]['wants'].get('count',1), defs[n]['wants']['id'] != 'minecraft:emerald', n))
for name in order:
    low, high = minimum(name, defs[name]), floors[name]
    floors[name] = low
    if solve().success:
        continue
    while low < high:
        mid = (low+high)//2
        floors[name] = mid
        if solve().success: high = mid
        else: low = mid+1
    floors[name] = low
result = solve()
assert result.success
profile = {n: {'minimum':int(floors[n]), 'base':int(d['wants'].get('count', 1)),
               'vanillaMinimum':int(minimum(n, d)), 'wants':d['wants'], 'gives':d['gives'],
               'additional_wants':d.get('additional_wants'), 'maxUses':d.get('max_uses', 4),
               'xp':d.get('xp', 1), 'discount':d.get('reputation_discount', 0),
               'fingerprint':hashlib.sha256(json.dumps(blueprints[n],sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest()}
           for n,d in sorted(defs.items()) if floors[n] > minimum(n,d)}
report = {'version':1, 'minecraft':'26.3', 'profile':profile,
          'method':'Whole-inventory native and exhaustive conversion constraints, positive emerald anchor, greedy whole-count discount restoration',
          'definitions':len(defs), 'affectedDefinitions':len(profile),
          'ordinaryCostIncreases':{n:{'before':p['base'], 'after':p['minimum']} for n,p in profile.items() if p['minimum'] > p['base']},
          'constraints':len(rows), 'nativeFacts':args.native_facts.name}
args.output.write_text(json.dumps(report, indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k != 'profile'}, indent=2), flush=True)
