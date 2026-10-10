package org.yonicomponents.visiblesend;

import java.util.Collections;

/** Entirely invented input. This example does not inspect a screen or connect to another app. */
public final class SyntheticExample {
    private static final String TEXT = "Synthetic example message";

    private static ConversationFrame frame(String composer, int matchingCount, long time) {
        return new ConversationFrame("example.chat", "Example conversation", 7, composer,
                Collections.singletonMap(ConversationFrame.bodyHash(TEXT), matchingCount), time, true);
    }

    public static void main(String[] args) {
        VisibleSendTracker normal = new VisibleSendTracker();
        normal.observe(frame(TEXT, 0, 100), false);
        VisibleSendTracker.ConfirmedSend inferred = normal.observe(frame("", 1, 200), false);
        if (inferred == null) throw new AssertionError("Expected this synthetic trace to be accepted");
        System.out.println("Synthetic draft -> cleared composer -> one extra match: accepted.");

        VisibleSendTracker ambiguous = new VisibleSendTracker();
        ambiguous.observe(frame(TEXT, 0, 100), false);
        // Interpretation A: a new send appeared. Interpretation B: the draft was deleted and
        // one old matching bubble entered the viewport. Both yield exactly these same frames.
        VisibleSendTracker.ConfirmedSend indistinguishable = ambiguous.observe(frame("", 1, 200), false);
        if (indistinguishable == null) throw new AssertionError("The unchanged model accepts both interpretations");
        System.out.println("Synthetic deleted draft + newly visible old match: also accepted.");
        System.out.println("This demonstrates an evidence gap, not proof of a send or delivery.");

        VisibleSendTracker resetOnContextChange = new VisibleSendTracker();
        resetOnContextChange.observe(frame(TEXT, 0, 100), false);
        resetOnContextChange.clear();
        if (resetOnContextChange.observe(frame("", 1, 200), false) != null) {
            throw new AssertionError("An explicitly invalidated observation must not be confirmed");
        }
        System.out.println("Explicit context invalidation: rejected until a new draft is observed.");
    }
}
