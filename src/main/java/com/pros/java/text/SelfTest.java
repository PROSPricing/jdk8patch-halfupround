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

import static java.lang.System.out;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Executes a suite of rounding test cases and reports one of several outcomes based
 * on the result of the tests and the state of the JVM/agent/patch.  The patch and the test
 * should be compatible with the Oracle JVM version range [1.6, 1.8] (inclusive).
 * Compatibility with unreleased 1.9+ (and any official fixes for this issue in 1.8.x) is unknown.
 * <p>
 * Run the test without the agent to verify the behavior of a virgin JVM.
 * Run the test with the agent installed (using {@code -javaagent}) to verify the patch behavior.
 *
 * @author William Price
 */
final class SelfTest implements Runnable
{
    @Override
    public void run()
    {
        out.printf("%nPATCH SELF TEST:%n"
            + "  java.text.DigitList.shouldRoundUp(int,boolean,boolean)%n"
            + "  HALF_UP case%n");

        // Tests must run BEFORE DigitListPatch.applied can be trusted
        boolean behaviorOK =
            stackoverflow24426438()
            & stackoverflow24426438answer24426907()
            & jdk8041961()
            & jdk8039915();

        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        out.printf("%nAbove tests used Java %s (%s)%n", javaVersion, javaVendor);
        out.printf("installed at %s%n", System.getProperty("java.home"));

        out.printf("%nAgent installed: %s%nPatch applied  : %s%n%nOverall result : ",
            DigitListPatch.installed ? "yes" : "NO (missing -javaagent?)",
            DigitListPatch.applied ? "yes" : "NO");

        int resultCode = (behaviorOK ? 0x1 : 0)
            | (DigitListPatch.applied ? 0x2 : 0)
            | (isJava8orNewer(javaVersion, javaVendor) ? 0x4 : 0);
        String result;
        switch (resultCode)
        {
            case 1: // pass, !applied, <java8
            case 5: // pass, !applied, >=java8
                result = "OK (no patch necessary)";
                break;
            case 3: // pass, applied, <java8
                result = "FIXED (WARNING: unexpected Java version/vendor)";
                break;
            case 7: // pass, applied, >=java8
                result = "FIXED";
                break;
            case 2: // failed, applied, <java8
            case 6: // failed, applied, >=java8
                result = "BAD PATCH (!) on " + javaVendor + " Java " + javaVersion;
                break;
            case 4: // failed, !applied, >=java8
                result = DigitListPatch.installed
                    ? "NOT FIXED (patch not supported on this Java version/vendor?)"
                    : "NOT FIXED (expected on Java 1.8)";
                break;
            default: // failed, !applied, <java8
                result = "BAD (maybe a different bug in this version of Java?)";
        }
        out.println(result);
    }

    boolean isJava8orNewer(String version, String vendor)
    {
        if (System.getProperty("java.vendor").toLowerCase(Locale.US).contains("oracle"))
        {
            String[] versionParts = version.split("\\D");
            try
            {
                return versionParts.length >= 2
                    && "1".equals(versionParts[0])
                    && Integer.parseInt(versionParts[1]) >= 8;
            }
            catch (NumberFormatException nfe)
            {
                System.err.println(nfe);
            }
        }
        return false;
    }

    private boolean jdk8041961()
    {
        out.println();
        out.println("JDK-8041961 test case:");
        NumberFormat format = DecimalFormat.getInstance(Locale.US); // was: new DecimalFormat();
        format.setMaximumFractionDigits(2);
        format.setRoundingMode(RoundingMode.HALF_UP);

        return expect("100", format, 99.9989)
            &  expect("100", format, 99.999);
    }

    private boolean jdk8039915()
    {
        out.println();
        out.println("JDK-8039915 test case:");
        NumberFormat format = DecimalFormat.getInstance(Locale.US);
        format.setGroupingUsed(false);
        format.setMaximumFractionDigits(6);
        format.setRoundingMode(RoundingMode.HALF_UP);

        return expect("0.950001", format, 0.950000550000)
            &  expect("0.950001", format, 0.950000600000);
    }

    private boolean stackoverflow24426438()
    {
        out.println();
        out.println("StackOverflow.com question 24426438 test case 1:");

        NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
        nf.setGroupingUsed(false);
        nf.setMaximumFractionDigits(3);
        nf.setRoundingMode(RoundingMode.HALF_UP);

        return expect("6.209", nf, 6.2088)
            &  expect("6.209", nf, 6.2089);
    }

    private boolean stackoverflow24426438answer24426907()
    {
        out.println();
        out.println("StackOverflow.com question 24426438 test case 2:");

        NumberFormat nf = NumberFormat.getInstance(Locale.ENGLISH);
        nf.setMaximumFractionDigits(1);
        // implied: nf.setRoundingMode(RoundingMode.HALF_EVEN);

        NumberFormat nf2 = NumberFormat.getInstance(Locale.ENGLISH);
        nf2.setMaximumFractionDigits(1);
        nf2.setRoundingMode(RoundingMode.HALF_UP);

        boolean overallPass = true;
        for (int i = 0; i < 100; i++)
        {
            double num = i / 100.0;
            String round1 = nf.format(num);

            // Next line deviates from the SO test loop to account for nf's HALF_EVEN behavior
            if (i % 10 == 5) round1 = nf.format(num + 0.01);

            overallPass &= expect(round1, nf2, num);
        }
        return overallPass;
    }

    private boolean expect(String correct, NumberFormat nf, double unroundedInput)
    {
        String actual = nf.format(unroundedInput);
        out.printf("%15s %s --> %-15s\t", unroundedInput, nf.getRoundingMode(), actual);
        if (correct.equals(actual))
        {
            out.println("OK");
            return true;
        }
        out.printf("expected: %s%n", correct);
        return false;
    }
}
