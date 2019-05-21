package org.sagebionetworks.bridge.models.schedules;

import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.OperatingSystem;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ScheduleCriteriaTest {
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(ScheduleCriteria.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void setsAreNeverNull() {
        Schedule schedule = TestUtils.getSchedule("A schedule");
        
        Criteria criteria = Criteria.create();
        criteria.setMinAppVersion(OperatingSystem.IOS, 2);
        criteria.setMaxAppVersion(OperatingSystem.IOS, 10);
        
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        
        assertTrue(scheduleCriteria.getCriteria().getAllOfGroups().isEmpty());
        assertTrue(scheduleCriteria.getCriteria().getNoneOfGroups().isEmpty());
        
        // Passing nulls into the builder's add* methods... this is always a mistake and should throw a NPE.
    }
}
