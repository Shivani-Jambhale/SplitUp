package splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Turns a set of net balances into the SMALLEST-POSSIBLE-ish set of payments
 * that settles everyone up.
 *
 * Naively, if 5 people owe each other money in a chain, you could end up
 * with a payment for every single expense ever logged. Instead, we only
 * care about each person's final NET balance (owed vs owes), and match the
 * biggest creditor against the biggest debtor, over and over, using two
 * heaps:
 *
 *   - a MAX-heap of creditors (people who are owed money), ordered by
 *     amount owed to them, largest first
 *   - a MIN-heap of debtors (people who owe money, stored as negative
 *     numbers), so the most-negative (biggest debt) naturally comes out
 *     first from a normal min-heap -- no separate reversal needed
 *
 * Each round: pop the biggest creditor and biggest debtor, settle
 * min(creditor's amount, debtor's amount) between them as ONE transaction,
 * update both balances, and push back whichever side still has a nonzero
 * balance left. Every round fully zeroes out at least one person, so with
 * n people holding a nonzero balance, this produces AT MOST (n - 1)
 * transactions.
 *
 * Honest caveat worth knowing for an interview: this greedy approach is NOT
 * always the mathematically minimal number of transactions possible --
 * finding the true minimum is equivalent to a subset-sum partitioning
 * problem and is NP-hard in general. Greedy gets very close in practice and
 * runs in O(n log n), which is the right tradeoff for a real app -- exact
 * optimality here isn't worth exponential runtime.
 */
public class DebtSimplifier {

    public static class Transaction {
        public final String from;
        public final String to;
        public final long amountCents;
        public Transaction(String from, String to, long amountCents) {
            this.from = from;
            this.to = to;
            this.amountCents = amountCents;
        }
    }

    private static class Balance {
        String name;
        long amount; // cents; positive = creditor, negative = debtor
        Balance(String name, long amount) { this.name = name; this.amount = amount; }
    }

    public static List<Transaction> simplify(Map<String, Long> netBalancesCents) {
        PriorityQueue<Balance> creditors = new PriorityQueue<>((a, b) -> Long.compare(b.amount, a.amount)); // max-heap
        PriorityQueue<Balance> debtors = new PriorityQueue<>((a, b) -> Long.compare(a.amount, b.amount));    // min-heap (most negative first)

        for (Map.Entry<String, Long> e : netBalancesCents.entrySet()) {
            long amt = e.getValue();
            if (amt > 0) creditors.add(new Balance(e.getKey(), amt));
            else if (amt < 0) debtors.add(new Balance(e.getKey(), amt));
            // amt == 0 -> already settled, nothing to do
        }

        List<Transaction> transactions = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Balance creditor = creditors.poll();
            Balance debtor = debtors.poll();

            long settle = Math.min(creditor.amount, -debtor.amount);
            transactions.add(new Transaction(debtor.name, creditor.name, settle));

            creditor.amount -= settle;
            debtor.amount += settle;

            if (creditor.amount > 0) creditors.add(creditor);
            if (debtor.amount < 0) debtors.add(debtor);
        }

        return transactions;
    }
}
