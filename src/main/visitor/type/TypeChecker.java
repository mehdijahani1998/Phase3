package main.visitor.type;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.*;
import main.ast.nodes.declaration.struct.*;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.statement.*;
import main.ast.types.FptrType;
import main.ast.types.NoType;
import main.ast.types.StructType;
import main.ast.types.Type;
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
        //Todo
        return null;
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
        //Todo
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
        for (Statement stmt: blockStmt.getStatements())
            stmt.accept(this);
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

        //visit then body
        conditionalStmt.getThenBody().accept(this);

        //visit else body
        if(conditionalStmt.getElseBody() != null) {
            conditionalStmt.getElseBody().accept(this);
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
        if(argType instanceof BoolType || argType instanceof IntType) {
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

        //check the case in which we are in a getter scope
        if (checkGetter) {
            if (!expressionTypeChecker.checkSpecialTypeEquality(globalGetterType, returnType)) {
                ReturnValueNotMatchFunctionReturnType error = new ReturnValueNotMatchFunctionReturnType(returnStmt.getLine());
                returnStmt.addError(error);
            }
        }

        //check the case in which we are in a setter scope
        if (checkSetter){
            CannotUseReturn exception = new CannotUseReturn(returnStmt.getLine());
            returnStmt.addError(exception);
        }
        return null;
    }

    @Override
    public Void visit(LoopStmt loopStmt) {
        Type conditionType = loopStmt.getCondition().accept(expressionTypeChecker);

        //check unhandled types.
        if(!(conditionType instanceof BoolType || conditionType instanceof NoType)) {
            ConditionNotBool exception = new ConditionNotBool(loopStmt.getLine());
            loopStmt.addError(exception);
        }

        loopStmt.getBody().accept(this);
        return null;
    }


    @Override
    public Void visit(VarDecStmt varDecStmt) {
        for (VariableDeclaration varDec: varDecStmt.getVars())
            varDec.accept(this);
        return null;
    }

    @Override
    public Void visit(ListAppendStmt listAppendStmt) {
        listAppendStmt.getListAppendExpr().accept(expressionTypeChecker);
        return null;
    }

    @Override
    public Void visit(ListSizeStmt listSizeStmt) {
        listSizeStmt.getListSizeExpr().accept(this);
        return null;
    }
}