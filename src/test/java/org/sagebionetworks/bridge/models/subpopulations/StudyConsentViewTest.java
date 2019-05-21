package org.sagebionetworks.bridge.models.subpopulations;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

public class StudyConsentViewTest {

    @Test
    public void testSerialization() throws Exception {
        long createdOn = 200L;
        
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setCreatedOn(createdOn);
        consent.setSubpopulationGuid("test");
        consent.setStoragePath("test."+createdOn);
        consent.setVersion(2L);
        
        StudyConsentView view = new StudyConsentView(consent, "<document/>");
        
        String json = BridgeObjectMapper.get().writeValueAsString(view);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("documentContent").asText(), "<document/>");
        assertEquals(node.get("createdOn").asText(), "1970-01-01T00:00:00.200Z");
        assertEquals(node.get("subpopulationGuid").asText(), "test");
        assertEquals(node.get("type").asText(), "StudyConsent");
        
        StudyConsentForm form = BridgeObjectMapper.get().readValue(json, StudyConsentForm.class);
        assertEquals(form.getDocumentContent(), "<document/>");
    }
    
}
