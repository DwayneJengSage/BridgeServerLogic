package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.SubscriptionRequest;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.validators.Validate;

public class TestUtils {
    private static final DateTime TEST_CREATED_ON = DateTime.parse("2015-01-27T00:38:32.486Z");

    /**
     * Using @Test(expected=SomeException.class) has led us to create tests that pass because
     * exceptions are being thrown from the wrong place in the code under test. This utility method
     * will also verify the message in the exception, which can help us find and fix these misleading
     * succeeding tests.
     */
    public static void assertException(Class<? extends Exception> cls, String message, Runnable runnable) {
        try {
            runnable.run();
        } catch(Exception e) {
            if (!e.getClass().isAssignableFrom(cls)) {
                throw e;
            } else if (!e.getMessage().equals(message)) {
                throw e;
            }
            return;
        }
        fail("Should have thrown exception: " + cls.getName() + ", message: '" + message + "'");
    }

    /**
     * Mocks this DAO method behavior so that you can verify that AccountDao.editAccount() was called, and
     * that your mock account was correctly edited.
     * @param mockAccountDao
     *      A mocked version of the AccountDao interface
     * @param mockAccount
     *      A mocked version of the Account interface
     */
    @SuppressWarnings("unchecked")
    public static void mockEditAccount(AccountDao mockAccountDao, Account mockAccount) {
        Mockito.mockingDetails(mockAccountDao).isMock();
        Mockito.mockingDetails(mockAccount).isMock();
        doAnswer(invocation -> {
            Consumer<Account> accountEdits = (Consumer<Account>)invocation.getArgument(2);
            accountEdits.accept(mockAccount);
            return null;
        }).when(mockAccountDao).editAccount(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    public static void assertDatesWithTimeZoneEqual(DateTime date1, DateTime date2) {
        // I don't know of a one line test for this... maybe just comparing ISO string formats of the date.
        assertTrue(date1.isEqual(date2));
        // This ensures that zones such as "America/Los_Angeles" and "-07:00" are equal 
        assertEquals( date1.getZone().getOffset(date1), date2.getZone().getOffset(date2) );
    }

    public static <E> void assertListIsImmutable(List<E> list, E sampleElement) {
        try {
            list.add(sampleElement);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }
    }

    public static <K, V> void assertMapIsImmutable(Map<K, V> map, K sampleKey, V sampleValue) {
        try {
            map.put(sampleKey, sampleValue);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }
    }

    public static <E> void assertSetIsImmutable(Set<E> set, E sampleElement) {
        try {
            set.add(sampleElement);
            fail("expected exception");
        } catch (RuntimeException ex) {
            // expected exception
        }
    }

    /**
     * Asserts that on validation, InvalidEntityException has been thrown with an error key that is the nested path to
     * the object value that is invalid, and the correct error message.
     */
    public static void assertValidatorMessage(Validator validator, Object object, String fieldName, String error) {
        String fieldNameAsLabel = fieldName;
        if (!error.startsWith(" ")) {
            error = " " + error;
        }
        try {
            Validate.entityThrowingException(validator, object);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            if (e.getErrors().get(fieldName).contains(fieldNameAsLabel+error)) {
                return;
            }
            fail("Did not find error message in errors object");
        }
    }

    public static Map<SubpopulationGuid,ConsentStatus> toMap(ConsentStatus... statuses) {
        return TestUtils.toMap(Lists.newArrayList(statuses));
    }

    public static Map<SubpopulationGuid,ConsentStatus> toMap(Collection<ConsentStatus> statuses) {
        ImmutableMap.Builder<SubpopulationGuid, ConsentStatus> builder = new ImmutableMap.Builder<SubpopulationGuid, ConsentStatus>();
        if (statuses != null) {
            for (ConsentStatus status : statuses) {
                builder.put(SubpopulationGuid.create(status.getSubpopulationGuid()), status);
            }
        }
        return builder.build();
    }

    public static String randomName(Class<?> clazz) {
        return "test-" + clazz.getSimpleName().toLowerCase() + "-" + RandomStringUtils.randomAlphabetic(5).toLowerCase();
    }

    public static final NotificationMessage getNotificationMessage() {
        return new NotificationMessage.Builder()
                .withSubject("a subject").withMessage("a message").build();
    }

    public static final SubscriptionRequest getSubscriptionRequest() {
        return new SubscriptionRequest(Sets.newHashSet("topicA", "topicB"));
    }

    public static NotificationTopic getNotificationTopic() {
        NotificationTopic topic = NotificationTopic.create();
        topic.setGuid("topicGuid");
        topic.setName("Test Topic Name");
        topic.setShortName("Short Name");
        topic.setDescription("Test Description");
        topic.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        topic.setTopicARN("atopicArn");
        return topic;
    }

    public static final IntentToParticipate.Builder getIntentToParticipate(long timestamp) {
        ConsentSignature consentSignature = new ConsentSignature.Builder()
                .withName("Gladlight Stonewell")
                .withBirthdate("1980-10-10")
                .withConsentCreatedOn(timestamp)
                .withImageData("image-data")
                .withImageMimeType("image/png").build();
        return new IntentToParticipate.Builder()
                .withStudyId(TestConstants.TEST_STUDY_IDENTIFIER)
                .withScope(SharingScope.SPONSORS_AND_PARTNERS)
                .withPhone(TestConstants.PHONE)
                .withSubpopGuid("subpopGuid")
                .withConsentSignature(consentSignature);
    }

    public static NotificationRegistration getNotificationRegistration() {
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setDeviceId("deviceId");
        registration.setEndpoint("endpoint");
        registration.setGuid("registrationGuid");
        registration.setHealthCode("healthCode");
        registration.setOsName("osName");
        registration.setCreatedOn(1484173675648L);
        return registration;
    }

    public static final StudyParticipant getStudyParticipant(Class<?> clazz) {
        String randomName = TestUtils.randomName(clazz);
        return new StudyParticipant.Builder()
                .withFirstName("FirstName")
                .withLastName("LastName")
                .withExternalId("externalId")
                .withEmail("bridge-testing+"+randomName+"@sagebase.org")
                .withPassword("password")
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withNotifyByEmail(true)
                .withDataGroups(Sets.newHashSet("group1"))
                .withAttributes(new ImmutableMap.Builder<String,String>().put("can_be_recontacted","true").build())
                .withLanguages(ImmutableList.of("fr")).build();
    }

    public static List<ScheduledActivity> runSchedulerForActivities(List<SchedulePlan> plans, ScheduleContext context) {
        List<ScheduledActivity> scheduledActivities = Lists.newArrayList();
        for (SchedulePlan plan : plans) {
            Schedule schedule = plan.getStrategy().getScheduleForUser(plan, context);
            // It's become possible for a user to match no schedule
            if (schedule != null) {
                scheduledActivities.addAll(schedule.getScheduler().getScheduledActivities(plan, context));
            }
        }
        Collections.sort(scheduledActivities, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        return scheduledActivities;
    }

    public static List<ScheduledActivity> runSchedulerForActivities(ScheduleContext context) {
        return runSchedulerForActivities(getSchedulePlans(context.getCriteriaContext().getStudyIdentifier()), context);
    }

    public static List<SchedulePlan> getSchedulePlans(StudyIdentifier studyId) {
        List<SchedulePlan> plans = Lists.newArrayListWithCapacity(3);

        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("DDD");
        plan.setStrategy(getStrategy("P3D", getActivity1()));
        plan.setStudyKey(studyId.getIdentifier());
        plans.add(plan);

        plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");
        plan.setStrategy(getStrategy("P1D", getActivity2()));
        plan.setStudyKey(studyId.getIdentifier());
        plans.add(plan);

        plan = new DynamoSchedulePlan();
        plan.setGuid("CCC");
        plan.setStrategy(getStrategy("P2D", getActivity3()));
        plan.setStudyKey(studyId.getIdentifier());
        plans.add(plan);

        return plans;
    }

    public static Activity getActivity1() {
        return new Activity.Builder().withGuid("activity1guid").withLabel("Activity1")
                .withPublishedSurvey("identifier1", "AAA").build();
    }

    public static Activity getActivity2() {
        return new Activity.Builder().withGuid("activity2guid").withLabel("Activity2")
                .withPublishedSurvey("identifier2", "BBB").build();
    }

    public static Activity getActivity3() {
        return new Activity.Builder().withLabel("Activity3").withGuid("AAA").withTask("tapTest").build();
    }

    public static SchedulePlan getSimpleSchedulePlan(StudyIdentifier studyId) {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 8 ? * TUE *");
        schedule.addActivity(new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Do task CCC")
                .withTask("CCC").build());
        schedule.setExpires(Period.parse("PT1H"));
        schedule.setLabel("Test label for the user");

        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);

        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("Simple Test Plan");
        plan.setGuid("GGG");
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(studyId.getIdentifier());
        plan.setStrategy(strategy);
        return plan;
    }

    public static ScheduleStrategy getStrategy(String interval, Activity activity) {
        Schedule schedule = new Schedule();
        schedule.setLabel("Schedule " + activity.getLabel());
        schedule.setInterval(interval);
        schedule.setDelay("P1D");
        schedule.addTimes("13:00");
        schedule.setExpires("PT10H");
        schedule.addActivity(activity);
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        return strategy;
    }

    public static DynamoStudy getValidStudy(Class<?> clazz) {
        String id = TestUtils.randomName(clazz);

        Map<String,String> pushNotificationARNs = Maps.newHashMap();
        pushNotificationARNs.put(OperatingSystem.IOS, "arn:ios:"+id);
        pushNotificationARNs.put(OperatingSystem.ANDROID, "arn:android:"+id);

        // This study will save without further modification.
        DynamoStudy study = new DynamoStudy();
        study.setName("Test Study ["+clazz.getSimpleName()+"]");
        study.setShortName("ShortName");
        study.setAutoVerificationEmailSuppressed(true);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        study.setStudyIdExcludedInExport(true);
        study.setVerifyEmailTemplate(new EmailTemplate("verifyEmail subject", "body with ${url}", MimeType.TEXT));
        study.setResetPasswordTemplate(new EmailTemplate("resetPassword subject", "body with ${url}", MimeType.TEXT));
        study.setEmailSignInTemplate(new EmailTemplate("${studyName} link", "Follow link ${url}", MimeType.TEXT));
        study.setAccountExistsTemplate(new EmailTemplate("accountExists subject", "body with ${resetPasswordUrl}", MimeType.TEXT));
        study.setSignedConsentTemplate(new EmailTemplate("signedConsent subject", "body", MimeType.TEXT));
        study.setAppInstallLinkTemplate(new EmailTemplate("app install subject", "body ${appInstallUrl}", MimeType.TEXT));
        study.setResetPasswordSmsTemplate(new SmsTemplate("resetPasswordSmsTemplate ${resetPasswordUrl}"));
        study.setPhoneSignInSmsTemplate(new SmsTemplate("phoneSignInSmsTemplate ${token}"));
        study.setAppInstallLinkSmsTemplate(new SmsTemplate("appInstallLinkSmsTemplate ${appInstallUrl}"));
        study.setVerifyPhoneSmsTemplate(new SmsTemplate("verifyPhoneSmsTemplate ${token}"));
        study.setAccountExistsSmsTemplate(new SmsTemplate("accountExistsSmsTemplate ${token}"));
        study.setSignedConsentSmsTemplate(new SmsTemplate("signedConsent ${consentUrl}"));
        study.setIdentifier(id);
        study.setMinAgeOfConsent(18);
        study.setSponsorName("The Council on Test Studies");
        study.setConsentNotificationEmail("bridge-testing+consent@sagebase.org");
        study.setConsentNotificationEmailVerified(true);
        study.setSynapseDataAccessTeamId(1234L);
        study.setSynapseProjectId("test-synapse-project-id");
        study.setTechnicalEmail("bridge-testing+technical@sagebase.org");
        study.setUploadValidationStrictness(UploadValidationStrictness.REPORT);
        study.setUsesCustomExportSchedule(true);
        study.setSupportEmail("bridge-testing+support@sagebase.org");
        study.setUserProfileAttributes(Sets.newHashSet("a", "b"));
        study.setTaskIdentifiers(Sets.newHashSet("task1", "task2"));
        study.setActivityEventKeys(Sets.newHashSet("event1", "event2"));
        study.setDataGroups(Sets.newHashSet("beta_users", "production_users"));
        study.setStrictUploadValidationEnabled(true);
        study.setHealthCodeExportEnabled(true);
        study.setEmailVerificationEnabled(true);
        study.setExternalIdValidationEnabled(true);
        study.setReauthenticationEnabled(true);
        study.setEmailSignInEnabled(true);
        study.setPhoneSignInEnabled(true);
        study.setVerifyChannelOnSignInEnabled(true);
        study.setExternalIdRequiredOnSignup(true);
        study.setActive(true);
        study.setDisableExport(false);
        study.setAccountLimit(0);
        study.setPushNotificationARNs(pushNotificationARNs);
        study.setAutoVerificationPhoneSuppressed(true);
        return study;
    }

    public static SchedulePlan getABTestSchedulePlan(StudyIdentifier studyId) {
        Schedule schedule1 = new Schedule();
        schedule1.setScheduleType(ScheduleType.RECURRING);
        schedule1.setCronTrigger("0 0 8 ? * TUE *");
        schedule1.addActivity(new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Do AAA task")
                .withTask("AAA").build());
        schedule1.setExpires(Period.parse("PT1H"));
        schedule1.setLabel("Schedule 1");

        Schedule schedule2 = new Schedule();
        schedule2.setScheduleType(ScheduleType.RECURRING);
        schedule2.setCronTrigger("0 0 8 ? * TUE *");
        schedule2.addActivity(new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Do BBB task")
                .withTask("BBB").build());
        schedule2.setExpires(Period.parse("PT1H"));
        schedule2.setLabel("Schedule 2");

        Schedule schedule3 = new Schedule();
        schedule3.setScheduleType(ScheduleType.RECURRING);
        schedule3.setCronTrigger("0 0 8 ? * TUE *");
        schedule3.addActivity(new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Do CCC task")
                .withTask("CCC").build());
        schedule3.setExpires(Period.parse("PT1H"));
        schedule3.setLabel("Schedule 3");

        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("AAA");
        plan.setLabel("Test A/B Schedule");
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(studyId.getIdentifier());

        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        strategy.addGroup(40, schedule1);
        strategy.addGroup(40, schedule2);
        strategy.addGroup(20, schedule3);
        plan.setStrategy(strategy);

        return plan;
    }

    public static Schedule getSchedule(String label) {
        Activity activity = new Activity.Builder().withGuid(BridgeUtils.generateGuid()).withLabel("Test survey")
                .withSurvey("identifier", "ABC", TEST_CREATED_ON).build();

        Schedule schedule = new Schedule();
        schedule.setLabel(label);
        schedule.addActivity(activity);
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setCronTrigger("0 0 8 ? * TUE *");
        return schedule;
    }

    public static AppConfigElement getAppConfigElement() {
        AppConfigElement element = AppConfigElement.create();
        element.setId("id");
        element.setRevision(3L);
        element.setDeleted(false);
        element.setData(getClientData());
        element.setCreatedOn(DateTime.now().minusHours(2).getMillis());
        element.setModifiedOn(DateTime.now().minusHours(1).getMillis());
        element.setVersion(1L);
        return element;
    }

    public static JsonNode getClientData() {
        try {
            String json = TestUtils.createJson("{'booleanFlag':true,'stringValue':'testString','intValue':4}");
            return BridgeObjectMapper.get().readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode getOtherClientData() {
        JsonNode clientData = TestUtils.getClientData();
        ((ObjectNode)clientData).put("newField", "newValue");
        return clientData;
    }

    /**
     * Converts single quote marks to double quote marks to convert JSON using single quotes to valid JSON.
     * Useful to create more readable inline JSON in tests, because double quotes must be escaped in Java.
     */
    public static String createJson(String json) {
        return json.replaceAll("'", "\"");
    }

    public static Criteria createCriteria(Integer minAppVersion, Integer maxAppVersion, Set<String> allOfGroups, Set<String> noneOfGroups) {
        DynamoCriteria crit = new DynamoCriteria();
        crit.setMinAppVersion(OperatingSystem.IOS, minAppVersion);
        crit.setMaxAppVersion(OperatingSystem.IOS, maxAppVersion);
        crit.setAllOfGroups(allOfGroups);
        crit.setNoneOfGroups(noneOfGroups);
        return crit;
    }

    public static Criteria copyCriteria(Criteria criteria) {
        DynamoCriteria crit = new DynamoCriteria();
        if (criteria != null) {
            crit.setKey(criteria.getKey());
            crit.setLanguage(criteria.getLanguage());
            for (String osName : criteria.getAppVersionOperatingSystems()) {
                crit.setMinAppVersion(osName, criteria.getMinAppVersion(osName));
                crit.setMaxAppVersion(osName, criteria.getMaxAppVersion(osName));
            }
            crit.setNoneOfGroups(criteria.getNoneOfGroups());
            crit.setAllOfGroups(criteria.getAllOfGroups());
        }
        return crit;
    }

    /**
     * Guava does not have a version of this method that also lets you add items.
     */
    @SuppressWarnings("unchecked")
    public static <T> LinkedHashSet<T> newLinkedHashSet(T... items) {
        LinkedHashSet<T> set = new LinkedHashSet<T>();
        for (T item : items) {
            set.add(item);
        }
        return set;
    }

    public static String makeRandomTestEmail(Class<?> cls) {
        String devPart = BridgeConfigFactory.getConfig().getUser();
        String rndPart = TestUtils.randomName(cls);
        return String.format("bridge-testing+%s-%s@sagebase.org", devPart, rndPart);
    }
 }
