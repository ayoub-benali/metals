package tests.sbt

import java.util.concurrent.TimeUnit

import scala.meta.internal.builds.SbtBuildTool
import scala.meta.internal.builds.SbtDigest
import scala.meta.internal.metals.Messages
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.ServerCommands
import scala.meta.internal.metals.{BuildInfo => V}
import scala.meta.io.AbsolutePath

import tests.BaseImportSuite
import tests.ScriptsAssertions

/**
 * Basic suite to ensure that a connection to sbt server can be made.
 */
class SbtServerSuite
    extends BaseImportSuite("sbt-server")
    with ScriptsAssertions {

  val preBspVersion = "1.3.13"
  val supportedBspVersion = V.sbtVersion
  val scalaVersion = V.scala212
  val buildTool: SbtBuildTool = SbtBuildTool(None, () => userConfig)

  override def currentDigest(
      workspace: AbsolutePath
  ): Option[String] = SbtDigest.current(workspace)

  test("too-old") {
    cleanWorkspace()
    for {
      _ <- server.initialize(
        s"""|/project/build.properties
            |sbt.version=$preBspVersion
            |/build.sbt
            |scalaVersion := "${V.scala212}"
            |""".stripMargin
      )
      _ = assertNoDiff(
        client.workspaceMessageRequests,
        List(
          importBuildMessage,
          progressMessage
        ).mkString("\n")
      )
      _ = client.messageRequests.clear()
      _ = assertStatus(_.isInstalled)
      // Attempt to create a .bsp/sbt.json file
      _ <- server.executeCommand(ServerCommands.GenerateBspConfig.id)
    } yield {
      assertNoDiff(
        client.workspaceShowMessages,
        Messages.NoBspSupport.toString
      )
    }
  }

  test("generate") {
    def sbtBspConfig = workspace.resolve(".bsp/sbt.json")
    def sbtBspPlugin = workspace.resolve("project/metals.sbt")
    cleanWorkspace()
    for {
      _ <- server.initialize(
        s"""|/project/build.properties
            |sbt.version=$supportedBspVersion
            |/build.sbt
            |scalaVersion := "${V.scala212}"
            |""".stripMargin
      )
      _ = assertNoDiff(
        client.workspaceMessageRequests,
        List(
          // Project has no .bloop directory so user is asked to "import via bloop"
          // since bloop is still the default
          importBuildMessage,
          progressMessage
        ).mkString("\n")
      )
      _ = client.messageRequests.clear() // restart
      _ = assertStatus(_.isInstalled)
      _ = assert(!sbtBspConfig.exists)
      // At this point, we want to use sbt server, so create the sbt.json file.
      _ <- server.executeCommand(ServerCommands.GenerateBspConfig.id)
    } yield {
      assert(sbtBspPlugin.exists)
      assert(sbtBspConfig.exists)
    }
  }

  test("reload") {
    cleanWorkspace()
    for {
      _ <- server.initialize(
        s"""|/project/build.properties
            |sbt.version=$supportedBspVersion
            |/build.sbt
            |scalaVersion := "${V.scala212}"
            |""".stripMargin
      )
      _ <- server.executeCommand(ServerCommands.GenerateBspConfig.id)
      // A bit obnoxious, but this taks a long time to connect in CI
      _ = Thread.sleep(TimeUnit.SECONDS.toMillis(20))
      _ <- server.didSave("build.sbt") { text =>
        s"""$text
           |ibraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.1.4"
           |""".stripMargin
      }
      _ = {
        val msgs = client.workspaceErrorShowMessages
        assertNoDiff(msgs, Messages.ReloadProjectFailed.getMessage())
        client.showMessages.clear()
      }
      _ <- server.didSave("build.sbt") { _ =>
        s"""scalaVersion := "${V.scala212}"
           |libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.1.4"
           |""".stripMargin
      }
      _ = {
        assert(client.workspaceErrorShowMessages.isEmpty())
      }
    } yield ()
  }
}
