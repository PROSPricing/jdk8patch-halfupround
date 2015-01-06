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

import static com.pros.java.text.DigitListPatch.ASM_VERSION;
import static org.junit.Assert.*;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

/**
 * Validates assumptions about how switch labels behave for two labels that are implemented
 * by exactly the same code block.
 *
 * @author William Price
 */
public class EnumSwitchCommonCaseBranchLabelOffsetTest
{
    static enum Symbols { A, B, ONE }

    private String methodContainingSwitch(Symbols symbol)
    {
        switch (symbol)
        {
            case A:
            case B:
                return "It's a letter!";
            case ONE:
                return "One of these things is not like the others.";
        }
        throw new IllegalArgumentException();
    }

    @Test
    public void commonBranchForTwoLabels()
    throws Exception
    {
        final Label[] capturedLabels = new Label[Symbols.values().length];
        class TestMethodAdapter extends MethodVisitor
        {
            TestMethodAdapter(MethodVisitor mv)
            {
                super(ASM_VERSION, mv);
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels)
            {
                capturedLabels[Symbols.A.ordinal()] = labels[Symbols.A.ordinal()];
                capturedLabels[Symbols.B.ordinal()] = labels[Symbols.B.ordinal()];
                capturedLabels[Symbols.ONE.ordinal()] = labels[Symbols.ONE.ordinal()];
                super.visitTableSwitchInsn(min, max, dflt, labels);
            }
        }

        ClassVisitor adapter = new ClassVisitor(ASM_VERSION, new ClassWriter(/* flags */ 0))
        {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String desc, String signature, String[] exceptions)
            {
                MethodVisitor writerVisitor =
                    super.visitMethod(access, name, desc, signature, exceptions);
                if ("methodContainingSwitch".equals(name))
                {
                    return new TestMethodAdapter(writerVisitor);
                }
                return writerVisitor;
            }
        };

        byte[] ownBytecode = DigitListPatch.extractBytecode(getClass());
        new ClassReader(ownBytecode).accept(adapter, /* flags */ 0);

        assertNotNull("label A", capturedLabels[Symbols.A.ordinal()]);
        assertNotNull("label B", capturedLabels[Symbols.B.ordinal()]);
        assertNotNull("label ONE", capturedLabels[Symbols.ONE.ordinal()]);

        int offsetLabelA = capturedLabels[Symbols.A.ordinal()].getOffset();
        int offsetLabelB = capturedLabels[Symbols.B.ordinal()].getOffset();
        int offsetLabelONE = capturedLabels[Symbols.ONE.ordinal()].getOffset();
        assertEquals("A and B offests", offsetLabelA, offsetLabelB);
        assertFalse("A and ONE offsets", offsetLabelA == offsetLabelONE);
        assertFalse("B and ONE offsets", offsetLabelB == offsetLabelONE);
    }
}
