/*
 * Copyright (c) 2014 by PROS, Inc.  All Rights Reserved.
 * This software is the confidential and proprietary information of
 * PROS, Inc. ("Confidential Information").
 * You may not disclose such Confidential Information, and may only
 * use such Confidential Information in accordance with the terms of
 * the license agreement you entered into with PROS.
 */

package com.pros.java.text;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

/**
 * A {@code ClassVisitor} that does nothing by default.
 * Extend this class and override methods as needed.
 *
 * @author William Price
 */
abstract class AbstractClassVisitor implements ClassVisitor
{
    @Override
    public void visit(int i, int i1, String s, String s2, String s3, String[] strings)
    {
    }

    @Override
    public void visitSource(String string, String string1)
    {
    }

    @Override
    public void visitOuterClass(String string, String string1, String string2)
    {
    }

    @Override
    public AnnotationVisitor visitAnnotation(String string, boolean bln)
    {
        return null;
    }

    @Override
    public void visitAttribute(Attribute atrbt)
    {
    }

    @Override
    public void visitInnerClass(String string, String string1, String string2, int i)
    {
    }

    @Override
    public FieldVisitor visitField(int i, String s1, String s2, String s3,  Object o)
    {
        return null;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String desc, String signature, String[] exceptions)
    {
        return null;
    }

    @Override
    public void visitEnd()
    {
    }
}
