package net.nikosnook.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Amounts at the persistence boundary are integer hundredths, never floating point. */
public final class Money {
    public static final long MAX = 100_000_000_000L;
    private Money() {}
    public static long parse(String input) {
        if (!input.matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Use an amount such as 12.50, with at most two decimals.");
        try {
            long cents = new BigDecimal(input).movePointRight(2).longValueExact();
            if (cents <= 0 || cents > MAX) throw new IllegalArgumentException("Amount is out of range.");
            return cents;
        } catch (ArithmeticException e) { throw new IllegalArgumentException("Amount is out of range."); }
    }
    public static String format(long cents) { return "₦" + BigDecimal.valueOf(cents, 2).setScale(2).toPlainString(); }
    public static long prorate(long weekly, long remainingMillis, long periodMillis) {
        if (weekly <= 0 || weekly > MAX || periodMillis <= 0 || remainingMillis < 0 || remainingMillis > periodMillis) throw new IllegalArgumentException("Invalid rental interval.");
        return BigDecimal.valueOf(weekly).multiply(BigDecimal.valueOf(remainingMillis)).divide(BigDecimal.valueOf(periodMillis), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
