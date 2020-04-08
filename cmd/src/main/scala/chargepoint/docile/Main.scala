package chargepoint.docile

import java.net.URI

import chargepoint.docile.dsl._
import chargepoint.docile.test._
import ResultSummary.summarizeResults
import ch.qos.logback.classic.{Level, Logger}
import com.thenewmotion.ocpp.{Version, VersionFamily}
import com.typesafe.scalalogging.{StrictLogging}
import javax.net.ssl.SSLContext
import org.rogach.scallop._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object Main extends App with StrictLogging {

  object conf extends ScallopConf(args) {
    implicit val versionConverter =
      singleArgConverter(
        Version.withName(_).get, {
          case _: NoSuchElementException => Left("Invalid OCPP version provided")
        }
      )

    val version = opt[Version](
      default = Some(Version.V16),
      descr = "OCPP version"
    )

    val authKey = opt[String](
      descr = "Authorization key to use for Basic Auth (hex-encoded, 40 characters)"
    )

    val keystoreFile = opt[String](
      default = None,
      descr = "Keystore file for ssl (e.g: ./keystore.jks)"
    )

    val keystorePassword = opt[String](
      default = None,
      short = 'p',
      descr = "Keystore password to unlock the keystore file"
    )

    val chargePointId = opt[String](
      default = Some("03000001"),
      descr = "ChargePointIdentity to identify ourselves to the Central System"
    )

    val forever = toggle(
      default = Some(false),
      descrYes = "Keep executing script until terminated"
    )

    val interactive = toggle(
      default = Some(false),
      descrYes = "Start REPL to enter and run a test interactively"
    )

    val numberInParallel = opt[Int](
      default = Some(1),
      descr = "Start given number of instances of the script at the same time (can be combined with --repeat)"
    )

    val repeat = opt[Int](
      default = Some(1),
      descr = "Repeat execution of the scripts this number of times"
    )

    val repeatPause = opt[Int](
      default = Some(1000),
      descr = "Number of milliseconds to wait between repeat runs"
    )

    val timeout = opt[Int](
      default = Some(45000),
      descr = "Number of milliseconds to wait for expected incoming OCPP messages (-1 for no timeout)"
    )

    val untilSuccess = toggle(
      default = Some(false),
      descrYes = "Keep executing scripts until they all succeed"
    )

    val verbose = opt[Int](
      default = Some(3),
      descr = "Verbosity (0-5)"
    )

    val uri = trailArg[URI](
      descr = "URI of the Central System"
    )

    val files = trailArg[List[String]](
      required = false,
      descr = "files with test cases to load"
    )

    def makesSense: Either[String, Unit] = {
      val repeatModesSpecified =
        List(
          conf.forever(),
          conf.repeat.toOption.exists(_ > 1),
          conf.untilSuccess()
        ).count(identity)

      val senseChecks = List(
        "Tssk, grapjas" ->
          conf.numberInParallel.toOption.exists(_ < 1),
        "You can't combine -i and -n, sorry" ->
          (conf.interactive() && conf.numberInParallel() > 1),
        "You can only specify one of --forever, --repeat and --until-success" ->
          (repeatModesSpecified > 1),
        "You have to give files on the command-line for a non-interactive run" ->
          (!conf.interactive() && !conf.files.toOption.exists(_.nonEmpty))
      )

      senseChecks.find(_._2).map(_._1).toLeft(())
    }

    verify()
  }

  val rootLogger = LoggerFactory.getLogger("ROOT").asInstanceOf[Logger]

  val rootLogLevel = conf.verbose() match {
    case 0 => Level.OFF
    case 1 => Level.ERROR
    case 2 => Level.WARN
    case 3 => Level.INFO
    case 4 => Level.DEBUG
    case 5 => Level.TRACE
    case _ => sys.error("Invalid verbosity, should be 0, 1, 2, 3, 4 or 5")
  }

  rootLogger.setLevel(rootLogLevel)

  implicit val ec: ExecutionContext = concurrent.ExecutionContext.Implicits.global

  conf.makesSense.left.foreach { errMsg =>
    logger.error(errMsg)
    sys.exit(1)
  }

  val version = conf.version()

  val repeatMode =
    if (conf.repeat() > 1)
      Repeat(conf.repeat(), conf.repeatPause())
    else if (conf.untilSuccess())
      UntilSuccess(conf.repeatPause())
    else if (conf.forever())
      Indefinitely(conf.repeatPause())
    else RunOnce

  val runnerCfg = RunnerConfig(
    number = conf.numberInParallel(),
    chargePointId = conf.chargePointId(),
    uri = conf.uri(),
    ocppVersion = conf.version(),
    authKey = conf.authKey.toOption,
    sslContext = {
      conf.keystoreFile.toOption.fold(SSLContext.getDefault) { file =>
        SslContext(file, conf.keystorePassword.toOption.getOrElse(""))
      }
    },
    repeat = repeatMode,
    defaultAwaitTimeout =
      if (conf.timeout() < 0)
        InfiniteAwaitTimeout
      else
        AwaitTimeoutInMillis(conf.timeout())
  )

  val runner: Runner[_] =
    if (conf.interactive())
      interactiveRunner(conf.version().family)
    else
      Runner.forFiles(conf.version().family, conf.files())

  Try(runner.run(runnerCfg)) match {
    case Success(testsPassed) =>
      val succeeded = summarizeResults(testsPassed, {
        case s: String => logger.info(s)
        case x         => logger.info("%s".format(x))
      })
      sys.exit(if (succeeded) 0 else 1)
    case Failure(e) =>
      System.err.println(s"Could not run tests: ${e.getMessage}")
      e.printStackTrace()
      sys.exit(2)
  }

   def interactiveRunner(vfam: VersionFamily): Runner[vfam.type] = new Runner[vfam.type](
     Seq(TestCase("Interactive test", () => InteractiveOcppTest(vfam)))
   )
}
