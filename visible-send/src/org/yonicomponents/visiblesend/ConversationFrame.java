package org.yonicomponents.visiblesend;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded in-memory observation; never serialized or represented as a delivery receipt. */
public final class ConversationFrame {
    public static final int MAX_BODY_CHARS = 16_384;
    public static final int MAX_TITLE_CHARS = 256;
    public final String packageName;
    public final String title;
    public final int windowId;
    public final String composer;
    public final long elapsedMillis;
    public final boolean complete;
    public final String sendLabel, sendClass;
    private final Map<String, Integer> outgoingCounts;

    public ConversationFrame(String packageName, String title, int windowId, String composer,
                             Map<String, Integer> outgoingCounts, long elapsedMillis,
                             boolean complete) {
        this(packageName, title, windowId, composer, outgoingCounts, elapsedMillis, complete, "", "");
    }
    public ConversationFrame(String packageName, String title, int windowId, String composer,
                             Map<String, Integer> outgoingCounts, long elapsedMillis,
                             boolean complete, String sendLabel, String sendClass) {
        this.packageName = packageName;
        this.title = title;
        this.windowId = windowId;
        this.composer = composer == null ? "" : composer;
        this.elapsedMillis = elapsedMillis;
        this.sendLabel = sendLabel;
        this.sendClass = sendClass;
        boolean validCounts = outgoingCounts.size() <= 128;
        for (Integer count : outgoingCounts.values()) {
            validCounts &= count != null && count >= 0 && count <= 128;
        }
        this.complete = complete && packageName != null && title != null
                && !title.trim().isEmpty() && title.length() <= MAX_TITLE_CHARS
                && this.composer.length() <= MAX_BODY_CHARS && windowId >= 0 && validCounts;
        this.outgoingCounts = Collections.unmodifiableMap(new HashMap<>(outgoingCounts));
    }

    /** Only surrounding whitespace is normalized; literal markup and emoji stay distinct. */
    public static String bodyHash(String text) {
        return Hashing.sha256Hex(text == null ? "" : text.trim());
    }

    /** Scope/counts only: the tracker must not retain a second plaintext draft in previous. */
    public ConversationFrame withoutComposer() {
        return new ConversationFrame(packageName, title, windowId, "", outgoingCounts, elapsedMillis, complete,
                sendLabel, sendClass);
    }

    public boolean sameConversation(ConversationFrame other) {
        return other != null && windowId == other.windowId
                && Objects.equals(packageName, other.packageName) && Objects.equals(title, other.title);
    }

    public int outgoingCount(String bodyHash) {
        return outgoingCounts.getOrDefault(bodyHash, 0);
    }
}

