package summingbird

import sbt._
import Keys._
import sbtassembly.Plugin._
import AssemblyKeys._
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform._

object SummingbirdBuild extends Build {
  def withCross(dep: ModuleID) =
    dep cross CrossVersion.binaryMapped {
      case "2.9.3" => "2.9.2" // TODO: hack because twitter hasn't built things against 2.9.3
      case version if version startsWith "2.10" => "2.10" // TODO: hack because sbt is broken
      case x => x
    }

  def specs2Import(scalaVersion: String) = scalaVersion match {
      case version if version startsWith "2.9" => "org.specs2" %% "specs2" % "1.12.4.1" % "test"
      case version if version startsWith "2.10" => "org.specs2" %% "specs2" % "1.13" % "test"
  }

  def isScala210x(scalaVersion: String) = scalaVersion match {
      case version if version startsWith "2.9" => false
      case version if version startsWith "2.10" => true
  }

  def real210Version(dep: ModuleID) = dep cross CrossVersion.binaryMapped {
    case version if version startsWith "2.10" => "2.10.3"
    case x => x
  }

  val extraSettings = Project.defaultSettings ++ mimaDefaultSettings ++ scalariformSettings

  val sharedSettings = extraSettings ++ Seq(
    organization := "com.twitter",
    version := "0.5.1",
    scalaVersion := "2.9.3",
    crossScalaVersions := Seq("2.9.3", "2.10.4"),
    // To support hadoop 1.x
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),

    javacOptions in doc ~= { (options: Seq[String]) =>
      val targetPos = options.indexOf("-target")
      if(targetPos > -1) {
        options.take(targetPos) ++ options.drop(targetPos + 2)
      } else options
    },

    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.11" % "test",
      "org.slf4j" % "slf4j-api" % slf4jVersion force(),
      "org.scalacheck" %% "scalacheck" % "1.10.0" % "test",
      // These satisify's scaldings log4j needs when in test mode
      "log4j" % "log4j" % "1.2.16" % "test",
      "org.slf4j" % "slf4j-log4j12" % slf4jVersion % "test"
    ),

    libraryDependencies <+= scalaVersion(specs2Import(_)),

    libraryDependencies += "com.novocode" % "junit-interface" % "0.10" % "test",

    resolvers ++= Seq(
      Opts.resolver.sonatypeSnapshots,
      Opts.resolver.sonatypeReleases,
      "Clojars Repository" at "http://clojars.org/repo",
      "Conjars Repository" at "http://conjars.org/repo",
      "Twitter Maven" at "http://maven.twttr.com"
    ),

    parallelExecution in Test := true,

    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-Yresolve-term-conflict:package"
    ),

    // Publishing options:
    publishMavenStyle := true,

    publishArtifact in Test := false,

    pomIncludeRepository := { x => false },

    publishTo <<= version { v =>
      Some(
        if (v.trim.toUpperCase.endsWith("SNAPSHOT"))
          Opts.resolver.sonatypeSnapshots
        else
          Opts.resolver.sonatypeStaging
          //"twttr" at "http://artifactory.local.twitter.com/libs-releases-local"
      )
    },

    pomExtra := (
      <url>https://github.com/twitter/summingbird</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
          <comments>A business-friendly OSS license</comments>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:twitter/summingbird.git</url>
        <connection>scm:git:git@github.com:twitter/summingbird.git</connection>
      </scm>
      <developers>
        <developer>
          <id>oscar</id>
          <name>Oscar Boykin</name>
          <url>http://twitter.com/posco</url>
        </developer>
        <developer>
          <id>sritchie</id>
          <name>Sam Ritchie</name>
          <url>http://twitter.com/sritchie</url>
        </developer>
        <developer>
          <id>asinghal</id>
          <name>Ashutosh Singhal</name>
          <url>http://twitter.com/daashu</url>
        </developer>
      </developers>)
  )

  lazy val formattingPreferences = {
   import scalariform.formatter.preferences._
   FormattingPreferences().
     setPreference(AlignParameters, false).
     setPreference(PreserveSpaceBeforeArguments, true)
  }

  lazy val summingbird = Project(
    id = "summingbird",
    base = file("."),
    settings = sharedSettings ++ DocGen.publishSettings
  ).settings(
    test := { },
    publish := { }, // skip publishing for this root project.
    publishLocal := { }
  ).aggregate(
    summingbirdCore,
    summingbirdCoreJava,
    summingbirdBatch,
    summingbirdBatchHadoop,
    summingbirdOnline,
    summingbirdClient,
    summingbirdStorm,
    summingbirdStormTest,
    summingbirdStormJava,
    summingbirdScalding,
    summingbirdScaldingTest,
    summingbirdSpark,
    summingbirdBuilder,
    summingbirdChill,
    summingbirdExample
  )

  val dfsDatastoresVersion = "1.3.4"
  val bijectionVersion = "0.6.3"
  val algebirdVersion = "0.7.0"
  val scaldingVersion = "0.11.3rc1"
  val storehausVersion = "0.9.1"
  val utilVersion = "6.3.8"
  val chillVersion = "0.4.0"
  val tormentaVersion = "0.7.0"

  lazy val slf4jVersion = "1.6.6"

  /**
    * This returns the youngest jar we released that is compatible with
    * the current.
    */
  val unreleasedModules = Set[String]()

  def youngestForwardCompatible(subProj: String) =
    Some(subProj)
      .filterNot(unreleasedModules.contains(_))
      .map { s => "com.twitter" % ("summingbird-" + s + "_2.9.3") % "0.5.0" }

  def module(name: String) = {
    val id = "summingbird-%s".format(name)
    Project(id = id, base = file(id), settings = sharedSettings ++ Seq(
      Keys.name := id,
      previousArtifact := youngestForwardCompatible(name))
    )
  }

  lazy val summingbirdBatch = module("batch").settings(
    libraryDependencies ++= Seq(
      "com.twitter" %% "algebird-core" % algebirdVersion,
      "com.twitter" %% "bijection-core" % bijectionVersion,
      "com.twitter" %% "scalding-date" % scaldingVersion
    )
  )

  lazy val summingbirdChill = module("chill").settings(
    libraryDependencies ++= Seq(
      "com.twitter" %% "chill" % chillVersion,
      "com.twitter" %% "chill-bijection" % chillVersion
    )
  ).dependsOn(
      summingbirdCore,
      summingbirdBatch
  )

  lazy val summingbirdClient = module("client").settings(
    libraryDependencies ++= Seq(
      "com.twitter" %% "algebird-core" % algebirdVersion,
      "com.twitter" %% "algebird-util" % algebirdVersion,
      "com.twitter" %% "bijection-core" % bijectionVersion,
      "com.twitter" %% "storehaus-core" % storehausVersion,
      "com.twitter" %% "storehaus-algebra" % storehausVersion
    )
  ).dependsOn(summingbirdBatch)

  lazy val summingbirdCore = module("core").settings(
    libraryDependencies += "com.twitter" %% "algebird-core" % algebirdVersion
  )

  lazy val summingbirdCoreJava = module("core-java").dependsOn(
    summingbirdCore % "test->test;compile->compile"
  )

  lazy val summingbirdOnline = module("online").settings(
    libraryDependencies ++= Seq(
      "com.twitter" %% "algebird-core" % algebirdVersion,
      "com.twitter" %% "algebird-util" % algebirdVersion,
      "com.twitter" %% "bijection-core" % bijectionVersion,
      "com.twitter" %% "storehaus-core" % storehausVersion,
      "com.twitter" %% "chill" % chillVersion,
      "com.twitter" %% "storehaus-algebra" % storehausVersion,
      withCross("com.twitter" %% "util-core" % utilVersion)
    )
  ).dependsOn(
    summingbirdCore % "test->test;compile->compile",
    summingbirdBatch
  )

  lazy val summingbirdStorm = module("storm").settings(
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "com.twitter" %% "algebird-core" % algebirdVersion,
      "com.twitter" %% "bijection-core" % bijectionVersion,
      "com.twitter" %% "chill" % chillVersion,
      "com.twitter" % "chill-storm" % chillVersion,
      "com.twitter" %% "chill-bijection" % chillVersion,
      "com.twitter" %% "storehaus-core" % storehausVersion,
      "com.twitter" %% "storehaus-algebra" % storehausVersion,
      "com.twitter" %% "scalding-args" % scaldingVersion,
      "com.twitter" %% "tormenta-core" % tormentaVersion,
      withCross("com.twitter" %% "util-core" % utilVersion),
      "storm" % "storm" % "0.9.0-wip15" % "provided"
    )
  ).dependsOn(
    summingbirdCore % "test->test;compile->compile",
    summingbirdOnline,
    summingbirdChill,
    summingbirdBatch
  )

  lazy val summingbirdStormTest = module("storm-test").settings(
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "com.twitter" %% "algebird-core" % algebirdVersion,
      "com.twitter" %% "bijection-core" % bijectionVersion,
      "com.twitter" %% "storehaus-core" % storehausVersion,
      "com.twitter" %% "storehaus-algebra" % storehausVersion,
      "com.twitter" %% "tormenta-core" % tormentaVersion,
      withCross("com.twitter" %% "util-core" % utilVersion),
      "storm" % "storm" % "0.9.0-wip15" % "provided"
    )
  ).dependsOn(
    summingbirdCore % "test->test;compile->compile",
    summingbirdStorm
  )

  lazy val summingbirdStormJava = module("storm-java").settings(
    libraryDependencies ++= Seq(
      "storm" % "storm" % "0.9.0-wip15" % "provided"
    )
  ).dependsOn(
    summingbirdCore % "test->test;compile->compile",
    summingbirdCoreJava % "test->test;compile->compile",
    summingbirdStorm % "test->test;compile->compile"
  )

  val scaldingDeps = Seq(
    "com.backtype" % "dfs-datastores" % dfsDatastoresVersion,
    "com.backtype" % "dfs-datastores-cascading" % dfsDatastoresVersion,
    "com.twitter" %% "algebird-core" % algebirdVersion,
    "com.twitter" %% "algebird-util" % algebirdVersion,
    "com.twitter" %% "algebird-bijection" % algebirdVersion,
    "com.twitter" %% "bijection-json" % bijectionVersion,
    "com.twitter" %% "chill" % chillVersion,
    "com.twitter" % "chill-hadoop" % chillVersion,
    "com.twitter" %% "chill-bijection" % chillVersion,
    "commons-lang" % "commons-lang" % "2.6",
    "com.twitter" %% "scalding-core" % scaldingVersion exclude("org.scalacheck", "scalacheck_2.10"),
    "com.twitter" %% "scalding-commons" % scaldingVersion exclude("ch.qos.logback", "logback-core")  exclude("ch.qos.logback", "logback-classic")
  )

  def scaldingDeps210Extras = Seq(
    "com.twitter" %% "storehaus-cascading" % storehausVersion,
    "com.twitter" %% "storehaus-cassandra" % storehausVersion exclude("ch.qos.logback", "logback-core")  exclude("ch.qos.logback", "logback-classic"),
    real210Version("com.chuusai" %% "shapeless" % "2.0.0")
  )

  def buildScaldingDeps(scalaVersion: String) = if (!isScala210x(scalaVersion)) { scaldingDeps } else { scaldingDeps ++ scaldingDeps210Extras }

  def buildScaldingExcludeFilter(scalaVersion: String) = if (isScala210x(scalaVersion)) {
    HiddenFileFilter
  } else {
    HiddenFileFilter || "storehaus" || "Storehaus*.scala"
  }

  lazy val summingbirdScalding = module("scalding").settings(
    libraryDependencies ++= buildScaldingDeps(scalaVersion.value),
    excludeFilter in unmanagedSources := buildScaldingExcludeFilter(scalaVersion.value)
  ).dependsOn(
    summingbirdCore % "test->test;compile->compile",
    summingbirdChill,
    summingbirdBatchHadoop,
    summingbirdBatch
  )

  lazy val summingbirdScaldingTest = module("scalding-test").settings(
    dependencyOverrides += "org.scalacheck" %% "scalacheck" % "1.10.0",
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % "1.10.0"
    )
  ).dependsOn(
    summingbirdCore % "test->test;compile->compile",
    summingbirdChill,
    summingbirdBatchHadoop,
    summingbirdScalding 
  )

  lazy val summingbirdBatchHadoop = module("batch-hadoop").settings(
    libraryDependencies ++= Seq(
      "com.backtype" % "dfs-datastores" % dfsDatastoresVersion,
      "com.twitter" %% "algebird-core" % algebirdVersion,
      "com.twitter" %% "bijection-json" % bijectionVersion,
      "com.twitter" %% "scalding-date" % scaldingVersion
    )
  ).dependsOn(
    summingbirdCore % "test->test;compile->compile",
    summingbirdBatch
  )

  lazy val summingbirdBuilder = module("builder").settings(
    libraryDependencies ++= Seq(
      "storm" % "storm" % "0.9.0-wip15" % "provided"
    )
  ).dependsOn(
    summingbirdCore,
    summingbirdStorm,
    summingbirdScalding
  )

  lazy val summingbirdExample = module("example").settings(
    libraryDependencies ++= Seq(
      "log4j" % "log4j" % "1.2.16",
      "org.slf4j" % "slf4j-log4j12" % slf4jVersion,
      "storm" % "storm" % "0.9.0-wip15" exclude("org.slf4j", "log4j-over-slf4j") exclude("ch.qos.logback", "logback-classic"),
      "com.twitter" %% "bijection-netty" % bijectionVersion,
      "com.twitter" %% "tormenta-twitter" % tormentaVersion,
      "com.twitter" %% "storehaus-memcache" % storehausVersion
    )
  ).dependsOn(summingbirdCore, summingbirdCoreJava, summingbirdStorm, summingbirdStormJava)

  lazy val sparkAssemblyMergeSettings = assemblySettings :+ {
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
      {
        //case PathList("org", "w3c", xs @ _*) => MergeStrategy.first
        //case "about.html"     => MergeStrategy.discard
        case PathList("com", "esotericsoftware", "minlog", xs @ _*) => MergeStrategy.first
        case PathList("org", "apache", "commons", "beanutils", xs @ _*) => MergeStrategy.first
        case PathList("org", "apache", "commons", "collections", xs @ _*) => MergeStrategy.first
        case PathList("org", "apache", "jasper", xs @ _*) => MergeStrategy.first
        case "log4j.properties"     => MergeStrategy.concat
        case x if x.endsWith(".xsd") || x.endsWith(".dtd") => MergeStrategy.first
        case x => old(x)
      }
    }
  }

  val sparkDeps = Seq(
    "com.twitter" %% "algebird-core" % algebirdVersion,
    "com.twitter" %% "algebird-util" % algebirdVersion,
    "com.twitter" %% "algebird-bijection" % algebirdVersion,
    "com.twitter" %% "bijection-json" % bijectionVersion,
    "com.twitter" %% "chill" % chillVersion,
    "com.twitter" % "chill-hadoop" % chillVersion,
    "com.twitter" %% "chill-bijection" % chillVersion,
    "commons-lang" % "commons-lang" % "2.6",
    "commons-httpclient" % "commons-httpclient" % "3.1",
    "org.apache.spark" %% "spark-core" % "0.9.0-incubating" % "provided"
  )

  def buildSparkDeps(scalaVersion: String) = if (isScala210x(scalaVersion)) sparkDeps else Seq()

  lazy val summingbirdSpark = module("spark").settings(
    resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    skip in compile := !isScala210x(scalaVersion.value),
    skip in doc := !isScala210x(scalaVersion.value),
    skip in test := !isScala210x(scalaVersion.value),
    publishArtifact := isScala210x(scalaVersion.value),
    libraryDependencies ++= buildSparkDeps(scalaVersion.value)
  )
  .settings(sparkAssemblyMergeSettings:_*)
  .dependsOn(
    summingbirdCore % "test->test;compile->compile",
    summingbirdChill
  )
}
