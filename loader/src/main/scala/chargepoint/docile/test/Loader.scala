package chargepoint.docile.test

import java.io.File
import java.nio.charset.StandardCharsets

import scala.tools.reflect.ToolBox

import chargepoint.docile.dsl.OcppTest
import com.thenewmotion.ocpp.VersionFamily
import com.thenewmotion.ocpp.VersionFamily.{V1X, V20}
import org.slf4j.LoggerFactory

object Loader {

  private val logger = LoggerFactory.getLogger("loader")

  def runnerFor(vfam: VersionFamily, filenames: Seq[String]): Runner[vfam.type] =
    new Runner(filenames.map(loadFile(vfam, _)))

  private def loadFile(vfam: VersionFamily, f: String): TestCase[vfam.type] = {
    val file = new File(f)
    val testNameRegex = "(?:.*/)?([^/]+?)(?:\\.[^.]*)?$".r
    val testName = f match {
      case testNameRegex(n) => n
      case _ => f
    }
    val fileSource = scala.io.Source.fromFile(file)
    var fileContents = ""
    try {
      fileContents = fileSource.getLines.mkString("\n")
    } finally {
      fileSource.close()
    }

    runnerFor(vfam, testName, fileContents)
  }

  def runnerFor(vfam: VersionFamily, name:String, fileContent: Array[Byte]): Runner[vfam.type] =
    new Runner(Seq(runnerFor(vfam, name, new String(fileContent, StandardCharsets.UTF_8))))


  def runnerFor(vfam: VersionFamily, name:String, fileContent: String): TestCase[vfam.type] = {
    import reflect.runtime.currentMirror
    val toolbox = currentMirror.mkToolBox()

    val appendix = ";\n  }\n}"

    logger.info(s"Parsing and compiling script '$name'")

    val preamble = preambleForVersionFamily(vfam)

    val fileAst = toolbox.parse(preamble + fileContent + appendix)

    logger.info(s"Parsed '$name'")

    val compiledCode = toolbox.compile(fileAst)

    logger.info(s"Compiled '$name'")

    TestCase(name, () => compiledCode().asInstanceOf[OcppTest[vfam.type]])
  }

  private def preambleForVersionFamily(vfam: VersionFamily): String = {
    val (messagesPackage, instantiatedType, csmsMessagesWitness, csMessagesWitness) = vfam match {
      case V1X => (
        "v1x",
        "chargepoint.docile.dsl.Ocpp1XTest with chargepoint.docile.dsl.Ocpp1XTest.V1XOps",
        "com.thenewmotion.ocpp.VersionFamily.V1XCentralSystemMessages",
        "com.thenewmotion.ocpp.VersionFamily.V1XChargePointMessages"
      )
      case V20 => (
        "v20",
        "chargepoint.docile.dsl.Ocpp20Test with chargepoint.docile.dsl.Ocpp20Test.V20Ops",
        "com.thenewmotion.ocpp.VersionFamily.V20CsmsMessages",
        "com.thenewmotion.ocpp.VersionFamily.V20CsMessages"
      )
    }

    s"""
       |import com.thenewmotion.ocpp.messages.$messagesPackage._
       |
       |import scala.language.postfixOps
       |import scala.concurrent.duration._
       |import scala.concurrent.ExecutionContext
       |import scala.util.Random
       |import java.time._
       |import com.typesafe.scalalogging.Logger
       |import org.slf4j.LoggerFactory
       |
       |import chargepoint.docile.dsl.AwaitTimeout
       |import chargepoint.docile.dsl.Randomized._
       |
       |new $instantiatedType {
       |
       |  implicit val executionContext: ExecutionContext = ExecutionContext.global
       |  implicit val csmsMessageTypes = $csmsMessagesWitness
       |  implicit val csMessageTypes = $csMessagesWitness
       |
       |  private implicit val rand: Random = new Random()
       |
       |  def run(defaultAwaitTimeout: AwaitTimeout) {
       |    implicit val awaitTimeout: AwaitTimeout = defaultAwaitTimeout;
       |
   """.stripMargin
  }
}
