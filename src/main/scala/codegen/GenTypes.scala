package codegen

import java.nio.file.{Files, Path}

/** Regenerates the committed Scala types from the committed p4info, by calling
  * P4R-Type's `typegen.generate` — the same library entry point the P4R-Type repo
  * drives from its own `p4rt.sh gen-types`. This is the second half of `gen.sh`:
  * p4c produces the p4info (first half), typegen turns it into typed Scala here.
  *
  * The output is committed (src/main/scala/generated/forwarding.scala) so a plain
  * `sbt run` needs neither p4c nor this step — you only run `gen.sh` when the .p4
  * changes. That mirrors how P4R-Type commits its own generated fixtures.
  *
  *   sbt "runMain codegen.genTypes"
  */
@main def genTypes(): Unit =
  // Resolved against the working directory (sbt runs mains from the project root).
  val root       = Path.of(System.getProperty("user.dir"))
  val p4infoPath = root.resolve("src/main/resources/forwarding.p4info.json")
  val outPath    = root.resolve("src/main/scala/generated/forwarding.scala")

  // The package name becomes the generated package: `ipforwarding.connect`, etc.
  val packageName = "ipforwarding"

  val p4infoJson =
    try scala.util.Using.resource(scala.io.Source.fromFile(p4infoPath.toFile))(_.mkString)
    catch case e: Throwable =>
      Console.err.println(s"could not read $p4infoPath: ${e.getMessage}")
      sys.exit(1)

  typegen.generate(p4infoJson, packageName) match
    case Left(err) =>
      Console.err.println(s"typegen failed: $err")
      sys.exit(1)
    case Right(source) =>
      Files.writeString(outPath, source)
      Console.err.println(s"wrote $outPath")
