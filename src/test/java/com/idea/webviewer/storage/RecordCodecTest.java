package com.idea.webviewer.storage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecordCodecTest {

    @Test
    public void fieldRoundTripKeepsSpecialCharacters() {
        String raw = "中文标题 \"quoted\" | a=1&b=2\n第二行";
        assertEquals(raw, RecordCodec.decodeField(RecordCodec.encodeField(raw)));
    }

    @Test
    public void nullAndEmptyFieldsSurviveRoundTrip() {
        assertEquals("", RecordCodec.decodeField(RecordCodec.encodeField(null)));
        assertEquals("", RecordCodec.decodeField(RecordCodec.encodeField("")));
    }

    @Test
    public void fieldSeparatorInsideValueDoesNotBreakRecord() {
        String title = "a|b|c";
        String url = "https://example.com/?x=1|2";

        List<String> fields = RecordCodec.decodeRecord(RecordCodec.encodeRecord(title, url));

        assertEquals(Arrays.asList(title, url), fields);
    }

    @Test
    public void recordsRoundTripAndIgnoreBlankLines() {
        String first = RecordCodec.encodeRecord("t1", "u1");
        String second = RecordCodec.encodeRecord("t2", "u2");
        String stored = RecordCodec.encodeRecords(Arrays.asList(first, "", second));

        List<List<String>> decoded = RecordCodec.decodeRecords(stored);

        assertEquals(2, decoded.size());
        assertEquals(Arrays.asList("t1", "u1"), decoded.get(0));
        assertEquals(Arrays.asList("t2", "u2"), decoded.get(1));
    }

    @Test
    public void emptyInputsProduceEmptyResults() {
        assertEquals("", RecordCodec.encodeRecords(List.of()));
        assertTrue(RecordCodec.decodeRecords("").isEmpty());
        assertTrue(RecordCodec.decodeRecords(null).isEmpty());
    }

    @Test
    public void invalidBase64DegradesToEmptyString() {
        assertEquals("", RecordCodec.decodeField("不是 base64!!"));
    }
}
