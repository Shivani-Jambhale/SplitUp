package splitter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Entry point + HTTP server. Built entirely on the JDK's own
 * com.sun.net.httpserver -- no Spring, no external web framework, no build
 * tool needed. Keeps the whole thing runnable with just javac/java and easy
 * to explain end-to-end.
 *
 * Routes:
 *   GET  /, /index.html, /app.js, /style.css   -> static frontend
 *   POST /api/group/create                      -> {members:[...]} -> new group + code
 *   GET  /api/group/get?code=XYZ                -> members, expenses, balances
 *   POST /api/group/expense?code=XYZ            -> add an expense
 *   GET  /api/group/simplify?code=XYZ           -> minimal settlement plan
 */
public class Server {

    private static final String WEB_ROOT = "web";
    private static final Store store = new Store();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/group/create", new CreateGroupHandler());
        server.createContext("/api/group/get", new GetGroupHandler());
        server.createContext("/api/group/expense", new AddExpenseHandler());
        server.createContext("/api/group/simplify", new SimplifyHandler());
        server.createContext("/", new StaticFileHandler());
        // server.setExecutor(null) would use the JDK's default, which processes
        // requests ONE AT A TIME on a single thread -- not true concurrency. A fixed
        // thread pool is what actually lets multiple roommates hit the API at once,
        // which is the whole reason Group/Store use synchronized/ConcurrentHashMap.
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("Server running at http://localhost:" + port);
    }

    // ---------- static files ----------

    static class StaticFileHandler implements HttpHandler {
        private static final Path WEB_ROOT_DIR = Path.of(WEB_ROOT).toAbsolutePath().normalize();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            // Resolve against WEB_ROOT and normalize (collapses "..") BEFORE touching
            // the filesystem, then verify the result is still inside WEB_ROOT. Without
            // this check, a request like GET /../src/splitter/Server.java would escape
            // the web/ folder and let anyone read arbitrary files on the server.
            Path file = WEB_ROOT_DIR.resolve(path.substring(1)).normalize();
            if (!file.startsWith(WEB_ROOT_DIR) || !Files.exists(file) || Files.isDirectory(file)) {
                sendText(exchange, 404, "text/plain", "Not found");
                return;
            }
            String contentType = path.endsWith(".html") ? "text/html"
                    : path.endsWith(".js") ? "application/javascript"
                    : path.endsWith(".css") ? "text/css"
                    : "application/octet-stream";

            byte[] bytes = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }

    // ---------- POST /api/group/create ----------

    static class CreateGroupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) { sendText(exchange, 405, "text/plain", "Use POST"); return; }

            String body = readBody(exchange);
            if (body == null) return; // 413 already sent

            List<String> members = normalizeNames(JsonUtil.parseStringArray(body, "members"));

            if (members.isEmpty()) {
                sendText(exchange, 400, "application/json", "{\"error\":\"At least one member name is required\"}");
                return;
            }

            Group group = store.createGroup(members);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("code", group.code);
            sendText(exchange, 200, "application/json", JsonUtil.toJson(resp));
        }
    }

    // ---------- GET /api/group/get?code=... ----------

    static class GetGroupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String code = queryParam(exchange, "code");
            Group group = code == null ? null : store.get(code);
            if (group == null) { sendText(exchange, 404, "application/json", "{\"error\":\"Group not found\"}"); return; }

            sendText(exchange, 200, "application/json", groupToJson(group));
        }
    }

    // ---------- POST /api/group/expense?code=... ----------

    static class AddExpenseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if (!"POST".equals(method) && !"DELETE".equals(method)) {
                sendText(exchange, 405, "text/plain", "Use POST or DELETE");
                return;
            }

            String code = queryParam(exchange, "code");
            Group group = code == null ? null : store.get(code);
            if (group == null) { sendText(exchange, 404, "application/json", "{\"error\":\"Group not found\"}"); return; }

            if ("DELETE".equals(method)) {
                String idParam = queryParam(exchange, "id");
                int id = idParam == null ? -1 : (int) parseDoubleOr(idParam, -1);
                boolean removed = group.deleteExpense(id);
                if (!removed) { sendText(exchange, 404, "application/json", "{\"error\":\"Expense not found\"}"); return; }
                sendText(exchange, 200, "application/json", groupToJson(group));
                return;
            }

            String body = readBody(exchange);
            if (body == null) return; // 413 already sent

            Map<String, String> fields = JsonUtil.parseFlat(body);
            String paidBy = fields.get("paidBy") == null ? null : fields.get("paidBy").trim();
            String description = fields.getOrDefault("description", "");

            // Raw shares map's keys aren't trimmed yet -- rebuild with trimmed,
            // non-blank keys (duplicate-after-trim keys simply overwrite, same as
            // the JSON parser already does for literal duplicate keys).
            Map<String, Double> rawShares = JsonUtil.parseNumberObject(body, "shares");
            Map<String, Double> shares = new LinkedHashMap<>();
            for (Map.Entry<String, Double> e : rawShares.entrySet()) {
                String name = e.getKey() == null ? null : e.getKey().trim();
                if (name != null && !name.isEmpty()) shares.put(name, e.getValue());
            }

            if (paidBy == null || paidBy.isEmpty()) {
                sendText(exchange, 400, "application/json", "{\"error\":\"paidBy is required\"}");
                return;
            }

            Expense expense;
            List<String> splitAmong;
            if (!shares.isEmpty()) {
                // custom split: each person's exact share was provided
                splitAmong = new ArrayList<>(shares.keySet());
                long[] sharesCents = new long[splitAmong.size()];
                long total = 0;
                boolean valid = true;
                for (int i = 0; i < splitAmong.size(); i++) {
                    double v = shares.get(splitAmong.get(i));
                    if (!Double.isFinite(v) || v <= 0 || v > MAX_REASONABLE_AMOUNT) { valid = false; break; }
                    sharesCents[i] = Math.round(v * 100);
                    total += sharesCents[i];
                }
                if (!valid || total <= 0) {
                    sendText(exchange, 400, "application/json", "{\"error\":\"Each custom share must be a positive, reasonable amount\"}");
                    return;
                }
                expense = Expense.customSplit(paidBy, total, description, splitAmong, sharesCents);
            } else {
                double amount = parseDoubleOr(fields.get("amount"), -1);
                splitAmong = normalizeNames(JsonUtil.parseStringArray(body, "splitAmong"));

                if (!Double.isFinite(amount) || amount <= 0 || amount > MAX_REASONABLE_AMOUNT || splitAmong.isEmpty()) {
                    sendText(exchange, 400, "application/json", "{\"error\":\"A positive, reasonable amount and splitAmong are required\"}");
                    return;
                }
                expense = Expense.equalSplit(paidBy, Math.round(amount * 100), description, splitAmong);
            }

            // Validate paidBy and everyone in splitAmong are actually members of this
            // group -- without this, a typo (e.g. "Alise" instead of "Alice") would
            // silently create a brand-new phantom member instead of surfacing a clear
            // error, corrupting the group's data.
            List<String> currentMembers = group.getMembers();
            if (!currentMembers.contains(paidBy)) {
                sendText(exchange, 400, "application/json",
                        "{\"error\":" + JsonUtil.jsonString(paidBy + " is not a member of this group") + "}");
                return;
            }
            for (String person : splitAmong) {
                if (!currentMembers.contains(person)) {
                    sendText(exchange, 400, "application/json",
                            "{\"error\":" + JsonUtil.jsonString(person + " is not a member of this group") + "}");
                    return;
                }
            }

            group.addExpense(expense);

            sendText(exchange, 200, "application/json", groupToJson(group));
        }
    }

    // ---------- GET /api/group/simplify?code=... ----------

    static class SimplifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String code = queryParam(exchange, "code");
            Group group = code == null ? null : store.get(code);
            if (group == null) { sendText(exchange, 404, "application/json", "{\"error\":\"Group not found\"}"); return; }

            List<DebtSimplifier.Transaction> transactions = DebtSimplifier.simplify(group.netBalancesCents());

            StringBuilder sb = new StringBuilder("{\"transactions\":[");
            for (int i = 0; i < transactions.size(); i++) {
                if (i > 0) sb.append(",");
                DebtSimplifier.Transaction t = transactions.get(i);
                sb.append("{\"from\":").append(JsonUtil.jsonString(t.from))
                  .append(",\"to\":").append(JsonUtil.jsonString(t.to))
                  .append(",\"amount\":").append(centsToDecimalString(t.amountCents))
                  .append("}");
            }
            sb.append("]}");
            sendText(exchange, 200, "application/json", sb.toString());
        }
    }

    // ---------- helpers ----------

    // Sanity ceiling on any single amount -- rejects accidental or malicious
    // non-realistic values (e.g. Double.parseDouble happily accepts the literal
    // strings "Infinity"/"NaN", which without this check would corrupt a group's
    // balances via Math.round(Infinity * 100) overflowing to Long.MAX_VALUE).
    private static final double MAX_REASONABLE_AMOUNT = 10_000_000;

    /** Trims, drops blanks, and de-duplicates (preserving first-occurrence order). */
    private static List<String> normalizeNames(List<String> names) {
        List<String> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String n : names) {
            if (n == null) continue;
            String trimmed = n.trim();
            if (!trimmed.isEmpty() && seen.add(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String groupToJson(Group group) {
        Map<String, Long> balances = group.netBalancesCents();

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"code\":").append(JsonUtil.jsonString(group.code)).append(",");
        sb.append("\"members\":").append(JsonUtil.jsonStringArray(group.getMembers())).append(",");

        sb.append("\"expenses\":[");
        List<Expense> expenses = group.getExpenses();
        for (int i = 0; i < expenses.size(); i++) {
            if (i > 0) sb.append(",");
            Expense e = expenses.get(i);
            sb.append("{\"id\":").append(e.id)
              .append(",\"paidBy\":").append(JsonUtil.jsonString(e.paidBy))
              .append(",\"amount\":").append(centsToDecimalString(e.amountCents))
              .append(",\"description\":").append(JsonUtil.jsonString(e.description))
              .append(",\"splitAmong\":").append(JsonUtil.jsonStringArray(e.splitAmong))
              .append(",\"shares\":[");
            for (int j = 0; j < e.splitAmong.size(); j++) {
                if (j > 0) sb.append(",");
                sb.append("{\"name\":").append(JsonUtil.jsonString(e.splitAmong.get(j)))
                  .append(",\"amount\":").append(centsToDecimalString(e.sharesCents[j]))
                  .append("}");
            }
            sb.append("]}");
        }
        sb.append("],");

        sb.append("\"balances\":[");
        boolean first = true;
        for (Map.Entry<String, Long> entry : balances.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":").append(JsonUtil.jsonString(entry.getKey()))
              .append(",\"amount\":").append(centsToDecimalString(entry.getValue()))
              .append("}");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private static String centsToDecimalString(long cents) {
        // Bug this replaces: Java's integer division truncates toward zero, so for
        // example -50/100 == 0, not -1. That silently dropped the minus sign for any
        // balance between -0.01 and -0.99 (e.g. owing exactly 50 cents rendered as a
        // POSITIVE "0.50" instead of "-0.50"). Working from the absolute value and
        // re-applying the sign afterward avoids that entirely, for any magnitude.
        boolean negative = cents < 0;
        long absCents = Math.abs(cents);
        long whole = absCents / 100;
        long frac = absCents % 100;
        String result = whole + "." + (frac < 10 ? "0" + frac : String.valueOf(frac));
        return negative ? "-" + result : result;
    }

    private static String queryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq == -1) continue;
            String k = java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            if (k.equals(key)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static final int MAX_BODY_BYTES = 256 * 1024; // 256 KB -- generous for this app's tiny JSON payloads

    /**
     * Reads the request body, capped at MAX_BODY_BYTES. Returns null if the body was
     * too large -- in that case a 413 response has ALREADY been sent, so the caller
     * should just `return;` immediately without sending anything further.
     */
    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            sendText(exchange, 413, "application/json", "{\"error\":\"Request body too large\"}");
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void sendText(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private static double parseDoubleOr(String s, double fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }
}
