Patch for JDK8 `HALF_UP` rounding bug
=====================================

## The problem

This patch attempts to address the problem described in the following OpenJDK issues:
- [JDK-8039915](https://bugs.openjdk.java.net/browse/JDK-8039915): NumberFormat.format() does not consider required no. of fraction digits properly
- [JDK-8041961](https://bugs.openjdk.java.net/browse/JDK-8041961) (duplicate): DecimalFormat RoundingMode.HALF_UP is broken (HALF_EVEN is OK)

The problem was introduced in Java 8 at the same time as the fix for:
- [JDK-7131459](https://bugs.openjdk.java.net/browse/JDK-7131459): DecimalFormat produces wrong format() results when close to a tie

The fix for `JDK-7131459` has caused some confusion since it _purposefully_ changes the
rounding behavior as compared to Java 7 and prior versions.  A number of bugs were opened
and subsequently closed (duplicate, wontfix) because of "rounding differences" that were
assumed to be (and in many cases were) the result of the intentional change.  However the
first two bugs listed above provide testcases that show _obviously incorrect_ rounding
behavior in the `HALF_UP` mode.

    99.9989 -> 100.00
    99.9990 ->  99.99

To put it simply, this demonstrates a case where **a higher number rounds _down_, 
and a lower number rounds _up_**; `(x <= y) != (round(x) <= round(y))`.  It appears to
only affect the `HALF_UP` rounding mode, which is the kind of rounding taught in
grade school arithmetic classes: `0.5` rounds away from zero, always.

## The fix

Until Oracle releases an official fix, software that depends on the `HALF_UP` rounding mode
for formatting decimal values into a `String` will produce incorrect results.  The root cause
appears to have been correctly identified [on StackOverflow](http://stackoverflow.com/a/24427356/2390644)
thanks to user `Holger` with a link to the OpenJDK source file for `java.text.DigitList`.
Specifically, the `shouldRoundUp` method was modified as a part of `JDK-7131459` and seemingly
incorrectly so for the `HALF_UP` switch case block.

`java.text.DigitList` is a system class and loaded by the boot classloader.  In order to patch
it, one can decompile `rt.jar`, apply the fix, recompile, and recreate `rt.jar`.  But that solution
has a number of drawbacks:

1. Potential issues with the Oracle JDK/JVM license and/or terms of use
1. Requires a moderate degree of technical skill outside the normal workflow for many sysadmins
1. It's non-selective; it unconditionally applies to _all programs_ that use that JVM (share that copy of `rt.jar`)
1. Changes will be lost when upgrading to a new version of the JVM that may also be buggy (the original 8.0, update 5, and update 11 are all affected)
1. Not easily shipped with a product or application that doesn't already bundle its own JRE

Instead, this patch applies changes to `java.text.DigitList` _in memory_ and only for those
programs or environments that are explicitly configured to use it.  That configuration will stay
local to that application/environment and so has the potential to survive JVM upgrades.

This is done by implementing a
[Java Agent](http://docs.oracle.com/javase/6/docs/api/java/lang/instrument/package-summary.html),
a feature introduced in JDK 5, that can optionally _redefine_ a class's bytecode when the target
class is first loaded by any classloader.  The agent is injected _before_ the application's (or
container's) `main` method and so should be completely transparent to the application.
Furthermore, the application does not need to be modified other than to enable its startup
script(s) to inject the agent as a JVM command line option.

Even though the bug only appears in Java 8, this patch should be compiled with **Java 6** in order
to maintain maximum compatibility for those applications that may need to support running on a range
of older Java releases and not be limited _only_ to the latest-and-greatest.  This is possible as
long as nothing in the target class requires new bytecode formats/instructions, such as `INVOKEDYNAMIC`;
thankfully, that condition appears to hold true at this time.

The patch, as implemented here, looks for a specific method signature that also changed at the time
the bug was introduced.  If it finds it, it attempts to patch the bytecode; otherwise, it stays out
of the way.  Therefore, it is hoped that this patch is well-behaved and compatible with current Java 8
releases as well as older JVMs all the way back to Java 6.

## Test, Test, TEST!

You SHOULD NOT ship this patch with your application without testing it, first.   There is a
self-test included in the patch (see: _Enable the patch_) to help determine if your JVM is
affected and whether the patch works on that particular JVM, but it is not a replacement for
due diligence and thorough testing of your own application.

This patch _appears_ to work, but the authors have done _neither_ exhaustive testing across
the vast range of decimal values _nor_ have they tested every possible rounding path inside
the JDK libraries.  More eyes on the patch code, and more real-world tests would be much
appreciated.  Please consider _opening an issue_ in Github with the keyword `WFM` in the
title if this patch worked for your product, library, or application.  Of course, if the patch
failed to work in a situation, _definitely_ open an issue here in Github with as much detail
as you can provide.

**Pull requests for new testcases, and of course bug fixes, are highly desired and welcome.**


## Enable the patch

Pre-requisites:

1. This patch library's JAR
2. Its dependencies (currently ASM version >= 3.1 and < 4.0) must be _on the classpath_.

If your application does not currently use the ASM library, there is a version of this patch that
includes the ASM 3.1 classes already in the JAR.  Or, use your favorite build system and package
management tool to get `asm:asm:3.1` into your project's classpath.  (If you need a newer version
of ASM or a different tool, contributions to the project are welcome.)

Precompiled JARs are available publicly:

Repo     | [jCenter](https://bintray.com/bintray/jcenter)
Group    | com.pros.opensource.java
Artifact | jdk8patch-halfupround-asm31
Version  | 0.9

Use the classifier `all` if you want the JAR that _bundles ASM 3.1 inside_ (in its original package
structure), then you can _exclude_ the transitive dependency on the ASM library.  (If you take
this approach, we assume you know how to manage your dependencies and avoid classpath collisions.)

Applying the patch is as simple as adding one additional entry to the _beginning_ of the command
line that starts your application's JVM:

    java -javaagent:path/to/patch.jar ...

The patch library includes a **self test** you can use to determine if your particular JVM is
affected by the bug, or if the patch will apply itself correctly.  Execute the self test by hand
using one of the following methods:

    # Standalone patch JAR with no bundled dependencies
    $ java -javaagent:path/to/patch.jar -cp <classpath_with_patch_and_ASM_jars> com.pros.java.text.DigitListPatch
    
    # Patch JAR that includes ASM
    $ java -javaagent:path/to/patch-with-asm.jar -jar path/to/patch-with-asm.jar

Example self-test output from an unaffected Java 7 release:

    JDK-8041961 test case:
             99.9989 HALF_UP --> 100                 OK
              99.999 HALF_UP --> 100                 OK
    
    JDK-8039915 test case:
          0.95000055 HALF_UP --> 0.950001            OK
           0.9500006 HALF_UP --> 0.950001            OK
    
    Above tests used Java 1.7.0_51 (Oracle Corporation)
    installed at C:\JAVA\v7\jre
    
    Agent installed: yes
    Patch applied  : NO
    
    Overall result : OK (no patch necessary)

Example self-test output from a *patched* Java 8 release:

    JDK-8041961 test case:
            99.9989 HALF_UP --> 100                 OK
             99.999 HALF_UP --> 100                 OK
    
    JDK-8039915 test case:
         0.95000055 HALF_UP --> 0.950001            OK
          0.9500006 HALF_UP --> 0.950001            OK
    
    Above tests used Java 1.8.0 (Oracle Corporation)
    installed at C:\JAVA\v8\jre
    
    Agent installed: yes
    Patch applied  : yes
    
    Overall result : FIXED

However, without the patch (without the `-javaagent` option) the self-test on early GA releases of Java 8 looks like this:

    JDK-8041961 test case:
            99.9989 HALF_UP --> 100                 OK
             99.999 HALF_UP --> 99.99               expected: 100
    
    JDK-8039915 test case:
         0.95000055 HALF_UP --> 0.950001            OK
          0.9500006 HALF_UP --> 0.95                expected: 0.950001
    
    Above tests used Java 1.8.0 (Oracle Corporation)
    installed at C:\JAVA\v8\jre
    
    Agent installed: NO (missing -javaagent?)
    Patch applied  : NO
    
    Overall result : NOT FIXED (expected on Java 1.8)

## Copyright

Copyright (c) 2014 by [PROS, Inc.](http://www.pros.com/)  All Rights Reserved.

## License

The original OpenJDK `java.text.DigitList` source code is released under the
[GNU Public License Version 2 with Classpath Exception](https://github.com/PROSPricing/jdk8patch-halfupround/blob/master/LICENSE)
as designated by Oracle.  Development of this patch required knowledge of that
source code, which makes the patch a _derivative work_ and subject to the same
license terms.  The authors of this patch wish to extend and apply the same
_Classpath Exception_ to users of binary executable versions of this patch.

    This code is free software; you can redistribute it and/or modify it
    under the terms of the GNU General Public License version 2 only, as
    published by the Free Software Foundation.

    Linking this library statically or dynamically with other modules is making
    a combined work based on this library.  Thus, the terms and conditions of
    the GNU General Public License cover the whole combination.

    As a special exception, the copyright holders of this library give you
    permission to link this library with independent modules to produce an
    executable, regardless of the license terms of these independent modules,
    and to copy and distribute the resulting executable under terms of your
    choice, provided that you also meet, for each linked independent module,
    the terms and conditions of the license of that module.  An independent
    module is a module which is not derived from or based on this library.  If
    you modify this library, you may extend this exception to your version of
    the library, but you are not obligated to do so.  If you do not wish to do
    so, delete this exception statement from your version.

    This code is distributed in the hope that it will be useful, but WITHOUT
    ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
    FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
    version 2 for more details (a copy is included in the LICENSE file that
    accompanied this code).
