package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Set;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleCriteria;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class DynamoSchedulePlanDaoMockTest {
    
    private static final Set<String> ALL_OF_GROUPS = Sets.newHashSet("a","b");
    private static final Set<String> NONE_OF_GROUPS = Sets.newHashSet("c","d");
    
    private DynamoSchedulePlanDao dao;

    private DynamoSchedulePlan schedulePlan;
    
    @Mock
    private DynamoDBMapper mapper;
    
    @Mock
    private CriteriaDao criteriaDao;
    
    @Mock
    private QueryResultPage<DynamoSchedulePlan> queryResultsPage;
    
    @Captor
    private ArgumentCaptor<SchedulePlan> schedulePlanCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoDBQueryExpression<DynamoSchedulePlan>> queryCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        dao = new DynamoSchedulePlanDao();
        dao.setSchedulePlanMapper(mapper);
        dao.setCriteriaDao(criteriaDao);
        
        schedulePlan = (DynamoSchedulePlan)createSchedulePlan();
        Criteria criteria = ((CriteriaScheduleStrategy) schedulePlan.getStrategy()).getScheduleCriteria().get(0)
                .getCriteria();
        
        when(criteriaDao.getCriteria("scheduleCriteria:"+schedulePlan.getGuid()+":0")).thenReturn(criteria);
    }
    
    private void mockSchedulePlanQuery() {
        List<DynamoSchedulePlan> list = Lists.newArrayList(schedulePlan);
        when(queryResultsPage.getResults()).thenReturn(list);
        when(mapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(queryResultsPage);
    }
    
    private SchedulePlan createSchedulePlan() {
        SchedulePlan schedulePlan = new DynamoSchedulePlan();
        schedulePlan.setGuid(BridgeUtils.generateGuid());
        schedulePlan.setLabel("Schedule Plan");
        schedulePlan.setStudyKey(TEST_STUDY_IDENTIFIER);
        schedulePlan.setDeleted(false);
        
        Schedule schedule = TestUtils.getSchedule("My Schedule");
        Criteria criteria = TestUtils.createCriteria(2, 10, ALL_OF_GROUPS, NONE_OF_GROUPS);
        criteria.setKey("scheduleCriteria:"+schedulePlan.getGuid()+":0");
        CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        strategy.getScheduleCriteria().add(scheduleCriteria);
        
        schedulePlan.setStrategy(strategy);
        return schedulePlan;
    }
    
    @Test
    public void getSchedulePlans() {
        mockSchedulePlanQuery();
        List<SchedulePlan> plans = dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, false);
        assertEquals(plans.size(), 1);
        
        verify(mapper).queryPage(eq(DynamoSchedulePlan.class), queryCaptor.capture());
        
        assertEquals(queryCaptor.getValue().getHashKeyValues().getStudyKey(), TEST_STUDY.getIdentifier());
    }
    
    @Test
    public void getSchedulePlansRetrievesCriteria() {
        mockSchedulePlanQuery();
        List<SchedulePlan> plans = dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, false);
        
        SchedulePlan plan = plans.get(0);
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        Criteria criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertCriteria(criteria);
        
        String key = criteria.getKey();
        verify(criteriaDao).getCriteria(key);
        
        // now have criteriaDao return a different criteria object, that should update the plan
        Criteria persistedCriteria = Criteria.create();
        persistedCriteria.setMinAppVersion(IOS, 1);
        persistedCriteria.setMaxAppVersion(IOS, 65);
        when(criteriaDao.getCriteria(key)).thenReturn(persistedCriteria);
        
        plans = dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, false);
        plan = plans.get(0);
        strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertEquals(criteria.getMinAppVersion(IOS), new Integer(1));
        assertEquals(criteria.getMaxAppVersion(IOS), new Integer(65));
        assertTrue(criteria.getAllOfGroups().isEmpty());
        assertTrue(criteria.getNoneOfGroups().isEmpty());
    }
    
    @Test
    public void getSchedulePlanRetrievesCriteria() {
        mockSchedulePlanQuery();
        SchedulePlan plan = dao.getSchedulePlan(TEST_STUDY, schedulePlan.getGuid());
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        Criteria criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertCriteria(criteria);
        
        String key = criteria.getKey();
        verify(criteriaDao).getCriteria(key);
        
        // now have criteriaDao return a different criteria object, that should update the plan
        Criteria persistedCriteria = Criteria.create();
        persistedCriteria.setMinAppVersion(IOS, 1);
        persistedCriteria.setMaxAppVersion(IOS, 65);
        when(criteriaDao.getCriteria(key)).thenReturn(persistedCriteria);
        
        plan = dao.getSchedulePlan(TEST_STUDY, plan.getGuid());
        strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertEquals(criteria.getMinAppVersion(IOS), new Integer(1));
        assertEquals(criteria.getMaxAppVersion(IOS), new Integer(65));
        assertTrue(criteria.getAllOfGroups().isEmpty());
        assertTrue(criteria.getNoneOfGroups().isEmpty());
    }
    
    @Test
    public void createSchedulePlanWritesCriteria() {
        SchedulePlan plan = dao.createSchedulePlan(TEST_STUDY, schedulePlan);
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        Criteria criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertCriteria(criteria);
        
        ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        
        verify(criteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria crit = criteriaCaptor.getValue();
        assertCriteria(crit);        
    }
    
    @Test
    public void updateSchedulePlanWritesCriteria() {
        mockSchedulePlanQuery();
        // Create a copy of the schedulePlan or this test does not work
        SchedulePlan update = SchedulePlan.create();
        update.setGuid(schedulePlan.getGuid());
        update.setLabel(schedulePlan.getLabel());
        update.setStudyKey(schedulePlan.getStudyKey());
        
        // But modify the criteria...
        Schedule schedule = TestUtils.getSchedule("My Schedule");
        Criteria criteria = TestUtils.createCriteria(100, 200, ALL_OF_GROUPS, NONE_OF_GROUPS);
        criteria.setKey("scheduleCriteria:"+schedulePlan.getGuid()+":0");
        CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        strategy.getScheduleCriteria().add(scheduleCriteria);
        
        update.setStrategy(strategy);
        
        ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        
        SchedulePlan updated = dao.updateSchedulePlan(TEST_STUDY, update);
        
        // Verify returned object still has changes
        strategy = (CriteriaScheduleStrategy)updated.getStrategy();
        scheduleCriteria = strategy.getScheduleCriteria().get(0);
        Criteria returnedCriteria = scheduleCriteria.getCriteria();
        assertEquals(returnedCriteria.getMinAppVersion(IOS), new Integer(100));
        assertEquals(returnedCriteria.getMaxAppVersion(IOS), new Integer(200));        
        
        // Verify they were persisted
        verify(criteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria updatedCriteria = criteriaCaptor.getValue();
        assertEquals(updatedCriteria.getMinAppVersion(IOS), new Integer(100));
        assertEquals(updatedCriteria.getMaxAppVersion(IOS), new Integer(200));        
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateMissingSchedulePlan() {
        when(queryResultsPage.getResults()).thenReturn(ImmutableList.of());
        when(mapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(queryResultsPage);
        
        SchedulePlan update = SchedulePlan.create();
        update.setGuid("doesNotExist");
        dao.updateSchedulePlan(TEST_STUDY, update);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateDeletedSchedulePlan() {
        schedulePlan.setDeleted(true);
        mockSchedulePlanQuery();
        
        SchedulePlan update = SchedulePlan.create();
        update.setDeleted(true);
        update.setGuid(schedulePlan.getGuid());
        dao.updateSchedulePlan(TEST_STUDY, update);
    }
    
    @Test
    public void createSchedulePlanNeverDeleted() {
        SchedulePlan plan = SchedulePlan.create();
        plan.setDeleted(true);
        
        dao.createSchedulePlan(TestConstants.TEST_STUDY, plan);
        
        verify(mapper).save(schedulePlanCaptor.capture());
        assertFalse(schedulePlanCaptor.getValue().isDeleted());
    }
    
    @Test
    public void getSchedulePlansExcludesLogicallyDeleted() {
        mockSchedulePlanQuery();
        dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TestConstants.TEST_STUDY, false);
        
        verify(mapper).queryPage(eq(DynamoSchedulePlan.class), queryCaptor.capture());
        
        Condition condition = new Condition().withComparisonOperator(ComparisonOperator.NE)
                .withAttributeValueList(new AttributeValue().withN("1"));
        
        assertEquals(queryCaptor.getValue().getQueryFilter().get("deleted"), condition);
    }
    
    @Test
    public void getSchedulePlansIncludesLogicallyDeleted() {
        mockSchedulePlanQuery();
        dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TestConstants.TEST_STUDY, true);
        
        verify(mapper).queryPage(eq(DynamoSchedulePlan.class), queryCaptor.capture());
        
        assertNull(queryCaptor.getValue().getQueryFilter());
    }

    @Test
    public void deleteSchedulePlan() {
        mockSchedulePlanQuery();
        assertFalse(schedulePlan.isDeleted());
        
        dao.deleteSchedulePlan(TEST_STUDY, schedulePlan.getGuid());
        
        verify(mapper).save(schedulePlanCaptor.capture());
        verify(criteriaDao, never()).deleteCriteria(any());
        
        assertTrue(schedulePlanCaptor.getValue().isDeleted());
    }
    
    @Test
    public void deleteSchedulePlanPermanently() {
        mockSchedulePlanQuery();
        dao.deleteSchedulePlanPermanently(TEST_STUDY, schedulePlan.getGuid());
        
        verify(mapper).delete(schedulePlanCaptor.capture());
        verify(criteriaDao).deleteCriteria(any());
    }
    
    @Test
    public void deleteSchedulePlanPermanentlyDeletesCriteria() {
        mockSchedulePlanQuery();
        dao.deleteSchedulePlanPermanently(TEST_STUDY, schedulePlan.getGuid());
        
        verify(criteriaDao).deleteCriteria("scheduleCriteria:"+schedulePlan.getGuid()+":0");
    }
    
    @Test
    public void logicallyDeletingMissingSchedulePlanThrows() {
        when(queryResultsPage.getResults()).thenReturn(ImmutableList.of());
        when(mapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(queryResultsPage);
        
        try {
            dao.deleteSchedulePlan(TEST_STUDY, "does-not-exist");
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mapper, never()).save(any());
    }

    @Test
    public void permanentlyDeletingMissingSchedulePlanThrows() {
        when(queryResultsPage.getResults()).thenReturn(ImmutableList.of());
        when(mapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(queryResultsPage);
        try {
            dao.deleteSchedulePlanPermanently(TEST_STUDY, "does-not-exist");
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mapper, never()).delete(any());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateLogicallyDeletedSchedulePlanThrows() {
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(true);
        
        // Both are deleted, so this should throw.
        dao.updateSchedulePlan(TEST_STUDY, schedulePlan);
    }
    
    @Test
    public void updateCanUndeleteSchedulePlan() { 
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(true);
        
        SchedulePlan update = createSchedulePlan();
        update.setDeleted(false);
        
        SchedulePlan returned = dao.updateSchedulePlan(TEST_STUDY, update);
        assertFalse(returned.isDeleted());
        verify(mapper).save(schedulePlanCaptor.capture());
        
        assertFalse(schedulePlanCaptor.getValue().isDeleted());
    }
    
    @Test
    public void updateCanDeleteSchedulePlan() {
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(false);
        
        SchedulePlan update = createSchedulePlan();
        update.setDeleted(true);
        
        SchedulePlan returned = dao.updateSchedulePlan(TEST_STUDY, update);
        assertTrue(returned.isDeleted());
        verify(mapper).save(schedulePlanCaptor.capture());
        
        assertTrue(schedulePlanCaptor.getValue().isDeleted());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteLogicallyDeletedSchedulePlanThrows() {
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(true);
        
        dao.deleteSchedulePlan(TEST_STUDY, schedulePlan.getGuid());
    }
    
    @Test
    public void deletePermenantlyLogicallyDeletedSchedulePlanWorks() {
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(true);
        
        dao.deleteSchedulePlanPermanently(TEST_STUDY, schedulePlan.getGuid());
        
        verify(mapper).delete(any());
    }
    
    private void assertCriteria(Criteria criteria) {
        assertEquals(criteria.getMinAppVersion(IOS), new Integer(2));
        assertEquals(criteria.getMaxAppVersion(IOS), new Integer(10));
        assertEquals(criteria.getAllOfGroups(), ALL_OF_GROUPS);
        assertEquals(criteria.getNoneOfGroups(), NONE_OF_GROUPS);
    }
}
