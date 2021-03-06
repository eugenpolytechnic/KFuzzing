package saarland.cispa.se.tribble
package generation

import saarland.cispa.se.tribble.model.{DNode, DTree, DerivationRule}

trait TreeGenerator {
  def generate(implicit grammar: GrammarRepr): DTree = gen(grammar.root, None, 0)
  def gen(decl: DerivationRule, parent: Option[DNode], currentDepth: Int)(implicit grammar: GrammarRepr): DTree
}
