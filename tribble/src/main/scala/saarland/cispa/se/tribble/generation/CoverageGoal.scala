package saarland.cispa.se.tribble
package generation

import saarland.cispa.se.tribble.model._

import scala.collection.mutable
import scala.util.Random

/** A Goal consists of multiple Targets */
trait CoverageGoal {
  def name: String
  def usedDerivation(rule: DerivationRule, parent: Option[DNode]): Unit
  def cost(from: DerivationRule): Int
  def targetReached: Boolean
  def nextTarget(): Boolean
  def targetCount: Int
  def coveredTargets: Int
}


class KPathCoverageGoal(k: Int)(implicit val grammar: GrammarRepr, implicit val random: Random) extends ReachabilityInformation with CoverageGoal {
  require(k > 0, s"k must be greater than one! ($k given)")
  val name = s"$k-path coverage"
  protected[tribble] val targets: mutable.Set[List[DerivationRule]] = {
    val reachableSymbols = reachability(grammar.root).keys.toSeq
    if (k == 1)
      mutable.Set(random.shuffle(reachableSymbols.map(List(_))):_*)
    else
      mutable.Set(random.shuffle(reachableSymbols.collect { case ref: Reference => ref :: Nil }.flatMap(calcTuples)): _*)
  }
  // the target is ordered such that the next rule to be reached is in head position. E.g. root :: child1 :: grandchild3 :: Nil
  protected var target: List[DerivationRule] = grammar.root :: Nil

  override val targetCount: Int = targets.size

  override def coveredTargets: Int = targetCount - targets.size

  override def nextTarget(): Boolean = {
    require(targetReached, s"Cannot select a new target because the current target has not been reached! $target")
    val hasNext = targets.nonEmpty
    if (hasNext) target = targets.head
    hasNext
  }

  override def usedDerivation(rule: DerivationRule, parent: Option[DNode]): Unit = {
    val tuple = getTupleEndingIn(rule, parent)
    if (tuple.nonEmpty)
      targets -= tuple
    if (target.headOption.contains(rule))
      target = target.tail
  }

  override def cost(from: DerivationRule): Int = target.headOption.fold(Int.MaxValue)(reachability(from).getOrElse(_, Int.MaxValue))

  override def targetReached: Boolean = target.isEmpty

  private def calcTuples(prefix: List[Reference]): Set[List[DerivationRule]] = {
    require(prefix.nonEmpty)
    if (prefix.size == k) {
      return Set(prefix)
    }
    val start = prefix.head
    val immediateSteps = computeImmediateSteps(grammar(start)).keys // reachability(grammar(start)).withFilter(_._2 == 0).map(_._1)
    if (prefix.lengthCompare(k - 1) == 0) { // last element: nonterminals and terminals allowed as last element
      immediateSteps.collect { case ref: Reference => ref case t: TerminalRule => t }.map(x => (x :: prefix).reverse).toSet
    } else { // only interested in nonterminals
      immediateSteps.collect { case ref: Reference => ref }.map(_ :: prefix).flatMap(calcTuples).toSet
    }
  }

  private def lookUp(p: Option[DNode], limit: Int): mutable.ListBuffer[DerivationRule] = {
    val prefix = mutable.ListBuffer[DerivationRule]()
    var n = p
    while (prefix.size < limit && n.isDefined) {
      n.get.decl match {
        case r: Reference => prefix.prepend(r)
        case _ =>
      }
      n = n.get.parent
    }
    prefix
  }

  protected def getTupleEndingIn(rule: DerivationRule, parent: Option[DNode]): List[DerivationRule] = rule match {
    case _: Reference | _: TerminalRule =>
      val prefix = lookUp(parent, k - 1)
      prefix.append(rule)
      prefix.toList
    case _ => Nil
  }

}

