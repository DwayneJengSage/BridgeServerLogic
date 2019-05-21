package org.sagebionetworks.bridge.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.testng.annotations.Test;

/**
 * Adopted from the OpenGamma library (Apache License v2):
 * 
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
public class BridgeCollectorsTest {

    @Test
    public void toImmutableList() {
        List<String> list = Arrays.asList("a", "ab", "b", "bb", "c", "a");
        ImmutableList<String> test = list.stream().filter(s -> s.length() == 1)
                .collect(BridgeCollectors.toImmutableList());
        assertEquals(ImmutableList.of("a", "b", "c", "a"), test);
        assertTrue(test instanceof ImmutableList);
    }

    @Test
    public void toImmutableSet() {
        List<String> list = Arrays.asList("a", "ab", "b", "bb", "c", "a");
        ImmutableSet<String> test = list.stream().filter(s -> s.length() == 1)
                .collect(BridgeCollectors.toImmutableSet());
        assertEquals(ImmutableSet.of("a", "b", "c"), test);
        assertTrue(test instanceof ImmutableSet);
    }

}
