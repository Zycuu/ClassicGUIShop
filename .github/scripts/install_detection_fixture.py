"""Install a tiny detector fixture in an explicitly disposable test server."""
import argparse
import json
import pathlib
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('server', type=pathlib.Path)
parser.add_argument('--disposable', action='store_true')
args = parser.parse_args()
if not args.disposable:
    parser.error('--disposable is required')
target = args.server / 'world' / 'datapacks' / 'guishop-detection-fixture.zip'
target.parent.mkdir(parents=True, exist_ok=True)
if target.exists():
    raise SystemExit('Fixture already exists; preserve it or use another test server.')
component = {'type': 'minecraft:set_components', 'components': {
    'minecraft:item_name': {'text': 'Detection Fixture'},
    'minecraft:custom_data': {'marker': 'guishop_detection_test', 'nbt': 'literal metadata'}}}
def table(modifiers):
    return {'type': 'minecraft:chest', 'pools': [{'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': 'minecraft:stone', 'modifier': modifiers}]}]}
with zipfile.ZipFile(target, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('pack.mcmeta', json.dumps({'pack': {
        'min_format': 121, 'max_format': 121, 'description': 'ClassicGUIShop disposable detector fixture'}}))
    archive.writestr('data/guishop_detection_test/loot_table/items/static.json', json.dumps(table([component])))
    archive.writestr('data/guishop_detection_test/loot_table/items/unsupported.json', json.dumps(table([
        component, {'type': 'minecraft:set_count', 'count': 1}])))
    extra = {'type': 'minecraft:set_components', 'components': {
        'minecraft:item_name': {'text': 'Second Namespace Fixture'},
        'minecraft:custom_data': {'marker': 'guishop_detection_test_second'}}}
    archive.writestr('data/guishop_detection_second/loot_table/items/static.json', json.dumps(table([extra])))
    # The same exact variant in another namespace must not double the pack count.
    archive.writestr('data/guishop_detection_second/loot_table/items/repeated.json', json.dumps(table([component])))
print('Installed disposable detection fixture:', target)
