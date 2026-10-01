package splitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A group of people sharing expenses (e.g. roommates, a trip).
 * All mutating methods are synchronized on the instance: multiple people
 * (roommates on their own phones) can hit the API for the same group at
 * once, and a plain ArrayList/LinkedHashMap isn't safe under concurrent
 * modification. For this app's scale a simple synchronized block is enough
 * -- no need for anything fancier like a ConcurrentHashMap of locks.
 */
public class Group {
    public final String code;
    private final Set<String> members = new LinkedHashSet<>();
    private final List<Expense> expenses = new ArrayList<>();
    private int nextExpenseId = 1;

    public Group(String code, List<String> initialMembers) {
        this.code = code;
        this.members.addAll(initialMembers);
    }

    public synchronized List<String> getMembers() {
        return new ArrayList<>(members);
    }

    public synchronized List<Expense> getExpenses() {
        return new ArrayList<>(expenses);
    }

    /**
     * Adds an expense. Callers (Server.java) are responsible for validating that
     * expense.paidBy and everyone in expense.splitAmong are already members of this
     * group before calling this -- this method intentionally does NOT auto-add new
     * members anymore. It used to, but that meant a typo in a name (e.g. "Alise"
     * instead of "Alice") would silently create a phantom member instead of
     * surfacing a clear error, which is a real data-consistency bug.
     */
    public synchronized void addExpense(Expense expense) {
        expense.id = nextExpenseId++;
        expenses.add(expense);
    }

    /** Removes an expense by id. Returns true if something was actually removed. */
    public synchronized boolean deleteExpense(int id) {
        return expenses.removeIf(e -> e.id == id);
    }

    /**
     * Net balance per person, in cents. Positive = this person is OWED money
     * overall (they paid more than their share). Negative = this person
     * OWES money overall.
     */
    public synchronized Map<String, Long> netBalancesCents() {
        Map<String, Long> balances = new LinkedHashMap<>();
        for (String m : members) balances.put(m, 0L);

        for (Expense e : expenses) {
            balances.merge(e.paidBy, e.amountCents, Long::sum);
            for (int i = 0; i < e.splitAmong.size(); i++) {
                String person = e.splitAmong.get(i);
                balances.merge(person, -e.sharesCents[i], Long::sum);
            }
        }
        return balances;
    }
}
