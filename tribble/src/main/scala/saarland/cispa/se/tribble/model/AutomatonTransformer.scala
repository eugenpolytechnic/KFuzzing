package saarland.cispa.se.tribble
package model

import dk.brics.automaton.{Automaton, Transition}

import scala.collection.JavaConverters._
import scala.collection.mutable

// xxx migrate character ranges to CharRange
object AutomatonTransformer {
  /** How many characters of a range to split into alternatives */
  private val ALTERNATIVE_THRESHOLD = 20
  /** Split ranges larger than ALTERNATIVE_THRESHOLD into this many ranges */
  private val ALTERNATIVE_SPLIT = 10

  /**
    * Transforms an automaton into a mini-grammar.
    *
    * The idea is for each transition to create a Literal or CharRange (so far Regex) and then
    */
  def transform(automaton: Automaton, namePrefix: String): (NonTerminal, Map[NonTerminal, DerivationRule]) = {
    val result = mutable.Map[NonTerminal, DerivationRule]()
    val entryPointName = s"${namePrefix}0"
    val initialState = automaton.getInitialState
    // prepare a reference for each state that has outgoing transitions
    val states = automaton.getStates.asScala
      .filter(_ != initialState)
      .filterNot(_.getTransitions.isEmpty)
      .zipWithIndex
      .toMap
      .mapValues(i => Reference(namePrefix + (i + 1))) +
      // make sure the initial state gets a known name
      (initialState -> Reference(entryPointName))

    // for each state with outgoing transitions construct rules
    states.foreach { case (currentState, Reference(name, _)) =>
      val alts = currentState.getTransitions.asScala.flatMap { transition =>
        val terminal: DerivationRule = transformTransition(transition)
        val destState = transition.getDest
        val rules: Set[DerivationRule] =
          if (states.contains(destState)) { // has continuation
            val continuation = states(destState)
            if (destState.isAccept)
            // if it also can be stopped here
              Set(Concatenation(Seq(terminal, continuation)), transformTransition(transition)) // making sure to create a separate object
            else
              Set(Concatenation(Seq(terminal, continuation)))
          } else {
            Set(terminal)
          }
        rules
      }
      // add resulting alternatives to the mini-grammar
      addAlternatives(result, name, alts.toSet)
    }
    // if the initial state is accepting, add an epsilon alternative
    if (initialState.isAccept)
      addAlternatives(result, entryPointName, Set(Literal("")))
    entryPointName -> result.toMap
  }

  private def transformTransition(transition: Transition): DerivationRule = {
    val min = transition.getMin
    val max = transition.getMax
    if (min == max) {
      Literal(min.toString)
    } else if (max - min < ALTERNATIVE_THRESHOLD) {
      Alternation((min to max).map(c => Literal(c.toString)).toSet)
    } else {
      // split up into sub-ranges
      Alternation((min to max).grouped((max - min) / ALTERNATIVE_SPLIT).map(v => new Regex(v.head, v.last, 0)).toSet)
    }
  }

  private def addAlternatives(result: mutable.Map[NonTerminal, DerivationRule], name: NonTerminal, alts: Set[DerivationRule]): Unit = {
    if (result.contains(name)) {
      val oldRule = result(name)
      val newAlts = oldRule match {
        case Alternation(alternatives) => alternatives ++ alts
        case _ => Set(oldRule) ++ alts
      }
      result(name) = if (newAlts.size > 1) Alternation(newAlts) else newAlts.head
    } else {
      val rule = if (alts.size > 1) Alternation(alts) else alts.head
      result += name -> rule
    }
  }
}
