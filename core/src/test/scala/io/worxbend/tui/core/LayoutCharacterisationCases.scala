package io.worxbend.tui.core

/** The input matrix shared by the layout characterisation test and, while it was being written, the generator that
  * produced its expected values.
  */
object LayoutCharacterisationCases:

  final case class Case(label: String, layout: Layout, extent: Int)

  private val constraintSets: Seq[(String, Seq[Constraint])] = Seq(
    "len"            -> Seq(Constraint.Length(3), Constraint.Length(4)),
    "len-overflow"   -> Seq(Constraint.Length(30), Constraint.Length(40)),
    "pct-halves"     -> Seq(Constraint.Percentage(50), Constraint.Percentage(50)),
    "pct-thirds"     -> Seq(Constraint.Percentage(33), Constraint.Percentage(33), Constraint.Percentage(33)),
    "pct-half-only"  -> Seq(Constraint.Percentage(50)),
    "pct-negative"   -> Seq(Constraint.Percentage(-10), Constraint.Percentage(60)),
    "ratio-thirds"   -> Seq(Constraint.Ratio(1, 3), Constraint.Ratio(1, 3), Constraint.Ratio(1, 3)),
    "ratio-zero-den" -> Seq(Constraint.Ratio(1, 0), Constraint.Length(2)),
    "fill-even"      -> Seq(Constraint.Fill(1), Constraint.Fill(1)),
    "fill-weighted"  -> Seq(Constraint.Fill(1), Constraint.Fill(3)),
    "fill-zero"      -> Seq(Constraint.Fill(0), Constraint.Length(2)),
    "min-len"        -> Seq(Constraint.Min(3), Constraint.Length(2)),
    "min-min"        -> Seq(Constraint.Min(3), Constraint.Min(2)),
    "max-len"        -> Seq(Constraint.Max(4), Constraint.Length(2)),
    "max-max"        -> Seq(Constraint.Max(4), Constraint.Max(3)),
    "max-fill"       -> Seq(Constraint.Max(4), Constraint.Fill(1)),
    "min-max-fill"   -> Seq(Constraint.Min(2), Constraint.Max(3), Constraint.Fill(2)),
    "pct-fill"       -> Seq(Constraint.Percentage(50), Constraint.Fill(1)),
    "mixed"          -> Seq(Constraint.Length(2), Constraint.Percentage(30), Constraint.Fill(1), Constraint.Max(3)),
  )

  private val extents: Seq[Int] = Seq(0, 1, 5, 7, 10, 13, 100)

  private val flexes: Seq[Flex] = Flex.values.toSeq

  val cases: Seq[Case] =
    for
      (name, constraints) <- constraintSets
      extent              <- extents
      spacing             <- Seq(0, 1, 2)
      flex                <- flexes
    yield Case(
      s"$name/$extent/$spacing/$flex",
      Layout(Direction.Horizontal, constraints, spacing, flex),
      extent,
    )
