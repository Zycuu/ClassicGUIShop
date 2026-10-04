"""Explicit test listings for disposable CI servers; does not perform discovery."""
import json
import sys
from pathlib import Path


def fixture(version):
    newer_item = {
        "26.2": "minecraft:chiseled_cinnabar",
        "26.3": "minecraft:black_concrete_slab",
    }[version]
    return [
        {"item": item, "listingId": item, "name": item,
         "buy": 123.45, "sell": 12.34, "manualPrice": True}
        for item in ("minecraft:diamond", newer_item)
    ]


def main():
    mode, version, directory = sys.argv[1:]
    path = Path(directory) / "shops.json"
    expected = fixture(version)
    if mode == "seed":
        path.parent.mkdir(parents=True, exist_ok=True)
        if path.exists() and json.loads(path.read_text()).get("categories"):
            raise RuntimeError("Refusing to overwrite an existing shop configuration")
        path.write_text(json.dumps({"categories": [{"id": "compatibility_fixture",
            "name": "Compatibility Fixture", "icon": "minecraft:diamond", "items": expected}]}, indent=2))
    elif mode == "verify":
        actual = {item["listingId"]: item for category in json.loads(path.read_text())["categories"]
                  for item in category["items"]}
        assert set(actual) == {item["listingId"] for item in expected}, "Fixture listings changed"
        for item in expected:
            returned = actual[item["listingId"]]
            assert returned["buy"] == item["buy"] and returned["sell"] == item["sell"]
            assert returned["manualPrice"] is True
        print("Version-switch fixture listings and manual prices preserved")
    else:
        raise ValueError("Use seed or verify")


if __name__ == "__main__":
    main()
