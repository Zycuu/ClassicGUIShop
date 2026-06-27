#!/usr/bin/env python3

import json
import shutil
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path


def read_json(url: str):
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)


def fabric_api_version(minecraft_version: str) -> str:
    metadata_url = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
    with urllib.request.urlopen(metadata_url, timeout=60) as response:
        root = ET.fromstring(response.read())

    versions = [
        node.text
        for node in root.findall("./versioning/versions/version")
        if node.text
        and node.text.endswith("+" + minecraft_version)
        and "SNAPSHOT" not in node.text.upper()
    ]
    if not versions:
        raise RuntimeError(f"No Fabric API release found for Minecraft {minecraft_version}")
    return versions[-1]


def selected_loader_version(minecraft_version: str, requested: str | None) -> str:
    if requested:
        return requested

    loaders = read_json(f"https://meta.fabricmc.net/v2/versions/loader/{minecraft_version}")
    if not loaders:
        raise RuntimeError(f"No Fabric Loader release found for Minecraft {minecraft_version}")
    loader_entry = next(
        (entry for entry in loaders if entry.get("loader", {}).get("stable")),
        loaders[0],
    )
    return loader_entry["loader"]["version"]


def main() -> None:
    if len(sys.argv) not in (4, 6):
        raise SystemExit(
            "Usage: setup_fabric_server.py <minecraft-version> <server-directory> <universal-jar> "
            "[<loader-version> <install-fabric-api:true|false>]"
        )

    minecraft_version = sys.argv[1]
    server_directory = Path(sys.argv[2]).resolve()
    universal_jar = Path(sys.argv[3]).resolve()
    requested_loader = sys.argv[4] if len(sys.argv) == 6 else None
    install_fabric_api = sys.argv[5].lower() == "true" if len(sys.argv) == 6 else True

    if not universal_jar.is_file():
        raise FileNotFoundError(universal_jar)

    server_directory.mkdir(parents=True, exist_ok=True)
    mods_directory = server_directory / "mods"
    mods_directory.mkdir(parents=True, exist_ok=True)

    installers = read_json("https://meta.fabricmc.net/v2/versions/installer")
    installer = next((entry for entry in installers if entry.get("stable")), installers[0])
    loader_version = selected_loader_version(minecraft_version, requested_loader)

    installer_path = server_directory / "fabric-installer.jar"
    urllib.request.urlretrieve(installer["url"], installer_path)

    api_version = None
    if install_fabric_api:
        api_version = fabric_api_version(minecraft_version)
        api_url = (
            "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/"
            f"{api_version}/fabric-api-{api_version}.jar"
        )
        urllib.request.urlretrieve(api_url, mods_directory / "fabric-api.jar")

    subprocess.run(
        [
            "java",
            "-jar",
            str(installer_path),
            "server",
            "-mcversion",
            minecraft_version,
            "-loader",
            loader_version,
            "-downloadMinecraft",
        ],
        cwd=server_directory,
        check=True,
    )

    launcher = server_directory / "fabric-server-launch.jar"
    if not launcher.is_file():
        raise FileNotFoundError(launcher)

    shutil.copy2(universal_jar, mods_directory / universal_jar.name)
    (server_directory / "eula.txt").write_text("eula=true\n", encoding="utf-8")

    api_label = api_version if api_version else "not installed"
    print(
        f"Prepared Minecraft {minecraft_version}; Fabric Loader {loader_version}; "
        f"Installer {installer['version']}; Fabric API {api_label}"
    )


if __name__ == "__main__":
    main()
