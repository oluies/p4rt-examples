#!/usr/bin/env bash
# Regenerate everything derived from p4/forwarding.p4:
#   1. p4c -> src/main/resources/forwarding.p4info.json  (P4Runtime schema)
#            src/main/resources/forwarding.json          (bmv2 device config)
#   2. typegen -> src/main/scala/generated/forwarding.scala  (typed Scala)
#
# All three are committed, so you only need this when the .p4 changes. Step 2 is
# P4R-Type's own `typegen.generate`, invoked via `sbt runMain codegen.genTypes`.
#
# Pinned to the same p4c the P4R-Type repo pins, so the emitted p4info is stable.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

P4C_IMAGE="p4lang/p4c:1.2.5.15"
RES="src/main/resources"

# p4lang publishes linux/amd64 only; explicit platform so this also runs (emulated)
# on Apple silicon. p4c writes the p4info where --p4runtime-files points and the
# compiled bmv2 JSON into -o; mount the repo so both land under src/main/resources.
docker run --rm --platform linux/amd64 -v "$HERE:/w" -w /w "$P4C_IMAGE" \
  sh -c "p4c --target bmv2 --arch v1model \
             --p4runtime-files /w/$RES/forwarding.p4info.json \
             -o /w/$RES p4/forwarding.p4"
echo "refreshed $RES/forwarding.p4info.json and $RES/forwarding.json"

# p4c also drops a forwarding.p4i (preprocessed source) next to the JSON; it is a
# build artifact, not something to commit.
rm -f "$RES/forwarding.p4i"

sbt -batch "runMain codegen.genTypes"
echo "done. review the diff, then rebuild: sbt compile"
