# SplitUp — Group Expense Splitter with Debt Simplification

Track shared expenses with roommates/friends and settle up using the
FEWEST possible payments — the same "minimum cash flow" idea real apps
like Splitwise use, implemented from scratch with a greedy heap algorithm.

Backend is plain Java (JDK's built-in `com.sun.net.httpserver` — no Spring,
no external JSON library, no database, no build tool). Frontend is a small
mobile-first HTML/JS page. Everything lives in memory for the life of the
process — the entire stack is just core Java and a browser, nothing else to
install or explain. This means it deploys as a single process and opens
fine on any phone browser via a normal link — no app install needed.

![screenshot placeholder](docs/demo.gif)
<!-- record a short screen capture before putting this on your resume -->

## The core idea (this is the part to lead with in an interview)
If Alice paid for a trip, Bob paid for food, and everyone owes each other a
little, a naive system would generate one payment per expense — way more
transactions than necessary. Instead, this app:
1. Computes each person's **net balance** (positive = owed money, negative = owes money)
2. Runs a **greedy algorithm using two heaps** — a max-heap of creditors and
   a min-heap of debtors — repeatedly matching the biggest creditor against
   the biggest debtor until everyone is settled
3. Produces at most `n - 1` payments for `n` people with a nonzero balance

See `src/splitter/DebtSimplifier.java` — it's fully commented with the
algorithm explanation and an honest note that the *provably* minimal number
of transactions is NP-hard to compute exactly (it reduces to subset-sum
partitioning), so this greedy approach is the right practical tradeoff.

## Two ways to split an expense
Real group expenses aren't always "everyone pays equally" — sometimes only
some of the group was involved, and sometimes the amounts aren't equal
(e.g. someone ordered something pricier). Both are supported:
- **Split equally** — pick who was involved (not necessarily everyone in
  the group), the total splits evenly among just them, with any leftover
  cent handed out one at a time so shares always add up exactly.
- **Custom amounts** — enter each involved person's exact share; the total
  expense amount is simply the sum of what you enter. The payer doesn't
  have to be one of the people splitting it (e.g. someone paying for
  others without taking a share themselves).

## Project structure
```
expense-splitter/
├── src/splitter/
│   ├── Expense.java        # expense model (money handled in integer cents)
│   ├── Group.java          # members + expenses + balance calculation
│   ├── DebtSimplifier.java # the core algorithm — read this one first
│   ├── Store.java          # in-memory group storage, keyed by shareable code
│   ├── JsonUtil.java       # minimal hand-rolled JSON (no dependency)
│   └── Server.java         # HTTP server + routes + static file serving
├── web/
│   ├── index.html
│   ├── style.css            # mobile-first styling
│   └── app.js
└── README.md
```

## Run it locally
No build tool, no database, no dependencies to install — just the JDK.
```bash
cd expense-splitter
mkdir -p out
javac -d out src/splitter/*.java
java -cp out splitter.Server
```
Open **http://localhost:8080**. Create a group, add expenses, click "Show
Settlement Plan".

### Try it on your phone (same wifi)
Find your computer's local IP (e.g. `ipconfig` on Windows / `ifconfig` or
`ip addr` on Mac/Linux, look for something like `192.168.x.x`), then open
`http://192.168.x.x:8080` on your phone while on the same wifi network.

### A note on data lifetime
All groups and expenses live in memory for as long as the server process
runs. Restarting the server clears everything — there's no database here
by design, to keep the stack minimal and entirely explainable. If you
wanted data to survive a restart, the clean extension point is `Store.java`
(swap the in-memory map for something that persists) — see "Possible
extensions" below.

## Getting a real link anyone can open on their phone
This reads its port from the `PORT` environment variable, so it deploys
as-is on a free web service, with no extra configuration (no database to
provision, no connection strings to set):

1. Push this folder to a GitHub repo.
2. On **Render** (or Railway): New → Web Service → connect the repo.
   - Build command: `javac -d out src/splitter/*.java`
   - Start command: `java -cp out splitter.Server`
3. You get a public `https://your-app.onrender.com` link — open it on any
   phone, no wifi restriction, no app install. Share the invite link (or
   just the 6-character group code) with your group.

Note: free tiers on these platforms usually spin the server down after a
period of inactivity and take a few seconds to wake back up on the next
request — and since there's no database, that also means an idle restart
clears any groups created so far. Worth knowing (and mentioning) rather
than being surprised by it during a demo.

## Talking points for interviews

**"Walk me through the debt simplification algorithm."**
> Every expense affects two things: the payer's balance goes up by the
> full amount, and everyone splitting it has their balance go down by their
> share. Once I have everyone's final net balance, I don't care how it got
> there — I just want to settle it in as few payments as possible. I put
> creditors in a max-heap and debtors in a min-heap (storing debts as
> negative numbers so the biggest debtor pops out naturally). Each round I
> match the biggest creditor with the biggest debtor, settle the smaller of
> the two amounts, and push back whoever still has a balance left. Every
> round fully zeroes out at least one person, so it terminates in at most
> n-1 payments.

**"Is that actually the minimum number of transactions?"**
> Not provably — finding the true minimum is NP-hard, it's equivalent to
> partitioning balances into subsets that each sum to zero. My greedy
> approach runs in O(n log n) and gets very close in practice, which is the
> right tradeoff — exact optimality isn't worth exponential runtime for an
> app like this.

**"Why store money as cents/longs instead of doubles?"**
> Floating point can't represent most decimal fractions exactly — 0.1 + 0.2
> isn't exactly 0.3 in binary floating point — and those tiny errors
> compound across many expenses. Doing all the arithmetic in integer cents
> and only converting to a decimal string for display avoids that entirely.
> I also had to handle the remainder when splitting an odd number of cents
> evenly (e.g. 100 cents / 3 people) — I hand the leftover cents out one at
> a time instead of losing them to integer division.

**"How do you handle concurrent access — what if two roommates use it at once?"**
> Two layers. The HTTP server itself uses a fixed thread pool
> (`Executors.newFixedThreadPool(8)`) instead of the JDK's default executor
> — the default actually processes requests one at a time on a single
> thread, which I caught during a review and fixed, since it meant true
> concurrent requests weren't possible at all. Below that, the group store
> is a ConcurrentHashMap so different groups don't contend, and each
> Group's own mutating methods are `synchronized`, since a plain
> List/HashMap isn't safe if two people add expenses to the same group at
> the same moment. Both layers matter — the thread pool without the
> synchronized data structures would crash under real concurrency, and the
> synchronized data structures without the thread pool were just unused
> defensive code.

**"Why not use a framework or a database?"**
> The API surface is small — four routes with simple JSON payloads — so I
> wrote a minimal JSON parser instead of pulling in Jackson, and used the
> JDK's built-in HTTP server instead of Spring. I also deliberately left
> persistence out: this project is meant to showcase the algorithm and the
> server-side design, not a database integration, so adding one would have
> been extra surface area without adding to the actual point of the
> project. For anything bigger or longer-lived I'd reach for a real
> framework and a database without hesitation — here it kept the whole
> thing understandable end to end with zero setup for anyone reviewing it.

**"What happens if someone adds an expense by mistake?"**
> You can delete it — each expense gets an id when it's added, and deleting
> just removes it from the group's expense list. Because balances are
> always recalculated from scratch from the full expense history rather
> than kept as a running total, removing one expense automatically corrects
> every balance and the settlement plan with it — no separate "undo" logic
> needed, and `DebtSimplifier` doesn't change at all.

**"Have you looked for security issues in this?"**
> Yes — I ran a deliberate audit before using this in interviews. The most
> serious finding was a path traversal bug in the static file handler: it
> built the file path straight from the URL with no sanitization, so a
> request like `GET /../src/splitter/Server.java` could escape the `web/`
> folder and read arbitrary files on the server. The fix resolves and
> normalizes the requested path against the web root first, then verifies
> the result is still inside it before serving anything. I also found and
> fixed a money bug in the same pass: Java's integer division truncates
> toward zero, so for any balance between -0.01 and -0.99, dividing by 100
> gave `0` instead of `-1`, silently dropping the minus sign — a debt could
> display as a positive number. Both are exactly the kind of bug that's
> easy to miss because the "normal" test cases (larger, cleaner numbers)
> don't trigger them.

**"What about input validation — could a bad request corrupt the data?"**
> It could, before I checked. `Double.parseDouble` in Java happily accepts
> the literal strings `"Infinity"` and `"NaN"` as valid numbers, so a
> crafted request with `"amount":Infinity` would produce
> `Math.round(Infinity * 100)`, which overflows to `Long.MAX_VALUE` and
> permanently corrupts that group's balances. I added an explicit
> `Double.isFinite()` check plus a sane upper bound on any single amount.
> I also found that a typo in a name (like "Alise" instead of "Alice")
> used to silently create a brand-new phantom member instead of erroring,
> since the code auto-added whoever was named in an expense. Now `paidBy`
> and everyone in `splitAmong` are validated against the group's actual
> membership first, with a clear 400 error otherwise.

## Possible extensions
- Persisting groups so they survive a restart — the clean extension point
  is `Store.java`: swap its in-memory `ConcurrentHashMap` for something
  that writes through to disk or a database. Deliberately left out of the
  current version to keep the project focused on the algorithm and the
  server design rather than a persistence layer.
- Editing an expense in place (currently you delete and re-add instead)
- Multiple currencies
- Push notifications when someone adds an expense
- Auth (currently anyone with the code can add expenses — a trust-based MVP, like a shared link)
- Rate limiting on the API (not implemented — acceptable for a trust-based MVP at this scale, but worth naming if asked)
