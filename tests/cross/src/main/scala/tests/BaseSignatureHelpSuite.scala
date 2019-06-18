package tests

import scala.collection.JavaConverters._
import scala.meta.internal.metals.CompilerOffsetParams

abstract class BaseSignatureHelpSuite extends BasePCSuite {
  def checkDoc(
      name: String,
      code: String,
      expected: String,
      compat: Map[String, String] = Map.empty
  ): Unit = {
    check(name, code, expected, includeDocs = true, compat = compat)
  }
  def check(
      name: String,
      original: String,
      expected: String,
      includeDocs: Boolean = false,
      compat: Map[String, String] = Map.empty,
      stableOrder: Boolean = true
  ): Unit = {
    test(name) {
      val pkg = scala.meta.Term.Name(name).syntax
      val (code, offset) = params(s"package $pkg\n" + original)
      val result =
        pc.signatureHelp(CompilerOffsetParams("A.scala", code, offset)).get()
      val out = new StringBuilder()
      if (result != null) {
        result.getSignatures.asScala.zipWithIndex.foreach {
          case (signature, i) =>
            if (includeDocs) {
              val sdoc = doc(signature.getDocumentation)
              if (sdoc.nonEmpty) {
                out.append(sdoc).append("\n")
              }
            }
            out
              .append(signature.getLabel)
              .append("\n")
            if (result.getActiveSignature == i && result.getActiveParameter != null) {
              val param = signature.getParameters.get(result.getActiveParameter)
              val column = signature.getLabel.indexOf(param.getLabel.getLeft())
              if (column < 0) {
                fail(s"""invalid parameter label
                        |  param.label    : ${param.getLabel}
                        |  signature.label: ${signature.getLabel}
                        |""".stripMargin)
              }
              val indent = " " * column
              out
                .append(indent)
                .append("^" * param.getLabel.getLeft().length)
                .append("\n")
              signature.getParameters.asScala.foreach { param =>
                val pdoc = doc(param.getDocumentation)
                  .stripPrefix("```scala\n")
                  .stripSuffix("\n```")
                  .replaceAllLiterally("\n```\n", " ")
                if (includeDocs && pdoc.nonEmpty) {
                  out
                    .append("  @param ")
                    .append(param.getLabel.getLeft().replaceFirst(":.*", ""))
                    .append(" ")
                    .append(pdoc)
                    .append("\n")
                }
              }
            }
        }
      }
      assertNoDiff(
        sortLines(stableOrder, out.toString()),
        sortLines(stableOrder, getExpected(expected, compat))
      )
    }
  }

  override val compatProcess: Map[String, String => String] = Map(
    "2.13" -> { s =>
      s.replaceAllLiterally("valueOf(obj: Any)", "valueOf(obj: Object)")
        .replaceAllLiterally(
          "singleton[T](o: T)",
          "singleton[T <: Object](o: T)"
        )
        .replaceAllLiterally("Map[A, B]", "Map[K, V]")
        .replaceAllLiterally("apply[A](xs: A*)", "apply[A](elems: A*)")
        .replaceAllLiterally(
          "def map[B, That](f: Int => B)(implicit bf: CanBuildFrom[List[Int],B,That]): That",
          "def map[B](f: Int => B): List[B]"
        )
    }
  )
}
