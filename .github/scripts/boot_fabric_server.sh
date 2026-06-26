#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: boot_fabric_server.sh <server-directory> <log-file>" >&2
  exit 2
fi

server_directory="$(cd "$1" && pwd)"
log_file="$2"
cd "$server_directory"
rm -f "$log_file"

java -Xms512M -Xmx1536M -jar fabric-server-launch.jar nogui > "$log_file" 2>&1 &
server_pid=$!
ready=0

for _ in $(seq 1 150); do
  if grep -Fq "[ClassicGUIShop] Runtime listing filter parked" "$log_file" 2>/dev/null; then
    ready=1
    break
  fi

  if grep -Eq "Could not execute entrypoint|NoSuchMethodError|NoSuchFieldError|ClassNotFoundException|Mod resolution encountered an incompatible mod set|Exception in server tick loop" "$log_file" 2>/dev/null; then
    break
  fi

  if ! kill -0 "$server_pid" 2>/dev/null; then
    break
  fi
  sleep 1
done

if kill -0 "$server_pid" 2>/dev/null; then
  kill -INT "$server_pid" 2>/dev/null || true
  for _ in $(seq 1 20); do
    if ! kill -0 "$server_pid" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  if kill -0 "$server_pid" 2>/dev/null; then
    kill -TERM "$server_pid" 2>/dev/null || true
  fi
fi
wait "$server_pid" 2>/dev/null || true

if [ "$ready" -ne 1 ]; then
  echo "ClassicGUIShop did not complete its server-started compatibility pass." >&2
  tail -n 250 "$log_file" >&2 || true
  exit 1
fi

if ! grep -Fq "[ClassicGUIShop] Initialized with" "$log_file"; then
  echo "ClassicGUIShop did not initialize." >&2
  tail -n 250 "$log_file" >&2
  exit 1
fi

if grep -Eq "Could not execute entrypoint|NoSuchMethodError|NoSuchFieldError|ClassNotFoundException|Mod resolution encountered an incompatible mod set|Exception in server tick loop" "$log_file"; then
  echo "A compatibility failure was detected." >&2
  tail -n 250 "$log_file" >&2
  exit 1
fi

printf 'ClassicGUIShop startup verified in %s\n' "$server_directory"
tail -n 80 "$log_file"
