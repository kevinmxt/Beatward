package dev.beatward.auth;

import org.springframework.stereotype.Component;

@Component
public class AccountActivity {

    private final AppUserRepository userRepository;

    public AccountActivity(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Null owners exist only during the 4C-2a/4C-2b expand window; the 4C-2c
     * migration makes monitor.user_id non-null.
     */
    public boolean isActive(Long userId) {
        if (userId == null) {
            return true;
        }
        return userRepository.findById(userId)
                .map(user -> user.getStatus() == AccountStatus.ACTIVE)
                .orElse(false);
    }
}
