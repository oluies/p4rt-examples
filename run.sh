#!/usr/bin/env bash
# Start a bmv2 switch in Docker, run the demo against it, then tear the switch
# down. The demo installs forwarding.p4's pipeline itself (via P4R-Type's
# setForwardingPipelineConfig), so the switch starts with no pipeline (--no-p4).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

BMV2_IMAGE="p4lang/behavioral-model:latest"
NAME="p4rt-examples-bmv2"
PORT=9559

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

# The `--` before --grpc-server-addr is load-bearing: it is a target-specific
# option, and without the separator bmv2 prints usage and exits — while the
# published port still answers, so a port check alone would lie.
docker run -d --name "$NAME" --platform linux/amd64 -p "$PORT:$PORT" \
  "$BMV2_IMAGE" simple_switch_grpc --no-p4 -- --grpc-server-addr "0.0.0.0:$PORT" >/dev/null

# Poll the port; `-d` returns before gRPC is actually listening.
echo "waiting for bmv2 on localhost:$PORT ..."
for _ in $(seq 1 60); do
  nc -z localhost "$PORT" 2>/dev/null && break
  sleep 1
done
nc -z localhost "$PORT" 2>/dev/null || { echo "bmv2 never opened :$PORT"; docker logs "$NAME"; exit 1; }

P4RT_BMV2="localhost:$PORT" sbt -batch "run"
