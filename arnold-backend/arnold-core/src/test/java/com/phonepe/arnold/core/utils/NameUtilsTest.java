package com.phonepe.arnold.core.utils;

import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameUtilsTest {

    @Test
    void sanitizedNameFromEmail_withSimpleDottedEmail_returnsCapitalizedName() {
        assertEquals("Jane Doe", NameUtils.sanitizedName("jane.doe@example.com"));
    }

    @Test
    void sanitizedName_withTrailingDigits_stripsDigits() {
        assertEquals("John Smith", NameUtils.sanitizedName("john.smith35@example.com"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "@example.com", "123@example.com", "42", "._-@example.com"})
    void sanitizedNameFromEmail_withNoDerivableName_returnsEmptyString(String email) {
        assertEquals("", NameUtils.sanitizedName(email));
    }

    @ParameterizedTest
    @CsvSource({
            // basic cases
            "jane.doe@example.com, Jane Doe",
            "john.smith35@example.com, John Smith",
            // already capitalized / mixed case is normalized
            "Jane.DOE@example.com, Jane Doe",
            "JANE.doe@example.com, Jane Doe",
            // single token local part
            "jane@example.com, Jane",
            // digits only on one token
            "john99.doe@example.com, John Doe",
            // leading digits are preserved (only trailing stripped) -> non letters dropped in capitalize
            "underscore_separated@example.com, Underscore Separated",
            "dash-separated@example.com, Dash Separated",
            // multiple separators collapse
            "first..last@example.com, First Last",
            "first._.last@example.com, First Last",
            // no domain part still works
            "jane.doe, Jane Doe",
            // surrounding whitespace is trimmed
            "'  jane.doe@example.com  ', Jane Doe",
            // three tokens
            "a.b.c@example.com, A B C",
            // already clean name is preserved
            "Jane Doe, Jane Doe",

    })
    void sanitizedName_variousInputs_returnsExpected(String email, String expected) {
        assertEquals(expected, NameUtils.sanitizedName(email));
    }

    @Test
    void sanitizedName_withOnlyDigitsInToken_dropsThatToken() {
        assertEquals("Doe", NameUtils.sanitizedName("123.doe@example.com"));
    }

    @Test
    void sanitizedName_withPrincipal_prefersDisplayName() {
        final var principal = ArnoldPrincipal.builder().id("u1").type(ArnoldPrincipal.Type.USER)
                .displayName("Jane Doe").email("someone.else@example.com").build();
        assertEquals("Jane Doe", NameUtils.sanitizedName(principal));
    }

    @Test
    void sanitizedName_withPrincipalWithNoName_fallsBackToEmail() {
        final var principal = ArnoldPrincipal.builder().id("u1").type(ArnoldPrincipal.Type.USER)
                .email("jane.doe@example.com").build();
        assertEquals("Jane Doe", NameUtils.sanitizedName(principal));
    }
}
