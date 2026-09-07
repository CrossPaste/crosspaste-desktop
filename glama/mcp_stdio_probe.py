#!/usr/bin/env python3
"""Minimal MCP stdio client: runs a command, sends initialize + tools/list, prints results."""
import json
import subprocess
import sys
import threading
import time

cmd = sys.argv[1:]
p = subprocess.Popen(
    cmd,
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    bufsize=1,
)


def drain_stderr():
    for line in p.stderr:
        sys.stderr.write("[stderr] " + line)


threading.Thread(target=drain_stderr, daemon=True).start()


def send(obj):
    p.stdin.write(json.dumps(obj) + "\n")
    p.stdin.flush()


def recv(expected_id, timeout=120):
    """Return the response whose id matches; notifications and other messages are skipped."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        line = p.stdout.readline()
        if not line:
            if p.poll() is not None:
                raise SystemExit(f"process exited with {p.returncode}")
            time.sleep(0.1)
            continue
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except json.JSONDecodeError:
            sys.stderr.write("[stdout-noise] " + line + "\n")
            continue
        if msg.get("id") == expected_id:
            return msg
        sys.stderr.write("[skipped] " + line + "\n")
    raise SystemExit(f"timeout waiting for response id={expected_id}")


send({
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
        "protocolVersion": "2025-06-18",
        "capabilities": {},
        "clientInfo": {"name": "probe", "version": "0"},
    },
})
init = recv(1)
print("initialize ->", json.dumps(init.get("result", init))[:400])
send({"jsonrpc": "2.0", "method": "notifications/initialized"})
send({"jsonrpc": "2.0", "id": 2, "method": "tools/list"})
tools = recv(2)
names = [t["name"] for t in tools.get("result", {}).get("tools", [])]
print("tools/list ->", names)
send({"jsonrpc": "2.0", "id": 3, "method": "resources/list"})
res = recv(3)
print("resources/list ->", [r["name"] for r in res.get("result", {}).get("resources", [])])
p.stdin.close()
try:
    p.wait(timeout=10)
except subprocess.TimeoutExpired:
    p.kill()
sys.exit(0 if names else 1)
