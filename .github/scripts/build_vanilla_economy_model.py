"""Derive bounded vanilla conversion facts from Mojang's shipped 26.3 data.

This emits our own economic model, not copies of Minecraft source/resources.
Run against the unobfuscated official common JAR. Mods and external packs are
deliberately not inputs. Every recipe/trade receives a coverage classification.
"""
import argparse, collections, hashlib, json, math, pathlib, re, struct, zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('minecraft_jar', type=pathlib.Path)
parser.add_argument('--output', type=pathlib.Path, required=True)
args = parser.parse_args()
jar = zipfile.ZipFile(args.minecraft_jar)
data = {n[15:-5]: json.loads(jar.read(n)) for n in jar.namelist()
        if n.startswith('data/minecraft/') and n.endswith('.json')}
rules, coverage = [], []

def class_fields(name,descriptor):
    raw=jar.read(name);count=struct.unpack_from('>H',raw,8)[0];pos=10;utf={};i=1
    while i<count:
        typ=raw[pos];pos+=1
        if typ==1:
            size=struct.unpack_from('>H',raw,pos)[0];pos+=2;utf[i]=raw[pos:pos+size].decode('utf-8',errors='replace');pos+=size
        elif typ in (3,4,9,10,11,12,17,18):pos+=4
        elif typ in (5,6):pos+=8;i+=1
        elif typ in (7,8,16,19,20):pos+=2
        elif typ==15:pos+=3
        else:raise ValueError(('unknown class constant',name,typ))
        i+=1
    pos+=6;interfaces=struct.unpack_from('>H',raw,pos)[0];pos+=2+interfaces*2
    count=struct.unpack_from('>H',raw,pos)[0];pos+=2;fields=[]
    for _ in range(count):
        flags,field,desc,attributes=struct.unpack_from('>HHHH',raw,pos);pos+=8
        if flags&1 and flags&8 and descriptor in utf[desc] and re.fullmatch('[A-Z][A-Z0-9_]*',utf[field]):fields.append(utf[field].lower())
        for _ in range(attributes):
            size=struct.unpack_from('>I',raw,pos+2)[0];pos+=6+size
    return fields

vanilla_items=set('minecraft:'+v for cls in ['net/minecraft/references/ItemIds.class','net/minecraft/references/BlockItemIds.class'] for v in class_fields(cls,'BlockItemId' if cls.endswith('BlockItemIds.class') else 'ResourceKey'))
vanilla_potions=['minecraft:'+v for v in class_fields('net/minecraft/world/item/alchemy/Potions.class','Holder')]

assert len(vanilla_items)>1000 and len(vanilla_potions)>40,(len(vanilla_items),len(vanilla_potions))


def tag(kind, value, seen=()):
    if isinstance(value, list): return sorted(set(x for v in value for x in tag(kind, v, seen)))
    if isinstance(value, dict): return tag(kind, value.get('id', value.get('item')), seen)
    if not isinstance(value, str): raise ValueError(('unsupported ingredient', value))
    if not value.startswith('#'): return [value]
    name = value[1:].split(':', 1)[-1]
    key = f'tags/{kind}/{name}'
    if key in seen: raise ValueError(('tag cycle', key))
    if key not in data: raise ValueError(('missing vanilla tag', key))
    return sorted(set(x for v in data[key]['values'] for x in tag(kind, v, seen+(key,))))

for key,value in data.items():
    if key.startswith('tags/item/'):
        for entry in value['values']:
            entry=entry.get('id') if isinstance(entry,dict) else entry
            if isinstance(entry,str) and entry.startswith('minecraft:'):vanilla_items.add(entry)

def stack_key(stack):
    item = stack.get('id', stack.get('name'))
    contents = stack.get('components', {}).get('minecraft:potion_contents', {})
    if 'potion' in contents: return item+'|potion='+contents['potion']
    return item

def ingredient(value, count=1):
    return {'choices': tag('item', value), 'count': count}

def rule(name, kind, inputs, out, count=1, catalysts=(), block=None):
    if not out or not out.startswith('minecraft:'): raise ValueError(('bad output', name, out))
    if count <= 0: return
    vanilla_items.add(out.split('|',1)[0])
    for inp in inputs:
        vanilla_items.update(x.split('|',1)[0] for x in inp['choices'])
    row = {'name': name, 'kind': kind, 'inputs': inputs, 'output': out, 'count': count}
    if catalysts: row['catalysts'] = list(catalysts)
    if block: row['block'] = block
    rules.append(row)

remainders = {'minecraft:water_bucket':'minecraft:bucket', 'minecraft:lava_bucket':'minecraft:bucket',
              'minecraft:milk_bucket':'minecraft:bucket', 'minecraft:honey_bottle':'minecraft:glass_bottle',
              'minecraft:dragon_breath':'minecraft:glass_bottle'}

def crafting(name, inputs, output, count=1):
    # Water and milk can be refilled repeatedly without consuming another
    # resource. Honey/lava/breath and fish must not be invented by reusing their
    # empty container: their contents are consumed or gathered separately.
    paid, catalysts, returned = [], [], collections.Counter()
    for inp in inputs:
        choices = inp['choices']
        if choices and all(x in ['minecraft:water_bucket','minecraft:milk_bucket'] for x in choices):
            catalysts.extend(choices)
        else:
            paid.append(inp)
            if choices and all(x in remainders for x in choices):
                shells=set(remainders[x] for x in choices)
                if len(shells)!=1:raise ValueError(('mixed recipe remainder',name,choices))
                returned[next(iter(shells))]+=inp['count']
    rule(name, 'recipe', paid, output, count, catalysts)
    if catalysts: rules[-1]['pricingInputs']=inputs
    if returned:
        rules[-1]['share']=1/(1+len(returned))
        for shell,quantity in returned.items():
            rule(name+'/returned/'+shell,'recipe',paid,shell,quantity,catalysts)
            rules[-1]['share']=1/(1+len(returned))

def potion_ingredient(value):
    items = tag('item', value['item'])
    match = value.get('potion_contents')
    if not match: return items
    potions = match.get('potions', match.get('potion'))
    if not potions or set(match)-{'potions','potion'}: raise ValueError(('unknown potion predicate', value))
    return [item+'|potion='+p for item in items for p in tag('potion', potions)]

enchants = {key.split('/',1)[1]:value for key,value in data.items() if key.startswith('enchantment/')}
for name, recipe in sorted((k[7:],v) for k,v in data.items() if k.startswith('recipe/')):
    typ = recipe['type'].split(':')[1]; result = recipe.get('result', {})
    output, count = stack_key(result), result.get('count',1)
    status = 'bounded'
    if typ == 'crafting_shaped':
        inputs = [ingredient(recipe['key'][char]) for line in recipe['pattern'] for char in line if char!=' ']
        crafting(name,inputs,output,count)
    elif typ == 'crafting_shapeless': crafting(name,[ingredient(v) for v in recipe['ingredients']],output,count)
    elif typ in ('smelting','blasting','smoking','campfire_cooking','stonecutting'):
        # Ignoring fuel/tool wear is conservative and covers free campfire cooking.
        crafting(name,[ingredient(recipe['ingredient'])],output,count)
    elif typ == 'brewing':
        a,b = potion_ingredient(recipe['input']),potion_ingredient(recipe['reagent'])
        inputs=[{'choices':a,'count':3},{'choices':b,'count':1}]
        # One reagent services all three potion slots in the actual stand.
        crafting(name,inputs,stack_key(recipe['output']),recipe['output'].get('count',1)*3)
    elif typ == 'crafting_transmute':
        if output is None and recipe.get('add_material_count_to_result'):
            for source in tag('item',recipe['input']):rule(name+'/'+source,'copy',[ingredient(recipe['material'])],source,catalysts=[source])
        else: crafting(name,[ingredient(recipe['input']),ingredient(recipe['material'])],output,count)
    elif typ == 'smithing_transform': crafting(name,[ingredient(recipe[k]) for k in ('template','base','addition')],output,count)
    elif typ == 'crafting_decorated_pot': crafting(name,[ingredient(recipe[k]) for k in ('back','front','left','right')],output,count)
    elif typ == 'crafting_imbue':
        for potion in vanilla_potions:
            source=[x+'|potion='+potion for x in tag('item',recipe['source'])]
            rule(name+'/'+potion,'recipe',[ingredient(recipe['material'],8),{'choices':source,'count':1}],output+'|potion='+potion,count)
    elif typ == 'crafting_special_firework_rocket': crafting(name,[ingredient(recipe['fuel']),ingredient(recipe['shell'])],output,count)
    elif typ == 'crafting_special_firework_star': crafting(name,[ingredient(recipe['fuel']),ingredient(recipe['dye'])],output,count)
    elif typ in ('crafting_dye','crafting_special_firework_star_fade','crafting_special_shielddecoration','smithing_trim'):
        status='cosmetic: no extra ordinary item; component-specific variants remain buy-only'
    elif typ in ('crafting_special_bannerduplicate','crafting_special_bookcloning'):
        status='copy: source retained; each extra copy consumes a blank target'
        target = recipe.get('banner',recipe.get('material'))
        for blank in tag('item',target): rule(name+'/'+blank,'copy',[ingredient(blank)],output,count)
    elif typ=='crafting_special_mapextending': status='map: handled by map clone/extension interaction rules'
    elif typ=='crafting_special_repairitem': status='repair: two inputs produce one; durability never exceeds a new item'
    else: raise ValueError(('unclassified vanilla recipe',name,typ))
    coverage.append({'source':'recipe/'+name,'type':typ,'status':status})

def enchantment_keys(options):
    return [f'minecraft:enchanted_book|enchantment={e}@{level}' for e in tag('enchantment',options)
            for level in range(1,enchants[e.split(':')[1]]['max_level']+1)]

for name, trade in sorted((k[15:],v) for k,v in data.items() if k.startswith('villager_trade/')):
    wants = trade['wants']; base=wants.get('count',1)
    if not isinstance(base,(int,float)): raise ValueError(('unbounded trade count',name,base))
    # Survival maximum positive reputation: 20*5 + 25 + 25 = 150;
    # Hero V reduction: floor((0.3f + 0.0625f*4) * base). Demand never
    # contributes a negative amount. Additional wants are NOT discounted.
    if name.startswith('wandering_trader/'):
        minimum=max(1,base)
    else:
        minimum=max(1,base-math.floor(150*trade.get('reputation_discount',0))-max(1,math.floor(.550000011920929*base)))
    inputs=[{'choices':[stack_key(wants)],'count':minimum}]
    if 'additional_wants' in trade:
        extra=trade['additional_wants'];inputs.append({'choices':[stack_key(extra)],'count':extra.get('count',1)})
    gives=trade['gives']; outputs=[stack_key(gives)]; modifier=trade.get('given_item_modifier',[])
    modifiers=modifier if isinstance(modifier,list) else [modifier]
    for mod in modifiers:
        typ=mod['type'].split(':')[1]
        if typ=='enchant_randomly':
            # Extra emerald charges only increase the actual price. The
            # discounted one-emerald lower bound remains conservative.
            outputs=enchantment_keys(mod['options']) if gives['id']=='minecraft:enchanted_book' else [gives['id']]
        elif typ=='set_random_potion': outputs=[gives['id']+'|potion='+v for v in tag('potion',mod['options'])]
        elif typ=='set_potion': outputs=[gives['id']+'|potion='+mod['id']]
        elif typ in ('filtered','enchant_with_levels','set_random_dyes','exploration_map','set_stew_effect'): pass
        else: raise ValueError(('unclassified trade modifier',name,typ))
    for output in outputs: rule('trade/'+name,'trade',inputs,output,gives.get('count',1))
    coverage.append({'source':'villager_trade/'+name,'status':'bounded; merchant variants and discounts; secondary payment preserved'})

# Actual instant vanilla interactions, not a valuation of all world resources.
for filled in ['water_bucket','lava_bucket','milk_bucket','powder_snow_bucket','cod_bucket','salmon_bucket','pufferfish_bucket','tropical_fish_bucket','axolotl_bucket','tadpole_bucket','sulfur_cube_bucket']:
    if filled in ['water_bucket','milk_bucket']:
        rule('fill/'+filled,'interaction',[ingredient('minecraft:bucket')],'minecraft:'+filled)
    rule('empty/'+filled,'interaction',[ingredient('minecraft:'+filled)],'minecraft:bucket')
for filled in ['minecraft:potion|potion=minecraft:water']:
    rule('fill/'+filled,'interaction',[ingredient('minecraft:glass_bottle')],filled)
    rule('empty/'+filled,'interaction',[{'choices':[filled],'count':1}],'minecraft:glass_bottle')
for filled in ['minecraft:honey_bottle','minecraft:dragon_breath']:
    rule('empty/'+filled,'interaction',[ingredient(filled)],'minecraft:glass_bottle')
for food in ['mushroom_stew','suspicious_stew']:
    rule('mooshroom/'+food,'interaction',[ingredient('minecraft:bowl')],'minecraft:'+food)
for food in ['mushroom_stew','rabbit_stew','beetroot_soup','suspicious_stew']:
    rule('eat/'+food,'interaction',[ingredient('minecraft:'+food)],'minecraft:bowl')
for mapped in tag('item','#minecraft:clonable_maps'):
    rule('clone/'+mapped,'copy',[ingredient('minecraft:map')],mapped,catalysts=[mapped])

def clicked_blocks(predicate):
    """The upper-air predicates constrain use, not the consumed block."""
    if predicate.get('offset',[0,0,0]) != [0,0,0]:return []
    typ=predicate['type'].split(':')[1]
    if typ=='matching_blocks':return tag('block',predicate['blocks'])
    if typ=='matching_block_tag':return tag('block','#'+predicate['tag'])
    if typ=='all_of':
        groups=[set(v) for p in predicate['predicates'] if (v:=clicked_blocks(p))]
        return sorted(set.intersection(*groups)) if groups else []
    raise ValueError(('unknown tool predicate',predicate))

for name, transformers in sorted((k[18:],v) for k,v in data.items() if k.startswith('block_transformer/')):
    for transformer in transformers:
        provider=transformer['block_state_provider']
        if provider['type']!='minecraft:rule_based':raise ValueError(('unknown tool provider',name,provider))
        for conversion in provider['rules']:
            output=conversion['then'].get('source',conversion['then'])['id']
            for source in clicked_blocks(conversion['if_true']):
                # Farmland/path have no survival item, but both break to dirt.
                target='minecraft:dirt' if output in ['minecraft:farmland','minecraft:dirt_path'] else output
                rule('tool/'+name+'/'+source,'interaction',[ingredient(source)],target)
                if transformer.get('loot')=='minecraft:till/rooted_dirt':
                    rules[-1]['share']=.5
                    rule('tool/'+name+'/hanging_roots','interaction',[ingredient(source)],'minecraft:hanging_roots')
                    rules[-1]['share']=.5
                elif transformer.get('loot'):raise ValueError(('unknown tool loot',name,transformer['loot']))
    coverage.append({'source':'block_transformer/'+name,'status':'bounded; actual clicked-block predicates, survival drops, tool wear ignored'})

for colour in ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']:
    rule('harden/'+colour,'interaction',[ingredient('minecraft:'+colour+'_concrete_powder')],'minecraft:'+colour+'_concrete')
    for suffix in ['','_slab','_stairs']:
        if 'loot_table/blocks/'+colour+'_concrete_powder'+suffix in data:
            rule('harden/'+colour+suffix,'interaction',[ingredient('minecraft:'+colour+'_concrete_powder'+suffix)],'minecraft:'+colour+'_concrete'+suffix)
for wood in ['oak','spruce','birch','jungle','acacia','dark_oak','mangrove','cherry','pale_oak','poplar','crimson','warped','bamboo']:
    for suffix in ['log','wood','stem','hyphae','block']:
        if 'loot_table/blocks/'+wood+'_'+suffix in data:
            rule('strip/'+wood+'_'+suffix,'interaction',[ingredient('minecraft:'+wood+'_'+suffix)],'minecraft:stripped_'+wood+'_'+suffix)
for node in ['dirt','coarse_dirt','rooted_dirt']:
    rule('mud/'+node,'interaction',[ingredient('minecraft:'+node)],'minecraft:mud',catalysts=['minecraft:glass_bottle'])
rule('dry/mud','interaction',[ingredient('minecraft:mud')],'minecraft:clay')
rule('solidify/lava_source','interaction',[ingredient('minecraft:lava_bucket')],'minecraft:obsidian')
rules[-1]['share']=.5
rule('solidify/lava_source/returned_bucket','interaction',[ingredient('minecraft:lava_bucket')],'minecraft:bucket')
rules[-1]['share']=.5
for name in ['copper_block','cut_copper','cut_copper_slab','cut_copper_stairs','copper_grate','copper_bulb','copper_door','copper_trapdoor','chiseled_copper','copper_chest','copper_bars','copper_chain','copper_lantern','copper_torch']:
    stages=[name,'exposed_'+name,'weathered_'+name,'oxidized_'+name]
    for stage in stages:
        if 'loot_table/blocks/'+stage not in data:continue
        rule('scrape/'+stage,'interaction',[ingredient('minecraft:'+stage)],'minecraft:'+name)
        if 'loot_table/blocks/waxed_'+stage in data:
            rule('unwax/'+stage,'interaction',[ingredient('minecraft:waxed_'+stage)],'minecraft:'+stage)
        for other in stages:
            if 'loot_table/blocks/'+other in data:rule('weather/'+stage+'/'+other,'interaction',[ingredient('minecraft:'+stage)],'minecraft:'+other)

for raw in sorted(k[18:] for k in data if k.startswith('loot_table/blocks/') and ('copper' in k or k.endswith('lightning_rod'))):
    clean=raw.removeprefix('waxed_');base=re.sub(r'^(exposed_|weathered_|oxidized_)','',clean)
    if base=='copper':base='copper_block'
    if raw!=clean:rule('unwax/'+raw,'interaction',[ingredient('minecraft:'+raw)],'minecraft:'+clean)
    if clean!=base:rule('scrape/'+clean,'interaction',[ingredient('minecraft:'+clean)],'minecraft:'+base)
    if clean!=base:rule('weather/'+base+'/'+clean,'interaction',[ingredient('minecraft:'+base)],'minecraft:'+clean)
rule('wet/sponge','interaction',[ingredient('minecraft:sponge')],'minecraft:wet_sponge')
rule('shear/pumpkin','interaction',[ingredient('minecraft:pumpkin')],'minecraft:carved_pumpkin')
rules[-1]['share']=.5
rule('shear/pumpkin_seeds','interaction',[ingredient('minecraft:pumpkin')],'minecraft:pumpkin_seeds',4)
rules[-1]['share']=.5
for fish in ['cod','salmon','pufferfish','tropical_fish']:
    # Releasing/killing a purchased fish consumes the fish. The empty bucket
    # remains, but catching a different wild fish is ordinary resource gathering.
    for output in ['minecraft:'+fish,'minecraft:bone_meal','minecraft:bucket']:
        rule('release/'+fish+'/'+output,'interaction',[ingredient('minecraft:'+fish+'_bucket')],output)
        rules[-1]['share']=1/3
for coral in ['tube','brain','bubble','fire','horn']:
    for suffix in ['_coral','_coral_fan','_coral_block']:
        rule('dry/'+coral+suffix,'interaction',[ingredient('minecraft:'+coral+suffix)],'minecraft:dead_'+coral+suffix)
for colour in ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']:
    rule('wash/'+colour+'_shulker_box','interaction',[ingredient('minecraft:'+colour+'_shulker_box')],'minecraft:shulker_box')
# Remainders can be sold after the other outputs or reused; record their own
# bounded conversion. Crafting rules omit only these retained catalyst inputs.
for filled,empty in remainders.items():rule('remainder/'+filled,'interaction',[ingredient(filled)],empty)

def maximum(number):
    if isinstance(number,(int,float)):return max(0,number)
    typ=number.get('type','').split(':')[-1]
    if typ=='uniform':return max(0,number['max'])
    if typ=='binomial':return number['n'] if number['p']>0 else 0
    raise ValueError(('unknown loot number',number))

def modifiers(value):return value if isinstance(value,list) else [] if value is None else [value]
def loot_entries(entry, inherited=(), state=None):
    own=tuple(modifiers(entry.get('modifier')));mods=inherited+own
    condition=entry.get('condition',{})
    if isinstance(condition,dict) and condition.get('type')=='minecraft:match_block':state={**(state or {}),**condition.get('state',{})}
    if entry['type']=='minecraft:item':yield entry['name'],mods,state or {}
    else:
        for child in entry.get('children',[]):yield from loot_entries(child,mods,state)

def loot_count(mods):
    count=1
    for mod in mods:
        typ=mod['type'].split(':')[1]
        if typ=='set_count':
            condition=mod.get('condition',{})
            state=condition.get('state',{}) if isinstance(condition,dict) and condition.get('type')=='minecraft:match_block' else {}
            if any(k in state and str(state[k]) not in ('0','false') for k in ['age','stage','berries','honey_level']):continue
            count=maximum(mod['count'])+(count if mod.get('add') else 0)
        elif typ=='apply_bonus':
            level=enchants[mod['enchantment'].split(':')[1]]['max_level']; formula=mod['formula'].split(':')[1];p=mod.get('parameters',{})
            if formula=='ore_drops':count*=level+1
            elif formula=='uniform_bonus_count':count+=level*p['bonusMultiplier']
            elif formula=='binomial_with_bonus_count':count+=level+p['extra']
            else:raise ValueError(('unknown loot bonus',formula))
        elif typ=='enchanted_count_increase':
            level=enchants[mod['enchantment'].split(':')[1]]['max_level']
            count+=math.floor(maximum(mod['count'])*level+.5)
            if mod.get('limit',0)>0:count=min(count,mod['limit'])
        elif typ=='limit_count':count=min(count,mod['limit'].get('max',count))
        elif typ in ('explosion_decay','copy_components','copy_state'):pass
        else:raise ValueError(('unknown block loot modifier',typ))
    return count

# Summoning consumes nine ingots in a copper block and one pumpkin head, but
# produces BOTH a chest and a golem. Killing and statue conversion are distinct
# alternatives, so their paired outputs must never be combined into one event.
# Read the actual maximum death recovery, including vanilla Looting, from data.
golem_loot=data['loot_table/entities/copper_golem']
assert len(golem_loot['pools'])==1 and len(golem_loot['pools'][0]['entries'])==1
golem_pool=golem_loot['pools'][0]
golem_drops=list(loot_entries(golem_pool['entries'][0],tuple(modifiers(golem_loot.get('modifier')))+tuple(modifiers(golem_pool.get('modifier')))))
assert len(golem_drops)==1 and golem_drops[0][0]=='minecraft:copper_ingot'
recovered=loot_count(golem_drops[0][1])*maximum(golem_pool.get('rolls',1))
assert recovered==6,('Copper golem salvage changed; re-audit the native behavior',recovered)
heads=ingredient(['minecraft:carved_pumpkin','minecraft:jack_o_lantern'])
spawn_blocks=tag('block','#minecraft:copper')
assert len(spawn_blocks)==8,('Copper golem spawn blocks changed; re-audit native summoning',spawn_blocks)
for source_id in spawn_blocks:
    source=source_id.split(':')[1]
    chest=source.removesuffix('_block')+'_chest' if source.endswith('_block') else source+'_chest'
    inputs=[ingredient('minecraft:'+source),heads]
    outcomes=[('golem_death','minecraft:copper_ingot',recovered)]
    # A harvested statue can be scraped before sale without another consumed
    # material. Each reachable stage is an alternative, not four statues.
    outcomes.extend(('golem_statue_'+phase,'minecraft:'+phase+'copper_golem_statue',1)
                    for phase in ['', 'exposed_', 'weathered_', 'oxidized_'])
    for kind,other,count in outcomes:
        rule('summon/'+source+'/'+kind+'/chest',kind,inputs,'minecraft:'+chest)
        rules[-1]['share']=.5
        rule('summon/'+source+'/'+kind+'/other',kind,inputs,other,count)
        rules[-1]['share']=.5
    # Recovery is useful as crafting material even when its sell price is low.
    # Repeating summons spends only 9 - 6 fresh ingots per chest. This amortized
    # bound closes the recycle loop without splitting acquisition costs across
    # unrelated outputs or inventing free copper. Extra wax/oxidation setup
    # can only raise spending, so omitting it remains conservative.
    rule('summon/'+source+'/net-copper-chest','golem_recycle',
         [ingredient('minecraft:copper_ingot',9-recovered),heads],'minecraft:'+chest)
rule('revive/copper_golem_statue/salvage','interaction',
     [ingredient('minecraft:copper_golem_statue')],'minecraft:copper_ingot',recovered)
coverage.append({'source':'interaction/copper_golem',
                 'status':'native summon chest plus alternative death/statue; maximum Looting recovery and repeated recycling'})

for name, loot in sorted((k[18:],v) for k,v in data.items() if k.startswith('loot_table/blocks/')):
    pools=loot.get('pools',[]);share=max(1,len(pools))
    for pool in pools:
        # A bought composter starts empty. Its full-state bonus costs seven
        # successful compost inputs, already modeled at runtime. Do not turn
        # the stored contents of a pre-filled block into a free refill loop.
        condition=pool.get('condition',{})
        pool_state=condition.get('state',{}) if isinstance(condition,dict) and condition.get('type')=='minecraft:match_block' else {}
        if name=='composter' and str(pool_state.get('level','0'))=='8':continue
        for entry in pool['entries']:
            for item,mods,state in loot_entries(entry,tuple(modifiers(loot.get('modifier')))+tuple(modifiers(pool.get('modifier'))),pool_state):
                if any(k in state and str(state[k]) not in ('0','false') for k in ['age','stage','berries','honey_level']):continue
                count=loot_count(mods)*maximum(pool.get('rolls',1));quantity=1
                if name=='snow':quantity=int(state.get('layers',8 if item=='minecraft:snow_block' else 1))
                if state.get('type')=='double':quantity=2
                # Identity drops cannot share a budget with mutually exclusive
                # Fortune/non-Silk paths, or they would create artificial loops.
                if item=='minecraft:'+name:continue
                rule('break/'+name+'/'+item,'block_drop',[ingredient('minecraft:'+name,quantity)],item,count,block='minecraft:'+name)
                rules[-1]['share']=1/share
    coverage.append({'source':'loot_table/blocks/'+name,'status':'bounded immediate drops; maximum Fortune; growth states excluded; runtime BlockItem check'})

# Piglin barter: any possible vanilla outcome, with its maximum stack size.
for pool in data['loot_table/gameplay/piglin_bartering']['pools']:
    for entry in pool['entries']:
        outputs=[entry['name']];count=1
        for mod in modifiers(entry.get('modifier')):
            typ=mod['type'].split(':')[1]
            if typ=='set_count':count=maximum(mod['count'])
            elif typ=='set_potion':outputs=[entry['name']+'|potion='+mod['id']]
            elif typ=='enchant_randomly':outputs=enchantment_keys(mod['options']) if entry['name']=='minecraft:book' else outputs
            else:raise ValueError(('unhandled barter modifier',typ))
        for out in outputs:rule('barter/'+out,'barter',[ingredient('minecraft:gold_ingot')],out,count)

for enchant,definition in sorted(enchants.items()):
    for level in range(2,definition['max_level']+1):
        lower=f'minecraft:enchanted_book|enchantment=minecraft:{enchant}@{level-1}'
        rule('combine/'+enchant+'/'+str(level),'book',[{'choices':[lower],'count':2}],f'minecraft:enchanted_book|enchantment=minecraft:{enchant}@{level}')
for enchant in tag('enchantment','#minecraft:in_enchanting_table'):
    for level in range(1,enchants[enchant.split(':')[1]]['max_level']+1):
        # Experience, tools and setup are not recurring shop expenses. A
        # possible random result must not have a larger resale than its inputs.
        book='minecraft:enchanted_book|enchantment='+enchant+'@'+str(level)
        rule('enchant/'+enchant+'/'+str(level),'book',[ingredient('minecraft:book'),ingredient('minecraft:lapis_lazuli')],book)
for enchant,definition in sorted(enchants.items()):
    if 'minecraft:'+enchant in tag('enchantment','#minecraft:curse'):continue
    for level in range(1,definition['max_level']+1):
        book='minecraft:enchanted_book|enchantment=minecraft:'+enchant+'@'+str(level)
        rule('grind/'+enchant+'/'+str(level),'book',[{'choices':[book],'count':1}],'minecraft:book')

model={'version':1,'minecraft':'26.3','source_sha256':hashlib.sha256(args.minecraft_jar.read_bytes()).hexdigest(),
       'scope':'shipped vanilla data only; no mod or external datapack economy',
       'items':sorted(vanilla_items),'potions':vanilla_potions,'enchantments':{'minecraft:'+k:v['max_level'] for k,v in enchants.items()},
       'rules':rules,'coverage':coverage,'recipe_count':2042,'trade_count':391,
       'policy':'instant refills, retained catalysts, discounted merchants, batch outputs and possible maximum drops; timed resource farming is separate'}
assert sum(x['source'].startswith('recipe/') for x in coverage)==2042
assert sum(x['source'].startswith('villager_trade/') for x in coverage)==391
args.output.parent.mkdir(parents=True,exist_ok=True)
args.output.write_text(json.dumps(model,separators=(',',':'),sort_keys=True)+'\n',encoding='utf-8')
print(json.dumps({'rules':len(rules),'coverage':len(coverage),'kinds':dict(collections.Counter(x['kind'] for x in rules)),'bytes':args.output.stat().st_size},indent=2))
