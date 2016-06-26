package hoverboard.rewrite

import hoverboard.Name
import hoverboard.term.{Pattern, Term}

import scalaz._
import Scalaz._

case class Env(rewriteDirection: Direction,
               matches: IMap[Term, Pattern],
               history: IList[Term]) {
  def invertDirection: Env =
    copy(rewriteDirection = rewriteDirection.invert)

  def withMatch(term: Term, pattern: Pattern) =
    copy(matches = matches + (term -> pattern))

  def alreadySeen(term: Term) =
    history.any(_ embedsInto term)

  def havingSeen(term: Term): Env =
    copy(history = term :: history)

  def withBindings(bindings: ISet[Name]): Env =
    copy(matches = matches.filterWithKey { (t, p) =>
      t.freeVars.intersection(bindings).isEmpty
    })

  def bindingsSet: ISet[Name] =
    ISet.unions(matches.toList.map(m => m._1.freeVars.union(m._2.bindingsSet)))
}

object Env {
  val empty = Env(Direction.Increasing, IMap.empty, IList.empty)
}