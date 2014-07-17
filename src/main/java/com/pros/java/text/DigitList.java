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

/**
 * Patch shim template -- allows javac to generate the bytecode for what could be complex
 * behavior, and then the necessary parts will be transplanted into the bytecode of the
 * class targeted by the patch.  In this case, only the method (none of the fields) will
 * be copied; the member fields used within the method must match their definition in the
 * target class, however, so that the shim bytecode will be correct.
 *
 * @author William Price
 */
abstract class DigitList
{
    char[] digits;
    int count;

    /**
     * Shim method to be inserted into {@code java.text.DigitList} by the patch routine.
     * <p>
     * User <q>Holger</q> identified the cause of the bug,
     * <a href="http://stackoverflow.com/a/24427356/2390644">posted on StackOverflow.com</a>,
     * as the following switch case inside
     * {@code boolean shouldRoundUp(int, boolean, boolean)} (line 522).
     * <pre>
     * case HALF_UP:
     *     if (digits[maximumDigits] >= '5') {
     *         // We should not round up if the rounding digits position is
     *         // exactly the last index and if digits were already rounded.
     *         if ((maximumDigits == (count - 1)) &&
     *             (alreadyRounded))
     *             return false;
     *
     *          // Value was exactly at or was above tie. We must round up.
     *          return true;
     *     }
     *     break;
     *     // ... outside the switch: return false;
     * </pre>
     */
    boolean __patched__shouldRoundUp_HALF_UP(
        int maximumDigits, boolean alreadyRounded, boolean allDecimalDigits)
    {
        if (digits[maximumDigits] < '5')
        {
            return false;
        }
        else if (digits[maximumDigits] == '5'
            && maximumDigits == (count - 1)
            && allDecimalDigits
            && alreadyRounded)
        {
            return false;
        }
        return true;
    }
}
