/*
 * Copyright 2003-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.tools.groovydoc;

import org.codehaus.groovy.groovydoc.*;
import org.codehaus.groovy.ant.Groovydoc;

import java.util.*;

public class SimpleGroovyClassDoc extends SimpleGroovyProgramElementDoc implements GroovyClassDoc {
    private final List<GroovyConstructorDoc> constructors;
    private final List<GroovyFieldDoc> fields;
    private final List<GroovyFieldDoc> enumConstants;
    private final List<GroovyMethodDoc> methods;
    private final List<String> importedClassesAndPackages;

    private String fullPathName;
    private String superClassName;
    private GroovyClassDoc superClass;

    public SimpleGroovyClassDoc(List<String> importedClassesAndPackages, String name, List<Groovydoc.LinkArgument> links) {
        super(name, links);
        this.importedClassesAndPackages = importedClassesAndPackages;
        constructors = new ArrayList<GroovyConstructorDoc>();
        fields = new ArrayList<GroovyFieldDoc>();
        enumConstants = new ArrayList<GroovyFieldDoc>();
        methods = new ArrayList<GroovyMethodDoc>();
    }

    public SimpleGroovyClassDoc(List<String> importedClassesAndPackages, String name) {
        this(importedClassesAndPackages, name, new ArrayList<Groovydoc.LinkArgument>());
    }

    /**
     * returns a sorted array of constructors
     */
    public GroovyConstructorDoc[] constructors() {
        Collections.sort(constructors);
        return constructors.toArray(new GroovyConstructorDoc[constructors.size()]);
    }

    public boolean add(GroovyConstructorDoc constructor) {
        return constructors.add(constructor);
    }

    /**
     * returns a sorted array of fields
     */
    public GroovyFieldDoc[] fields() {
        Collections.sort(fields);
        return fields.toArray(new GroovyFieldDoc[fields.size()]);
    }

    public boolean add(GroovyFieldDoc field) {
        return fields.add(field);
    }

    /**
     * returns a sorted array of enum constants
     */
    public GroovyFieldDoc[] enumConstants() {
        Collections.sort(enumConstants);
        return enumConstants.toArray(new GroovyFieldDoc[enumConstants.size()]);
    }

    public boolean addEnumConstant(GroovyFieldDoc field) {
        return enumConstants.add(field);
    }

    /**
     * returns a sorted array of methods
     */
    public GroovyMethodDoc[] methods() {
        Collections.sort(methods);
        return methods.toArray(new GroovyMethodDoc[methods.size()]);
    }

    public boolean add(GroovyMethodDoc method) {
        return methods.add(method);
    }

    public String getSuperClassName() {
        return superClassName;
    }

    public void setSuperClassName(String className) {
        superClassName = className;
    }

    public GroovyClassDoc superclass() {
        return superClass;
    }

    public String getFullPathName() {
        return fullPathName;
    }

    public void setFullPathName(String fullPathName) {
        this.fullPathName = fullPathName;
    }

    public String getRelativeRootPath() {
        StringTokenizer tokenizer = new StringTokenizer(fullPathName, "/"); // todo windows??
        StringBuffer sb = new StringBuffer();
        if (tokenizer.hasMoreTokens()) {
            tokenizer.nextToken(); // ignore the first token, as we want n-1 parent dirs
        }
        while (tokenizer.hasMoreTokens()) {
            tokenizer.nextToken();
            sb.append("../");
        }
        return sb.toString();
    }

    public List<String> getParentClasses() {
        List<String> result = new LinkedList<String>();
        result.add(0, name());
        SimpleGroovyClassDoc next = this;
        while (next.superclass() != null) {
            next = (SimpleGroovyClassDoc) next.superclass();
            result.add(0, next.name().replaceAll("/", "."));
        }
        if ("Object".equals(next.name())) {
            return result;
        }
        Class nextClass = getClassOf(next.name());
        while (nextClass != null && nextClass.getSuperclass() != null && !Object.class.equals(nextClass)) {
            nextClass = nextClass.getSuperclass();
            result.add(0, nextClass.getName());
        }
        return result;
    }

    private Class getClassOf(String next) {
        try {
            return Class.forName(next.replaceAll("/", "."));
        } catch (Throwable t) {
            return null;
        }
    }

    void resolve(GroovyRootDoc rootDoc) {
        Map visibleClasses = rootDoc.getVisibleClasses(importedClassesAndPackages);

        // resolve constructor parameter types
        for (GroovyConstructorDoc constructor : constructors) {

            // parameters
            for (GroovyParameter groovyParameter : Arrays.asList(constructor.parameters())) {
                SimpleGroovyParameter param = (SimpleGroovyParameter) groovyParameter;
                String paramTypeName = param.typeName();
                if (visibleClasses.containsKey(paramTypeName)) {
                    param.setType((GroovyType) visibleClasses.get(paramTypeName));
                }
            }
        }

        // resolve method return types and parameter types
        for (GroovyMethodDoc method : methods) {

            // return types
            GroovyType returnType = method.returnType();
            String typeName = returnType.typeName();
            if (visibleClasses.containsKey(typeName)) {
                method.setReturnType((GroovyType) visibleClasses.get(typeName));
            }

            // parameters
            for (GroovyParameter groovyParameter : Arrays.asList(method.parameters())) {
                SimpleGroovyParameter param = (SimpleGroovyParameter) groovyParameter;
                String paramTypeName = param.typeName();
                if (visibleClasses.containsKey(paramTypeName)) {
                    param.setType((GroovyType) visibleClasses.get(paramTypeName));
                }
            }
        }

        //resolve type references
        if (superClassName != null) {
            superClass = rootDoc.classNamed(superClassName); // todo - take package names into account ?!
            if (superClass == null) {
                // The superClass is not in the tree being documented
                superClass = new SimpleGroovyClassDoc(null, superClassName); // dummy class with name, not to be put into main tree
            }
        } else {
            superClass = new SimpleGroovyClassDoc(null, "Object"); // dummy class representing java.lang.Object, not to be put into main tree
        }
    }
    // methods from GroovyClassDoc


    public GroovyConstructorDoc[] constructors(boolean filter) {/*todo*/
        return null;
    }

    public boolean definesSerializableFields() {/*todo*/
        return false;
    }

    public GroovyFieldDoc[] fields(boolean filter) {/*todo*/
        return null;
    }

    public GroovyClassDoc findClass(String className) {/*todo*/
        return null;
    }

    public GroovyClassDoc[] importedClasses() {/*todo*/
        return null;
    }

    public GroovyPackageDoc[] importedPackages() {/*todo*/
        return null;
    }

    public GroovyClassDoc[] innerClasses() {/*todo*/
        return null;
    } // not supported in groovy

    public GroovyClassDoc[] innerClasses(boolean filter) {/*todo*/
        return null;
    } // not supported in groovy

    public GroovyClassDoc[] interfaces() {/*todo*/
        return null;
    }

    public GroovyType[] interfaceTypes() {/*todo*/
        return null;
    }

    public boolean isAbstract() {/*todo*/
        return false;
    }

    public boolean isExternalizable() {/*todo*/
        return false;
    }

    public boolean isSerializable() {/*todo*/
        return false;
    }

    public GroovyMethodDoc[] methods(boolean filter) {/*todo*/
        return null;
    }

    public GroovyFieldDoc[] serializableFields() {/*todo*/
        return null;
    }

    public GroovyMethodDoc[] serializationMethods() {/*todo*/
        return null;
    }

    public boolean subclassOf(GroovyClassDoc gcd) {/*todo*/
        return false;
    }

    public GroovyType superclassType() {/*todo*/
        return null;
    }
//	public GroovyTypeVariable[] typeParameters() {/*todo*/return null;} // not supported in groovy
//	public GroovyParamTag[] typeParamTags() {/*todo*/return null;} // not supported in groovy


    // methods from GroovyType (todo: remove this horrible copy of SimpleGroovyType.java)

    //	public GroovyAnnotationTypeDoc asAnnotationTypeDoc() {/*todo*/return null;}
    public GroovyClassDoc asClassDoc() {/*todo*/
        return null;
    }
//	public GroovyParameterizedType asParameterizedType() {/*todo*/return null;}
//	public GroovyTypeVariable asTypeVariable() {/*todo*/return null;}

    //	public GroovyWildcardType asWildcardType() {/*todo*/return null;}
    public String dimension() {/*todo*/
        return null;
    }

    public boolean isPrimitive() {/*todo*/
        return false;
    }

    public String qualifiedTypeName() {/*todo*/
        return null;
    }

    public String simpleTypeName() {/*todo*/
        return null;
    }

    public String typeName() {/*todo*/
        return null;
    }

    // ----
    public String fullDottedName() {
        return fullPathName.replaceAll("/", ".");
    }

}
