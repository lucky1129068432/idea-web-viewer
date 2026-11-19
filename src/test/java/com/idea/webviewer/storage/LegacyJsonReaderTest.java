package com.idea.webviewer.storage;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyJsonReaderTest {

    @Test
    public void detectsLegacyGsonPayload() {
        assertTrue(LegacyJsonReader.looksLikeLegacyJson("[]"));
        assertTrue(LegacyJsonReader.looksLikeLegacyJson("  [{\"title\":\"a\"}]"));
        assertFalse(LegacyJsonReader.looksLikeLegacyJson(""));
        assertFalse(LegacyJsonReader.looksLikeLegacyJson(null));
        assertFalse(LegacyJsonReader.looksLikeLegacyJson("dGl0bGU="));
    }

    @Test
    public void parsesHistoryRecordsWithTimestamp() {
        String json = "[{\"title\":\"标题\",\"url\":\"https://a.com/?x=1&y=2\",\"timestamp\":1700000000000}]";

        List<String[]> records = LegacyJsonReader.parse(json);

        assertEquals(1, records.size());
        assertEquals("标题", records.get(0)[0]);
        assertEquals("https://a.com/?x=1&y=2", records.get(0)[1]);
        assertEquals("1700000000000", records.get(0)[2]);
    }

    @Test
    public void parsesBookmarksWithoutTimestamp() {
        List<String[]> records = LegacyJsonReader.parse("[{\"title\":\"t\",\"url\":\"u\"}]");

        assertEquals(1, records.size());
        assertEquals("t", records.get(0)[0]);
        assertEquals("u", records.get(0)[1]);
        assertEquals("0", records.get(0)[2]);
    }

    @Test
    public void unescapesGsonOutputIncludingUnicodeAndQuotes() {
        // Gson 会把 < > & 等转成 U+XXXX 形式，把引号转成引号转义
        String json = "[{\"title\":\"a\\u003cb\\u003e \\\"q\\\" \\\\ end\",\"url\":\"u\",\"timestamp\":1}]";

        String title = LegacyJsonReader.parse(json).get(0)[0];

        assertEquals("a<b> \"q\" \\ end", title);
    }

    @Test
    public void emptyArrayYieldsNoRecords() {
        assertTrue(LegacyJsonReader.parse("[]").isEmpty());
        assertTrue(LegacyJsonReader.parse(null).isEmpty());
        assertTrue(LegacyJsonReader.parse("").isEmpty());
    }
}
