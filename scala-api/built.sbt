name := "Scala-Api"

version := "0.0.0"

scalaVersion := "2.10.4"

libraryDependencies ++= {
        val akkaV = "2.3.9"
        val sprayV = "1.3.2"
        Seq(
        "io.spray" %% "spray-can" % sprayV,
        "io.spray" %% "spray-routing" % sprayV,
        "com.typesafe.akka" %% "akka-actor" % akkaV
        )
}
