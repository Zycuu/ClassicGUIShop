"""Independent no-arbitrage LP check of exported disposable-server preset facts.

Developer audit only: SciPy/HiGHS is not a mod dependency. A feasible value for
each item must be at least its sell price, no higher than its cheapest buy, and
cannot increase over a conversion. Simultaneous outputs share ONE constraint.
This checks combinations of the modeled routes, not arbitrary world farms or
modified data packs. The native game checks separately validate key mechanics.
"""
import argparse, collections, json, pathlib
import numpy as np
import scipy
from scipy.optimize import linprog
from scipy.sparse import coo_matrix

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('model',type=pathlib.Path)
parser.add_argument('report',type=pathlib.Path)
parser.add_argument('--output',type=pathlib.Path,required=True)
args=parser.parse_args()
model=json.loads(args.model.read_text(encoding='utf-8'))
report=json.loads(args.report.read_text(encoding='utf-8'))

def solve(preset,broken=False):
    available=set(preset['bounds'])
    keys=sorted(available);indices={key:i for i,key in enumerate(keys)}
    ranges=[[0,None] for key in keys]
    for row in preset['listings']:
        key=row.get('key')
        if key not in indices:continue
        i=indices[key];ranges[i][0]=max(ranges[i][0],row['sell'])
        if row['buy']>0:ranges[i][1]=row['buy'] if ranges[i][1] is None else min(ranges[i][1],row['buy'])
    if broken:
        log_buy=min(row['buy'] for row in preset['listings'] if row.get('key')=='minecraft:oak_log' and row['buy']>0)
        ranges[indices['minecraft:oak_planks']][0]=log_buy
    rows=[];auxiliaries={};groups={}
    def input_vector(rule):
        counts=collections.Counter()
        for inp in rule['inputs']:
            choices=tuple(sorted(set(inp['choices']) & available))
            if not choices:return None
            counts[choices]+=inp['count']
        return tuple(sorted(counts.items()))
    def consume(vector):
        row=collections.Counter()
        for choices,count in vector:
            if len(choices)==1:i=indices[choices[0]]
            else:
                if choices not in auxiliaries:
                    auxiliaries[choices]=len(ranges);ranges.append([0,None])
                    for key in choices:rows.append({auxiliaries[choices]:1,indices[key]:-1})
                i=auxiliaries[choices]
            row[i]-=count
        return row
    active=0
    for rule in preset.get('rules',report.get('_runtimeRules',model['rules'])):
        if rule['output'] not in available or any(k not in available for k in rule.get('catalysts',[])):continue
        if rule.get('block') and rule['block'] not in available:continue
        vector=input_vector(rule)
        if vector is None:continue
        active+=1
        # Paired native recipe/interaction outputs must be assessed together.
        # Max deduplication avoids double-counting repeated state predicates.
        if 'share' in rule:
            group=(rule['kind'],vector,tuple(sorted(set(rule.get('catalysts',[])))))
            outputs=groups.setdefault(group,{})
            outputs[rule['output']]=max(outputs.get(rule['output'],0),rule['count'])
        else:
            row=consume(vector);row[indices[rule['output']]]+=rule['count'];rows.append(dict(row))
    for (_,vector,_),outputs in groups.items():
        row=consume(vector)
        for key,count in outputs.items():row[indices[key]]+=count
        rows.append(dict(row))
    rr=[];cc=[];vv=[]
    for rownum,row in enumerate(rows):
        for column,value in row.items():
            if value:rr.append(rownum);cc.append(column);vv.append(value)
    matrix=coo_matrix((vv,(rr,cc)),shape=(len(rows),len(ranges))).tocsr()
    result=linprog(np.zeros(len(ranges)),A_ub=matrix,b_ub=np.zeros(len(rows)),bounds=ranges,method='highs',options={'primal_feasibility_tolerance':1e-9,'dual_feasibility_tolerance':1e-9})
    summary={'success':bool(result.success),'status':int(result.status),'message':result.message,'activeRules':active,'constraints':len(rows),'variables':len(ranges),'simultaneousOutputGroups':len(groups)}
    if result.success:
        residual=float(np.max(matrix @ result.x));summary['maximumResidual']=residual
        assert residual<=1e-7,summary
        assert all(result.x[i]>=lo-1e-7 and (hi is None or result.x[i]<=hi+1e-7) for i,(lo,hi) in enumerate(ranges))
    return summary

results={}
for name in ['normal','hard','easy','basics']:
    results[name]=solve(report[name]);print(name,json.dumps(results[name]),flush=True)
    assert results[name]['success'],f'No-arbitrage potential is infeasible for {name}: {results[name]}'
broken=solve(report['normal'],True)
assert not broken['success'] and broken['status']==2,('Overpriced crafted planks must fail the independent check',broken)
results['knownLogPlankExploitRejected']=broken
args.output.write_text(json.dumps({'scipy':scipy.__version__,'minecraft':'26.3','scope':model['scope'],'method':'HiGHS independent price potentials with combined output constraints','results':results},indent=2)+'\n',encoding='utf-8')
print('PASS: all four modeled vanilla presets have feasible no-arbitrage potentials; known log/plank exploit rejected')
