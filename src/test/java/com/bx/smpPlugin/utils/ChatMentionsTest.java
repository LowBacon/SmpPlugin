// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMentionsTest {

    @Test
    void findsNameAsWholeWordInAnyCaseWithOrWithoutAt() {
        assertTrue(ChatMentions.mentions("hey steve come here", "Steve"));
        assertTrue(ChatMentions.mentions("@STEVE look", "Steve"));
        assertTrue(ChatMentions.mentions("steve!", "Steve"));
        assertFalse(ChatMentions.mentions("stevens house", "Steve"));
        assertFalse(ChatMentions.mentions("bigsteve", "Steve"));
        assertFalse(ChatMentions.mentions("anything", "ab"));
    }

    @Test
    void highlightRewritesEveryMentionWithTheRealSpelling() {
        assertEquals("hi [@Steve] and [@Steve]!",
                ChatMentions.highlight("hi steve and @STEVE!", "Steve", "[", "]"));
        assertEquals("no one here", ChatMentions.highlight("no one here", "Steve", "[", "]"));
    }

    @Test
    void highlightIsSafeWithRegexCharactersInTheReplacement() {
        assertEquals("$@Bob$", ChatMentions.highlight("bob", "Bob", "$", "$"));
    }

    @Test
    void typedColorCodesAreRemovedFromMessages() {
        assertEquals("hello world", TypedColorPolicy.stripCodes("&chello &#FF00FFworld"));
        assertEquals("hi", TypedColorPolicy.stripCodes("&k&lhi&r"));
        assertEquals("a b", TypedColorPolicy.stripCodes("{#123456}a <#abcdef>b"));
        assertEquals("#ff00ff stays", TypedColorPolicy.stripCodes("#ff00ff stays"));
    }
}
