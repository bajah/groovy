package org.codehaus.groovy.classgen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class InnerClassVisitor extends ClassCodeVisitorSupport implements Opcodes {

    private final CompilationUnit compilationUnit;
    private final SourceUnit sourceUnit;
    private ClassNode classNode;
    
    
    public InnerClassVisitor(CompilationUnit cu, SourceUnit su) {
        compilationUnit = cu;
        sourceUnit = su;
    }
    
    protected SourceUnit getSourceUnit() {
        return sourceUnit;
    }
    
    @Override
    public void visitClass(ClassNode node) {
        this.classNode = node;
        super.visitClass(node);
        if (node.isEnum() || node.isInterface()) return;
        addDispatcherMethods();
        if (!(node instanceof InnerClassNode)) return;
        addDefaultMethods((InnerClassNode)node);
    }
    
    private void addDefaultMethods(InnerClassNode node) {
        if(node.getVariableScope()==null) return;
        
        final String classInternalName = BytecodeHelper.getClassInternalName(node);
        final String outerClassInternalName = BytecodeHelper.getClassInternalName(node.getOuterClass());
                
        // add method dispatcher
        Parameter[] parameters = new Parameter[] {
                new Parameter(ClassHelper.STRING_TYPE, "name"),
                new Parameter(ClassHelper.OBJECT_TYPE, "args")
        };
        MethodNode method = node.addSyntheticMethod(
                "methodMissing", 
                Opcodes.ACC_PUBLIC, 
                ClassHelper.OBJECT_TYPE, 
                parameters, 
                ClassNode.EMPTY_ARRAY, 
                null
        );

        BlockStatement block = new BlockStatement();
        block.addStatement(
                new BytecodeSequence(new BytecodeInstruction() {
                    public void visit(MethodVisitor mv) {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, classInternalName, "this$0", "Ljava/lang/Object;");
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitMethodInsn(INVOKESTATIC, outerClassInternalName, "this$dist$invoke", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;");
                        mv.visitInsn(ARETURN);
                    }
                })
        );
        method.setCode(block);
        
        // add property getter dispatcher
        parameters = new Parameter[] {
                new Parameter(ClassHelper.STRING_TYPE, "name"),
                new Parameter(ClassHelper.OBJECT_TYPE, "val")
        };
        method = node.addSyntheticMethod(
                "propertyMissing", 
                Opcodes.ACC_PUBLIC, 
                ClassHelper.OBJECT_TYPE,
                parameters, 
                ClassNode.EMPTY_ARRAY, 
                null
        );
        
        block = new BlockStatement();
        block.addStatement(
                new BytecodeSequence(new BytecodeInstruction() {
                    public void visit(MethodVisitor mv) {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, classInternalName, "this$0", "Ljava/lang/Object;");
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitMethodInsn(INVOKESTATIC, outerClassInternalName, "this$dist$set", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;");
                        mv.visitInsn(ARETURN);
                    }
                })
        );
        method.setCode(block);
        
        // add property setter dispatcher
        parameters = new Parameter[] {
                new Parameter(ClassHelper.STRING_TYPE, "name")
        };
        method = node.addSyntheticMethod(
                "propertyMissing", 
                Opcodes.ACC_PUBLIC, 
                ClassHelper.VOID_TYPE, 
                parameters, 
                ClassNode.EMPTY_ARRAY, 
                null
        );
        
        block = new BlockStatement();
        block.addStatement(
                new BytecodeSequence(new BytecodeInstruction() {
                    public void visit(MethodVisitor mv) {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, classInternalName, "this$0", "Ljava/lang/Object;");
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKESTATIC, outerClassInternalName, "this$dist$get", "(Ljava/lang/Object;Ljava/lang/String;)V");
                        mv.visitInsn(RETURN);
                    }
                })
        );
        method.setCode(block);
    }

    @Override
    public void visitConstructorCallExpression(ConstructorCallExpression call) {
        super.visitConstructorCallExpression(call);
        if (!call.isUsingAnnonymousInnerClass()) return;
        
        InnerClassNode innerClass = (InnerClassNode) call.getType();
        if (!innerClass.getDeclaredConstructors().isEmpty()) return;
        if ((innerClass.getModifiers() & Opcodes.ACC_STATIC)!=0) return;
        
        VariableScope scope = innerClass.getVariableScope();
        if (scope==null || scope.getReferencedLocalVariablesCount()==0) return;
        
        // expressions = constructor call arguments
        List<Expression> expressions = ((TupleExpression) call.getArguments()).getExpressions();
        // block = init code for the constructor we produce
        BlockStatement block = new BlockStatement();
        // parameters = parameters of the constructor
        List parameters = new ArrayList(expressions.size()+1+scope.getReferencedLocalVariablesCount());
        // superCallArguments = arguments for the super call == the constructor call arguments
        List superCallArguments = new ArrayList(expressions.size());
        
        // first we add a super() call for all expressions given in the 
        // constructor call expression
        int pCount = 0;
        for (Expression expr : expressions) {
            pCount++;
            // add one parameter for each expression in the 
            // constructor call
            Parameter param = new Parameter(ClassHelper.OBJECT_TYPE,"p"+pCount);
            parameters.add(param);
            // add to super call
            superCallArguments.add(new VariableExpression(param));
        }
        
        // add the super call
        ConstructorCallExpression cce = new ConstructorCallExpression(
                ClassNode.SUPER,
                new TupleExpression(superCallArguments)
        );
        block.addStatement(new ExpressionStatement(cce));
        
        // we need to add "this" to access unknown methods/properties
        // this is saved in a field named this$0
        expressions.add(VariableExpression.THIS_EXPRESSION);
        pCount++;
        Parameter thisParameter = new Parameter(classNode,"p"+pCount);
        parameters.add(thisParameter);
        int privateSynthetic = Opcodes.ACC_PRIVATE+Opcodes.ACC_SYNTHETIC;
        FieldNode thisField = innerClass.addField("this$0", privateSynthetic, ClassHelper.OBJECT_TYPE, null);
        addFieldInit(thisParameter,thisField,block,false);

        // for each shared variable we add a reference and save it as field
        for (Iterator it=scope.getReferencedLocalVariablesIterator(); it.hasNext();) {
            pCount++;
            org.codehaus.groovy.ast.Variable var = (org.codehaus.groovy.ast.Variable) it.next();
            VariableExpression ve = new VariableExpression(var);
            ve.setClosureSharedVariable(true);
            ve.setUseReferenceDirectly(true);
            expressions.add(ve);
            
            Parameter p = new Parameter(ClassHelper.REFERENCE_TYPE,"p"+pCount);
            //p.setClosureSharedVariable(true);
            parameters.add(p);
            FieldNode pField = innerClass.addField(ve.getName(), privateSynthetic, ClassHelper.REFERENCE_TYPE, null);
            pField.setHolder(true);
            addFieldInit(p,pField,block,true);            
        }
        
        innerClass.addConstructor(ACC_PUBLIC, (Parameter[]) parameters.toArray(new Parameter[0]), ClassNode.EMPTY_ARRAY, block);
        
    }
    
    private void addDispatcherMethods() {
        // since we added an anonymous inner class we should also
        // add the dispatcher methods
        
        // add method dispatcher
        Parameter[] parameters = new Parameter[] {
                new Parameter(ClassHelper.OBJECT_TYPE, "receiver"),
                new Parameter(ClassHelper.STRING_TYPE, "name"),
                new Parameter(ClassHelper.OBJECT_TYPE, "args")
        };
        MethodNode method = classNode.addSyntheticMethod(
                "this$dist$invoke", 
                ACC_PUBLIC+ACC_BRIDGE+ACC_SYNTHETIC+ACC_STATIC, 
                ClassHelper.OBJECT_TYPE, 
                parameters, 
                ClassNode.EMPTY_ARRAY, 
                null
        );

        BlockStatement block = new BlockStatement();
        List gStringStrings = new ArrayList();
        gStringStrings.add(new ConstantExpression(""));
        gStringStrings.add(new ConstantExpression(""));
        List gStringValues = new ArrayList();
        gStringValues.add(new VariableExpression(parameters[1]));
        block.addStatement(
                new ReturnStatement(
                        new MethodCallExpression(
                               new VariableExpression(parameters[0]),
                               new GStringExpression("$name",
                                       gStringStrings,
                                       gStringValues
                               ),
                               new ArgumentListExpression(
                                       new SpreadExpression(new VariableExpression(parameters[2]))
                               )
                        )
                )
        );
        method.setCode(block);
        
        // add property setter
        parameters = new Parameter[] {
                new Parameter(ClassHelper.OBJECT_TYPE, "receiver"),
                new Parameter(ClassHelper.STRING_TYPE, "name"),
                new Parameter(ClassHelper.OBJECT_TYPE, "value")
        };
        method = classNode.addSyntheticMethod(
                "this$dist$set", 
                ACC_PUBLIC+ACC_BRIDGE+ACC_SYNTHETIC+ACC_STATIC, 
                ClassHelper.VOID_TYPE, 
                parameters, 
                ClassNode.EMPTY_ARRAY, 
                null
        );

        block = new BlockStatement();
        gStringStrings = new ArrayList();
        gStringStrings.add(new ConstantExpression(""));
        gStringStrings.add(new ConstantExpression(""));
        gStringValues = new ArrayList();
        gStringValues.add(new VariableExpression(parameters[1]));
        block.addStatement(
                new ExpressionStatement(
                        new BinaryExpression(
                                new AttributeExpression(
                                        new VariableExpression(parameters[0]),
                                        new GStringExpression("$name",
                                                gStringStrings,
                                                gStringValues
                                        )
                                ),
                                Token.newSymbol(Types.ASSIGN, -1, -1),
                                new VariableExpression(parameters[2])
                        )
                )
        );
        method.setCode(block);

        // add property getter
        parameters = new Parameter[] {
                new Parameter(ClassHelper.OBJECT_TYPE, "receiver"),
                new Parameter(ClassHelper.STRING_TYPE, "name")
        };
        method = classNode.addSyntheticMethod(
                "this$dist$get", 
                ACC_PUBLIC+ACC_BRIDGE+ACC_SYNTHETIC+ACC_STATIC, 
                ClassHelper.OBJECT_TYPE, 
                parameters, 
                ClassNode.EMPTY_ARRAY, 
                null
        );

        block = new BlockStatement();
        gStringStrings = new ArrayList();
        gStringStrings.add(new ConstantExpression(""));
        gStringStrings.add(new ConstantExpression(""));
        gStringValues = new ArrayList();
        gStringValues.add(new VariableExpression(parameters[1]));
        block.addStatement(
                new ReturnStatement(
                        new AttributeExpression(
                                new VariableExpression(parameters[0]),
                                new GStringExpression("$name",
                                        gStringStrings,
                                        gStringValues
                                )
                        )
                )
        );
        method.setCode(block);
    }

    private static void addFieldInit(Parameter p, FieldNode fn, BlockStatement block, boolean ref) {
        VariableExpression ve = new VariableExpression(p);
        ve.setUseReferenceDirectly(ref);
        FieldExpression fe = new FieldExpression(fn);
        fe.setUseReferenceDirectly(ref);
        block.addStatement(new ExpressionStatement(
                new BinaryExpression(
                        fe,
                        Token.newSymbol(Types.ASSIGN, -1, -1),
                        ve
                )
        ));
    }
    
}
