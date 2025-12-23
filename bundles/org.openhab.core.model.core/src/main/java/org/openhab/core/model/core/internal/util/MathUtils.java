/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.model.core.internal.util;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * This class provides a few mathematical helper functions that are required by
 * code of this bundle.
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public class MathUtils {

    /**
     * Calculates the greatest common divisor of two numbers using the Euclidean algorithm.
     *
     * @param m first number, must be non-negative
     * @param n second number, must be positive
     * @return the gcd of m and n
     * @throws IllegalArgumentException if n is zero or negative
     */
    public static int gcd(int m, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Second number must be positive, was: " + n);
        }
        if (m < 0) {
            throw new IllegalArgumentException("First number must be non-negative, was: " + m);
        }
        if (m % n == 0) {
            return n;
        }
        return gcd(n, m % n);
    }

    /**
     * Calculates the least common multiple of two numbers.
     *
     * @param m first number, must be positive
     * @param n second number, must be positive
     * @return the lcm of m and n
     * @throws IllegalArgumentException if either number is zero or negative
     */
    public static int lcm(int m, int n) {
        if (m <= 0 || n <= 0) {
            throw new IllegalArgumentException("Both numbers must be positive, were: " + m + ", " + n);
        }
        return m * n / gcd(n, m);
    }

    /**
     * Calculates the greatest common divisor of n numbers.
     *
     * @param numbers an array of n numbers, must not be null or empty, all numbers must be non-negative
     * @return the gcd of the n numbers
     * @throws IllegalArgumentException if the array is null, empty, or contains invalid values
     */
    public static int gcd(Integer[] numbers) {
        if (numbers == null) {
            throw new IllegalArgumentException("Numbers array cannot be null");
        }
        if (numbers.length == 0) {
            throw new IllegalArgumentException("Numbers array cannot be empty");
        }
        int n = numbers[0];
        if (n < 0) {
            throw new IllegalArgumentException("Numbers must be non-negative, found: " + n);
        }
        for (int m : numbers) {
            if (m < 0) {
                throw new IllegalArgumentException("Numbers must be non-negative, found: " + m);
            }
            n = gcd(n, m);
        }
        return n;
    }

    /**
     * Determines the least common multiple of n numbers.
     *
     * @param numbers an array of n numbers, must not be null or empty, all numbers must be positive
     * @return the least common multiple of all numbers of the array
     * @throws IllegalArgumentException if the array is null, empty, or contains invalid values
     */
    public static int lcm(Integer[] numbers) {
        if (numbers == null) {
            throw new IllegalArgumentException("Numbers array cannot be null");
        }
        if (numbers.length == 0) {
            throw new IllegalArgumentException("Numbers array cannot be empty");
        }
        int n = numbers[0];
        if (n <= 0) {
            throw new IllegalArgumentException("Numbers must be positive, found: " + n);
        }
        for (int m : numbers) {
            if (m <= 0) {
                throw new IllegalArgumentException("Numbers must be positive, found: " + m);
            }
            n = lcm(n, m);
        }
        return n;
    }
}
