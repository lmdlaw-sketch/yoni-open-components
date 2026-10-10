package org.yonicomponents.visiblesend;

import java.util.UUID;

/**
 * Pure state machine: draft -> cleared -> exactly one new matching outgoing bubble.
 * A send click is optional and only widens the bounded confirmation window.
 * A click or a cleared field alone never publishes text. Caller confines this to one thread.
 */
public final class VisibleSendTracker {
    public static final long DRAFT_TTL_MS = 120_000L;
    public static final long CONFIRM_TTL_MS = 15_000L;
    public static final long CLEAR_CONFIRM_TTL_MS = 8_000L;
    private ConversationFrame previous;
    private ConversationFrame draftFrame;
    private String previousComposerHash;
    private boolean previousHadComposer;
    private Pending pending;
    private long draftChangedAt;
    private long clearedAt;
    private boolean draftDiscarded;

    public static final class ConfirmedSend {
        public final String occurrenceToken;
        public final String packageName;
        public final String title;
        public final String body;
        public final long observedElapsed;
        private ConfirmedSend(Pending pending, ConversationFrame frame) {
            occurrenceToken = pending.token;
            packageName = frame.packageName;
            title = frame.title;
            body = pending.body;
            observedElapsed = frame.elapsedMillis;
        }
    }

    private static final class Pending {
        final String token = UUID.randomUUID().toString();
        final String body;
        final String hash;
        final int beforeCount;
        final ConversationFrame scope;
        final long armedAt;
        boolean clicked;
        Pending(ConversationFrame before, long elapsed, boolean clicked) {
            body = before.composer.trim();
            hash = ConversationFrame.bodyHash(body);
            beforeCount = before.outgoingCount(hash);
            scope = before;
            armedAt = elapsed;
            this.clicked = clicked;
        }
        long ttl() { return clicked ? CONFIRM_TTL_MS : CLEAR_CONFIRM_TTL_MS; }
    }

    public ConfirmedSend observe(ConversationFrame frame, boolean sendClick) {
        if (frame == null || !frame.complete) {
            clear();
            return null;
        }
        if (previous == null || !frame.sameConversation(previous)
                || frame.elapsedMillis < previous.elapsedMillis) {
            clear();
            remember(frame);
            return null;
        }
        expire(frame.elapsedMillis);
        if (pending != null && (frame.elapsedMillis - pending.armedAt > pending.ttl()
                || !frame.sameConversation(pending.scope))) {
            pending = null;
            draftFrame = null;
        }
        boolean cleared = frame.composer.isEmpty();
        boolean clearedNow = cleared && previousHadComposer;
        if (clearedNow) clearedAt = frame.elapsedMillis;
        boolean recentDraft = draftFrame != null
                && frame.elapsedMillis - draftChangedAt <= DRAFT_TTL_MS;
        // Some frameworks deliver the click just after composer-clear. Retain the old
        // baseline briefly. A clear can arm observation, but only bubble proof confirms it.
        boolean clickContext = !cleared || (clearedAt > 0
                && frame.elapsedMillis - clearedAt <= 1_500L);
        if (pending == null && recentDraft && ((sendClick && clickContext) || clearedNow)) {
            pending = new Pending(draftFrame, frame.elapsedMillis, sendClick);
        } else if (pending != null && sendClick && clickContext) {
            // A late click belongs to this pending observation; never renew its deadline.
            pending.clicked = true;
        }
        if (pending != null) {
            if (!cleared && !frame.composer.trim().equals(pending.body)) {
                pending = null;
            } else if (cleared) {
                int after = frame.outgoingCount(pending.hash);
                if (after == pending.beforeCount + 1) {
                    ConfirmedSend send = new ConfirmedSend(pending, frame);
                    pending = null;
                    draftFrame = null;
                    clearedAt = 0;
                    remember(frame);
                    return send;
                }
                if (after > pending.beforeCount + 1 || after < pending.beforeCount) {
                    pending = null;
                    draftFrame = null;
                }
            }
        }
        remember(frame);
        return null;
    }

    private void remember(ConversationFrame frame) {
        String hash = Hashing.sha256Hex(frame.composer);
        boolean changed = !hash.equals(previousComposerHash);
        if (!frame.composer.isEmpty() && changed) {
            draftChangedAt = frame.elapsedMillis;
            clearedAt = 0;
            draftDiscarded = false;
        }
        if (!frame.composer.isEmpty() && frame.composer.trim().isEmpty()) draftFrame = null;
        if (!frame.composer.trim().isEmpty() && pending == null && !draftDiscarded
                && frame.elapsedMillis - draftChangedAt <= DRAFT_TTL_MS) draftFrame = frame;
        previousComposerHash = hash;
        previousHadComposer = !frame.composer.isEmpty();
        previous = frame.withoutComposer();
        expire(frame.elapsedMillis);
    }

    /** Drop plaintext references on an absolute deadline even if no UI event arrives. */
    public void expire(long now) {
        if (draftFrame != null && now - draftChangedAt > DRAFT_TTL_MS) draftFrame = null;
        if (pending != null && (now - pending.armedAt > pending.ttl()
                || now - draftChangedAt > DRAFT_TTL_MS)) {
            pending = null;
            draftFrame = null;
            // An expired send attempt must not become a fresh keyboard-send attempt on
            // its later clear. Only an actual composer edit can start another draft.
            draftDiscarded = true;
        }
        if (pending == null && clearedAt > 0 && now - clearedAt > 1_500L) draftFrame = null;
    }

    public long nextExpiryMillis() {
        long next = Long.MAX_VALUE;
        if (draftFrame != null || pending != null) next = draftChangedAt + DRAFT_TTL_MS + 1;
        if (pending != null) next = Math.min(next, pending.armedAt + pending.ttl() + 1);
        else if (draftFrame != null && clearedAt > 0) next = Math.min(next, clearedAt + 1_501L);
        return next;
    }

    // Package-private, content-free assertion for expiry regressions.
    boolean retainsPlaintextDraft() { return draftFrame != null || pending != null; }

    boolean awaitingBubble() { return pending != null; }

    public void clear() {
        previous = null;
        previousComposerHash = null;
        previousHadComposer = false;
        pending = null;
        draftFrame = null;
        draftChangedAt = 0;
        clearedAt = 0;
        draftDiscarded = false;
    }
}

