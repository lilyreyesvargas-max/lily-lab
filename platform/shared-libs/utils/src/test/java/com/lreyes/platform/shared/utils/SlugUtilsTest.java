package com.lreyes.platform.shared.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "'Acme Corp',         'acme-corp'",
            "'  Acme  Corp!  ',   'acme-corp'",
            "'Café & Résumé',     'cafe-resume'",
            "'tenant_one',        'tenant_one'",
            "'UPPER CASE',        'upper-case'",
            "'',                  ''",
    })
    void toSlug_shouldNormalizeInput(String input, String expected) {
        assertEquals(expected, SlugUtils.toSlug(input));
    }

    @Test
    void toSlug_null_shouldReturnEmpty() {
        assertEquals("", SlugUtils.toSlug(null));
    }
}
