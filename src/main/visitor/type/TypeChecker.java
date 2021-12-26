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
    ExpressionTypeChecker expressionTypeChecker;
    private FunctionSymbolTableItem currentFunction;
    private StructSymbolTableItem currentStruct;
    private boolean checkMain;

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


    public void TypeChecker(){
        this.expressionTypeChecker = new ExpressionTypeChecker();
    }

    @Override
    public Void visit(Program program) {

        program.getMain().accept(this);

        for (StructDeclaration structDec: program.getStructs())
            structDec.accept(this);

        for (FunctionDeclaration funcDec: program.getFunctions())
            funcDec.accept(this);

        return null;
    }

    @Override
    public Void visit(FunctionDeclaration functionDec) {

        currentFunction = findFSTI(functionDec.getFunctionName().getName());
        SymbolTable symbolTable = new SymbolTable(SymbolTable.root);
        currentFunction.setFunctionSymbolTable(symbolTable);
        expressionTypeChecker.setCurrentFunction(currentFunction);
        functionDec.getBody().accept(this);
        return null;
    }

    @Override
    public Void visit(MainDeclaration mainDec) {
        mainDec.getBody().accept(this);
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
        structDec.getBody().accept(this);
        return null;
    }

    @Override
    public Void visit(SetGetVarDeclaration setGetVarDec) {
        //Todo
        return null;
    }

    @Override
    public Void visit(AssignmentStmt assignmentStmt) {
        //Todo
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
        //Todo
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
        return null;
    }

    @Override
    public Void visit(LoopStmt loopStmt) {
        Type conditionType = loopStmt.getCondition().accept(expressionTypeChecker);

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