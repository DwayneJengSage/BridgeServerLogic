package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleImportStatus;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleType;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

public class SharedModuleServiceTest {
    private static final String MODULE_ID = "test-module";
    private static final String MODULE_NAME = "Test Module";
    private static final int MODULE_VERSION = 3;
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;

    private static final long LOCAL_SURVEY_CREATED_ON = 567890L;
    private static final String LOCAL_SURVEY_GUID = "local-survey-guid";
    private static final GuidCreatedOnVersionHolder LOCAL_SURVEY_KEY = new GuidCreatedOnVersionHolderImpl(
            LOCAL_SURVEY_GUID, LOCAL_SURVEY_CREATED_ON);

    private static final long SHARED_SURVEY_CREATED_ON = 123456L;
    private static final String SHARED_SURVEY_GUID = "shared-survey-guid";
    private static final GuidCreatedOnVersionHolder SHARED_SURVEY_KEY = new GuidCreatedOnVersionHolderImpl(
            SHARED_SURVEY_GUID, SHARED_SURVEY_CREATED_ON);

    private SharedModuleMetadataService mockMetadataService;
    private UploadSchemaService mockSchemaService;
    private SurveyService mockSurveyService;
    private SharedModuleService moduleService;

    @BeforeMethod
    public void before() {
        mockMetadataService = mock(SharedModuleMetadataService.class);
        mockSchemaService = mock(UploadSchemaService.class);
        mockSurveyService = mock(SurveyService.class);

        moduleService = new SharedModuleService();
        moduleService.setModuleMetadataService(mockMetadataService);
        moduleService.setSchemaService(mockSchemaService);
        moduleService.setSurveyService(mockSurveyService);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void byIdAndVersionNullId() {
        moduleService.importModuleByIdAndVersion(TestConstants.TEST_STUDY, null, MODULE_VERSION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void byIdAndVersionEmptyId() {
        moduleService.importModuleByIdAndVersion(TestConstants.TEST_STUDY, "", MODULE_VERSION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void byIdAndVersionBlankId() {
        moduleService.importModuleByIdAndVersion(TestConstants.TEST_STUDY, "   ", MODULE_VERSION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void byIdAndVersionNegativeVersion() {
        moduleService.importModuleByIdAndVersion(TestConstants.TEST_STUDY, MODULE_ID, -1);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void byIdAndVersionZeroVersion() {
        moduleService.importModuleByIdAndVersion(TestConstants.TEST_STUDY, MODULE_ID, 0);
    }

    @Test
    public void byIdAndVersionSchemaSuccess() {
        // mock metadata service
        when(mockMetadataService.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(
                makeValidMetadataWithSchema());

        ArgumentCaptor<UploadSchema> schemaArgumentCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSchemaService.createSchemaRevisionV4(any(), schemaArgumentCaptor.capture())).thenReturn(null);

        // mock schema service
        UploadSchema sharedSchema = UploadSchema.create();
        when(mockSchemaService.getUploadSchemaByIdAndRev(BridgeConstants.SHARED_STUDY_ID, SCHEMA_ID, SCHEMA_REV))
                .thenReturn(sharedSchema);

        // execute and validate import status
        SharedModuleImportStatus status = moduleService.importModuleByIdAndVersion(TestConstants.TEST_STUDY, MODULE_ID,
                MODULE_VERSION);
        assertEquals(status.getModuleType(), SharedModuleType.SCHEMA);
        assertEquals(status.getSchemaId(), SCHEMA_ID);
        assertEquals(status.getSchemaRevision().intValue(), SCHEMA_REV);

        UploadSchema modifiedSchema = schemaArgumentCaptor.getValue();
        assertEquals(modifiedSchema.getModuleId(), MODULE_ID);
        assertEquals(modifiedSchema.getModuleVersion().intValue(), MODULE_VERSION);

        // verify calls to create schema
        verify(mockSchemaService).createSchemaRevisionV4(TestConstants.TEST_STUDY, sharedSchema);
    }

    @Test
    public void byIdAndVersionSurveySuccess() {
        // mock metadata service
        when(mockMetadataService.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(
                makeValidMetadataWithSurvey());

        // mock survey service
        Survey sharedSurvey = Survey.create();
        when(mockSurveyService.getSurvey(BridgeConstants.SHARED_STUDY_ID, SHARED_SURVEY_KEY, true, true)).thenReturn(sharedSurvey);

        Survey localSurvey = Survey.create();
        localSurvey.setGuid(LOCAL_SURVEY_GUID);
        localSurvey.setCreatedOn(LOCAL_SURVEY_CREATED_ON);
        when(mockSurveyService.createSurvey(any())).thenReturn(localSurvey);

        // execute and validate import status
        SharedModuleImportStatus status = moduleService.importModuleByIdAndVersion(TestConstants.TEST_STUDY, MODULE_ID,
                MODULE_VERSION);
        assertEquals(status.getModuleType(), SharedModuleType.SURVEY);
        assertEquals(status.getSurveyCreatedOn().longValue(), LOCAL_SURVEY_CREATED_ON);
        assertEquals(status.getSurveyGuid(), LOCAL_SURVEY_GUID);

        // Verify calls to create survey. Verify that we set the study ID.
        ArgumentCaptor<Survey> surveyToCreateCaptor = ArgumentCaptor.forClass(Survey.class);
        verify(mockSurveyService).createSurvey(surveyToCreateCaptor.capture());
        Survey surveyToCreate = surveyToCreateCaptor.getValue();
        assertEquals(surveyToCreate.getStudyIdentifier(), TestConstants.TEST_STUDY_IDENTIFIER);
        assertEquals(surveyToCreate.getModuleId(), MODULE_ID);
        assertEquals(surveyToCreate.getModuleVersion().intValue(), MODULE_VERSION);

        // verify call to publish survey
        verify(mockSurveyService).publishSurvey(TestConstants.TEST_STUDY, LOCAL_SURVEY_KEY, true);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void latestPublishedNullId() {
        moduleService.importModuleByIdLatestPublishedVersion(TestConstants.TEST_STUDY, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void latestPublishedEmptyId() {
        moduleService.importModuleByIdLatestPublishedVersion(TestConstants.TEST_STUDY, "");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void latestPublishedBlankId() {
        moduleService.importModuleByIdLatestPublishedVersion(TestConstants.TEST_STUDY, "   ");
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void latestPublishedNotFound() {
        // mock metadataService to return empty list
        when(mockMetadataService.queryMetadataById(MODULE_ID, true, true, null, null, null, false))
                .thenReturn(ImmutableList.of());

        // execute
        moduleService.importModuleByIdLatestPublishedVersion(TestConstants.TEST_STUDY, MODULE_ID);
    }

    @Test
    public void latestPublishedSuccess() {
        // mock metadata service
        when(mockMetadataService.queryMetadataById(MODULE_ID, true, true, null, null, null, false))
                .thenReturn(ImmutableList.of(makeValidMetadataWithSchema()));

        ArgumentCaptor<UploadSchema> schemaArgumentCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        when(mockSchemaService.createSchemaRevisionV4(any(), schemaArgumentCaptor.capture())).thenReturn(null);

        // mock schema service
        UploadSchema sharedSchema = UploadSchema.create();
        when(mockSchemaService.getUploadSchemaByIdAndRev(BridgeConstants.SHARED_STUDY_ID, SCHEMA_ID, SCHEMA_REV))
                .thenReturn(sharedSchema);

        // execute and validate import status
        SharedModuleImportStatus status = moduleService.importModuleByIdLatestPublishedVersion(
                TestConstants.TEST_STUDY, MODULE_ID);
        assertEquals(status.getModuleType(), SharedModuleType.SCHEMA);
        assertEquals(status.getSchemaId(), SCHEMA_ID);
        assertEquals(status.getSchemaRevision().intValue(), SCHEMA_REV);

        UploadSchema modifiedSchema = schemaArgumentCaptor.getValue();
        assertEquals(modifiedSchema.getModuleId(), MODULE_ID);
        assertEquals(modifiedSchema.getModuleVersion().intValue(), MODULE_VERSION);

        // verify calls to create schema
        verify(mockSchemaService).createSchemaRevisionV4(TestConstants.TEST_STUDY, sharedSchema);
    }

    private static SharedModuleMetadata makeValidMetadataWithSchema() {
        SharedModuleMetadata metadata = makeValidMetadataWithoutSchemaOrSurvey();
        metadata.setSchemaId(SCHEMA_ID);
        metadata.setSchemaRevision(SCHEMA_REV);
        return metadata;
    }

    private static SharedModuleMetadata makeValidMetadataWithSurvey() {
        SharedModuleMetadata metadata = makeValidMetadataWithoutSchemaOrSurvey();
        metadata.setSurveyCreatedOn(SHARED_SURVEY_CREATED_ON);
        metadata.setSurveyGuid(SHARED_SURVEY_GUID);
        return metadata;
    }

    // slight misnomer: This isn't *quite* valid until you add a schema or survey.
    private static SharedModuleMetadata makeValidMetadataWithoutSchemaOrSurvey() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();
        metadata.setId(MODULE_ID);
        metadata.setName(MODULE_NAME);
        metadata.setVersion(MODULE_VERSION);
        return metadata;
    }
}
