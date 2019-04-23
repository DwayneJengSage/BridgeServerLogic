package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoCompoundActivityDefinition;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;

public class CompoundActivityDefinitionValidatorTest {
    private static final List<SchemaReference> SCHEMA_LIST = ImmutableList.of(new SchemaReference("test-schema",
            null));
    private static final List<SurveyReference> SURVEY_LIST = ImmutableList.of(new SurveyReference("test-survey",
            "test-survey-guid", null));
    private static final String TASK_ID = "test-task";

    // branch coverage
    @Test
    public void validatorSupportsClass() {
        assertTrue(CompoundActivityDefinitionValidator.INSTANCE.supports(CompoundActivityDefinition.class));
    }

    // branch coverage
    @Test
    public void validatorSupportsSubclass() {
        assertTrue(CompoundActivityDefinitionValidator.INSTANCE.supports(DynamoCompoundActivityDefinition.class));
    }

    // branch coverage
    @Test
    public void validatorDoesntSupportClass() {
        assertFalse(CompoundActivityDefinitionValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateNull() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "CompoundActivityDefinition");
        CompoundActivityDefinitionValidator.INSTANCE.validate(null, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateWrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "CompoundActivityDefinition");
        CompoundActivityDefinitionValidator.INSTANCE.validate("wrong class", errors);
        assertTrue(errors.hasErrors());
    }

    @Test
    public void valid() {
        Validate.entityThrowingException(CompoundActivityDefinitionValidator.INSTANCE, makeValidDef());
    }

    @Test
    public void nullStudyId() {
        blankStudyId(null);
    }

    @Test
    public void emptyStudyId() {
        blankStudyId("");
    }

    @Test
    public void blankStudyId() {
        blankStudyId("   ");
    }

    private static void blankStudyId(String studyId) {
        CompoundActivityDefinition def = makeValidDef();
        def.setStudyId(studyId);

        try {
            Validate.entityThrowingException(CompoundActivityDefinitionValidator.INSTANCE, def);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            assertTrue(ex.getMessage().contains("studyId must be specified"));
        }
    }

    @Test
    public void nullTaskId() {
        blankTaskId(null);
    }

    @Test
    public void emptyTaskId() {
        blankTaskId("");
    }

    @Test
    public void blankTaskId() {
        blankTaskId("   ");
    }

    private static void blankTaskId(String taskId) {
        CompoundActivityDefinition def = makeValidDef();
        def.setTaskId(taskId);

        try {
            Validate.entityThrowingException(CompoundActivityDefinitionValidator.INSTANCE, def);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            assertTrue(ex.getMessage().contains("taskId must be specified"));
        }
    }

    // no schemas or surveys
    @Test
    public void noSchemasOrSurveys() {
        CompoundActivityDefinition def = makeValidDef();
        def.setSchemaList(null);
        def.setSurveyList(null);

        try {
            Validate.entityThrowingException(CompoundActivityDefinitionValidator.INSTANCE, def);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            assertTrue(ex.getMessage().contains("compoundActivityDefinition must have at least one schema or at least "
                    + "one survey"));
        }
    }

    private static CompoundActivityDefinition makeValidDef() {
        CompoundActivityDefinition def = CompoundActivityDefinition.create();
        def.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        def.setTaskId(TASK_ID);
        def.setSchemaList(SCHEMA_LIST);
        def.setSurveyList(SURVEY_LIST);
        return def;
    }
}
