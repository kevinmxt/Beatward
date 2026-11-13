package dev.beatward.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRulesTest {

    @Test
    void acceptsTenTo32Characters() {
        assertThat(PasswordRules.isValid("a".repeat(10))).isTrue();
        assertThat(PasswordRules.isValid("a".repeat(32))).isTrue();
    }

    @Test
    void rejectsShortLongAndMissingPasswords() {
        assertThat(PasswordRules.isValid("a".repeat(9))).isFalse();
        assertThat(PasswordRules.isValid("a".repeat(33))).isFalse();
        assertThat(PasswordRules.isValid(null)).isFalse();
        assertThat(PasswordRules.isValid("")).isFalse();
    }

    @Test
    void doesNotTrimWhitespace() {
        assertThat(PasswordRules.isValid(" " + "a".repeat(8) + " ")).isTrue();
        assertThat(PasswordRules.isValid(" " + "a".repeat(7) + " ")).isFalse();
    }
}
