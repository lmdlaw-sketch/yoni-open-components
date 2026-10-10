package org.yonicomponents.visiblesend;

import java.util.Collections;

public final class VisibleSendTrackerTest {
    private static final String BODY = "Synthetic message";
    private ConversationFrame frame(String text, int count, long at) {
        return scope("example.chat.alpha", "Test conversation", 7, text, count, at, true);
    }
    private ConversationFrame scope(String pkg, String title, int window, String text, int count,
                                    long at, boolean complete) {
        return new ConversationFrame(pkg, title, window, text,
                Collections.singletonMap(Hashing.sha256Hex(BODY), count), at, complete);
    }
    private VisibleSendTracker armed() {
        VisibleSendTracker t = new VisibleSendTracker();
        assertNull(t.observe(frame(BODY, 0, 100), false));
        assertNull(t.observe(frame(BODY, 0, 200), true));
        return t;
    }
    public void clickClearAndNewBubbleConfirmExactlyOnce() {
        VisibleSendTracker t = armed();
        VisibleSendTracker.ConfirmedSend s = t.observe(frame("", 1, 300), false);
        assertNotNull(s); assertEquals(BODY, s.body); assertEquals("Test conversation", s.title);
        assertNull(t.observe(frame("", 1, 400), false));
    }
    public void clickAloneDoesNotConfirm() { assertNull(armed().observe(frame(BODY, 1, 300), false)); }
    public void clearAloneDoesNotConfirm() {
        VisibleSendTracker t = new VisibleSendTracker();t.observe(frame(BODY, 0, 100), false);
        assertNull(t.observe(frame("", 0, 200), false));
    }
    public void clearedComposerWithoutBubbleDoesNotConfirm() { assertNull(armed().observe(frame("", 0, 300), false)); }
    public void incomingBubbleDoesNotChangeOutgoingCount() { assertNull(armed().observe(frame("", 0, 300), false)); }
    public void twoNewMatchesAreAmbiguous() { assertNull(armed().observe(frame("", 2, 300), false)); }
    public void oldBubbleWithoutIncrementDoesNotConfirm() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,1,100),false);t.observe(frame(BODY,1,200),true);
        assertNull(t.observe(frame("",1,300),false));
    }
    public void repeatedIdenticalActualSendsAreDistinct() {
        VisibleSendTracker t=armed();VisibleSendTracker.ConfirmedSend a=t.observe(frame("",1,300),false);
        t.observe(frame(BODY,1,400),false);t.observe(frame(BODY,1,500),true);
        VisibleSendTracker.ConfirmedSend b=t.observe(frame("",2,600),false);
        assertNotNull(a);assertNotNull(b);assertNotEquals(a.occurrenceToken,b.occurrenceToken);
    }
    public void delayedClickAfterClearCanConfirm() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        assertNull(t.observe(frame("",0,200),false));assertNotNull(t.observe(frame("",1,300),true));
    }
    public void lateClickCannotResurrectClearedDraft() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        t.observe(frame("",1,200),false);assertNull(t.observe(frame("",1,2_000),true));
    }
    public void timeoutRejectsLateBubble() { assertNull(armed().observe(frame("",1,15_201),false)); }
    public void timeoutBoundaryIsInclusive() { assertNotNull(armed().observe(frame("",1,15_200),false)); }
    public void expiredClickCannotRearmWhenTheSameDraftLaterClears() {
        VisibleSendTracker t=armed();t.expire(15_201);
        assertFalse(t.retainsPlaintextDraft());
        t.observe(frame(BODY,0,15_300),false);
        assertFalse(t.retainsPlaintextDraft());
        assertNull(t.observe(frame("",1,15_400),false));
        t.observe(frame("edited",0,15_500),false);t.observe(frame(BODY,0,15_600),false);
        assertNotNull(t.observe(frame("",1,15_700),false));
    }
    public void changedChatInvalidatesPending() {
        assertNull(armed().observe(scope("example.chat.alpha","Other conversation",7,"",1,300,true),false));
    }
    public void changedPackageInvalidatesPending() {
        assertNull(armed().observe(scope("example.chat.beta","Test conversation",7,"",1,300,true),false));
    }
    public void changedWindowInvalidatesPending() {
        assertNull(armed().observe(scope("example.chat.alpha","Test conversation",8,"",1,300,true),false));
    }
    public void incompleteTreeInvalidatesPending() {
        VisibleSendTracker t=armed();assertNull(t.observe(scope("example.chat.alpha","Test conversation",7,"",1,300,false),false));
        assertNull(t.observe(frame("",1,400),false));
    }
    public void nullTreeInvalidatesPending() {
        VisibleSendTracker t=armed();assertNull(t.observe(null,false));assertNull(t.observe(frame("",1,300),false));
    }
    public void explicitResetReleasesDraft() {
        VisibleSendTracker t=armed();t.clear();assertNull(t.observe(frame("",1,300),true));
    }
    public void editedDraftAfterClickIsNotOldSend() {
        VisibleSendTracker t=armed();assertNull(t.observe(frame("changed",0,300),false));
        assertNull(t.observe(frame("",1,400),false));
    }
    public void backwardClockInvalidatesPending() { assertNull(armed().observe(frame("",1,50),false)); }
    public void staleDraftCannotArm() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        t.observe(frame(BODY,0,120_101),true);assertNull(t.observe(frame("",1,120_200),false));
    }
    public void firstObservationIsNeverEnough() {
        assertNull(new VisibleSendTracker().observe(frame("",1,100),true));
    }
    public void whitespaceDraftDoesNotArm() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame("  ",0,100),false);
        t.observe(frame("  ",0,200),true);assertNull(t.observe(frame("",1,300),false));
    }
    public void oversizedDraftDoesNotArm() {
        String large=String.join("",Collections.nCopies(ConversationFrame.MAX_BODY_CHARS+1,"x"));
        assertFalse(frame(large,0,100).complete);
    }

    public void layoutEventsCannotRetainAnUneditedDraftPastItsDeadline() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        t.observe(frame(BODY,0,60_100),false);t.observe(frame(BODY,0,120_101),false);
        assertFalse(t.retainsPlaintextDraft());
        t.observe(frame(BODY,0,180_100),true);
        assertNull(t.observe(frame("",1,180_200),false));
    }
    public void timerExpiryDropsDraftWithoutAnotherObservation() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        assertEquals(120_101,t.nextExpiryMillis());t.expire(120_101);
        assertFalse(t.retainsPlaintextDraft());assertEquals(Long.MAX_VALUE,t.nextExpiryMillis());
    }
    public void genuineEditAfterExpiryCanStartANewDraft() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame("old",0,100),false);
        t.observe(frame("old",0,60_100),false);t.expire(120_101);
        t.observe(frame(BODY,0,120_200),false);t.observe(frame(BODY,0,120_300),true);
        assertNotNull(t.observe(frame("",1,120_400),false));
    }
    public void pendingConfirmationCannotExtendAbsoluteDraftLifetime() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        t.observe(frame(BODY,0,119_100),true);t.expire(120_101);
        assertFalse(t.retainsPlaintextDraft());assertNull(t.observe(frame("",1,120_200),false));
    }
    public void viewportReplacementIsNotGuessedAsANewSend() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,1,100),false);
        t.observe(frame(BODY,1,200),true);assertNull(t.observe(frame("",1,300),false));
        assertNull(t.observe(frame("",1,15_201),false));
    }
    public void newDraftAfterManualClearGetsItsOwnLifetime() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame("discarded",0,100),false);
        t.observe(frame("",0,200),false);t.expire(1_701);
        t.observe(frame(BODY,0,2_000),false);t.observe(frame(BODY,0,2_100),true);
        assertNotNull(t.observe(frame("",1,2_200),false));
    }

    public void keyboardSendRequiresExactlyOneNewBubble() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        VisibleSendTracker.ConfirmedSend send=t.observe(frame("",1,200),false);
        assertNotNull(send);assertEquals(BODY,send.body);
        assertNull(t.observe(frame("",1,300),false));
        t.clear();t.observe(frame(BODY,0,400),false);
        assertNull(t.observe(frame("",2,500),false));
        assertFalse(t.awaitingBubble());
    }
    public void keyboardConfirmationMayFollowClearButHasAShortDeadline() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        assertNull(t.observe(frame("",0,200),false));
        assertNotNull(t.observe(frame("",1,8_200),false));
        t.clear();t.observe(frame(BODY,0,100),false);t.observe(frame("",0,200),false);
        assertNull(t.observe(frame("",1,8_201),false));
        assertFalse(t.retainsPlaintextDraft());
    }
    public void lateClickWidensWindowWithoutRearmingItsClock() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        t.observe(frame("",0,200),false);t.observe(frame("",0,300),true);
        assertEquals(15_201,t.nextExpiryMillis());
        assertNull(t.observe(frame("",1,15_201),false));
    }
    public void keyboardSendDoesNotReviveAnExpiredUneditedDraft() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        t.observe(frame(BODY,0,120_101),false);
        assertFalse(t.retainsPlaintextDraft());
        assertNull(t.observe(frame("",1,120_200),false));
    }
    public void keyboardPendingStillUsesAbsoluteDraftDeadline() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        t.observe(frame(BODY,0,119_100),false);t.observe(frame("",0,119_200),false);
        assertEquals(120_101,t.nextExpiryMillis());t.expire(120_101);
        assertFalse(t.retainsPlaintextDraft());assertNull(t.observe(frame("",1,120_200),false));
    }
    public void surroundingSpacesMatchButAreNotArchived() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(" "+BODY+" ",0,100),false);
        VisibleSendTracker.ConfirmedSend send=t.observe(frame("",1,200),false);
        assertNotNull(send);assertEquals(BODY,send.body);
    }
    public void whitespaceReplacingARealDraftCannotRearmItsOldText() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        t.observe(frame("  ",0,200),false);assertNull(t.observe(frame("",1,300),false));
    }
    public void matchingBubbleLeavingViewportInvalidatesPending() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,1,100),false);
        assertNull(t.observe(frame("",0,200),false));assertFalse(t.awaitingBubble());
        assertNull(t.observe(frame("",2,300),false));
    }
    public void identicalKeyboardSendsHaveDistinctOccurrenceTokens() {
        VisibleSendTracker t=new VisibleSendTracker();t.observe(frame(BODY,0,100),false);
        VisibleSendTracker.ConfirmedSend first=t.observe(frame("",1,200),false);
        t.observe(frame(BODY,1,300),false);
        VisibleSendTracker.ConfirmedSend second=t.observe(frame("",2,400),false);
        assertNotNull(first);assertNotNull(second);assertNotEquals(first.occurrenceToken,second.occurrenceToken);
    }
    public void bodyMatchingPreservesLiteralMarkupWhitespaceAndEmojiJoiners() {
        assertEquals(ConversationFrame.bodyHash("hello"),ConversationFrame.bodyHash(" hello "));
        assertNotEquals(ConversationFrame.bodyHash("ab"),ConversationFrame.bodyHash("a_b"));
        assertNotEquals(ConversationFrame.bodyHash("hello"),ConversationFrame.bodyHash("*hello*"));
        assertNotEquals(ConversationFrame.bodyHash("a b"),ConversationFrame.bodyHash("a\nb"));
        assertNotEquals(ConversationFrame.bodyHash("a b"),ConversationFrame.bodyHash("a  b"));
        assertNotEquals(ConversationFrame.bodyHash("👩👩"),ConversationFrame.bodyHash("👩\u200d👩"));
    }
    // Dependency-free runner; these are ordinary Java assertions, not JUnit.
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        run("clickClearAndNewBubbleConfirmExactlyOnce", new VisibleSendTrackerTest()::clickClearAndNewBubbleConfirmExactlyOnce);
        run("clickAloneDoesNotConfirm", new VisibleSendTrackerTest()::clickAloneDoesNotConfirm);
        run("clearAloneDoesNotConfirm", new VisibleSendTrackerTest()::clearAloneDoesNotConfirm);
        run("clearedComposerWithoutBubbleDoesNotConfirm", new VisibleSendTrackerTest()::clearedComposerWithoutBubbleDoesNotConfirm);
        run("incomingBubbleDoesNotChangeOutgoingCount", new VisibleSendTrackerTest()::incomingBubbleDoesNotChangeOutgoingCount);
        run("twoNewMatchesAreAmbiguous", new VisibleSendTrackerTest()::twoNewMatchesAreAmbiguous);
        run("oldBubbleWithoutIncrementDoesNotConfirm", new VisibleSendTrackerTest()::oldBubbleWithoutIncrementDoesNotConfirm);
        run("repeatedIdenticalActualSendsAreDistinct", new VisibleSendTrackerTest()::repeatedIdenticalActualSendsAreDistinct);
        run("delayedClickAfterClearCanConfirm", new VisibleSendTrackerTest()::delayedClickAfterClearCanConfirm);
        run("lateClickCannotResurrectClearedDraft", new VisibleSendTrackerTest()::lateClickCannotResurrectClearedDraft);
        run("timeoutRejectsLateBubble", new VisibleSendTrackerTest()::timeoutRejectsLateBubble);
        run("timeoutBoundaryIsInclusive", new VisibleSendTrackerTest()::timeoutBoundaryIsInclusive);
        run("expiredClickCannotRearmWhenTheSameDraftLaterClears", new VisibleSendTrackerTest()::expiredClickCannotRearmWhenTheSameDraftLaterClears);
        run("changedChatInvalidatesPending", new VisibleSendTrackerTest()::changedChatInvalidatesPending);
        run("changedPackageInvalidatesPending", new VisibleSendTrackerTest()::changedPackageInvalidatesPending);
        run("changedWindowInvalidatesPending", new VisibleSendTrackerTest()::changedWindowInvalidatesPending);
        run("incompleteTreeInvalidatesPending", new VisibleSendTrackerTest()::incompleteTreeInvalidatesPending);
        run("nullTreeInvalidatesPending", new VisibleSendTrackerTest()::nullTreeInvalidatesPending);
        run("explicitResetReleasesDraft", new VisibleSendTrackerTest()::explicitResetReleasesDraft);
        run("editedDraftAfterClickIsNotOldSend", new VisibleSendTrackerTest()::editedDraftAfterClickIsNotOldSend);
        run("backwardClockInvalidatesPending", new VisibleSendTrackerTest()::backwardClockInvalidatesPending);
        run("staleDraftCannotArm", new VisibleSendTrackerTest()::staleDraftCannotArm);
        run("firstObservationIsNeverEnough", new VisibleSendTrackerTest()::firstObservationIsNeverEnough);
        run("whitespaceDraftDoesNotArm", new VisibleSendTrackerTest()::whitespaceDraftDoesNotArm);
        run("oversizedDraftDoesNotArm", new VisibleSendTrackerTest()::oversizedDraftDoesNotArm);
        run("layoutEventsCannotRetainAnUneditedDraftPastItsDeadline", new VisibleSendTrackerTest()::layoutEventsCannotRetainAnUneditedDraftPastItsDeadline);
        run("timerExpiryDropsDraftWithoutAnotherObservation", new VisibleSendTrackerTest()::timerExpiryDropsDraftWithoutAnotherObservation);
        run("genuineEditAfterExpiryCanStartANewDraft", new VisibleSendTrackerTest()::genuineEditAfterExpiryCanStartANewDraft);
        run("pendingConfirmationCannotExtendAbsoluteDraftLifetime", new VisibleSendTrackerTest()::pendingConfirmationCannotExtendAbsoluteDraftLifetime);
        run("viewportReplacementIsNotGuessedAsANewSend", new VisibleSendTrackerTest()::viewportReplacementIsNotGuessedAsANewSend);
        run("newDraftAfterManualClearGetsItsOwnLifetime", new VisibleSendTrackerTest()::newDraftAfterManualClearGetsItsOwnLifetime);
        run("keyboardSendRequiresExactlyOneNewBubble", new VisibleSendTrackerTest()::keyboardSendRequiresExactlyOneNewBubble);
        run("keyboardConfirmationMayFollowClearButHasAShortDeadline", new VisibleSendTrackerTest()::keyboardConfirmationMayFollowClearButHasAShortDeadline);
        run("lateClickWidensWindowWithoutRearmingItsClock", new VisibleSendTrackerTest()::lateClickWidensWindowWithoutRearmingItsClock);
        run("keyboardSendDoesNotReviveAnExpiredUneditedDraft", new VisibleSendTrackerTest()::keyboardSendDoesNotReviveAnExpiredUneditedDraft);
        run("keyboardPendingStillUsesAbsoluteDraftDeadline", new VisibleSendTrackerTest()::keyboardPendingStillUsesAbsoluteDraftDeadline);
        run("surroundingSpacesMatchButAreNotArchived", new VisibleSendTrackerTest()::surroundingSpacesMatchButAreNotArchived);
        run("whitespaceReplacingARealDraftCannotRearmItsOldText", new VisibleSendTrackerTest()::whitespaceReplacingARealDraftCannotRearmItsOldText);
        run("matchingBubbleLeavingViewportInvalidatesPending", new VisibleSendTrackerTest()::matchingBubbleLeavingViewportInvalidatesPending);
        run("identicalKeyboardSendsHaveDistinctOccurrenceTokens", new VisibleSendTrackerTest()::identicalKeyboardSendsHaveDistinctOccurrenceTokens);
        run("bodyMatchingPreservesLiteralMarkupWhitespaceAndEmojiJoiners", new VisibleSendTrackerTest()::bodyMatchingPreservesLiteralMarkupWhitespaceAndEmojiJoiners);
        System.out.println("VisibleSendTracker regressions: " + passed + " passed, " + failed + " failed.");
        if (failed != 0) throw new AssertionError("VisibleSendTracker regression failures: " + failed);
    }

    private static void run(String name, Runnable test) {
        try {
            test.run();
            passed++;
        } catch (Throwable failure) {
            failed++;
            System.err.println("FAIL " + name + ": " + failure);
            failure.printStackTrace(System.err);
        }
    }

    private static void assertNull(Object actual) {
        if (actual != null) throw new AssertionError("Expected null");
    }
    private static void assertNotNull(Object actual) {
        if (actual == null) throw new AssertionError("Expected a value");
    }
    private static void assertTrue(boolean actual) {
        if (!actual) throw new AssertionError("Expected true");
    }
    private static void assertFalse(boolean actual) {
        if (actual) throw new AssertionError("Expected false");
    }
    private static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Values differ");
    }
    private static void assertNotEquals(Object first, Object second) {
        if (java.util.Objects.equals(first, second)) throw new AssertionError("Values unexpectedly equal");
    }

}

