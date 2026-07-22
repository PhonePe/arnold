package com.phonepe.devx.arnold.core.utils;

import com.phonepe.olympus.im.models.user.HumanUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameUtilsTest {

    @Test
    void sanitizedNameFromEmail_withSimpleDottedEmail_returnsCapitalizedName() {
        assertEquals("Tushar Naik", NameUtils.sanitizedName("user@example.com"));
    }

    @Test
    void sanitizedName_withTrailingDigits_stripsDigits() {
        assertEquals("Sanjay Kunia", NameUtils.sanitizedName("user@example.com"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "@phonepe.com", "user@example.com", "42", ".user@example.com"})
    void sanitizedNameFromEmail_withNoDerivableName_returnsEmptyString(String email) {
        assertEquals("", NameUtils.sanitizedName(email));
    }

    @ParameterizedTest
    @CsvSource({
            // basic cases
            "user@example.com, Tushar Naik",
            "user@example.com, Sanjay Kunia",
            // already capitalized / mixed case is normalized
            "user@example.com, Tushar Naik",
            "user@example.com, Tushar Naik",
            // single token local part
            "user@example.com, Tushar",
            // digits only on one token
            "user@example.com, John Doe",
            // leading digits are preserved (only trailing stripped) -> non letters dropped in capitalize
            "user@example.com, Underscore Separated",
            "user@example.com, Dash Separated",
            // multiple separators collapse
            "user@example.com, First Last",
            "user@example.com, First Last",
            // no domain part still works
            "tushar.naik, Tushar Naik",
            // surrounding whitespace is trimmed
            "'  user@example.com  ', Tushar Naik",
            // three tokens
            "user@example.com, A B C",
            // already clean name is preserved
            "Tushar Naik, Tushar Naik",

    })
    void sanitizedName_variousInputs_returnsExpected(String email, String expected) {
        assertEquals(expected, NameUtils.sanitizedName(email));
    }

    @Test
    void sanitizedName_withOnlyDigitsInToken_dropsThatToken() {
        assertEquals("Naik", NameUtils.sanitizedName("user@example.com"));
    }

    @Test
    void sanitizedName_withHumanUserDetails_returnsExpected() {
        HumanUserDetails userDetails = new HumanUserDetails();
        userDetails.setName("Tushar Naik");
        userDetails.setEmail("user@example.com");
        assertEquals("Tushar Naik", NameUtils.sanitizedName(userDetails));
    }

    @Test
    void sanitizedName_withHumanUserDetailsWithNoName_returnsExpected() {
        HumanUserDetails userDetails = new HumanUserDetails();
        userDetails.setEmail("user@example.com");
        assertEquals("Tushar Naik", NameUtils.sanitizedName(userDetails));
    }
}

