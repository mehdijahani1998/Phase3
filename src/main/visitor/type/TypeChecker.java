package main.visitor.type;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.*;
import main.ast.nodes.declaration.struct.*;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.statement.*;
import main.ast.types.*;
import main.ast.types.primitives.BoolType;
import main.ast.types.primitives.IntType;
import main.compileError.typeError.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemAlreadyExistsException;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.symbolTable.items.FunctionSymbolTableItem;
import main.symbolTable.items.StructSymbolTableItem;
import main.symbolTable.items.VariableSymbolTableItem;
import main.visitor.Visitor;



public class TypeChecker extends Visitor<Void> {
    private boolean checkMain;
    private boolean checkGetter;
    private Type globalGetterType;
    private boolean checkSetter;

    ExpressionTypeChecker expressionTypeChecker;
    private FunctionSymbolTableItem currentFunction;
    private StructSymbolTableItem currentStruct;



    private FunctionSymbolTableItem findFSTI(String function_name) {
        try{
            return (FunctionSymbolTableItem) SymbolTable.root.getItem(FunctionSymbolTableItem.START_KEY + function_name);
        }
        catch (ItemNotFoundException e){
            return null;
        }
    }

    private StructSymbolTableItem findSSTI(String struct_name) {
        try{
            return (StructSymbolTableItem) SymbolTable.root.getItem(StructSymbolTableItem.START_KEY + struct_name);
        }
        catch (ItemNotFoundException e){
            return null;
        }
    }

    @Override
    public Void visit(Program program) {

        for (StructDeclaration structDec: program.getStructs())
            structDec.accept(this);

        for (FunctionDeclaration funcDec: program.getFunctions())
            funcDec.accept(this);

        program.getMain().accept(this);
        return null;
    }

    @Override
    public Void visit(FunctionDeclaration functionDec) {
        Type funcRetType = functionDec.getReturnType();

        //check the case in which return object is a struct

        if(funcRetType instanceof StructType) {
            String stName = StructSymbolTableItem.START_KEY + ((StructType) funcRetType).getStructName().getName();

            try{
                SymbolTable.root.getItem(stName);
            }
            catch (ItemNotFoundException e){
                StructNotDeclared error = new StructNotDeclared(functionDec.getLine(), ((StructType) funcRetType).getStructName().getName());
                functionDec.addError(error);
            }
        }


        currentFunction = findFSTI(functionDec.getFunctionName().getName());
        SymbolTable funcSymbolTable = new SymbolTable(SymbolTable.root);
        currentFunction.setFunctionSymbolTable(funcSymbolTable);
        SymbolTable.push(funcSymbolTable);
        expressionTypeChecker.setCurrentFunction(currentFunction);
        for(VariableDeclaration arg: functionDec.getArgs()){
            arg.accept(this);
        }
        functionDec.getBody().accept(this);
        SymbolTable.pop();
        currentFunction = null;
        expressionTypeChecker.setCurrentFunction(currentFunction);
        return null;
    }

    @Override
    public Void visit(MainDeclaration mainDec) {
        checkMain = true;
        mainDec.getBody().accept(this);
        checkMain = false;
        return null;
    }

    @Override
    public Void visit(VariableDeclaration variableDec) {

        Type varNameType = variableDec.getVarType();
        Type valueType = null;

        //check if it's null and then visit with expressionTypeChecker
        if (variableDec.getDefaultValue() != null) {
            valueType = variableDec.getDefaultValue().accept(expressionTypeChecker);
        }

        if (valueType != null && !expressionTypeChecker.checkSpecialTypeEquality(valueType, varNameType)) {
            UnsupportedOperandType error = new UnsupportedOperandType(variableDec.getLine(), "assign");
            variableDec.addError(error);
        }

        //the case in which we're dealing with struct.
        if (varNameType instanceof StructType) {
            try {
                String stName = ((StructType) variableDec.getVarType()).getStructName().getName();
                String stFullName = StructSymbolTableItem.START_KEY + stName;

                SymbolTable.root.getItem(stFullName);
            } catch (ItemNotFoundException e) {
                StructNotDeclared error = new StructNotDeclared(variableDec.getLine(), ((StructType) variableDec.getVarType()).getStructName().getName());
                variableDec.addError(error);
                varNameType = new NoType();
            }
        }
        try {
            VariableSymbolTableItem newVariableSymbolTable = new VariableSymbolTableItem(variableDec.getVarName());
            newVariableSymbolTable.setType(varNameType);
            SymbolTable.top.put(newVariableSymbolTable);
        } catch (ItemAlreadyExistsException e1) {
            try {
                VariableSymbolTableItem variableSymbolTable = (VariableSymbolTableItem) SymbolTable.top.getItem(VariableSymbolTableItem.START_KEY + variableDec.getVarName().getName());
                variableSymbolTable.setType(varNameType);
            } catch (ItemNotFoundException e2) {}
        }
        return  null;
    }

    @Override
    public Void visit(StructDeclaration structDec) {
        currentStruct = findSSTI(structDec.getStructName().getName());
        expressionTypeChecker.setCurrentStruct(currentStruct);
        SymbolTable.push(currentStruct.getStructSymbolTable());
        structDec.getBody().accept(this);
        SymbolTable.pop();
        expressionTypeChecker.setCurrentStruct(currentStruct);
        return null;
    }

    @Override
    public Void visit(SetGetVarDeclaration setGetVarDec) {

        checkSetter = true;

        SymbolTable currentTable = new SymbolTable(SymbolTable.top);
        SymbolTable.push(currentTable);

        for (VariableDeclaration declaration : setGetVarDec.getArgs()) {
            declaration.accept(this);
        }

        setGetVarDec.getSetterBody().accept(this);
        SymbolTable.pop();
        checkSetter = false;

        checkGetter = true;
        globalGetterType = setGetVarDec.getVarType();
        setGetVarDec.getGetterBody().accept(this);
        globalGetterType = null;
        checkGetter = false;

        return null;
    }

    @Override
    public Void visit(AssignmentStmt assignmentStmt) {
        //this part is done based on what we did for binaryExpressions in the other part.
        Expression rxp = assignmentStmt.getRValue();
        Expression lxp = assignmentStmt.getLValue();

        Type rxpt = rxp.accept(expressionTypeChecker);
        Type lxpt = lxp.accept(expressionTypeChecker);

        boolean SA = lxp instanceof StructAccess;
        boolean LAbI = lxp instanceof ListAccessByIndex;
        boolean ID = lxp instanceof Identifier;

        boolean lNT = lxpt instanceof NoType;
        boolean rNT = rxpt instanceof NoType;

        //check unsuitable type for left hand side of the assignment statement
        if(SA || LAbI || ID){
            //check unhandled types for lxp and rxp.
            if(!expressionTypeChecker.checkSpecialTypeEquality(lxpt,rxpt) && !lNT && !rNT){
                UnsupportedOperandType error = new UnsupportedOperandType(lxp.getLine(), BinaryOperator.assign.name());
                assignmentStmt.addError(error);
            }
            else{
                LeftSideNotLvalue error = new LeftSideNotLvalue(assignmentStmt.getLine());
                assignmentStmt.addError(error);
            }
        }
        return null;
    }

    @Override
    public Void visit(BlockStmt blockStmt) {
        for (Statement statement: blockStmt.getStatements())
            statement.accept(this);
        return null;
    }

    @Override
    public Void visit(ConditionalStmt conditionalStmt) {

        //visiting conditionalStmt and accepting it
        Type conditionType = conditionalStmt.getCondition().accept(expressionTypeChecker);

        //checking unhandled errors.
        if(!(conditionType instanceof BoolType || conditionType instanceof NoType)) {
            ConditionNotBool error = new ConditionNotBool(conditionalStmt.getLine());
            conditionalStmt.addError(error);
        }

        //add scope and visit then body
        SymbolTable currentScope = new SymbolTable(SymbolTable.top);
        SymbolTable.push(currentScope);
        conditionalStmt.getThenBody().accept(this);
        SymbolTable.pop();



        //add scope and visit else body
        if(conditionalStmt.getElseBody() != null) {
            SymbolTable outerScope = new SymbolTable(SymbolTable.top);
            SymbolTable.push(outerScope);
            conditionalStmt.getElseBody().accept(this);
            SymbolTable.pop();
        }
        return null;
    }

    @Override
    public Void visit(FunctionCallStmt functionCallStmt) {
        expressionTypeChecker.setfCallStmt(true);
        functionCallStmt.getFunctionCall().accept(expressionTypeChecker);
        expressionTypeChecker.setfCallStmt(false);
        return null;
    }

    @Override
    public Void visit(DisplayStmt displayStmt) {
        Type argType =  displayStmt.getArg().accept(expressionTypeChecker);

        //check valid types.
        boolean nt = argType instanceof NoType;
        boolean lt = argType instanceof ListType;
        boolean bt = argType instanceof BoolType;
        boolean it = argType instanceof IntType;
        if(lt || nt || it || bt) {
            return null;
        }
        else {
            UnsupportedTypeForDisplay error = new UnsupportedTypeForDisplay(displayStmt.getLine());
            displayStmt.addError(error);
        }
        return null;
    }

    @Override
    public Void visit(ReturnStmt returnStmt) {

        Type returnType = returnStmt.getReturnedExpr().accept(expressionTypeChecker);

        if (currentFunction != null) {
            if (!expressionTypeChecker.checkSpecialTypeEquality(currentFunction.getReturnType(), returnType)) {
                ReturnValueNotMatchFunctionReturnType error = new ReturnValueNotMatchFunctionReturnType(returnStmt.getLine());
                returnStmt.addError(error);
            }
        }
        //check if we're in main scope
        if (checkMain) {
            CannotUseReturn error = new CannotUseReturn(returnStmt.getLine());
            returnStmt.addError(error);
        }

        //check the case in which we are in a setter scope
        if (checkSetter){
            CannotUseReturn exception = new CannotUseReturn(returnStmt.getLine());
            returnStmt.addError(exception);
        }

        //check the case in which we are in a getter scope
        if (checkGetter) {
            if (!expressionTypeChecker.checkSpecialTypeEquality(globalGetterType, returnType)) {
                ReturnValueNotMatchFunctionReturnType error = new ReturnValueNotMatchFunctionReturnType(returnStmt.getLine());
                returnStmt.addError(error);
            }
        }
        return null;
    }

    @Override
    public Void visit(LoopStmt loopStmt) {
        Type conditionType = loopStmt.getCondition().accept(expressionTypeChecker);

        //check unhandled types.
        boolean bt = conditionType instanceof BoolType;
        boolean nt = conditionType instanceof NoType;
        if(!(nt || bt)) {
            ConditionNotBool exception = new ConditionNotBool(loopStmt.getLine());
            loopStmt.addError(exception);
        }
        //assign type to symbol table variables
        SymbolTable currentScope = new SymbolTable(SymbolTable.top);
        SymbolTable.push(currentScope);
        loopStmt.getBody().accept(this);
        SymbolTable.pop();
        return null;
    }


    @Override
    public Void visit(VarDecStmt varDecStmt) {

        //we can't have a declaration in any getter or setter
        if (checkSetter || checkGetter) {
            CannotUseDefineVar error = new CannotUseDefineVar(varDecStmt.getLine());
            varDecStmt.addError(error);
        }
        for (VariableDeclaration varDec: varDecStmt.getVars())
            varDec.accept(this);
        return null;
    }

    @Override
    public Void visit(ListAppendStmt listAppendStmt) {
        expressionTypeChecker.setfCallStmt(true);
        listAppendStmt.getListAppendExpr().accept(expressionTypeChecker);
        expressionTypeChecker.setfCallStmt(false);
        return null;
    }

    @Override
    public Void visit(ListSizeStmt listSizeStmt) {
        listSizeStmt.getListSizeExpr().accept(this);
        return null;
    }
}