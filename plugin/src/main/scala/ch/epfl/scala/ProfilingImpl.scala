package ch.epfl.scala

import newtype.tools.Debugger

final class ProfilingImpl[G <: scala.tools.nsc.Global](val global: G) {
  import global._
  val debugger = new Debugger(global)

  final class ProfilingTraverser extends Traverser {
    override def traverse(tree: Tree): Unit = {
      super.traverse(tree)
    }
  }
}
