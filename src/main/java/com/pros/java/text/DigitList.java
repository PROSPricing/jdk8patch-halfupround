/*
 * Copyright (c) 2014 by PROS, Inc.  All Rights Reserved.
 * This software is the confidential and proprietary information of
 * PROS, Inc. ("Confidential Information").
 * You may not disclose such Confidential Information, and may only
 * use such Confidential Information in accordance with the terms of
 * the license agreement you entered into with PROS.
 */

package com.pros.java.text;

/**
 * Patch shim template -- allows javac to generate the bytecode for what could be complex
 * behavior, and then the necessary parts will be transplanted into the bytecode of the
 * class targeted by the patch.  In this case, only the method (none of the fields) will
 * be copied; the member fields used within the method must match their definition in the
 * target class, however, so that the shim bytecode will be correct.
 *
 * @author William Price (wprice)
 */
abstract class DigitList
{
    char[] digits;
    int count;

    /**
     * Shim method to be inserted into {@code java.text.DigitList} by the patch routine.
     * <p>
     * User <q>Holger</q> identified the cause of the bug,
     * <a href="http://stackoverflow.com/questions/24426438/numberformat-rounding-issue-with-java-8-only/24426907#24426907">
     * posted on StackOverflow.com</a>, as the following switch case inside
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
