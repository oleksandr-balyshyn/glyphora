package io.worxbend.tui.core

import scala.io.Source
import scala.util.Using

import org.scalatest.funsuite.AnyFunSuite

/** Locks down what the constraint solver currently produces for a wide matrix of inputs.
  *
  * The other layout suites state the rules a reader is meant to learn; this one exists so that restructuring the solver
  * cannot quietly change a single cell. Each line of `layout-characterisation.txt` records one case as
  * `label => x+width,x+width,...`, so a diff points straight at the constraint set, extent, spacing and flex that
  * changed. Regenerating the file is only correct when a behaviour change is intended and explained.
  */
final class LayoutCharacterisationSpec extends AnyFunSuite:

  private val expected: Map[String, String] =
    Using.resource(Source.fromInputStream(getClass.getResourceAsStream("/layout-characterisation.txt"), "UTF-8")) {
      source =>
        source.getLines().map(_.split(" => ", 2)).map(parts => parts(0) -> parts(1)).toMap
    }

  test("the recorded matrix is not empty"):
    assert(expected.sizeIs == LayoutCharacterisationCases.cases.size)

  LayoutCharacterisationCases.cases.foreach { layoutCase =>
    test(s"solver output is unchanged for ${layoutCase.label}"):
      val actual = layoutCase.layout
        .split(Rect(0, 0, layoutCase.extent, 4))
        .map(rect => s"${rect.x}+${rect.width}")
        .mkString(",")
      assert(actual == expected(layoutCase.label))
  }
