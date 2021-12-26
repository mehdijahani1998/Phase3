package main.visitor.type;

import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.expression.operators.UnaryOperator;
import main.ast.nodes.expression.values.primitive.BoolValue;
import main.ast.nodes.expression.values.primitive.IntValue;
import main.ast.types.ListType;
import main.ast.types.NoType;
import main.ast.types.StructType;
import main.ast.types.Type;
import main.ast.types.primitives.BoolType;
import main.ast.types.primitives.IntType;
import main.ast.types.primitives.VoidType;
import main.compileError.typeError.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.symbolTable.items.FunctionSymbolTableItem;
import main.symbolTable.items.StructSymbolTableItem;
import main.symbolTable.items.SymbolTableItem;
import main.symbolTable.items.VariableSymbolTableItem;
import main.visitor.Visitor;
import main.ast.types.FptrType;

import java.util.ArrayList;

public class ExpressionTypeChecker extends Visitor<Type> {

    private FunctionSymbolTableItem currentFunction;
    private StructSymbolTableItem currentStruct;
    private boolean fCallStmt;

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

    public void setfCallStmt(boolean fCallStmt) {
        this.fCallStmt = fCallStmt;
    }


    public boolean checkSpecialTypeEquality (Type t1, Type t2){
        //check NoType equality
        if(t1 instanceof NoType || t2 instanceof NoType) return true;

        //check Int, Bool, Void equality
        if(t1.equals(t2)){
            if(t1 instanceof IntType || t1 instanceof BoolType || t1 instanceof VoidType){
                return true;
            }
        }

        //check List equality
        if(t1 instanceof ListType && t2 instanceof ListType){
            return checkSpecialTypeEquality(((ListType) t1).getType(),((ListType) t2).getType());
        }


        //check Fptr equality
        if (t1 instanceof FptrType && t2 instanceof FptrType) {
            FptrType f1 = (FptrType) t1;
            FptrType f2 = (FptrType) t2;
            if (!checkSpecialTypeEquality(f1.getReturnType(), f2.getReturnType()))
                return false; //functions return type should be equal.

            //if return type was the same we have to check each argument

            ArrayList<Type> f1Arguments = f1.getArgsType();
            ArrayList<Type> f2Arguments = f1.getArgsType();

            if (f1Arguments.size() == f2Arguments.size()) {
                for (int index = 0; index < f1Arguments.size(); index++) {
                    if (!checkSpecialTypeEquality((f1Arguments.get(index)), f2Arguments.get(index))) return false;
                }
                return true;
            }
            return false;
        }

        //check Struct equality
        if(t1 instanceof StructType && t2 instanceof  StructType){
            return (((StructType) t1).getStructName().equals(((StructType) t2).getStructName()));
        }
        return false;
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
            if (lxpt instanceof IntType && rxpt instanceof IntType)
                return new BoolType();

            if ((lxpt instanceof NoType || lxpt instanceof IntType) &&
                    (rxpt instanceof IntType || rxpt instanceof NoType))
                return new NoType();
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
        fCallStmt = false;
        boolean holdFCall = fCallStmt;
        Expression fInstance = funcCall.getInstance();
        String fName = ((Identifier) fInstance).getName();
        ArrayList<Type> fArgType = new ArrayList<>();
        Type fT = funcCall.getInstance().accept(this);

        //check and visit function arguments type
        for (Expression expression : funcCall.getArgs()) {
            Type type = expression.accept(this);
            fArgType.add(type);
        }
        fCallStmt = holdFCall;

        //check unhandled types which aren't Fptr and NoType
        if (!(fT instanceof FptrType || fT instanceof NoType)){
            CallOnNoneFptrType error = new CallOnNoneFptrType(funcCall.getLine());
            funcCall.addError(error);
            return new NoType();
        }
        //check valid type
        if (fT instanceof FptrType) {

            boolean errorExists = false;
            boolean err = false;
            FunctionSymbolTableItem currFunc = searchFSTI(fName);
            assert currFunc != null;


            if (currFunc.getReturnType() instanceof VoidType && !fCallStmt) {
                CantUseValueOfVoidFunction error = new CantUseValueOfVoidFunction(funcCall.getLine());
                funcCall.addError(error);
                err = true;
            }

            //check if arguments match the original definition
            else if(funcCall.getArgs().size() != currFunc.getArgTypes().size())
                errorExists = true;
            else {
                if(fArgType.size() != 0) {
                    int index = 0;
                    for (Type ltype : currFunc.getArgTypes()) {
                        if (!checkSpecialTypeEquality(ltype, fArgType.get(index))) {
                            errorExists = true;
                            break;
                        }
                        index++;

                    }
                }
            }

            if (errorExists) {
                ArgsInFunctionCallNotMatchDefinition error = new ArgsInFunctionCallNotMatchDefinition(funcCall.getLine());
                funcCall.addError(error);
            }
            if (errorExists || err)
                return new NoType();
            else
                return currFunc.getReturnType();
        }
        else
            return new NoType();
    }

    @Override
    public Type visit(Identifier identifier) {
        String identifierName = identifier.getName();
        //check the function matched with the identifier
        try {
            FunctionSymbolTableItem fS = (FunctionSymbolTableItem) SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY + identifierName);
            return new FptrType(fS.getArgTypes(), fS.getReturnType());
        }
        catch (ItemNotFoundException e) {
            if (currentFunction != null) {
                try {
                    VariableSymbolTableItem vS = (VariableSymbolTableItem) currentFunction.getFunctionSymbolTable().getItem(VariableSymbolTableItem.START_KEY + identifierName);
                    return vS.getType();
                }
                catch (ItemNotFoundException e1) {
                    VarNotDeclared error = new VarNotDeclared(identifier.getLine(), identifierName);
                    identifier.addError(error);
                    return new NoType();
                }
            }

            //check if the identifier is a member of the struct
            if (currentStruct != null){
                try {
                    VariableSymbolTableItem vS = (VariableSymbolTableItem) currentStruct.getStructSymbolTable().getItem(VariableSymbolTableItem.START_KEY + identifierName);
                    return vS.getType();
                } catch (ItemNotFoundException e1) {
                    StructMemberNotFound exception = new StructMemberNotFound(identifier.getLine(), currentStruct.getName(), identifierName);
                    identifier.addError(exception);
                    return new NoType();
                }
            }

            //check if the identifier matches with any defined struct
            else {
                try {
                    VariableSymbolTableItem vS = (VariableSymbolTableItem) SymbolTable.top.getItem(VariableSymbolTableItem.START_KEY + identifierName);
                    return vS.getType();
                }
                catch (ItemNotFoundException e1) {
                    VarNotDeclared error = new VarNotDeclared(identifier.getLine(), identifierName);
                    identifier.addError(error);
                    return new NoType();
                }
            }
        }
    }

    @Override
    public Type visit(ListAccessByIndex listAccessByIndex) {

        Type instanceType = listAccessByIndex.getInstance().accept(this);
        Type indexType = listAccessByIndex.getIndex().accept(this);

        //check unhandled types that aren't NoType neither List Type
        if(!(indexType instanceof IntType || indexType instanceof NoType)){
            ListIndexNotInt error = new ListIndexNotInt(listAccessByIndex.getLine());
            listAccessByIndex.addError(error);
        }

        if(instanceType instanceof NoType)
            return new NoType();

        if(!(instanceType instanceof ListType)){
            AccessByIndexOnNonList error = new AccessByIndexOnNonList(listAccessByIndex.getLine());
            listAccessByIndex.addError(error);
            return new NoType();
        }

        else {
            if (indexType instanceof IntType)
                return ((ListType) instanceType).getType();
            else
                return new NoType();
        }
    }

    @Override
    public Type visit(StructAccess structAccess) {
        Type stInstanceType = structAccess.getInstance().accept(this);

        if(!(stInstanceType instanceof StructType || stInstanceType instanceof NoType)){
            AccessOnNonStruct error = new AccessOnNonStruct(structAccess.getLine());
            structAccess.addError(error);
        }

        if (stInstanceType instanceof StructType){
            String stName = StructSymbolTableItem.START_KEY + ((StructType) stInstanceType).getStructName().getName();
            String stVariableName = VariableSymbolTableItem.START_KEY + structAccess.getElement().getName();

            try {
                SymbolTableItem symbolTableItem = SymbolTable.root.getItem(stName);
                SymbolTable symbolTable = ((StructSymbolTableItem) symbolTableItem).getStructSymbolTable();
                SymbolTableItem elItem = symbolTable.getItem(stVariableName);
                return ((VariableSymbolTableItem) elItem).getType();

            } catch (ItemNotFoundException e) {
                StructMemberNotFound error = new StructMemberNotFound(structAccess.getLine(), ((StructType) stInstanceType).getStructName().getName(), structAccess.getElement().getName());
                structAccess.addError(error);
            }
        }
        return new NoType();
    }

    @Override
    public Type visit(ListSize listSize) {

        Type instanceType = listSize.getArg().accept(this);

        if(instanceType instanceof ListType)
            return new IntType();
        else {
            if(!(instanceType instanceof NoType)) {
                GetSizeOfNonList error = new GetSizeOfNonList(listSize.getLine());
                listSize.addError(error);
            }
            return new NoType();
        }
    }

    @Override
    public Type visit(ListAppend listAppend) {

        Type argType = listAppend.getListArg().accept(this);
        Type elType = listAppend.getElementArg().accept(this);

        //check if argType is unhandled.
        if (!(argType instanceof ListType || argType instanceof NoType)){
            AppendToNonList error = new AppendToNonList(listAppend.getLine());
            listAppend.addError(error);
            return new NoType();
        }
        //check if the given type for the argument matches with the original definition.
        if (((ListType) argType).getType().equals(elType)){
            NewElementTypeNotMatchListType error = new NewElementTypeNotMatchListType(listAppend.getLine());
            listAppend.addError(error);
            return new NoType();
        }
        return new VoidType(); //return value is choosed based on TA explanations.
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
