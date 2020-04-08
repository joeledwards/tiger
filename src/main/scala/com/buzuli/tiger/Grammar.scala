package com.buzuli.tiger

object Grammar {
}

trait LValue
case class ID(name: String) extends LValue
case class Subscript(value: LValue, expression: Option[Expression]) extends LValue
case class FieldExpression(value: LValue, id: ID) extends LValue

case class TypeId(override val name: String) extends ID(name)
case class VariableId(name: String)
case class FunctionId(name: String)

trait TType
case class ArrayType(id: TypeId) extends TType
case class RecordType(fields: List[FieldDeclaration]) extends TType

case class FieldDeclaration(id: ID, ttype: TType )
case class VariableDeclaration(id: ID, ttype: TType Expression)

trait Declaration
case class TypeDeclaration(id: TypeId) extends Declaration
case class VariableDeclaration(id: VariableId, typeId: Option[TypeId]) extends Declaration
case class FunctionDeclaration(id: FunctionId, fields: List[FieldDeclaration], typeId: Option[TypeId]) extends Declaration

trait InfixOperation

trait Expression
case class SequenceExpression(expressions: List[Expression]) extends Expression
case class Negation(expression: Expression)
case class CallExpression(id: ID, expressions: List[Expression])
case class InfixExpression(left: Expression, op: InfixOperation, right: Expression)
case class ArrayCreate(id: TypeId, map: Option[Expression], input: Expression)
case class RecordCreate(id: TypeId, factories: List[FieldCreate])
case class FieldCreate(id: ID, expression: Expression)
case class Assignment(value: LValue, expression: Expression)
case class IfThenElse(condition: Expression, left: Expression, right: Expression)
case class IfThen(condition: Expression, value: Expression)
case class WhileExpression(condition: Expression, action: Expression)
case class ForExpression(id: ID, start: Expression, end: Expression, action: Expression)
case class LetExpression(declaration: Declaration, expressions: List[Expression])
