package dev.beatward.auth;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    private final AppUserRepository userRepository;

    public CurrentUserProvider(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUserEntity require(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedException();
        }
        return userRepository.findByUsernameInput(authentication.getName())
                .orElseThrow(UnauthenticatedException::new);
    }

    public Long requireId(Authentication authentication) {
        return require(authentication).getId();
    }
}
