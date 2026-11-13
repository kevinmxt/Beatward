package dev.beatward.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokenGeneratorTest {

    private static final String INVITE_CODE_PATTERN = "[0-9A-HJKMNP-TV-Z]{8}";

    private final SecureTokenGenerator generator = new SecureTokenGenerator();

    @Test
    void inviteCodesAreEightUnambiguousCharacters() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.generateInviteCode()).matches(INVITE_CODE_PATTERN);
        }
    }

    @Test
    void inviteCodesAreNotRepeated() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generateInviteCode());
        }
        assertThat(codes).hasSize(1000);
    }
}
