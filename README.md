Patch for JDK8 HALF_UP rounding bug
===================================

## The problem

This patch attempts to address the problem described in the following OpenJDK issues:
- [JDK-8039915][JDK8039915]: Wrong NumberFormat.format() HALF_UP rounding when last digit exactly at rounding position greater than 5
- [JDK-8041961][JDK8041961] (duplicate): DecimalFormat RoundingMode.HALF_UP is broken (HALF_EVEN is OK)

The problem was introduced in Java 8 at the same time as the fix for:
- [JDK-7131459][JDK7131459]: DecimalFormat produces wrong format() results when close to a tie

The fix for JDK-7131459 has caused some confusion since it _purposefully_ changes the
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

This issue is known to exist in the Oracle/OpenJDK 1.8.0 GA release,
as well as in released updates: **u5**, **u11**, **u20**, **u25**, and **u31**.

## The solution

**Oracle has committed an official fix**

- Initial fix in Java _9_ ([bug 8039915][JDK8039915], [changeset][JDK9patch])
- Backported to Java _8_ ([bug 8061380][JDK8061380], [changeset][JDK8patch]), first
  included in Early Access build _8u40-b12_ (28 October 2014).
- **Java 8u40** public GA release, with JDK _1.8.0_40-b25_ (03 March 2015, [release notes][JDK8u40rel]).

If you are running Java 8 update 40 or later, you do not require this patch.  However, if you wish
to support earlier versions of Java 8, read on.

## About this patch

Prior to _update 40_, software running on Java 8 that depends on the `HALF_UP` rounding mode
for formatting decimal values into a `String` will produce incorrect results.  The root cause
appears to have been [correctly identified on StackOverflow.com][StackOverflow] by the user
_Holger_ with a link to the OpenJDK source file for `java.text.DigitList`.  Specifically, the
`shouldRoundUp` method was modified as a part of JDK-7131459 and seemingly incorrectly so for
the `HALF_UP` switch case block.

`java.text.DigitList` is a system class and loaded by the boot classloader.  In order to patch
it, one can decompile `rt.jar`, apply the fix, recompile, and recreate `rt.jar`.  But that solution
has a number of drawbacks:

1. Potential issues with the Oracle JDK/JVM license and/or terms of use
1. Requires a moderate degree of technical skill outside the normal workflow for many sysadmins
1. It's non-selective; it unconditionally applies to _all programs_ that use that JVM (share that copy of `rt.jar`)
1. Changes will be lost when upgrading to a new version of the JVM that may also be buggy
1. Not easily shipped with a product or application that doesn't already bundle its own JRE

Instead, this patch applies changes to `java.text.DigitList` _in memory_ and only for those
programs or environments that are explicitly configured to use it.  That configuration will stay
local to that application/environment and so has the potential to survive JVM upgrades.

This is done by implementing a [Java Agent][J6Agent], a feature introduced in JDK 5, that can
optionally _redefine_ a class's bytecode when the target class is first loaded by any classloader.
The agent is injected _before_ the application's (or container's) `main` method and so should be
completely transparent to the application.  Furthermore, the application requires no modification
other than to enable its startup script(s) to inject the agent as a JVM command line option.

Even though the bug only appears in Java 8, this patch should be compiled with **Java 6** in order
to maintain maximum compatibility for those applications that may need to support running on a range
of older Java releases and not be limited _only_ to the latest-and-greatest.  This is possible as
long as nothing in the target class requires new bytecode formats/instructions, such as
`INVOKEDYNAMIC`; thankfully, that condition appears to hold true at this time.

The patch, as implemented here, looks for specific bytecode signatures that changed at the time
the bug was introduced and when it was fixed.  It attempts to patch the bytecode only when it
finds the particular bytecode signatures that suggest the bug is present. Otherwise, it stays out
of the way.  It is _likely_ that this patch implementation is safe for use in all official Oracle
releases of Java for versions 6 through 8.

## Test, Test, TEST!

You SHOULD NOT ship this patch with your application without testing it, first.   There is a
self-test included in the patch (see: _Diagnostics_ below) to help determine if your JVM is
affected and whether the patch works on that particular JVM, but it is not a replacement for
due diligence and thorough testing of your own application.

This patch _appears_ to work, but the authors have done _neither_ exhaustive testing across
the vast range of decimal values _nor_ have they tested every possible rounding path inside
the JDK libraries.  More eyes on the patch code, and more real-world tests, never hurt.
Your feedback is appreciated.  Please consider _opening an issue_ in Github with the keyword
`WFM` in the title if this patch worked for your product, library, or application.  Of course,
if the patch failed to work in a situation, _definitely_ open an issue here in Github with as
much detail as you can provide.

## Enable the patch

Pre-requisites:

1. This patch library's JAR file.
2. An appropriate version of the ASM library must be _on the classpath_.

Applying the patch is as simple as adding one additional entry to the _beginning_ of the command
line that starts your application's JVM:

    java -javaagent:path/to/patch.jar ...

**Precompiled JARs are available publicly at [jCenter][JCenter]**. Refer to the
[current version][SELF] of this README file for compatibility information and Maven
coordinates. Also review the `changelog.txt` file for a summary of what has changed since
prior releases.

If your application does not already include the ASM library, use your favorite build system and
dependency management tool to get ASM into your project's classpath.  Or, download the library
using the Maven classifier `all` to get an alternate JAR that _pre-bundles ASM_ inside (in its
original package structure).  With the bundled JAR, you can _exclude_ the transitive dependency
on the ASM library.  (If you take the bundled approach, we assume you know how to manage your
dependencies and avoid package collisions in your classpath.)

### Latest available releases

<table>
  <thead>
    <tr>
      <th rowspan="2">Maven coordinates</td>
      <td colspan="3" align="center" nowrap>ASM compatibility (tested version)</td>
    </tr>
    <tr>
      <th nowrap><b>ASM 3.x</b> (3.1)</td>
      <th nowrap><b>ASM 4.x</b> (4.2)</td>
      <th nowrap><b>ASM 5.x</b> (5.0.3)</td>
    </tr>
  </thead>
  <tbody>
    <tr><td>Group</td><td colspan="3" align="center" nowrap>com.pros.opensource.java</td></tr>
    <tr><td>Artifact</td><td colspan="3" align="center" nowrap>jdk8patch-halfupround-asm</td></tr>
    <tr align="center">
      <td align="left">Version</td>
      <td>1.3</td>
      <td>1.4</td>
      <td>1.5</td>
    </tr>
  </tbody>
</table>

## Diagnostics

The patch library includes a **self test** you can use to determine if your particular JVM is
affected by the bug, or if the patch will apply itself correctly.  Execute the self test by hand
using one of the following methods:

    # Standalone patch JAR with no bundled dependencies
    $ java -javaagent:path/to/patch.jar -cp <classpath_with_patch_and_ASM_jars> com.pros.java.text.DigitListPatch
    
    # Patch JAR that includes ASM
    $ java -javaagent:path/to/patch-with-asm.jar -jar path/to/patch-with-asm.jar

#### Unaffected Java 7 sample output

You may notice that the test case for JDK-7131459 does not match on this earlier version of Java.
The test suite ignores the results for that particular test on versions prior to JDK 8.

    JDK-8041961 test case:
             99.9989 HALF_UP --> 100                 OK
              99.999 HALF_UP --> 100                 OK
    
    JDK-8039915 test case:
          0.95000055 HALF_UP --> 0.950001            OK
           0.9500006 HALF_UP --> 0.950001            OK
    
    JDK-7131459 test case:
    NOTE: These tests SHOULD NOT MATCH on earlier versions of Java.
               0.15 is actually: 0.1499999999999999944488848768742172978818416595458984375
                    HALF_UP  --> 0.2                expected: 0.1
               0.35 is actually: 0.34999999999999997779553950749686919152736663818359375
                    HALF_UP  --> 0.4                expected: 0.3
               0.85 is actually: 0.84999999999999997779553950749686919152736663818359375
                    HALF_UP  --> 0.9                expected: 0.8
               0.95 is actually: 0.9499999999999999555910790149937383830547332763671875
                    HALF_UP  --> 1                  expected: 0.9
    
    Above tests used Java 1.7.0_51 (Oracle Corporation)
    installed at C:\JAVA\v7\jre
    
    Agent installed: yes
    Patch applied  : NO
    
    Overall result : OK (no patch necessary)

#### Patched Java 8 example output

These are the results you should expect when this is working correctly on Java 8.
These results should match Java 8u40 and later, without this patch, too.

    JDK-8041961 test case:
            99.9989 HALF_UP --> 100                 OK
             99.999 HALF_UP --> 100                 OK
    
    JDK-8039915 test case:
         0.95000055 HALF_UP --> 0.950001            OK
          0.9500006 HALF_UP --> 0.950001            OK
    
    JDK-7131459 test case:
               0.15 is actually: 0.1499999999999999944488848768742172978818416595458984375
                    HALF_UP  --> 0.1                OK
               0.35 is actually: 0.34999999999999997779553950749686919152736663818359375
                    HALF_UP  --> 0.3                OK
               0.85 is actually: 0.84999999999999997779553950749686919152736663818359375
                    HALF_UP  --> 0.8                OK
               0.95 is actually: 0.9499999999999999555910790149937383830547332763671875
                    HALF_UP  --> 0.9                OK
    
    Above tests used Java 1.8.0_20 (Oracle Corporation)
    installed at C:\JAVA\v8\jre
    
    Agent installed: yes
    Patch applied  : yes
    
    Overall result : FIXED

#### Buggy (unpatched) Java 8 example output

Without the patch (without specifying the `-javaagent` option) the self-test
on releases of Java 8 _prior to update 40_ will look similar to the following.

    JDK-8041961 test case:
            99.9989 HALF_UP --> 100                 OK
             99.999 HALF_UP --> 99.99               expected: 100
    
    JDK-8039915 test case:
         0.95000055 HALF_UP --> 0.950001            OK
          0.9500006 HALF_UP --> 0.95                expected: 0.950001
    
    JDK-7131459 test case:
               0.15 is actually: 0.1499999999999999944488848768742172978818416595458984375
                    HALF_UP  --> 0.1                OK
               0.35 is actually: 0.34999999999999997779553950749686919152736663818359375
                    HALF_UP  --> 0.3                OK
               0.85 is actually: 0.84999999999999997779553950749686919152736663818359375
                    HALF_UP  --> 0.8                OK
               0.95 is actually: 0.9499999999999999555910790149937383830547332763671875
                    HALF_UP  --> 0.9                OK
    
    Above tests used Java 1.8.0_20 (Oracle Corporation)
    installed at C:\JAVA\v8\jre
    
    Agent installed: NO (missing -javaagent?)
    Patch applied  : NO
    
    Overall result : NOT FIXED (expected on Java 1.8 < u40)

## Copyright

Copyright (c) 2014 by [PROS, Inc.](http://www.pros.com/)  All Rights Reserved.

## License

The original OpenJDK `java.text.DigitList` source code is released under the
[GNU Public License Version 2 with Classpath Exception][GPLv2CPE]
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

[SELF]: https://github.com/PROSPricing/jdk8patch-halfupround/blob/master/README.md
[GPLv2CPE]: https://github.com/PROSPricing/jdk8patch-halfupround/blob/master/LICENSE
[JDK7131459]: https://bugs.openjdk.java.net/browse/JDK-7131459
[JDK8039915]: https://bugs.openjdk.java.net/browse/JDK-8039915
[JDK8041961]: https://bugs.openjdk.java.net/browse/JDK-8041961
[JDK8061380]: https://bugs.openjdk.java.net/browse/JDK-8061380
[JDK9patch]: http://hg.openjdk.java.net/jdk9/jdk9/jdk/rev/963ef28a8224
[JDK8patch]: http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/rev/147843e7006a
[StackOverflow]: http://stackoverflow.com/a/24427356/2390644
[J6Agent]: http://docs.oracle.com/javase/6/docs/api/java/lang/instrument/package-summary.html
[JCenter]: https://bintray.com/bintray/jcenter
[JDK8u40rel]: http://www.oracle.com/technetwork/java/javase/8u40-relnotes-2389089.html
