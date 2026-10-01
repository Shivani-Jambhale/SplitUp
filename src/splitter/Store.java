package splitter;

import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds all groups in memory, keyed by a short human-shareable code (like
 * "K7QX2P") rather than a UUID or auto-increment ID -- something you can
 * actually read out loud or type into a phone. ConcurrentHashMap so
 * multiple roommates hitting the API at the same time (creating a group,
 * adding an expense) don't race on the map itself; Group's own methods are
 * separately synchronized for its internal state (see Group.java).
 */
public class Store {

    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O/1/I to avoid confusion
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, Group> groups = new ConcurrentHashMap<>();

    public Group createGroup(List<String> members) {
        String code;
        do {
            code = generateCode();
        } while (groups.containsKey(code));

        Group group = new Group(code, members);
        groups.put(code, group);
        return group;
    }

    public Group get(String code) {
        return groups.get(code);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
