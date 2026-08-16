package demo

import p4rtype.{LPM, TableEntry, bytes, insert, read, setForwardingPipelineConfig, getForwardingPipelineConfig}
import ipforwarding.{TableMatchFields, TableAction, ActionParams}
import com.google.protobuf.ByteString
import p4.config.v1.p4info.P4Info

/** End-to-end demo against a real bmv2, driven entirely through P4R-Type 0.1.0
  * from Maven Central and the types typegen produced for `forwarding.p4`:
  *
  *   1. connect to the switch,
  *   2. push forwarding.p4's pipeline (the p4info + the compiled bmv2 config),
  *   3. insert one LPM entry into `ipv4_lpm`,
  *   4. read it back and check the round trip.
  *
  * Every table/action/param name below is checked at compile time against the
  * generated `ipforwarding` types: mistype "MyIngress.ipv4_lpm" or pair the wrong
  * action with the table and this file will not compile. That type safety is the
  * whole point P4R-Type exists to demonstrate — here shown on a third-party P4
  * program it never saw.
  *
  * Needs a bmv2 listening on P4RT_BMV2 (host:port). The switch is started with no
  * pipeline (`simple_switch_grpc --no-p4`); step 2 installs one. Run it with:
  *
  *   ./run.sh          # starts bmv2 in Docker, sets P4RT_BMV2, runs this
  */
@main def forwarding(): Unit =
  val target = Option(System.getenv("P4RT_BMV2")).getOrElse {
    Console.err.println(
      "set P4RT_BMV2=host:port to point at a running bmv2 " +
      "(or use ./run.sh, which starts one). Nothing to do without a switch."
    )
    sys.exit(1)
  }

  val Array(host, port) = target.split(":"): @unchecked
  val chan = ipforwarding.connect(0, host, port.toInt)

  try
    // --- 2. push the pipeline ------------------------------------------------
    val p4infoText = scala.io.Source.fromResource("forwarding.p4info.json").mkString
    val p4info =
      val b = com.google.protobuf.DynamicMessage.newBuilder(P4Info.javaDescriptor)
      com.google.protobuf.util.JsonFormat.parser().merge(p4infoText, b)
      P4Info.parseFrom(b.build().toByteArray)

    val deviceConfig = ByteString.copyFrom(
      scala.util.Using.resource(getClass.getResourceAsStream("/forwarding.json"))(_.readAllBytes())
    )

    setForwardingPipelineConfig(chan, p4info, deviceConfig) match
      case Left(err) => sys.error(s"could not install the pipeline: $err")
      case Right(_)  => println("pipeline installed: forwarding.p4")

    getForwardingPipelineConfig(chan) match
      case Left(err)  => sys.error(s"could not read the pipeline back: $err")
      case Right(cfg) =>
        val tables = cfg.p4Info.toSeq.flatMap(_.tables).flatMap(_.preamble).map(_.name)
        println(s"switch reports tables: ${tables.mkString(", ")}")

    // --- 3. insert one LPM entry: 10.0.1.0/24 -> ipv4_forward(mac, port 1) ----
    val entry = TableEntry[TableMatchFields, TableAction, ActionParams](
      "MyIngress.ipv4_lpm",
      // LPM key is Option[(fieldName, LPM)] in the generated type. 10.0.1.0/24.
      Some(("hdr.ipv4.dstAddr", LPM(bytes(10, 0, 1, 0), 24))),
      "MyIngress.ipv4_forward",
      // dstAddr: bit<48> MAC 08:00:00:00:01:11, port: bit<9> = 1
      (("dstAddr", bytes(0x08, 0, 0, 0, 0x01, 0x11)), ("port", bytes(1))),
      0 // LPM tables are ordered by prefix length, not priority
    )

    if !insert(chan, entry) then sys.error("insert returned false")
    println("inserted: 10.0.1.0/24 -> ipv4_forward(08:00:00:00:01:11, port 1)")

    // --- 4. read it back -----------------------------------------------------
    val found = read(chan, TableEntry[TableMatchFields, TableAction, ActionParams](
      "*", "*", "*", "*", 0
    ))
    println(s"read back ${found.size} entry/entries from the switch:")
    found.foreach { e =>
      println(s"  table=${e.table} action=${e.action} match=${e.matches}")
    }

    assert(found.size == 1, s"expected exactly the entry we inserted, got ${found.size}")
    println("round trip OK")
  finally
    chan.disconnect()
