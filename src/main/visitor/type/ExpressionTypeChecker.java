package main.visitor.type;

import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.expression.operators.UnaryOperator;
import main.ast.nodes.expression.values.primitive.BoolValue;
import main.ast.nodes.expression.values.primitive.IntValue;
import main.ast.types.ListType;
import main.ast.types.NoType;
import main.ast.types.Type;
import main.ast.types.primitives.BoolType;
import main.ast.types.primitives.IntType;
import main.compileError.typeError.UnsupportedOperandType;
import main.visitor.Visitor;

import java.util.ArrayList;

public class ExpressionTypeChecker extends Visitor<Type> {


    @Override
    public Type visit(BinaryExpression binaryExpression) {
        Expression lxp, rxp;
        lxp = binaryExpression.getFirstOperand();
        rxp = binaryExpression.getSecondOperand();
        Type lxpt, rxpt;
        lxpt = lxp.accept(this);
        rxpt = rxp.accept(this);

        BinaryOperator operator = binaryExpression.getBinaryOperator();

        if (operator.equals(BinaryOperator.add)  || operator.equals(BinaryOperator.sub)
                || operator.equals(BinaryOperator.mult) || operator.equals(BinaryOperator.div))
        {
            if (lxpt instanceof IntType && rxpt instanceof IntType) {
                return new IntType();
            }
            else if (lxpt instanceof NoType || rxpt instanceof NoType) {
                return new NoType();
            }
        }

        else if (operator.equals(BinaryOperator.or)  || operator.equals(BinaryOperator.and)){
            if (lxpt instanceof BoolType && rxpt instanceof BoolType) {
                return new BoolType();
            }
            else if (lxpt instanceof NoType || rxpt instanceof NoType) {
                return new NoType();
            }
        }

        else if (operator.equals(BinaryOperator.eq)){
            if(lxpt instanceof  ListType || rxpt instanceof ListType){
                UnsupportedOperandType error = new UnsupportedOperandType(lxp.getLine(), operator.name());
                binaryExpression.addError(error);
                return new NoType();
            }
            else if (lxpt.equals(rxpt)) {
                return new BoolType();
            }
            else if (lxpt instanceof NoType || rxpt instanceof NoType) {
                return new NoType();
            }

        }

        UnsupportedOperandType error = new UnsupportedOperandType(lxp.getLine(), operator.name());
        lxp.addError(error);
        return new NoType();

    }

    @Override
    public Type visit(UnaryExpression unaryExpression) {

        Expression exp;
        exp = unaryExpression.getOperand();
        UnaryOperator operator = unaryExpression.getOperator();
        Type expt = exp.accept(this);

        if (operator.equals(UnaryOperator.not)){
            if(expt instanceof BoolType){
                return new BoolType();
            }
            else if(expt instanceof NoType){
                return new NoType();
            }
        }

        else if (operator.equals(UnaryOperator.minus)){
            if (expt instanceof IntType){
                return new IntType();
            }
            else if(expt instanceof NoType){
                return new NoType();
            }
        }

        UnsupportedOperandType error = new UnsupportedOperandType(exp.getLine(), operator.name());
        exp.addError(error);
        return new NoType();
    }

    @Override
    public Type visit(FunctionCall funcCall) {
        Expression funcexp = funcCall.getInstance();
        ArrayList<Expression> funcargs = funcCall.getArgs();

        //Todo
        return null;
    }

    @Override
    public Type visit(Identifier identifier) {
        //Todo
        return null;
    }

    @Override
    public Type visit(ListAccessByIndex listAccessByIndex) {

        //Todo
        return null;
    }

    @Override
    public Type visit(StructAccess structAccess) {
        //Todo
        return null;
    }

    @Override
    public Type visit(ListSize listSize) {
        //Todo
        return null;
    }

    @Override
    public Type visit(ListAppend listAppend) {
        //Todo
        return null;
    }

    @Override
    public Type visit(ExprInPar exprInPar) {
        //Todo
        return null;
    }

    @Override
    public Type visit(IntValue intValue) {
        //Todo
        return null;
    }

    @Override
    public Type visit(BoolValue boolValue) {
        //Todo
        return null;
    }
}
