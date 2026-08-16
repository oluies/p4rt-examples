# p4rt-examples

A standalone, runnable demonstration of **[P4R-Type]** — the type-safe Scala 3
P4Runtime library — driving a third-party P4 program end-to-end: install the
pipeline, insert a table entry, read it back, all checked at compile time.

The P4 program here (`p4/forwarding.p4`, IPv4 longest-prefix-match forwarding) is
vendored from ETH Zürich's [nsg-ethz/p4-learning]. P4R-Type never saw it during
its own development — that's the point: the types are generated from *its*
p4info, and the demo won't compile if a table, action, or parameter name is
wrong.

[P4R-Type]: https://github.com/oluies/P4R-Type
[nsg-ethz/p4-learning]: https://github.com/nsg-ethz/p4-learning

## Why a separate repository (the licensing reason)

P4R-Type is **MIT**. The p4-learning examples are **GPLv3**. You cannot vendor
GPL sources into an MIT project without the copyleft clouding the whole tree — so
they don't live there. They live here:

- This repo is **GPLv3** (see `LICENSE`, `NOTICE`). It owns the GPL `.p4`, and
  everything derived from it — the p4c-emitted `forwarding.p4info.json` /
  `forwarding.json`, and the typegen-generated `forwarding.scala`.
- It depends on **`io.github.oluies::p4rt-scala:0.1.0`** from Maven Central — an
  ordinary binary dependency. A GPL application using an MIT library is fine; the
  MIT library stays MIT, and this repo's GPL terms never reach back into it.

That single dependency transitively brings the whole runtime — the gRPC transport
(grpc-netty), the P4Runtime protobufs (scalapb), and typegen — so `build.sbt` has
exactly one `libraryDependencies` line.

## Layout

```
p4/forwarding.p4                          vendored GPLv3 P4 source
src/main/resources/forwarding.p4info.json  generated — P4Runtime schema (committed)
src/main/resources/forwarding.json         generated — bmv2 device config (committed)
src/main/scala/generated/forwarding.scala  generated — typed Scala (committed)
src/main/scala/demo/Demo.scala             the demo: connect, push, insert, read
src/main/scala/codegen/GenTypes.scala      calls typegen.generate (used by gen.sh)
gen.sh                                     .p4 -> p4info/json -> Scala  (needs p4c)
run.sh                                     start bmv2, run the demo, tear down
```

The three generated files are committed, so `./run.sh` needs **no P4 toolchain**.

## Run it

Requires Docker (for the bmv2 switch) and sbt.

```bash
./run.sh
```

That starts `simple_switch_grpc --no-p4` in a container, then runs the demo, which:

1. connects to the switch,
2. installs `forwarding.p4`'s pipeline via `setForwardingPipelineConfig`,
3. inserts `10.0.1.0/24 -> ipv4_forward(08:00:00:00:01:11, port 1)` into
   `MyIngress.ipv4_lpm`,
4. reads the table back and checks the round trip.

Expected tail:

```
inserted: 10.0.1.0/24 -> ipv4_forward(08:00:00:00:01:11, port 1)
read back 1 entry/entries from the switch:
  table=MyIngress.ipv4_lpm action=MyIngress.ipv4_forward match=...
round trip OK
```

To point at a switch you started yourself, skip `run.sh` and set the target:

```bash
P4RT_BMV2=localhost:9559 sbt run
```

## The type safety, concretely

`Demo.scala` builds the entry against the generated `ipforwarding` types:

```scala
TableEntry[TableMatchFields, TableAction, ActionParams](
  "MyIngress.ipv4_lpm",                                   // must be a real table
  Some(("hdr.ipv4.dstAddr", LPM(bytes(10, 0, 1, 0), 24))),// LPM key, as the table declares
  "MyIngress.ipv4_forward",                               // an action this table allows
  (("dstAddr", bytes(...)), ("port", bytes(1))),          // exactly this action's params
  0
)
```

Change `"MyIngress.ipv4_lpm"` to a table that doesn't exist, pair it with an
action it doesn't list, or give `ipv4_forward` the wrong parameters, and it's a
**compile error** — not a runtime rejection from the switch.

## Regenerating (only when the .p4 changes)

```bash
./gen.sh     # p4c 1.2.5.15 in Docker, then P4R-Type's typegen; refreshes all three generated files
```

## Adding another example

1. Drop the (GPLv3) `.p4` under `p4/`, with an attribution header (see
   `forwarding.p4`) and an entry in `NOTICE`.
2. Point `gen.sh` and `GenTypes.scala` at it (or generalize them over a list).
3. Write a `demo/` main against the newly generated package.
