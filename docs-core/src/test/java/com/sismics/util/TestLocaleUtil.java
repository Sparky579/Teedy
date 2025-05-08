package com.sismics.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Locale;

/**
 * Test of the {@link LocaleUtil} class.
 * 
 * @author Claude
 */
public class TestLocaleUtil {
    
    @Test
    public void testGetLocale() {
        // Test with null or empty input
        Locale locale = LocaleUtil.getLocale(null);
        Assert.assertEquals(Locale.ENGLISH, locale);
        
        locale = LocaleUtil.getLocale("");
        Assert.assertEquals(Locale.ENGLISH, locale);
        
        // Test with language only
        locale = LocaleUtil.getLocale("fr");
        Assert.assertEquals("fr", locale.getLanguage());
        Assert.assertEquals("", locale.getCountry());
        Assert.assertEquals("", locale.getVariant());
        
        // Test with language and country
        locale = LocaleUtil.getLocale("fr_FR");
        Assert.assertEquals("fr", locale.getLanguage());
        Assert.assertEquals("FR", locale.getCountry());
        Assert.assertEquals("", locale.getVariant());
        
        // Test with language, country and variant
        locale = LocaleUtil.getLocale("fr_FR_POSIX");
        Assert.assertEquals("fr", locale.getLanguage());
        Assert.assertEquals("FR", locale.getCountry());
        Assert.assertEquals("POSIX", locale.getVariant());
    }
} 