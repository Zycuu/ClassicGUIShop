"""Independent primal inventory-flow audit. Does not import the earlier model or audit."""
from pathlib import Path
from collections import Counter, defaultdict
import json, sys
import numpy as np
from scipy.sparse import coo_matrix, vstack
from scipy.optimize import linprog

root = Path(__file__).resolve().parent
source=Path(sys.argv[1]).resolve()
data = json.loads(source.read_text(encoding='utf-8'))
actions = data['actions']

def audit(preset, *, negative_control=False, allowed_kinds=None, blocked_sales=()):
    preset_actions=actions+data.get('merchantActions',{}).get(preset,[])
    entries = data['prices'][preset]
    buys, sells = {}, {}
    for row in entries:
        k = row['key']
        if row['buy'] > 0: buys[k] = min(buys.get(k, float('inf')), row['buy'])
        if row['sell'] > 0: sells[k] = max(sells.get(k, 0), row['sell'])
    if negative_control:
        # Independent purchased-log -> plank conversion, even with NPCs protected.
        log=min(row['buy'] for row in entries if row['key']=='minecraft:oak_log' and row['buy']>0)
        sells['minecraft:oak_planks']=log
    for key in blocked_sales: sells.pop(key,None)
    kinds = allowed_kinds if allowed_kinds is not None else {a['kind'] for a in preset_actions} - {'farming-excluded'}
    processes = [a for a in preset_actions if a['kind'] in kinds]
    keys = sorted(set(buys) | set(sells) | {k for a in processes for k in a['outputs']} | {k for a in processes for s in a['inputs'] for k in s['choices']})
    index = {k:i for i,k in enumerate(keys)}
    cost, metadata, rr, cc, vv = [], [], [], [], []
    equality_rows = []
    budget_cols, process_cols = [], []
    def column(label, coefficients, objective=0):
        col = len(cost); cost.append(objective); metadata.append(label)
        for k,n in coefficients.items():
            if n: rr.append(index[k]); cc.append(col); vv.append(-n)
        return col
    for k,v in buys.items(): budget_cols.append((column({'buy':k,'price':v},{k:1},v),v))
    for k,v in sells.items(): column({'sell':k,'price':v},{k:-1},-v)
    for a in processes:
        coeff = Counter(a['outputs']); multi = []
        for s in a['inputs']:
            choices = sorted(set(s['choices']))
            if len(choices)==1: coeff[choices[0]] -= s['count']
            else: multi.append((choices,s['count']))
        p = column({'process':a['source'],'kind':a['kind'],'inputs':a['inputs'],'outputs':a['outputs']},coeff)
        process_cols.append(p)
        for choices,count in multi:
            eq = {p:-count}
            for k in choices: eq[column({'ingredient':k,'process':a['source']},{k:-1})] = 1
            equality_rows.append(eq)
    n = len(cost)
    inventory = coo_matrix((vv,(rr,cc)), shape=(len(keys),n)).tocsr()
    spend = coo_matrix(([v for _,v in budget_cols],([0]*len(budget_cols),[c for c,_ in budget_cols])),shape=(1,n)).tocsr()
    count = coo_matrix(([1]*len(process_cols),([0]*len(process_cols),process_cols)),shape=(1,n)).tocsr()
    ub = vstack([inventory,spend,count],format='csr')
    rhs = np.r_[np.zeros(len(keys)),10000,10000]
    eqr,eqc,eqv=[],[],[]
    for i,row in enumerate(equality_rows):
        for j,v in row.items(): eqr.append(i);eqc.append(j);eqv.append(v)
    eq = coo_matrix((eqv,(eqr,eqc)),shape=(len(equality_rows),n)).tocsr()
    result = linprog(cost,A_ub=ub,b_ub=rhs,A_eq=eq if equality_rows else None,b_eq=np.zeros(len(equality_rows)) if equality_rows else None,bounds=(0,None),method='highs',options={'dual_feasibility_tolerance':1e-8,'primal_feasibility_tolerance':1e-8})
    if not result.success: raise RuntimeError((preset,result.message))
    witness = [{**metadata[i],'quantity':round(float(v),8)} for i,v in enumerate(result.x) if v>1e-6]
    residual = ub@result.x-rhs
    answer = {'preset':preset,'negativeControl':negative_control,'temporarilyExcludedSales':list(blocked_sales),'method':'primal maximum currency profit with complete inventory conservation and joint returned-output vectors','processes':len(processes),'items':len(keys),'variables':n,'maxProfit':round(-result.fun,6),'maxViolation':float(max(residual.max(),0)),'witness':witness}
    print(f"{preset}{' control' if negative_control else ''}: profit {answer['maxProfit']}; {len(witness)} active decisions; {len(processes)} processes",flush=True)
    return answer

report = {'nativeFacts':sys.argv[1], 'coverageStatuses':dict(Counter(x['status'] for x in data['coverage'])), 'nativeDetails':{k:v for k,v in data['details'].items() if k not in ('nativeEquipmentSaleQuotes','vanillaItemInventory')},'results':[audit(p) for p in data['prices']]}
if any(result['maxProfit']>1e-6 for result in report['results']):
    excluded = sorted({row['key'] for rows in data['prices'].values() for row in rows if ('lightning_rod' in row['key'] or 'copper_golem_statue' in row['key'] or 'copper_chest' in row['key'])})
    report['diagnosticExcludingIdentifiedFamilies'] = [audit(p,blocked_sales=excluded) for p in data['prices']]
    immediate_kinds = {a['kind'] for a in actions} - {'world-timed','farming-excluded'}
    report['immediateRoutesOnly'] = [audit(p,allowed_kinds=immediate_kinds) for p in data['prices']]
report['negativeControl'] = audit('normal',negative_control=True)
if report['negativeControl']['maxProfit'] <= 0: raise AssertionError('Injected unsafe plank sale was not detected')
output = Path(sys.argv[2]).resolve() if len(sys.argv)>2 else source.with_name(source.stem+'-profit-report.json')
output.write_text(json.dumps(report,indent=2),encoding='utf-8')
if any(result['maxProfit']>1e-6 for result in report['results']):raise AssertionError('Profitable preset sequence found; inspect '+str(output))
print(f'Report: {output}',flush=True)
