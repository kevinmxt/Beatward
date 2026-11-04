package dev.beatward.auth;

import org.springframework.stereotype.Component;

@Component
public class AccountActivity {

    private final AppUserRepository userRepository;

    public AccountActivity(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isActive(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> user.getStatus() == AccountStatus.ACTIVE)
                .orElse(false);
    }
}
