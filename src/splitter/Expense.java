package splitter;

import java.util.List;

/**
 * A single shared expense.
 *
 * Amounts are stored in CENTS as longs, not as double dollars/rupees.
 * This matters: floating point can't represent most decimal fractions
 * exactly (0.1 + 0.2 != 0.3 in binary floating point), and errors compound
 * across many expenses. Any real money-handling code should do arithmetic
 * in the smallest currency unit (cents/paise) using integers, and only
 * convert to a decimal string at the very edge, for display.
 *
 * Each person's exact share is stored explicitly in sharesCents (parallel
 * array to splitAmong), rather than always being recomputed as an even
 * split. This is what allows two different modes of creating an expense:
 *   - equalSplit(...)  -- divides amountCents evenly (with remainder cents
 *                          handed out one at a time so shares always add up
 *                          to exactly amountCents)
 *   - customSplit(...) -- caller supplies each person's exact share
 *                          (validated to sum to amountCents)
 */
public class Expense {
    public final String paidBy;
    public final long amountCents;
    public final String description;
    public final List<String> splitAmong;
    public final long[] sharesCents; // parallel to splitAmong
    public final long timestamp;
    public int id = -1; // assigned by Group.addExpense() -- lets a specific expense be targeted for deletion

    private Expense(String paidBy, long amountCents, String description, List<String> splitAmong, long[] sharesCents) {
        this.paidBy = paidBy;
        this.amountCents = amountCents;
        this.description = description;
        this.splitAmong = splitAmong;
        this.sharesCents = sharesCents;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Splits amountCents evenly among splitAmong.size() people.
     * Integer division drops remainder cents (e.g. 100 cents / 3 = 33 each,
     * 1 cent left over) -- rather than silently losing that cent, we hand it
     * out one-by-one to the first few people, so the shares always add up
     * to exactly amountCents.
     */
    public static Expense equalSplit(String paidBy, long amountCents, String description, List<String> splitAmong) {
        int n = splitAmong.size();
        long base = amountCents / n;
        long remainder = amountCents % n;
        long[] shares = new long[n];
        for (int i = 0; i < n; i++) {
            shares[i] = base + (i < remainder ? 1 : 0);
        }
        return new Expense(paidBy, amountCents, description, splitAmong, shares);
    }

    /**
     * Splits by explicit per-person amounts (e.g. only 3 of 5 people bought
     * something, and not in equal amounts). sharesCents must sum to exactly
     * amountCents -- the caller (Server) is responsible for validating this
     * before constructing the expense, since giving a clear error back to
     * the user is more useful than silently rebalancing their numbers.
     */
    public static Expense customSplit(String paidBy, long amountCents, String description, List<String> splitAmong, long[] sharesCents) {
        return new Expense(paidBy, amountCents, description, splitAmong, sharesCents);
    }
}
