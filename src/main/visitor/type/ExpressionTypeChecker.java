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
import main.compileError.typeError.ArgsInFunctionCallNotMatchDefinition;
import main.compileError.typeError.UnsupportedOperandType;
import main.compileError.typeError.VarNotDeclared;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.symbolTable.items.FunctionSymbolTableItem;
import main.symbolTable.items.StructSymbolTableItem;
import main.visitor.Visitor;

import java.util.ArrayList;

public class ExpressionTypeChecker extends Visitor<Type> {

    private FunctionSymbolTableItem currentFunction;
    private StructSymbolTableItem currentStruct;

    public void setCurrentFunction(FunctionSymbolTableItem cf){
        this.currentFunction = cf;
    }

    public void setCurrentStruct(StructSymbolTableItem cs){
        this.currentStruct = cs;
    }

    private FunctionSymbolTableItem searchFSTI (String function_name){
        try{
            return (FunctionSymbolTableItem) SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY + function_name);
        }catch (ItemNotFoundException e){
            return null;
        }
    }

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

        else if (operator.equals(BinaryOperator.gt) || operator.equals(BinaryOperator.lt)){
            if ()
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

        for (Expression argument: funcargs){
            argument.accept(this);
        }

        try {
            SymbolTable.root.getItem((FunctionSymbolTableItem.START_KEY + ((Identifier) funcCall.getInstance()).getName()));
        } catch (ItemNotFoundException e) {
            VarNotDeclared error = new VarNotDeclared(funcCall.getLine(), ((Identifier) funcCall.getInstance()).getName());
            funcCall.addError(error);
            return new NoType();
        }

        try {
            FunctionSymbolTableItem functionSymbolTableItem = (FunctionSymbolTableItem) SymbolTable.root.getItem(
                    (FunctionSymbolTableItem.START_KEY + ((Identifier) funcCall.getInstance()).getName())
            );

            ArrayList<Type> original_arguments_type = functionSymbolTableItem.getArgTypes();

            if(original_arguments_type.size()!=funcargs.size()){
                ArgsInFunctionCallNotMatchDefinition error = new ArgsInFunctionCallNotMatchDefinition(funcCall.getLine());
                funcCall.addError(error);
                return new NoType();
            }

            for (int index = 0; index < original_arguments_type.size(); index++){

                if(!original_arguments_type.get(index).equals(funcargs.get(index))){
                    ArgsInFunctionCallNotMatchDefinition error = new ArgsInFunctionCallNotMatchDefinition(funcargs.get(index).getLine());
                    funcCall.addError(error);
                    return new NoType();
                }
            }
        } catch (ItemNotFoundException e) {
            return new NoType();
        }




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
        Type stInstanceType = structAccess.getInstance().accept(this);
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
        return exprInPar.getInputs().get(0).accept(this);
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
