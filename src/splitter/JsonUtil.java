package splitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small JSON helper covering exactly what this app needs: flat scalar
 * fields (strings/numbers) and arrays of strings. No external JSON library
 * -- the request/response shapes are simple enough that hand-writing this
 * is less overhead than adding and explaining a dependency.
 */
public class JsonUtil {

    /** Parses top-level scalar (string or number/bool) fields into raw strings. Skips arrays. */
    public static Map<String, String> parseFlat(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        int i = 0, n = json.length();
        while (i < n && json.charAt(i) != '{') i++;
        i++;

        while (i < n) {
            while (i < n && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
            if (i >= n || json.charAt(i) == '}') break;

            if (json.charAt(i) != '"') throw new IllegalArgumentException("Malformed JSON near index " + i);
            i++;
            StringBuilder key = new StringBuilder();
            while (i < n && json.charAt(i) != '"') {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < n) { key.append(unescape(json.charAt(i + 1))); i += 2; }
                else { key.append(c); i++; }
            }
            i++;

            while (i < n && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) i++;

            if (i < n && json.charAt(i) == '"') {
                i++;
                StringBuilder val = new StringBuilder();
                while (i < n && json.charAt(i) != '"') {
                    char c = json.charAt(i);
                    if (c == '\\' && i + 1 < n) { val.append(unescape(json.charAt(i + 1))); i += 2; }
                    else { val.append(c); i++; }
                }
                i++;
                map.put(key.toString(), val.toString());
            } else if (i < n && json.charAt(i) == '[') {
                // skip over an array value -- handled separately by parseStringArray
                int depth = 0;
                do {
                    if (json.charAt(i) == '[') depth++;
                    else if (json.charAt(i) == ']') depth--;
                    i++;
                } while (i < n && depth > 0);
                map.put(key.toString(), null);
            } else {
                int start = i;
                while (i < n && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
                map.put(key.toString(), json.substring(start, i).trim());
            }
        }
        return map;
    }

    /** Extracts a JSON array of strings for the given key, e.g. "members": ["Alice","Bob"]. */
    public static List<String> parseStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String needle = "\"" + key + "\"";
        int keyIdx = json.indexOf(needle);
        if (keyIdx == -1) return result;

        int i = keyIdx + needle.length();
        while (i < json.length() && json.charAt(i) != '[') i++;
        if (i >= json.length()) return result;
        i++; // skip '['

        while (i < json.length() && json.charAt(i) != ']') {
            while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
            if (i >= json.length() || json.charAt(i) == ']') break;
            if (json.charAt(i) != '"') { i++; continue; }
            i++; // opening quote
            StringBuilder val = new StringBuilder();
            while (i < json.length() && json.charAt(i) != '"') {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) { val.append(unescape(json.charAt(i + 1))); i += 2; }
                else { val.append(c); i++; }
            }
            i++; // closing quote
            result.add(val.toString());
        }
        return result;
    }

    /** Extracts a JSON object of string->number for the given key, e.g. "shares": {"Alice":20.5,"Bob":15}. */
    public static Map<String, Double> parseNumberObject(String json, String key) {
        Map<String, Double> result = new LinkedHashMap<>();
        String needle = "\"" + key + "\"";
        int keyIdx = json.indexOf(needle);
        if (keyIdx == -1) return result;

        int i = keyIdx + needle.length();
        while (i < json.length() && json.charAt(i) != '{') i++;
        if (i >= json.length()) return result;
        i++; // skip '{'

        while (i < json.length() && json.charAt(i) != '}') {
            while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
            if (i >= json.length() || json.charAt(i) == '}') break;
            if (json.charAt(i) != '"') { i++; continue; }
            i++; // opening quote of name
            StringBuilder name = new StringBuilder();
            while (i < json.length() && json.charAt(i) != '"') {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) { name.append(unescape(json.charAt(i + 1))); i += 2; }
                else { name.append(c); i++; }
            }
            i++; // closing quote

            while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) i++;

            int start = i;
            while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
            String numStr = json.substring(start, i).trim();
            try {
                result.put(name.toString(), Double.parseDouble(numStr));
            } catch (NumberFormatException ignored) {
                // skip malformed entry
            }
        }
        return result;
    }

    private static char unescape(char c) {
        switch (c) {
            case 'n': return '\n';
            case 't': return '\t';
            case 'r': return '\r';
            case '"': return '"';
            case '\\': return '\\';
            default: return c;
        }
    }

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String jsonString(String s) {
        return "\"" + escape(s) + "\"";
    }

    /** Builds a JSON array of strings, e.g. ["Alice","Bob"]. */
    public static String jsonStringArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(jsonString(items.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(jsonString(e.getKey())).append(":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof String) sb.append(jsonString((String) v));
            else if (v instanceof Number || v instanceof Boolean) sb.append(v.toString());
            else sb.append(v.toString()); // caller is responsible for pre-built raw JSON (arrays/objects)
        }
        sb.append("}");
        return sb.toString();
    }
}
