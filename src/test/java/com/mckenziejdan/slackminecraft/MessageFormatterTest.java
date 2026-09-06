package com.mckenziejdan.slackminecraft;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MessageFormatterTest {
    @Test void escapesSlackMarkupBeforeInsertingKnownMentions() {
        assertEquals("&lt;!channel&gt; &amp; &lt;@U2&gt; <@U1>",
                MessageFormatter.toSlack("<!channel> & <@U2> @dan", Map.of("dan", "U1")));
    }
    @Test void leavesUnknownMentionsAndEmailAddressesAlone() {
        assertEquals("mail@dan.com @unknown <@U1>",
                MessageFormatter.toSlack("mail@dan.com @unknown @DAN", Map.of("dan", "U1")));
    }
    @Test void replacementNamesCanContainRegexSpecialCharacters() {
        assertEquals("Hi @Dan $5\\test", MessageFormatter.toMinecraft("Hi <@U1>", id -> "Dan $5\\test"));
    }
    @Test void decodesSlackLinksAndEntitiesWithoutDoubleDecoding() {
        assertEquals("Docs (https://example.com) <hello> &lt;", MessageFormatter.toMinecraft(
                "<https://example.com|Docs> &lt;hello&gt; &amp;lt;", id -> id));
    }
}
