package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;

public class ExportViaSqsServiceTest {
    private static final String EXPECTED_END_DATE_TIME_STRING = "2017-08-10T15:53:24.769-07:00";

    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
    private static final long MOCK_NOW = DateTime.parse("2017-08-10T15:53:29.769-07:00").getMillis();
    private static final String SQS_MESSAGE_ID = "dummy-message-id";
    private static final String SQS_URL = "dummy-sqs-url";

    @BeforeMethod
    public void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW);
    }

    @AfterMethod
    public void cleanup() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void test() throws Exception {
        // mock config
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getProperty(ExportViaSqsService.CONFIG_KEY_EXPORTER_SQS_QUEUE_URL)).thenReturn(SQS_URL);

        // mock SQS
        AmazonSQSClient mockSqsClient = mock(AmazonSQSClient.class);
        SendMessageResult mockSqsResult = new SendMessageResult().withMessageId(SQS_MESSAGE_ID);
        ArgumentCaptor<String> sqsMessageCaptor = ArgumentCaptor.forClass(String.class);
        when(mockSqsClient.sendMessage(eq(SQS_URL), sqsMessageCaptor.capture())).thenReturn(mockSqsResult);

        // set up test service
        ExportViaSqsService service = new ExportViaSqsService();
        service.setBridgeConfig(mockConfig);
        service.setSqsClient(mockSqsClient);

        // execute and validate
        service.startOnDemandExport(TestConstants.TEST_STUDY);

        String sqsMessageText = sqsMessageCaptor.getValue();
        JsonNode sqsMessageNode = JSON_OBJECT_MAPPER.readTree(sqsMessageText);
        assertEquals(sqsMessageNode.size(), 4);
        assertEquals(sqsMessageNode.get(ExportViaSqsService.REQUEST_KEY_END_DATE_TIME).textValue(),
                EXPECTED_END_DATE_TIME_STRING);
        assertEquals(sqsMessageNode.get(ExportViaSqsService.REQUEST_KEY_TAG).textValue(), "On-Demand Export studyId="
                + TestConstants.TEST_STUDY_IDENTIFIER + " endDateTime=" + EXPECTED_END_DATE_TIME_STRING);
        assertTrue(sqsMessageNode.get(ExportViaSqsService.REQUEST_KEY_USE_LAST_EXPORT_TIME).booleanValue());

        JsonNode studyWhitelistNode = sqsMessageNode.get(ExportViaSqsService.REQUEST_KEY_STUDY_WHITELIST);
        assertEquals(studyWhitelistNode.size(), 1);
        assertEquals(studyWhitelistNode.get(0).textValue(), TestConstants.TEST_STUDY_IDENTIFIER);
    }
}
