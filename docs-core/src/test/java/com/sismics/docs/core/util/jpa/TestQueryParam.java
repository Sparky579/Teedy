package com.sismics.docs.core.util.jpa;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Test of the {@link QueryParam} class.
 * 
 * @author Claude
 */
public class TestQueryParam {
    
    @Test
    public void testQueryParam() {
        // Test constructor and getters
        String queryString = "SELECT d FROM Document d WHERE d.id = :id";
        Map<String, Object> parameterMap = new HashMap<>();
        parameterMap.put("id", "document-id");
        
        QueryParam queryParam = new QueryParam(queryString, parameterMap);
        
        Assert.assertEquals(queryString, queryParam.getQueryString());
        Assert.assertEquals(parameterMap, queryParam.getParameterMap());
        Assert.assertEquals(1, queryParam.getParameterMap().size());
        Assert.assertEquals("document-id", queryParam.getParameterMap().get("id"));
    }
} 