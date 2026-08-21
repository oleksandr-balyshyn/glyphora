package io.worxbend.tui.widgets

import io.worxbend.tui.testsupport.BufferAssertions.{rendered, trimmedLines}

import org.scalatest.funsuite.AnyFunSuite

final class LinkSpec extends AnyFunSuite:

  test("a link renders its label with the url attached to the style"):
    val buffer = rendered(Link("docs", "https://example.com"), 10, 1)
    assert(trimmedLines(buffer).head == "docs")
    assert(buffer.get(0, 0).style.link.contains("https://example.com"))
