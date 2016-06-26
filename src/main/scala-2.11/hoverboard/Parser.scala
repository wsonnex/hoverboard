package hoverboard

import hoverboard.term._

import scalaz._
import Scalaz._

sealed trait Statement {
  def apply(program: Program): Program
}

case class TermDef(name: String, term: Term) extends Statement {
  def apply(program: Program) = program + (name -> term.withName(name))
}

case class ConstructorDef(constr: Constructor) extends Statement {
  def apply(program: Program) =
    program + (constr.name.toString -> constr)
}

object Parser {

  private class Rules(program: Program) {
    import fastparse.WhitespaceApi
    import fastparse.noApi._

    val White = WhitespaceApi.Wrapper {
      import fastparse.all._
      NoTrace((" " | "\n" | "\r").rep)
    }
    import White._

    val keywords = Set("fix", "fn", "case", "of", "else", "let", "end", "unfold", "rel")

    val lowercase = P(CharIn('a' to 'z') | CharIn(Seq('_')))
    val uppercase = P(CharIn('A' to 'Z') | CharIn('0' to '9') | CharIn(Seq('\'')))

    val varName: P[Name] = P((lowercase ~~ (uppercase | lowercase).repX).!).filter(n => !keywords.contains(n)).map(Name(_))
    val definitionName: P[String] = P((uppercase ~~ (uppercase | lowercase).repX).!)

    val definedTerm: P[Term] = P(definitionName).map(n => program.definitionOf(n))
    val fixIndex: P[Name] = P("[" ~ varName ~ "]")

    val termVar: P[Term] = P(varName).map(Var)
    val simpleTerm: P[Term] = P(bot | unfold | termVar | definedTerm | "(" ~/ term ~ ")")
    val unfold: P[Term] = P("unfold" ~/ definedTerm).map(_.asInstanceOf[Fix].unfold)
    val fix: P[Fix] = P("fix" ~/ fixIndex.? ~ varName.rep(1) ~ "->" ~ term)
      .map(m => Fix(Lam(IList(m._2 : _*).toNel.get, m._3), m._1.map(Fix.Finite).getOrElse(Name.freshIndex)))
    val lam: P[Term] = P("fn" ~/ varName.rep(1) ~ "->" ~ term).map(m => Lam(IList(m._1 : _*), m._2))
    val app: P[Term] = P(simpleTerm ~/ simpleTerm.rep).map(m => m._1(m._2 : _*))
    val bot: P[Term] = P("_|_").map(_ => Bot)
    val leq: P[Leq] = P("rel" ~/ term ~ "=<" ~/ term).map(m => Leq(m._1, m._2))
    val caseOf: P[Term] = P("case" ~/ term ~ branch.rep(1) ~ "end").map(m => Case(m._1, IList(m._2 : _*).toNel.get))

    val pattern: P[Pattern] = P(definedTerm ~ varName.rep).map(m => Pattern(m._1.asInstanceOf[Constructor], IList(m._2 : _*)))
    val branch: P[Branch] = {
      val defaultBranch: P[Branch] = P("|" ~ "else" ~/ "->" ~ term).map(DefaultBranch)
      val patternBranch: P[Branch] = P("|" ~ pattern ~/ "->" ~ term).map(m => PatternBranch(m._1, m._2))
      P(defaultBranch | patternBranch)
    }

    val term: P[Term] = P(leq | fix | lam | app | caseOf | unfold)

    val constructorDef: P[Constructor] = {
      val typeArg: P[Boolean] = P("*").map(_ => true) | P("_").map(_ => false)
      P(definitionName ~ typeArg.rep).map { m =>
        val recArgs = m._2.indices.filter(i => m._2(i))
        Constructor(Name(m._1), m._2.length, ISet.fromList(List(recArgs : _*)))
      }
    }

    val statement: P[Option[Statement]] = {
      val termDef: P[Statement] = P("let" ~/ definitionName ~ "=" ~ term ~ ";").map(m => TermDef(m._1, m._2))
      val conDefs: P[Statement] = P("data" ~/ constructorDef ~ ";").map(ConstructorDef)

      P(P(termDef | conDefs).map(Some(_)) | P(End).map(_ => None))
    }
  }

  /**
    * Parses the first definition in the given string.
    */
  def parseStatement(text: String)(implicit program: Program): Option[(Statement, String)] = {
    val trimmedText = text.trim
    val parsed = new Rules(program).statement.parse(trimmedText).get
    parsed.value match {
      case None => None
      case Some(stmt) => Some((stmt, trimmedText.substring(parsed.index)))
    }
  }

  /**
    * Parses statements one by one from a string
    * @return Successive [[Program]] objects after each statement has been loaded.
    */
  def parseAll(text: String)(termHandler: TermDef => TermDef)(implicit program: Program): Program =
    Scalaz.unfold((text, program)) { case (text, program) =>
      parseStatement(text)(program).map { case (stmt, remaining) =>
        val newProgram = stmt match {
          case stmt: TermDef => termHandler(stmt)(program)
          case _ => stmt(program)
        }
        (newProgram, (remaining, newProgram))
      }
    }.last

  def parseTerm(text: String)(implicit program: Program): Term =
    new Rules(program).term.parse(text).get.value
}