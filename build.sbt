// A standalone *consumer* of P4R-Type (io.github.oluies::p4rt-scala), kept in its
// own repository on purpose. See README.md for why: the P4 sources under p4/ are
// GPLv3 (vendored from nsg-ethz/p4-learning), so everything derived from them —
// the p4info, the bmv2 config, and the typegen-generated Scala — is GPLv3 too.
// P4R-Type itself is MIT and is consumed here only as a published artifact, the
// way any GPL application may use an MIT library. Keeping this out of the P4R-Type
// tree is what stops the copyleft from reaching back into that MIT project.

// TASTy is not forward-compatible, so the compiler here cannot be older than the
// one p4rt-scala was built with. That is read off its POM rather than assumed:
//
//   p4rt-scala 0.1.0  ->  scala3-library_3 3.8.4
//   p4rt-scala 0.2.0  ->  scala3-library_3 3.9.0
//   p4rt-scala 0.2.1  ->  scala3-library_3 3.9.0
//
// So this is a floor, not a preference, and it moved when 0.2.0 did. Match it
// exactly, and re-read the POM on the next bump instead of trusting this list.
//
// Renovate and Steward cannot maintain this pairing. A bump to the dependency
// alone leaves the compiler behind and the build fails on TASTy; a bump to
// scala3-library alone edits the numbers in this comment, which is all PR #4
// did -- it rewrote the prose and left `scalaVersion` untouched.
ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization  := "io.github.oluies.examples"
ThisBuild / version       := "0.1.0-SNAPSHOT"

// The one dependency. Its POM pulls scalapb-runtime(+grpc), protobuf-java-util and
// grpc-netty transitively at compile scope, so the whole runtime — the gRPC
// transport, the P4Runtime protobufs, and typegen — arrives with this single line.
val p4rtScala = "io.github.oluies" %% "p4rt-scala" % "0.2.1"

lazy val root = (project in file("."))
  .settings(
    name := "p4rt-examples",
    libraryDependencies += p4rtScala,
    // The generated forwarding.scala uses match types on singleton string
    // literals; nothing here needs experimental flags, but keep warnings honest.
    scalacOptions ++= Seq("-deprecation", "-feature"),
    // `sbt run` picks the demo automatically rather than prompting between it and
    // the codegen main.
    Compile / run / mainClass := Some("demo.forwarding"),
  )
