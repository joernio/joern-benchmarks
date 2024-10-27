package io.joern.benchmarks

import java.io.File
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Failure, Success, Try}

package object runner {

  def runCmd(in: String, cwd: File): RunOutput = {
    val qb  = Process(in, cwd)
    var out = List[String]()
    var err = List[String]()

    val exit = qb ! ProcessLogger(s => out ::= s, s => err ::= s)

    RunOutput(exit, out.reverse, err.reverse)
  }

  case class RunOutput(exitCode: Int, stdOut: List[String], stdErr: List[String]) {
    def toTry: Try[List[String]] = {
      exitCode match {
        case 0 => Success(stdOut)
        case _ => Failure(new RuntimeException(stdErr.mkString("\n")))
      }
    }
  }

}
