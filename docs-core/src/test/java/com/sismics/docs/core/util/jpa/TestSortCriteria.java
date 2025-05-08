package com.sismics.docs.core.util.jpa;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test of the {@link SortCriteria} class.
 * 
 * @author Claude
 */
public class TestSortCriteria {
    
    @Test
    public void testSortCriteria() {
        // Test with explicit values
        SortCriteria sortCriteria = new SortCriteria(2, false);
        Assert.assertEquals(2, sortCriteria.getColumn());
        Assert.assertFalse(sortCriteria.isAsc());
        
        // Test with default values
        sortCriteria = new SortCriteria(null, null);
        Assert.assertEquals(0, sortCriteria.getColumn());
        Assert.assertTrue(sortCriteria.isAsc());
        
        // Test with mixed values
        sortCriteria = new SortCriteria(3, null);
        Assert.assertEquals(3, sortCriteria.getColumn());
        Assert.assertTrue(sortCriteria.isAsc());
        
        sortCriteria = new SortCriteria(null, false);
        Assert.assertEquals(0, sortCriteria.getColumn());
        Assert.assertFalse(sortCriteria.isAsc());
    }
} 