package org.sagebionetworks.bridge.validators;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportType;

public class ReportDataKeyValidatorTest {
    
    @Test
    public void validStudyKey() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withIdentifier("foo")
                .withReportType(ReportType.STUDY).build();
        
        assertEquals(key.getKeyString(), "foo:api");
    }
    
    @Test
    public void validParticipantKey() {
        ReportDataKey key = new ReportDataKey.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withHealthCode("ABC")
                .withIdentifier("foo")
                .withReportType(ReportType.PARTICIPANT).build();

        assertEquals(key.getKeyString(), "ABC:foo:api");
    }
    
    @Test
    public void reportTypeMissing() {
        test(() -> {
            new ReportDataKey.Builder().withStudyIdentifier(TestConstants.TEST_STUDY).withHealthCode("ABC")
                    .withIdentifier("foo").build();
            
        }, "reportType", "is required");
    }
    
    @Test
    public void healthCodeMissing() {
        test(() -> {
            new ReportDataKey.Builder().withStudyIdentifier(TestConstants.TEST_STUDY).withIdentifier("foo")
                    .withReportType(ReportType.PARTICIPANT).build();
        }, "healthCode", "is required for participant reports");
    }
    
    @Test
    public void identifierMissing() {
        test(() -> {
            new ReportDataKey.Builder().withStudyIdentifier(TestConstants.TEST_STUDY)
                    .withReportType(ReportType.STUDY).build();
        }, "identifier", "cannot be missing or blank");
    }
    
    @Test
    public void identifierInvalid() {
        test(() -> {
            new ReportDataKey.Builder().withStudyIdentifier(TestConstants.TEST_STUDY).withIdentifier("My Report")
                    .withReportType(ReportType.STUDY).build();
        }, "identifier", "can only contain letters, numbers, underscore and dash");
    }
    
    @Test
    public void studyIdentifierMissing() {
        test(() -> {
            new ReportDataKey.Builder()
                    .withIdentifier("foo")
                    .withReportType(ReportType.STUDY).build();
        }, "studyId", "is required");
    }
    
    private void test(Runnable runnable, String fieldName, String error) {
        try {
            runnable.run();
        } catch(InvalidEntityException e) {
            String message = e.getErrors().get(fieldName).get(0);
            assertEquals(message, fieldName + " " + error);
        }
    }
}
