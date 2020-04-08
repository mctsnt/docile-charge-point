package chargepoint.docile.test

import chargepoint.docile.dsl.{ExecutionError, ExpectationFailed}

object ResultSummary {

  def summarizeResults(testResults: Map[String, Seq[Map[String, TestResult]]], report:Any => Unit): Boolean = {

    // we do result formatting differently depending on whether we're doing a
    // single run (one charge point, one pass through the test script), or if
    // we're doing a complex one (multiple charge point and/or multiple repeats)
    val isSingleRun = testResults.size == 1 && testResults.toSeq.headOption.exists(_._2.size == 1)
    if (isSingleRun) {
      val singleRunResult =
        testResults
          .headOption
          .flatMap(_._2.headOption)
          .getOrElse(Map.empty[String, TestResult])

      summarizeSingleRun(singleRunResult, report)
    } else {
      summarizeComplexRun(testResults, report)
    }
  }

  private def summarizeSingleRun(testResults: Map[String, TestResult], report: Any => Unit): Boolean = {
    val outcomes = testResults map  { case (testName, outcome) =>

      val outcomeDescription = outcome match {
        case TestFailed(ExpectationFailed(msg)) =>
          s"❌  $msg"
        case TestFailed(ExecutionError(e)) =>
          s"💥  ${e.getClass.getSimpleName} ${e.getMessage}\n" +
            s"\t${e.getStackTrace.mkString("\n\t")}"
        case TestPassed =>
          s"✅"
      }

      report(s"$testName: $outcomeDescription")

      outcome
    }

    outcomes.collect({ case TestFailed(_) => }).isEmpty
  }

  private def summarizeComplexRun(testResults: Map[String, Seq[Map[String, TestResult]]], report: Any => Unit): Boolean = {
    val countsPerChargePoint: Map[String, (Int, Int, Int)] = testResults.mapValues { runs =>
      runs.foldLeft((0, 0, 0)) { case (counts, results) =>
        val countsForRun = results.values.foldLeft((0,0,0)) {
          case ((f, e, p), TestFailed(ExpectationFailed(_))) => (f+1, e  , p)
          case ((f, e, p), TestFailed(ExecutionError(_)))    => (f  , e+1, p)
          case ((f, e, p), TestPassed)                       => (f  , e  , p+1)
        }

        (counts._1 + countsForRun._1, counts._2 + countsForRun._2, counts._3 + countsForRun._3)
      }
    }

    countsPerChargePoint foreach { case (chargePointId, counts) =>
      report(s"$chargePointId: ${counts._1} failed / ${counts._2} errors / ${counts._3} passed")
    }

    !countsPerChargePoint.values.exists(c => c._1 != 0 || c._2 != 0)
  }

}
