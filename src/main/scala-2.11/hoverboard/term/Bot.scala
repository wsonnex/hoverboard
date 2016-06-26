package hoverboard.term

import hoverboard.rewrite.Env

import scalaz.NonEmptyList

case object Bot extends Atom {
  override def driveHeadApp(env: Env, args: NonEmptyList[Term]): Term = this

  override def driveHeadCase(env: Env, branches: NonEmptyList[Branch]): Term = this

  override def toString = "_|_"

  def arbitraryOrderingNumber: Int = 1
}