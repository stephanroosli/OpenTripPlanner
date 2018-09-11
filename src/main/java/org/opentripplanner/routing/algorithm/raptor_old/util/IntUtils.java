package org.opentripplanner.routing.algorithm.raptor_old.util;

import java.util.Arrays;

public final class IntUtils {
    /** protect this class from being instantiated. */
    private IntUtils() {};

    public static int[] newIntArray(int size, int initalValue) {
        int [] array = new int[size];
        Arrays.fill(array, initalValue);
        return array;
    }

    public static String intToString(int value, int notSetValue) {
        return value == notSetValue ? "" : Integer.toString(value);
    }
}
