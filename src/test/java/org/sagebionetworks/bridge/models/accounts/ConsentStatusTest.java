package org.sagebionetworks.bridge.models.accounts;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ConsentStatusTest {
    
    private static final DateTime TIMESTAMP = DateTime.now(DateTimeZone.UTC);
    private Map<SubpopulationGuid,ConsentStatus> statuses;
    
    @BeforeMethod
    public void before() {
        statuses = Maps.newHashMap();
    }
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(ConsentStatus.class).allFieldsShouldBeUsed().verify(); 
    }
    
    // Will be stored as JSON in the the session, via the User object, so it must serialize.
    @Test
    public void canSerialize() throws Exception {
        ConsentStatus status = new ConsentStatus.Builder().withName("Name").withGuid(SubpopulationGuid.create("GUID"))
                .withConsented(true).withRequired(true).withSignedMostRecentConsent(true)
                .withSignedOn(TIMESTAMP.getMillis()).build();

        String json = BridgeObjectMapper.get().writeValueAsString(status);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("name").textValue(), "Name");
        assertEquals(node.get("subpopulationGuid").textValue(), "GUID");
        assertTrue(node.get("required").booleanValue());
        assertTrue(node.get("consented").booleanValue());
        assertTrue(node.get("signedMostRecentConsent").booleanValue());
        assertEquals(node.get("signedOn").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("type").textValue(), "ConsentStatus");
        
        ConsentStatus status2 = BridgeObjectMapper.get().readValue(json, ConsentStatus.class);
        assertEquals(status2, status);
    }
    
    @Test
    public void consentStatusWithoutSignedOnTimestamp() throws Exception {
        ConsentStatus status = new ConsentStatus.Builder().withName("Name").withGuid(SubpopulationGuid.create("GUID"))
                .withConsented(true).withRequired(true).withSignedMostRecentConsent(true)
                .build();
        JsonNode node = BridgeObjectMapper.get().valueToTree(status);
        
        assertNull(node.get("signedOn"));
    }

    @Test
    public void forSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString("test");
        
        assertNull(statuses.get(subpop.getGuid()));
        
        ConsentStatus status1 = new ConsentStatus("Name1", "foo", false, false, false, TIMESTAMP.getMillis());
        ConsentStatus status2 = new ConsentStatus("Name2", "test", false, false, false, TIMESTAMP.getMillis());
        statuses.put(subpop.getGuid(), status1);
        statuses.put(subpop.getGuid(), status2);
        
        assertEquals(statuses.get(subpop.getGuid()), status2);
    }
    
    private void add(ConsentStatus status) {
        statuses.put(SubpopulationGuid.create(status.getSubpopulationGuid()), status);
    }
    
    @Test
    public void isUserConsented() {
        assertFalse(ConsentStatus.isUserConsented(statuses));
        
        add(TestConstants.REQUIRED_UNSIGNED);
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.OPTIONAL_SIGNED_CURRENT);
        add(TestConstants.OPTIONAL_SIGNED_OBSOLETE);
        assertFalse(ConsentStatus.isUserConsented(statuses));
        
        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_UNSIGNED);
        add(TestConstants.OPTIONAL_UNSIGNED);
        add(TestConstants.OPTIONAL_SIGNED_OBSOLETE);
        assertFalse(ConsentStatus.isUserConsented(statuses));

        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_SIGNED_OBSOLETE);
        add(TestConstants.OPTIONAL_UNSIGNED);
        add(TestConstants.OPTIONAL_SIGNED_OBSOLETE);
        assertTrue(ConsentStatus.isUserConsented(statuses));
    }

    @Test
    public void isConsentCurrent() {
        assertFalse(ConsentStatus.isConsentCurrent(statuses));
        
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_SIGNED_OBSOLETE);
        add(TestConstants.OPTIONAL_UNSIGNED);
        assertFalse(ConsentStatus.isConsentCurrent(statuses));
        
        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.OPTIONAL_SIGNED_OBSOLETE); // only required consents are considered 
        assertTrue(ConsentStatus.isConsentCurrent(statuses));
        
        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.OPTIONAL_UNSIGNED);
        assertTrue(ConsentStatus.isConsentCurrent(statuses));
    }
    
    @Test
    public void hasOnlyOneSignedConsent() {
        assertFalse(ConsentStatus.hasOnlyOneSignedConsent(statuses));
        
        add(TestConstants.REQUIRED_UNSIGNED);
        add(TestConstants.OPTIONAL_SIGNED_OBSOLETE);
        assertTrue(ConsentStatus.hasOnlyOneSignedConsent(statuses));
        
        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_SIGNED_OBSOLETE);
        add(TestConstants.REQUIRED_UNSIGNED);
        assertFalse(ConsentStatus.hasOnlyOneSignedConsent(statuses));
        
        statuses.clear();
        add(TestConstants.REQUIRED_SIGNED_CURRENT);
        add(TestConstants.REQUIRED_UNSIGNED);
        add(TestConstants.OPTIONAL_UNSIGNED);
        assertTrue(ConsentStatus.hasOnlyOneSignedConsent(statuses));
    }
}
