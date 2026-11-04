package dev.beatward.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountActivityTest {

    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final AccountActivity accountActivity = new AccountActivity(userRepository);

    @Test
    void treatsNullOwnerAsInactive() {
        assertThat(accountActivity.isActive(null)).isFalse();
    }

    @Test
    void treatsMissingAccountAsInactive() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThat(accountActivity.isActive(7L)).isFalse();
    }

    @Test
    void followsAccountStatus() {
        AppUserEntity user = new AppUserEntity();
        user.setStatus(AccountStatus.DISABLED);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThat(accountActivity.isActive(7L)).isFalse();

        user.setStatus(AccountStatus.ACTIVE);
        assertThat(accountActivity.isActive(7L)).isTrue();
    }
}
