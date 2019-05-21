package org.sagebionetworks.bridge.models.studies;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.BadRequestException;

public class StudyIdentifierImplTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(StudyIdentifierImpl.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void throwWithNull() {
        new StudyIdentifierImpl(null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void throwWithEmpty() {
        new StudyIdentifierImpl("");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void throwWithBlank() {
        new StudyIdentifierImpl(" ");
    }
}
