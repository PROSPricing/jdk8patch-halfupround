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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.math.RoundingMode;
import java.security.ProtectionDomain;
import java.util.Arrays;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * A Java Agent-implemented driver and related inner classes that will patch the bytecode
 * of the {@code java.text.DigitList} class.  The driver conditionally applies the patch
 * by identifying an internal method signature that changed in the same JDK release that
 * introduced the bug; previous versions not requiring the patch should be unaffected.
 * <p>
 * The implementation of the patch <em>hopes</em> to be future-proof, but <strong>THERE IS
 * RISK</strong> of incorrect behavior in <em>future</em> releases of Java; it will depend
 * on how Oracle chooses to implement an eventual fix.
 * </p>
 *
 * @author William Price
 */
public final class DigitListPatch implements ClassFileTransformer
{
    /** Will be {@code true} if this driver was initialized as an Agent by the JVM bootstrapping. */
    static volatile boolean installed;

    /** Will be {@code true} if this driver changed the bytecode of the target class. */
    static volatile boolean applied;

    private static final String TARGET_CLASS_INTERNAL_NAME = "java/text/DigitList";
    private static final String PATCH_METHOD_NAME = "__patched__shouldRoundUp_HALF_UP";

    private DigitListPatch()
    {
        // nop
    }

    // Self-test entry point
    public static void main(String ... args)
    {
        new SelfTest().run();
    }

    // Java Agent entry point
    public static void premain(String agentArgs, Instrumentation inst)
    {
        inst.addTransformer(new DigitListPatch());
        installed = true;
    }

    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBytes)
    throws IllegalClassFormatException
    {
        if (!TARGET_CLASS_INTERNAL_NAME.equals(className))
        {
            return null; // no transformation to be done
        }
        ClassWriter writer = new ClassWriter(/* flags */ 0);
        ClassVisitor visitor = new TargetClassAdapter(writer);
        try
        {
            new ClassReader(classfileBytes).accept(visitor, /* flags */ 0);
            return writer.toByteArray();
        }
        catch (Exception e)
        {
            System.err.println("Failed to patch " + TARGET_CLASS_INTERNAL_NAME);
            System.err.println(e);
            return null; // make no changes
        }
    }

    /**
     * This adapter should only be applied to the target class; it searches for
     * the method signature that (1) indicates that the bug may be present and
     * (2) contains the faulty code.
     */
    private static class TargetClassAdapter extends ClassAdapter
    {
        TargetClassAdapter(ClassVisitor cv)
        {
            super(cv);
        }

        @Override
        public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions)
        {
            MethodVisitor writerVisitor =
                super.visitMethod(access, name, desc, signature, exceptions);

            if ("shouldRoundUp".equals(name)
                && Type.BOOLEAN_TYPE.equals(Type.getReturnType(desc)))
            {
                Type[] args = Type.getArgumentTypes(desc);
                Type[] target = { Type.INT_TYPE, Type.BOOLEAN_TYPE, Type.BOOLEAN_TYPE };
                if (Arrays.equals(target, args))
                {
                    return new TargetMethodAdapter(this, writerVisitor);
                }
            }
            return writerVisitor;
        }
    }

    /**
     * This adapter should only be applied to the method containing the faulty code,
     * which is conveniently preceeded immediately by a specific switch case label.
     * The faulty bytecode is bypassed by injecting a call to an alternate implementation.
     */
    private static class TargetMethodAdapter extends MethodAdapter implements Opcodes
    {
        private final ClassVisitor cv;
        private Label halfUpSwitchCaseLabel;

        TargetMethodAdapter(ClassVisitor classVisitor, MethodVisitor mv)
        {
            super(mv);
            cv = classVisitor;
        }

        // Thankfully, there is only one switch block in the target method!
        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label[] labels)
        {
            int targetRoundingModeOrdinal = RoundingMode.HALF_UP.ordinal();
            if (labels.length <= RoundingMode.values().length
                && labels.length > targetRoundingModeOrdinal)
            {
                // Switches on Enum types should use the enum constant's ordinal() value
                // as the label index inside the TABLESWITCH
                halfUpSwitchCaseLabel = labels[targetRoundingModeOrdinal];
            }
            // else: bad assumption, and cannot patch!
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] ints, Label[] labels)
        {
            //super.visitLookupSwitchInsn(dflt, ints, labels);
            throw new UnsupportedOperationException(
                "Expected switch() case as a TABLESWITCH; was LOOKUPSWITCH");
        }

        // At some point after visiting the TABLESWITCH, we'll encounter the case labels
        @Override
        public void visitLabel(Label label)
        {
            mv.visitLabel(label);
            if (label.equals(halfUpSwitchCaseLabel))
            {
                halfUpSwitchCaseLabel = null; // avoid accidentally doing this more than once

                // Inject call to and return value from the fix method, first.
                // The existing byte code will follow (but should be unreachable).
                redirectCaseToInvokePatchMethod();
            }
        }

        /* case HALF_UP:
         *
         *   // INSERTED PATCH POINT:
         *   return __patched__shouldRoundUp_HALF_UP(maximumDigits, alreadyRounded, allDecimalDigits);
         *
         *   // (existing bytecode continues, remainder of case should be unreachable)
         *   break;
         */
        private void redirectCaseToInvokePatchMethod()
        {
            String patchMethodDesc;
            try
            {
                Method patchMethod = DigitList.class.getDeclaredMethod(
                    PATCH_METHOD_NAME, Integer.TYPE, Boolean.TYPE, Boolean.TYPE);
                patchMethodDesc = Type.getMethodDescriptor(patchMethod);
            }
            catch (NoSuchMethodException oops)
            {
                throw new IllegalStateException(oops);
            }
            applied = true; // volatile write

            // target object reference for INVOKEVIRTUAL
            mv.visitIntInsn(ALOAD, 0); // 'this' (java.text.DigitList)

            // target method arg 0: maximumDigits (int)
            mv.visitIntInsn(ILOAD, 1); // calling method's argument 0

            // target method arg 1: alreadyRounded (boolean, represented as int)
            mv.visitIntInsn(ILOAD, 2); // calling method's argument 1

            // target method arg 2: allDecimalDigits (boolean, represented as int)
            mv.visitIntInsn(ILOAD, 3); // calling method's argument 2

            mv.visitMethodInsn(
                INVOKEVIRTUAL, TARGET_CLASS_INTERNAL_NAME, PATCH_METHOD_NAME, patchMethodDesc);
            mv.visitInsn(IRETURN);
        }

        // Once we finish patching the buggy method with our extra method call, we need to add
        // the method that will be called.  The method needs to be inserted directly into the
        // bytecode of the patched class because JDK API classes are loaded by the boot classloader
        // and won't (by default) be able to see any third-party classes (like this one).
        @Override
        public void visitEnd()
        {
            super.visitEnd(); // current method

            if (applied) // volatile read
            {
                // Need to create the NEW method that is called by the redirected case block,
                // mirroring the patch implementation inside our template.
                new ClassReader(extractClassBytecode(DigitList.class))
                    .accept(new TemplateClassAdapter(cv), /* flags */ 0);
            }
        }
    }

    /**
     * This adapter should be applied by visiting the shim/template class, while it writes
     * to the patch target class.  This way, parts of the shim can be transferred directly
     * into the target bytecode without having to write that bytecode by hand.  (The shim
     * was compiled into bytecode by {@code javac}, which is a lot better than I am.)
     * <p>
     * For this patch, ignore everything (the default behavior of {@link AbstractClassVisitor})
     * except the shim method.  Specifically, we <em>don't want</em> fields declared in the shim
     * because those fields (in this case) are already present in the original JDK class.
     */
    private static class TemplateClassAdapter extends AbstractClassVisitor
    {
        ClassVisitor outputTarget;

        TemplateClassAdapter(ClassVisitor target)
        {
            outputTarget = target;
        }

        @Override
        public MethodVisitor visitMethod(
            int access, String name, String desc, String signature, String[] exceptions)
        {
            if (PATCH_METHOD_NAME.equals(name))
            {
                return new TemplateMethodAdapter(
                    outputTarget.visitMethod(access, name, desc, signature, exceptions));
            }
            return null; // ignore anything else
        }
    }

    /**
     * Mirrors the template method, except all references to the template class
     * will be replaced with references to the target class.
     */
    private static class TemplateMethodAdapter extends MethodAdapter
    {
        private static final String TEMPLATE_CLASS_INTERNAL_NAME =
            Type.getInternalName(DigitList.class);

        TemplateMethodAdapter(MethodVisitor mv)
        {
            super(mv);
        }

        private String masquerade(String owner)
        {
            if (TEMPLATE_CLASS_INTERNAL_NAME.equals(owner))
            {
                return TARGET_CLASS_INTERNAL_NAME;
            }
            return owner;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc)
        {
            super.visitFieldInsn(opcode, masquerade(owner), name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc)
        {
            super.visitFieldInsn(opcode, masquerade(owner), name, desc);
        }

        @Override
        public void visitTypeInsn(int opcode, String type)
        {
            super.visitTypeInsn(opcode, masquerade(type));
        }

        @Override
        public void visitLineNumber(int line, Label start)
        {
            // nop: not reporting line numbers as they wouldn't make any sense
        }
    }

    // Helper for loading the bytecode of the template/shim
    private static byte[] extractClassBytecode(Class<?> clazz)
    {
        InputStream inStream = null;
        try
        {
            String resourceName = Type.getInternalName(clazz) + ".class";
            inStream = clazz.getClassLoader().getResourceAsStream(resourceName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buff = new byte[1024];
            int bytesRead;
            while ((bytesRead = inStream.read(buff)) > -1)
            {
                baos.write(buff, 0, bytesRead);
            }
            baos.flush();
            baos.close();
            return baos.toByteArray();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Could not retrieve bytecode for: " + clazz);
        }
        finally
        {
            if (inStream != null)
            {
                try
                {
                    inStream.close();
                }
                catch (IOException ioe)
                {
                    // ignore
                }
            }
        }
    }
}
