package org.sagebionetworks.bridge.upload;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

@SuppressWarnings({ "ConstantConditions", "unchecked" })
public class UploadUtilTest {
    @Test
    public void calculateFieldSizeSimpleField() {
        // { fieldType, expectedBytes }
        Object[][] testCaseArray = {
                { UploadFieldType.ATTACHMENT_BLOB, 20 },
                { UploadFieldType.ATTACHMENT_CSV, 20 },
                { UploadFieldType.ATTACHMENT_JSON_BLOB, 20 },
                { UploadFieldType.ATTACHMENT_JSON_TABLE, 20 },
                { UploadFieldType.ATTACHMENT_V2, 20 },
                { UploadFieldType.BOOLEAN, 5 },
                { UploadFieldType.CALENDAR_DATE, 30 },
                { UploadFieldType.DURATION_V2, 72 },
                { UploadFieldType.FLOAT, 23 },
                { UploadFieldType.INT, 20 },
                { UploadFieldType.LARGE_TEXT_ATTACHMENT, 3000 },
                { UploadFieldType.TIME_V2, 36 },
        };

        for (Object[] testCase : testCaseArray) {
            UploadFieldType fieldType = (UploadFieldType) testCase[0];
            int expectedBytes = (int) testCase[1];
            UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field").withType(fieldType)
                    .build();
            UploadFieldSize fieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(fieldDef));
            assertEquals(fieldSize.getNumBytes(), expectedBytes, "bytes for " + fieldType);
            assertEquals(fieldSize.getNumColumns(), 1, "columns for " + fieldType);
        }
    }

    @Test
    public void calculateFieldSizeNullType() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field").withType(null).build();
        UploadFieldSize fieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(fieldDef));
        assertEquals(fieldSize.getNumBytes(), 0);
        assertEquals(fieldSize.getNumColumns(), 0);
    }

    @Test
    public void calculateFieldSizeStringField() {
        Set<UploadFieldType> stringFieldTypeSet = EnumSet.of(UploadFieldType.INLINE_JSON_BLOB,
                UploadFieldType.SINGLE_CHOICE, UploadFieldType.STRING);

        for (UploadFieldType fieldType : stringFieldTypeSet) {
            UploadFieldDefinition smallStringField = new UploadFieldDefinition.Builder().withName("small-field")
                    .withType(fieldType).withMaxLength(1).build();
            UploadFieldSize smallFieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(smallStringField));
            assertEquals(smallFieldSize.getNumBytes(), 3, "small string field bytes for " + fieldType);
            assertEquals(smallFieldSize.getNumColumns(), 1, "small string field columns for " + fieldType);

            UploadFieldDefinition mediumStringField = new UploadFieldDefinition.Builder().withName("medium-field")
                    .withType(fieldType).withMaxLength(128).build();
            UploadFieldSize mediumFieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(mediumStringField));
            assertEquals(mediumFieldSize.getNumBytes(), 384, "medium string field bytes for " + fieldType);
            assertEquals(mediumFieldSize.getNumColumns(), 1, "medium string field columns for " + fieldType);

            UploadFieldDefinition bigStringField = new UploadFieldDefinition.Builder().withName("big-field")
                    .withType(fieldType).withMaxLength(500).build();
            UploadFieldSize bigFieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(bigStringField));
            assertEquals(bigFieldSize.getNumBytes(), 1500, "big string field bytes for " + fieldType);
            assertEquals(bigFieldSize.getNumColumns(), 1, "big string field columns for " + fieldType);

            UploadFieldDefinition defaultStringField = new UploadFieldDefinition.Builder().withName("default-field")
                    .withType(fieldType).withMaxLength(null).build();
            UploadFieldSize defaultFieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(defaultStringField));
            assertEquals(defaultFieldSize.getNumBytes(), 300, "default string field bytes for " + fieldType);
            assertEquals(defaultFieldSize.getNumColumns(), 1, "default string field columns for " + fieldType);

            UploadFieldDefinition unboundedStringField = new UploadFieldDefinition.Builder()
                    .withName("unbounded-field").withType(fieldType).withUnboundedText(true).build();
            UploadFieldSize unboundedFieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(unboundedStringField));
            assertEquals(unboundedFieldSize.getNumBytes(), 3000, "unbounded string field bytes for " + fieldType);
            assertEquals(unboundedFieldSize.getNumColumns(), 1, "unbounded string field columns for " + fieldType);
        }
    }

    @Test
    public void calculateFieldSizeMultiChoiceWithEmptyList() {
        // Edge case, but we should make sure we handle this.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList().build();
        UploadFieldSize fieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(fieldDef));
        assertEquals(fieldSize.getNumBytes(), 0);
        assertEquals(fieldSize.getNumColumns(), 0);
    }

    @Test
    public void calculateFieldSizeMultiChoiceWithOneChoice() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("foo").build();
        UploadFieldSize fieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(fieldDef));
        assertEquals(fieldSize.getNumBytes(), 5);
        assertEquals(fieldSize.getNumColumns(), 1);
    }

    @Test
    public void calculateFieldSizeMultiChoiceWithManyChoice() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field")
                .withType(UploadFieldType.MULTI_CHOICE)
                .withMultiChoiceAnswerList("foo", "bar", "baz", "qwerty", "asdf").build();
        UploadFieldSize fieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(fieldDef));
        assertEquals(fieldSize.getNumBytes(), 25);
        assertEquals(fieldSize.getNumColumns(), 5);
    }

    @Test
    public void calculateFieldSizeMultiChoiceWithOtherChoice() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("foo", "bar")
                .withAllowOtherChoices(true).build();
        UploadFieldSize fieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(fieldDef));
        assertEquals(fieldSize.getNumBytes(), 3010);
        assertEquals(fieldSize.getNumColumns(), 3);
    }

    @Test
    public void calculateFieldSizeTimestamps() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field")
                .withType(UploadFieldType.TIMESTAMP).build();
        UploadFieldSize fieldSize = UploadUtil.calculateFieldSize(ImmutableList.of(fieldDef));
        assertEquals(fieldSize.getNumBytes(), 35);
        assertEquals(fieldSize.getNumColumns(), 2);
    }

    @Test
    public void calculateFieldSizeMultipleFields() {
        // Make fields
        UploadFieldDefinition stringField = new UploadFieldDefinition.Builder().withName("string-field")
                .withType(UploadFieldType.STRING).withMaxLength(50).build();
        UploadFieldDefinition multiChoiceField = new UploadFieldDefinition.Builder().withName("multi-choice-field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("foo", "bar")
                .withAllowOtherChoices(true).build();
        UploadFieldDefinition timestampField = new UploadFieldDefinition.Builder().withName("timestamp-field")
                .withType(UploadFieldType.TIMESTAMP).build();
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(stringField, multiChoiceField, timestampField);

        // Execute and validate.
        UploadFieldSize fieldSize = UploadUtil.calculateFieldSize(fieldDefList);
        assertEquals(fieldSize.getNumBytes(), 3195);
        assertEquals(fieldSize.getNumColumns(), 6);
    }

    @Test
    public void canonicalize() throws Exception {
        // { inputNode, fieldType, expectedNode, expectedErrorMessage, expectedIsValid }
        Object[][] testCaseArray = {
                // java null
                { null, UploadFieldType.BOOLEAN, null, true },
                // json null
                { NullNode.instance, UploadFieldType.BOOLEAN, NullNode.instance, true },
                // attachment blob
                { new TextNode("dummy attachment"), UploadFieldType.ATTACHMENT_BLOB, new TextNode("dummy attachment"),
                        true },
                // attachment csv
                { new TextNode("dummy attachment"), UploadFieldType.ATTACHMENT_CSV, new TextNode("dummy attachment"),
                        true },
                // attachment json blob
                { new TextNode("dummy attachment"), UploadFieldType.ATTACHMENT_JSON_BLOB,
                        new TextNode("dummy attachment"), true },
                // attachment json table
                { new TextNode("dummy attachment"), UploadFieldType.ATTACHMENT_JSON_TABLE,
                        new TextNode("dummy attachment"), true },
                // attachment v2
                { new TextNode("dummy attachment"), UploadFieldType.ATTACHMENT_V2, new TextNode("dummy attachment"),
                        true },
                // inline json
                { new TextNode("dummy inline JSON"), UploadFieldType.INLINE_JSON_BLOB,
                        new TextNode("dummy inline JSON"), true },
                // large text attachment
                { new TextNode("dummy large text"), UploadFieldType.LARGE_TEXT_ATTACHMENT,
                        new TextNode("dummy large text"), true },
                // boolean zero false
                { new IntNode(0), UploadFieldType.BOOLEAN, BooleanNode.FALSE, true },
                // boolean positive true
                { new IntNode(3), UploadFieldType.BOOLEAN, BooleanNode.TRUE, true },
                // boolean negative true
                { new IntNode(-3), UploadFieldType.BOOLEAN, BooleanNode.TRUE, true },
                // boolean string false
                { new TextNode("false"), UploadFieldType.BOOLEAN, BooleanNode.FALSE, true },
                // boolean mixed case string false
                { new TextNode("fALSE"), UploadFieldType.BOOLEAN, BooleanNode.FALSE, true },
                // boolean string true
                { new TextNode("true"), UploadFieldType.BOOLEAN, BooleanNode.TRUE, true },
                // boolean mixed case string true
                { new TextNode("TrUe"), UploadFieldType.BOOLEAN, BooleanNode.TRUE, true },
                // boolean empty string
                { new TextNode(""), UploadFieldType.BOOLEAN, null, false },
                // boolean invalid string
                { new TextNode("Yes"), UploadFieldType.BOOLEAN, null, false },
                // boolean boolean false
                { BooleanNode.FALSE, UploadFieldType.BOOLEAN, BooleanNode.FALSE, true },
                // boolean boolean true
                { BooleanNode.TRUE, UploadFieldType.BOOLEAN, BooleanNode.TRUE, true },
                // boolean invalid type
                { new DoubleNode(3.14), UploadFieldType.BOOLEAN, null, false },
                // calendar date invalid type
                { new IntNode(1234), UploadFieldType.CALENDAR_DATE, null, false },
                // calendar date empty string
                { new TextNode(""), UploadFieldType.CALENDAR_DATE, null, false },
                // calendar date invalid string
                { new TextNode("June 1, 2016"), UploadFieldType.CALENDAR_DATE, null, false },
                // calendar date valid
                { new TextNode("2016-06-01"), UploadFieldType.CALENDAR_DATE, new TextNode("2016-06-01"), true },
                // calendar date full datetime
                { new TextNode("2016-06-01T11:00Z"), UploadFieldType.CALENDAR_DATE, new TextNode("2016-06-01"), true },
                // duration invalid type
                { new TextNode("2016-06-01"), UploadFieldType.CALENDAR_DATE, new TextNode("2016-06-01"), true },
                // duration empty string
                { new TextNode(""), UploadFieldType.DURATION_V2, null, false },
                // duration invalid string
                { new TextNode("one hour"), UploadFieldType.DURATION_V2, null, false },
                // duration valid
                { new TextNode("PT1H"), UploadFieldType.DURATION_V2, new TextNode("PT1H"), true },
                // float valid
                { new DecimalNode(new BigDecimal("3.14")), UploadFieldType.FLOAT,
                        new DecimalNode(new BigDecimal("3.14")), true },
                // float from int
                { new IntNode(42), UploadFieldType.FLOAT, new IntNode(42), true },
                // float from string
                { new TextNode("2.718"), UploadFieldType.FLOAT, new DecimalNode(new BigDecimal("2.718")), true },
                // float from int string
                { new TextNode("13"), UploadFieldType.FLOAT, new DecimalNode(new BigDecimal("13")), true },
                // float empty string
                { new TextNode(""), UploadFieldType.FLOAT, null, false },
                // float invalid string
                { new TextNode("three point one four"), UploadFieldType.FLOAT, null, false },
                // float invalid type
                { BooleanNode.FALSE, UploadFieldType.FLOAT, null, false },
                // int valid
                { new IntNode(57), UploadFieldType.INT, new IntNode(57), true },
                // int from float
                { new DoubleNode(3.14), UploadFieldType.INT, new BigIntegerNode(new BigInteger("3")), true },
                // int from string
                { new TextNode("1234"), UploadFieldType.INT, new BigIntegerNode(new BigInteger("1234")), true },
                // int from float string
                { new TextNode("12.34"), UploadFieldType.INT, new BigIntegerNode(new BigInteger("12")), true },
                // int empty string
                { new TextNode(""), UploadFieldType.INT, null, false },
                // int invalid string
                { new TextNode("twelve"), UploadFieldType.INT, null, false },
                // int invalid type
                { BooleanNode.TRUE, UploadFieldType.INT, null, false },
                // multi-choice not array
                { new TextNode("[3, 5, 7]"), UploadFieldType.MULTI_CHOICE, null, false },
                // multi-choice valid (some elements aren't strings)
                { BridgeObjectMapper.get().readTree("[true, false, \"Don't Know\"]"), UploadFieldType.MULTI_CHOICE,
                        BridgeObjectMapper.get().readTree("[\"true\", \"false\", \"Don_t Know\"]"), true },
                // multi-choice with sanitizing answers
                { BridgeObjectMapper.get().readTree("[\".foo..bar\", \"$baz\"]"), UploadFieldType.MULTI_CHOICE,
                        BridgeObjectMapper.get().readTree("[\"_foo.bar\", \"_baz\"]"), true },
                // single-choice array
                { BridgeObjectMapper.get().readTree("[\"football\"]"), UploadFieldType.SINGLE_CHOICE,
                        new TextNode("football"), true },
                // single-choice empty array
                { BridgeObjectMapper.get().readTree("[]"), UploadFieldType.SINGLE_CHOICE, null, false },
                // single-choice multi array
                { BridgeObjectMapper.get().readTree("[\"foo\", \"bar\"]"), UploadFieldType.SINGLE_CHOICE, null,
                        false },
                // single-choice array non-string
                { BridgeObjectMapper.get().readTree("[42]"), UploadFieldType.SINGLE_CHOICE, new TextNode("42"), true },
                // single-choice string
                { new TextNode("swimming"), UploadFieldType.SINGLE_CHOICE, new TextNode("swimming"), true },
                // single-choice other type
                { new IntNode(23), UploadFieldType.SINGLE_CHOICE, new TextNode("23"), true },
                // string valid
                { new TextNode("Other"), UploadFieldType.STRING, new TextNode("Other"), true },
                // string from non-string
                { new IntNode(1337), UploadFieldType.STRING, new TextNode("1337"), true },
                // local time invalid type
                { new IntNode(2300), UploadFieldType.TIME_V2, null, false },
                // local time empty string
                { new TextNode(""), UploadFieldType.TIME_V2, null, false },
                // local time invalid string
                { new TextNode("11pm"), UploadFieldType.TIME_V2, null, false },
                // local time valid
                { new TextNode("12:34:56.789"), UploadFieldType.TIME_V2, new TextNode("12:34:56.789"), true },
                // local time full datetime
                { new TextNode("2016-06-01T12:34:56.789-0700"), UploadFieldType.TIME_V2, new TextNode("12:34:56.789"),
                        true },
                // datetime invalid type
                { BooleanNode.TRUE, UploadFieldType.TIMESTAMP, null, false },
                // datetime number
                { new LongNode(1464825450123L), UploadFieldType.TIMESTAMP, new TextNode("2016-06-01T23:57:30.123Z"),
                        true },
                // datetime empty string
                { new TextNode(""), UploadFieldType.TIMESTAMP, null, false },
                // datetime invalid string
                { new TextNode("Jun 1 2016 11:59pm"), UploadFieldType.TIMESTAMP, null, false },
                // datetime valid
                { new TextNode("2016-06-01T23:59:59.999Z"), UploadFieldType.TIMESTAMP,
                        new TextNode("2016-06-01T23:59:59.999Z"), true },
        };

        for (Object[] oneTestCase : testCaseArray) {
            JsonNode inputNode = (JsonNode) oneTestCase[0];
            String inputNodeStr = BridgeObjectMapper.get().writeValueAsString(inputNode);
            UploadFieldType fieldType = (UploadFieldType) oneTestCase[1];

            CanonicalizationResult result = UploadUtil.canonicalize(inputNode, fieldType);
            assertEquals(result.getCanonicalizedValueNode(), oneTestCase[2],
                    "Test case: " + inputNodeStr + " as " + fieldType.name());

            boolean expectedIsValid = (boolean) oneTestCase[3];
            if (expectedIsValid) {
                assertTrue(result.isValid(), "Test case: " + inputNodeStr + " as " + fieldType.name());
                assertNull(result.getErrorMessage(), "Test case: " + inputNodeStr + " as " + fieldType.name());
            } else {
                assertFalse(result.isValid(), "Test case: " + inputNodeStr + " as " + fieldType.name());
                assertNotNull(result.getErrorMessage(), "Test case: " + inputNodeStr + " as " + fieldType.name());
            }
        }
    }

    @Test
    public void convertToStringNode() throws Exception {
        // java null
        {
            JsonNode result = UploadUtil.convertToStringNode(null);
            assertNull(result);
        }

        // json null
        {
            JsonNode result = UploadUtil.convertToStringNode(NullNode.instance);
            assertTrue(result.isNull());
        }

        // { inputNode, outputString }
        Object[][] testCaseArray = {
                // not a string
                { new IntNode(42), "42" },
                // empty string
                { new TextNode(""), "" },
                // is a string
                { new TextNode("foobarbaz"), "foobarbaz" },
        };

        for (Object[] oneTestCase : testCaseArray) {
            JsonNode inputNode = (JsonNode) oneTestCase[0];
            String inputNodeStr = BridgeObjectMapper.get().writeValueAsString(inputNode);

            JsonNode result = UploadUtil.convertToStringNode(inputNode);
            assertTrue(result.isTextual(), "Test case: " + inputNodeStr);
            assertEquals(result.textValue(), oneTestCase[1], "Test case: " + inputNodeStr);
        }

        // arbitrary JSON object
        {
            JsonNode inputNode = BridgeObjectMapper.get().readTree("{\"key\":\"value\"}");
            JsonNode result = UploadUtil.convertToStringNode(inputNode);
            assertTrue(result.isTextual());

            // We don't want to couple to a specific way of JSON formatting. So instead of string checking, convert the
            // string back into JSON and compare JSON directly.
            JsonNode resultNestedJson = BridgeObjectMapper.get().readTree(result.textValue());
            assertEquals(resultNestedJson, inputNode);
        }
    }

    @Test
    public void getJsonNodeAsString() throws Exception {
        // { inputNode, expectedOutputString }
        Object[][] testCaseArray = {
                // java null
                { null, null },
                // json null
                { NullNode.instance, null },
                // not a string
                { BooleanNode.FALSE, "false" },
                // empty string
                { new TextNode(""), "" },
                // is a string
                { new TextNode("my string"), "my string" },
        };

        for (Object[] oneTestCase : testCaseArray) {
            JsonNode inputNode = (JsonNode) oneTestCase[0];
            String inputNodeStr = BridgeObjectMapper.get().writeValueAsString(inputNode);

            String retVal = UploadUtil.getAsString(inputNode);
            assertEquals(retVal, oneTestCase[1], "Test case: " + inputNodeStr);
        }

        // arbitrary JSON object
        {
            JsonNode inputNode = BridgeObjectMapper.get().readTree("{\"key\":\"value\"}");
            String retVal = UploadUtil.getAsString(inputNode);

            // We don't want to couple to a specific way of JSON formatting. So instead of string checking, convert the
            // string back into JSON and compare JSON directly.
            JsonNode resultNestedJson = BridgeObjectMapper.get().readTree(retVal);
            assertEquals(resultNestedJson, inputNode);
        }
    }

    @Test
    public void invalidFieldNameAndAnswerChoice() {
        String[] testCases = {
                null,
                "",
                "   ",
                "_foo",
                "foo_",
                "-foo",
                "foo-",
                ".foo",
                "foo.",
                ".foo",
                "foo.",
                "foo*bar",
                "foo__bar",
                "foo--bar",
                "foo..bar",
                "foo  bar",
                "foo-_-bar",
        };

        for (String oneTestCase : testCases) {
            assertFalse(UploadUtil.isValidAnswerChoice(oneTestCase), oneTestCase + " should be invalid answer choice");
            assertFalse(UploadUtil.isValidSchemaFieldName(oneTestCase), oneTestCase + " should be invalid field name");
        }
    }

    @Test
    public void invalidFieldNameValidAnswerChoice() {
        String[] testCases = {
                "row_etag",
                "row_id",
                "row_ID",
                "row_version",
                "Row_Version",
        };

        for (String oneTestCase : testCases) {
            assertTrue(UploadUtil.isValidAnswerChoice(oneTestCase), oneTestCase + " should be valid answer choice");
            assertFalse(UploadUtil.isValidSchemaFieldName(oneTestCase), oneTestCase + " should be invalid field name");
        }
    }

    @Test
    public void validFieldNameAndAnswerChoice() {
        String[] testCases = {
                "foo",
                "foo_bar",
                "foo-bar",
                "foo.bar",
                "foo bar",
                "foo-bar_baz.qwerty asdf",
                "select",
                "where",
                "time",
                "true",
                "false",
        };

        for (String oneTestCase : testCases) {
            assertTrue(UploadUtil.isValidAnswerChoice(oneTestCase), oneTestCase + " should be valid answer choice");
            assertTrue(UploadUtil.isValidSchemaFieldName(oneTestCase), oneTestCase + " should be valid field name");
        }
    }

    @Test
    public void fieldNameAndAnswerChoiceTooLong() {
        // Generate name with 130 characters.
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            nameBuilder.append("abcdefghij");
        }
        String name = nameBuilder.toString();

        assertFalse(UploadUtil.isValidAnswerChoice(name));
        assertFalse(UploadUtil.isValidSchemaFieldName(name));
    }

    @Test
    public void isCompatibleFieldDef() {
        // { old, new, expected }
        Object[][] testCases = {
                {
                        new UploadFieldDefinition.Builder().withName("field").withType(UploadFieldType.INT)
                                .build(),
                        new UploadFieldDefinition.Builder().withName("field").withType(UploadFieldType.INT)
                                .build(),
                        true
                },
                {
                        new UploadFieldDefinition.Builder().withName("field")
                                .withType(UploadFieldType.ATTACHMENT_V2).withFileExtension(".txt")
                                .withMimeType("text/plain").build(),
                        new UploadFieldDefinition.Builder().withName("field")
                                .withType(UploadFieldType.ATTACHMENT_V2).withFileExtension(".json")
                                .withMimeType("text/json").build(),
                        true
                },
                {
                        new UploadFieldDefinition.Builder().withName("field").withType(UploadFieldType.INT)
                                .build(),
                        new UploadFieldDefinition.Builder().withName("field").withType(UploadFieldType.BOOLEAN)
                                .build(),
                        false
                },
                {
                        new UploadFieldDefinition.Builder().withName("field").withType(UploadFieldType.INT)
                                .build(),
                        new UploadFieldDefinition.Builder().withName("field").withType(UploadFieldType.STRING)
                                .build(),
                        true
                },
                {
                        new UploadFieldDefinition.Builder().withName("foo-field").withType(UploadFieldType.INT)
                                .build(),
                        new UploadFieldDefinition.Builder().withName("bar-field").withType(UploadFieldType.INT)
                                .build(),
                        false
                },
        };

        for (Object[] oneTestCase : testCases) {
            assertEquals(UploadUtil.isCompatibleFieldDef((UploadFieldDefinition) oneTestCase[0],
                    (UploadFieldDefinition) oneTestCase[1]), oneTestCase[2]);
        }
    }

    @Test
    public void isCompatibleFieldDefBoolValueTests() {
        // { oldValue, newValue, expected (allowOther), expected (unboundedText }
        Boolean[][] testCases = {
                { null, null, true, true },
                { null, false, true, true },
                { null, true, true, true },
                { false, null, true, true },
                { false, false, true, true },
                { false, true, true, true },
                { true, null, false, false },
                { true, false, false, false },
                { true, true, true, true },
        };

        for (Boolean[] oneTestCase : testCases) {
            // allowOther
            {
                UploadFieldDefinition oldFieldDef = new UploadFieldDefinition.Builder().withName("field")
                        .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("foo", "bar", "baz")
                        .withAllowOtherChoices(oneTestCase[0]).build();
                UploadFieldDefinition newFieldDef = new UploadFieldDefinition.Builder().withName("field")
                        .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("foo", "bar", "baz")
                        .withAllowOtherChoices(oneTestCase[1]).build();
                assertEquals((Boolean)UploadUtil.isCompatibleFieldDef(oldFieldDef, newFieldDef), (Boolean)oneTestCase[2]);
            }

            // unboundedText
            {
                UploadFieldDefinition oldFieldDef = new UploadFieldDefinition.Builder().withName("field")
                        .withType(UploadFieldType.STRING).withUnboundedText(oneTestCase[0]).build();
                UploadFieldDefinition newFieldDef = new UploadFieldDefinition.Builder().withName("field")
                        .withType(UploadFieldType.STRING).withUnboundedText(oneTestCase[1]).build();
                assertEquals((Boolean)UploadUtil.isCompatibleFieldDef(oldFieldDef, newFieldDef), (Boolean)oneTestCase[3]);
            }
        }
    }

    @Test
    public void isCompatibleFieldDefMaxLengthTests() {
        // { oldValue, newValue, expected }
        Object[][] testCases = {
                { null, null, true },
                { null, 10, false },
                { null, 200, true },
                { 10, null, true },
                { 200, null, false },
                { 10, 10, true },
                { 10, 15,  true },
                { 10, 5, false },
                { 999, 1000, true },
                { 1001, 1001, true },
                { 1000, 1001, true },
        };

        for (Object[] oneTestCase : testCases) {
            UploadFieldDefinition oldFieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType(UploadFieldType.STRING).withMaxLength((Integer) oneTestCase[0]).build();
            UploadFieldDefinition newFieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType(UploadFieldType.STRING).withMaxLength((Integer) oneTestCase[1]).build();
            assertEquals(UploadUtil.isCompatibleFieldDef(oldFieldDef, newFieldDef), oneTestCase[2]);
        }
    }

    @Test
    public void isCompatibleFieldDefMaxLengthWithNonStrings() {
        // { oldType, newType, newMaxLength, expected }
        Object[][] testCases = {
                { UploadFieldType.INT, UploadFieldType.FLOAT, null, true },
                { UploadFieldType.CALENDAR_DATE, UploadFieldType.STRING, 9, false },
                { UploadFieldType.CALENDAR_DATE, UploadFieldType.STRING, 11, true },
                { UploadFieldType.DURATION_V2, UploadFieldType.STRING, 23, false },
                { UploadFieldType.DURATION_V2, UploadFieldType.STRING, 25, true },
                { UploadFieldType.FLOAT, UploadFieldType.STRING, 21, false },
                { UploadFieldType.FLOAT, UploadFieldType.STRING, 23, true },
                { UploadFieldType.INT, UploadFieldType.STRING, 19, false },
                { UploadFieldType.INT, UploadFieldType.STRING, 21, true },
                { UploadFieldType.TIME_V2, UploadFieldType.STRING, 11, false },
                { UploadFieldType.TIME_V2, UploadFieldType.STRING, 13, true },
        };

        for (Object[] oneTestCase : testCases) {
            UploadFieldDefinition oldFieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType((UploadFieldType) oneTestCase[0]).withMaxLength(null).build();
            UploadFieldDefinition newFieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType((UploadFieldType) oneTestCase[1]).withMaxLength((Integer) oneTestCase[2]).build();
            assertEquals(UploadUtil.isCompatibleFieldDef(oldFieldDef, newFieldDef), oneTestCase[3]);
        }
    }

    @Test
    public void isCompatibleFieldDefAnswerList() {
        // { oldList, newList, expected }
        Object[][] testCases = {
                { null, null, true },
                { null, ImmutableList.of("foo", "bar"), true },
                { ImmutableList.of("foo", "bar"), null, false },
                { ImmutableList.of("foo", "bar"), ImmutableList.of("foo", "bar"), true },
                { ImmutableList.of("foo", "bar"), ImmutableList.of("foo"), false },
                { ImmutableList.of("foo", "bar"), ImmutableList.of("foo", "bar", "baz"), true },
                { ImmutableList.of("foo", "bar"), ImmutableList.of("foo", "baz"), false },
        };

        for (Object[] oneTestCase : testCases) {
            UploadFieldDefinition oldFieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList((List<String>) oneTestCase[0])
                    .build();
            UploadFieldDefinition newFieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList((List<String>) oneTestCase[1])
                    .build();
            assertEquals(UploadUtil.isCompatibleFieldDef(oldFieldDef, newFieldDef), oneTestCase[2]);
        }
    }


    @Test
    public void isCompatibleFieldDefRequired() {
        // { oldRequired, newRequired, expected }
        Object[][] testCases = {
                { false, false, true },
                { true, true, true },
                { true, false, true },
                { false, true, true },
        };

        for (Object[] oneTestCase : testCases) {
            UploadFieldDefinition oldFieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType(UploadFieldType.INT).withRequired((boolean) oneTestCase[0]).build();
            UploadFieldDefinition newFieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType(UploadFieldType.INT).withRequired((boolean) oneTestCase[1]).build();
            assertEquals(UploadUtil.isCompatibleFieldDef(oldFieldDef, newFieldDef), oneTestCase[2]);
        }
    }


    @Test
    public void nullCalendarDate() {
        assertNull(UploadUtil.parseIosCalendarDate(null));
    }

    @Test
    public void emptyCalendarDate() {
        assertNull(UploadUtil.parseIosCalendarDate(""));
    }

    @Test
    public void blankCalendarDate() {
        assertNull(UploadUtil.parseIosCalendarDate("   "));
    }

    @Test
    public void shortMalformedCalendarDate() {
        assertNull(UploadUtil.parseIosCalendarDate("Xmas2015"));
    }

    @Test
    public void longMalformedCalendarDate() {
        assertNull(UploadUtil.parseIosCalendarDate("December 25 2015"));
    }

    @Test
    public void validCalendarDate() {
        LocalDate date = UploadUtil.parseIosCalendarDate("2015-12-25");
        assertEquals(date.getYear(), 2015);
        assertEquals(date.getMonthOfYear(), 12);
        assertEquals(date.getDayOfMonth(), 25);
    }

    @Test
    public void timestampCalendarDate() {
        LocalDate date = UploadUtil.parseIosCalendarDate("2015-12-25T14:33-0800");
        assertEquals(date.getYear(), 2015);
        assertEquals(date.getMonthOfYear(), 12);
        assertEquals(date.getDayOfMonth(), 25);
    }

    @Test
    public void truncatesIntoValidCalendarDate() {
        LocalDate date = UploadUtil.parseIosCalendarDate("2015-12-25 @ lunchtime");
        assertEquals(date.getYear(), 2015);
        assertEquals(date.getMonthOfYear(), 12);
        assertEquals(date.getDayOfMonth(), 25);
    }

    @Test
    public void nullTimestamp() {
        assertNull(UploadUtil.parseIosTimestamp(null));
    }

    @Test
    public void emptyTimestamp() {
        assertNull(UploadUtil.parseIosTimestamp(""));
    }

    @Test
    public void blankTimestamp() {
        assertNull(UploadUtil.parseIosTimestamp("   "));
    }

    @Test
    public void calendarDate() {
        assertNull(UploadUtil.parseIosTimestamp("2015-08-26"));
    }

    @Test
    public void shortMalformedTimestamp() {
        assertNull(UploadUtil.parseIosTimestamp("foo"));
    }

    @Test
    public void longMalformedTimestamp() {
        assertNull(UploadUtil.parseIosTimestamp("August 26, 2015 @ 4:54:04pm PDT"));
    }

    @Test
    public void properTimestampUtc() {
        String timestampStr = "2015-08-26T23:54:04Z";
        long expectedMillis = DateTime.parse(timestampStr).getMillis();
        DateTime parsedTimestamp = UploadUtil.parseIosTimestamp(timestampStr);
        assertEquals(parsedTimestamp.getMillis(), expectedMillis);
    }

    @Test
    public void properTimestampWithTimezone() {
        String timestampStr = "2015-08-26T16:54:04-07:00";
        long expectedMillis = DateTime.parse(timestampStr).getMillis();
        DateTime parsedTimestamp = UploadUtil.parseIosTimestamp(timestampStr);
        assertEquals(parsedTimestamp.getMillis(), expectedMillis);
    }

    @Test
    public void iosTimestamp() {
        long expectedMillis = DateTime.parse("2015-08-26T16:54:04-07:00").getMillis();
        DateTime parsedTimestamp = UploadUtil.parseIosTimestamp("2015-08-26 16:54:04 -0700");
        assertEquals(parsedTimestamp.getMillis(), expectedMillis);
    }

    @Test
    public void sanitizeFieldNames() {
        // Test cases: map with 2 fields, one of which needs to be sanitized.
        // Use an immutable map. If we attempt to modify the original map, this will throw.
        // Use a map of strings, for simplicity of testing.
        Map<String, String> inputMap = ImmutableMap.<String, String>builder().put("foo", "bar")
                .put("sanitize!@#$this", "sanitize this's value").build();

        // Execute and validate.
        Map<String, String> outputMap = UploadUtil.sanitizeFieldNames(inputMap);
        assertEquals(outputMap.size(), 2);
        assertEquals(outputMap.get("foo"), "bar");
        assertEquals(outputMap.get("sanitize____this"), "sanitize this's value");
    }
}
