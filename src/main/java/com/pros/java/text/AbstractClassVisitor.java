/*
 * Copyright (c) 2014 by PROS, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * Linking this library statically or dynamically with other modules is making
 * a combined work based on this library.  Thus, the terms and conditions of
 * the GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module.  An independent
 * module is a module which is not derived from or based on this library.  If
 * you modify this library, you may extend this exception to your version of
 * the library, but you are not obligated to do so.  If you do not wish to do
 * so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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
