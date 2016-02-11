package ru.avhaliullin.whatever.semantic

import ru.avhaliullin.whatever.common.PrettyPrint

/**
  * @author avhaliullin
  */
sealed trait SemanticTreeNode {
  def pretty: PrettyPrint
}

object SemanticTreeNode {

  sealed trait Definition extends SemanticTreeNode

  sealed trait Expression extends SemanticTreeNode {
    def tpe: Tpe

    def mute: Expression

    def pretty = prettyExpr.prepend(s"[$tpe]")

    def prettyExpr: PrettyPrint
  }

  sealed trait Statement extends Expression {
    override final def tpe: Tpe = Tpe.UNIT

    def mute = this
  }

  case class VarDefinition(id: VarId, varType: Tpe) extends Statement {
    def prettyExpr = PrettyPrint.Literal(s"VarDef($id : $varType)")
  }

  sealed trait Assignment extends Expression {
    def value: Expression

    def read: Boolean

    def tpe = if (read) value.tpe else Tpe.UNIT
  }

  case class VarAssignment(id: VarId, value: Expression, read: Boolean) extends Assignment {
    def mute = if (read) copy(read = false) else this

    def prettyExpr = PrettyPrint.Complex(s"Assign(", ")", Seq(PrettyPrint.Literal(id.toString), value.pretty))
  }

  case class FieldAccess(field: Structure.Field, structure: Structure, expr: Expression) extends Expression {
    def tpe = field.tpe

    def mute = expr.mute

    def prettyExpr = PrettyPrint.Complex(s"FieldAccess(", ")", Seq(PrettyPrint.Literal(structure.name + "." + field.name), expr.pretty))
  }

  case class FieldAssignment(field: FieldAccess, value: Expression, read: Boolean) extends Assignment {
    def mute = if (read) copy(read = false) else this

    def prettyExpr = PrettyPrint.Complex(s"Assignment(", ")", Seq(field.pretty, value.pretty))
  }

  case class VarRead(id: VarId, tpe: Tpe) extends Expression {
    def mute = Nop

    def prettyExpr = PrettyPrint.Literal(s"VarRead($id)")
  }

  case class Block(code: Seq[SemanticTreeNode.Expression], tpe: Tpe) extends Expression {
    def mute = {
      if (tpe == Tpe.UNIT) {
        this
      } else {
        Block(code.dropRight(1) :+ code.last.mute, Tpe.UNIT)
      }
    }

    def prettyExpr = PrettyPrint.Complex("{", "}", code.map(_.pretty))
  }

  case class StructureInstantiation(desc: Structure, args: IndexedSeq[Expression], evalOrder: Seq[Int]) extends Expression {
    val tpe = Tpe.Struct(desc.name)

    def mute = {
      Block(evalOrder.map(args(_).mute), Tpe.UNIT)
    }

    def prettyExpr = PrettyPrint.Complex(s"new ${desc.name}(", ")", args.map(_.pretty) :+ PrettyPrint.Literal("evaluation order: " + evalOrder.mkString(", ")))
  }

  case class StructureDefinition(desc: Structure) extends Definition {
    def pretty = PrettyPrint.Literal("struct " + desc.name + "(" + desc.fields.map(f => f.name + ": " + f.tpe).mkString(", ") + ")")
  }

  case class FnDefinition(sig: FnSignature, code: Seq[SemanticTreeNode.Expression]) extends Definition {
    def pretty = PrettyPrint.Complex("fn " + sig.name + "(" + sig.args.map(a => a.name + ": " + a.tpe).mkString(", ") + "): " + sig.returnType + " = {", "}", code.map(_.pretty))
  }

  case class FnCall(sig: FnSignature, args: Seq[Expression]) extends Expression {
    override def tpe: Tpe = sig.returnType

    def mute = {
      if (tpe == Tpe.UNIT) {
        this
      } else {
        Block(Seq(this, Pop(tpe)), Tpe.UNIT)
      }
    }

    def prettyExpr = PrettyPrint.Complex("FnCall(", ")", PrettyPrint.Literal(sig.name) +: args.map(_.pretty))
  }

  case class Echo(expr: Expression) extends Statement {
    def prettyExpr = PrettyPrint.Complex("Echo(", ")", Seq(expr.pretty))
  }

  case class BOperator(arg1: Expression, arg2: Expression, op: BinaryOperator) extends Expression {
    val tpe = op.retType

    def mute = {
      if (op.conditionalEval) {
        Block(Seq(this, Pop(op.retType)), Tpe.UNIT)
      } else {
        Block(Seq(arg1.mute, arg2.mute), Tpe.UNIT)
      }
    }

    def prettyExpr = PrettyPrint.Complex("BinOp-" + op + "(", ")", Seq(arg1.pretty, arg2.pretty))
  }

  case class UOperator(arg: Expression, op: UnaryOperator) extends Expression {
    val tpe = op.retType

    def mute = arg.mute

    def prettyExpr = PrettyPrint.Complex("UnOp-" + op + "(", ")", Seq(arg.pretty))
  }

  sealed trait Const extends Expression {
    def literal: String

    def mute = Nop

    def prettyExpr = PrettyPrint.Literal(literal)
  }

  case class IntConst(value: Int) extends Const {
    val tpe = Tpe.INT

    def literal = value.toString
  }

  case class BoolConst(value: Boolean) extends Const {
    val tpe = Tpe.BOOL

    def literal = value.toString
  }

  case class IfExpr(cond: Expression, thenBlock: Expression, elseBlock: Expression, tpe: Tpe) extends Expression {
    def mute = {
      if (tpe == Tpe.UNIT) {
        this
      } else {
        IfExpr(cond, thenBlock.mute, elseBlock.mute, Tpe.UNIT)
      }
    }

    def prettyExpr = PrettyPrint.Complex("If(", ")", Seq(cond.pretty.prepend("condition: "), thenBlock.pretty.prepend("then: "), elseBlock.pretty.prepend("else: ")))
  }

  case object Nop extends Statement {
    def prettyExpr = PrettyPrint.Literal("NOP")
  }

  case class Pop(argType: Tpe) extends Statement {
    def prettyExpr = PrettyPrint.Literal(s"POP[$argType]")
  }

}
