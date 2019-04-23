package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

public class DynamoUpload2Test {
    
    /**
     * We will be returning this object through the API in a later update to the server. For now, 
     * we just want to know we are persisting an object that can return the correct JSON. We 
     * never read this object in <i>from</i> JSON.
     */
    @Test
    public void canSerialize() throws Exception {
        DateTime requestedOn = DateTime.now().withZone(DateTimeZone.UTC);
        DateTime completedOn = DateTime.now().withZone(DateTimeZone.UTC);
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setCompletedBy(UploadCompletionClient.S3_WORKER);
        upload.setRequestedOn(requestedOn.getMillis());
        upload.setCompletedOn(completedOn.getMillis());
        upload.setContentLength(10000L);
        upload.setContentMd5("abc");
        upload.setContentType("application/json");
        upload.setDuplicateUploadId("original-upload-id");
        upload.setFilename("filename.zip");
        upload.setHealthCode("healthCode");
        upload.setRecordId("ABC");
        upload.setStatus(UploadStatus.SUCCEEDED);
        upload.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        upload.setUploadDate(LocalDate.parse("2016-10-10"));
        upload.setUploadId("DEF");
        upload.setValidationMessageList(Lists.newArrayList("message 1", "message 2"));
        upload.setVersion(2L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(upload);
        assertEquals("s3_worker", node.get("completedBy").textValue());
        assertEquals(requestedOn.toString(), node.get("requestedOn").textValue());
        assertEquals(completedOn.toString(), node.get("completedOn").textValue());
        assertEquals(10000L, node.get("contentLength").longValue());
        assertEquals("original-upload-id", node.get("duplicateUploadId").textValue());
        assertEquals("succeeded", node.get("status").textValue());
        assertEquals("2016-10-10", node.get("uploadDate").textValue());
        assertEquals("ABC", node.get("recordId").textValue());
        assertEquals("DEF", node.get("uploadId").textValue());
        assertEquals("abc", node.get("contentMd5").textValue());
        assertEquals("application/json", node.get("contentType").textValue());
        assertEquals("filename.zip", node.get("filename").textValue());
        assertEquals("api", node.get("studyId").textValue());
        assertEquals(2L, node.get("version").longValue());
        assertEquals("healthCode", node.get("healthCode").textValue());
        
        ArrayNode messages = (ArrayNode)node.get("validationMessageList");
        assertEquals("message 1", messages.get(0).textValue());
        assertEquals("message 2", messages.get(1).textValue());
    }
    
    @Test
    public void testGetSetValidationMessageList() {
        DynamoUpload2 upload2 = new DynamoUpload2();

        // initial get gives empty list
        List<String> initialList = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        // set and validate
        upload2.setValidationMessageList(ImmutableList.of("first message"));
        List<String> list2 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        // set should overwrite
        upload2.setValidationMessageList(ImmutableList.of("second message"));
        List<String> list3 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        assertEquals(1, list3.size());
        assertEquals("second message", list3.get(0));
    }

    @Test
    public void testGetAppendValidationMessageList() {
        DynamoUpload2 upload2 = new DynamoUpload2();

        // initial get gives empty list
        List<String> initialList = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        // append and validate
        upload2.appendValidationMessages(ImmutableList.of("first message"));
        List<String> list2 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        // append again
        upload2.appendValidationMessages(ImmutableList.of("second message"));
        List<String> list3 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        assertEquals(2, list3.size());
        assertEquals("first message", list3.get(0));
        assertEquals("second message", list3.get(1));
    }

    @Test
    public void testGetSetAppendValidationMessageList() {
        DynamoUpload2 upload2 = new DynamoUpload2();

        // initial get gives empty list
        List<String> initialList = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        // set on an empty list
        upload2.setValidationMessageList(ImmutableList.of("first message"));
        List<String> list2 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        // append on a set
        upload2.appendValidationMessages(ImmutableList.of("second message"));
        List<String> list3 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        assertEquals(2, list3.size());
        assertEquals("first message", list3.get(0));
        assertEquals("second message", list3.get(1));

        // set should overwrite the append
        upload2.setValidationMessageList(ImmutableList.of("third message"));
        List<String> list4 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(1, list2.size());
        assertEquals("first message", list2.get(0));

        assertEquals(2, list3.size());
        assertEquals("first message", list3.get(0));
        assertEquals("second message", list3.get(1));

        assertEquals(1, list4.size());
        assertEquals("third message", list4.get(0));
    }
    
    @Test
    public void emptyTimestampsAreNotSerialized() throws Exception {
        DynamoUpload2 upload = new DynamoUpload2();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(upload);
        assertNull(node.get("requestedOn"));
        assertNull(node.get("completedOn"));
    }
}
