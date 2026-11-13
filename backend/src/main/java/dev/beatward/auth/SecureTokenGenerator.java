package dev.beatward.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class SecureTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private static final int INVITE_CODE_LENGTH = 8;
    // Crockford Base32: no I, L, O or U, so codes stay unambiguous when typed.
    private static final char[] INVITE_CODE_ALPHABET =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String generateInviteCode() {
        char[] code = new char[INVITE_CODE_LENGTH];
        for (int i = 0; i < code.length; i++) {
            code[i] = INVITE_CODE_ALPHABET[random.nextInt(INVITE_CODE_ALPHABET.length)];
        }
        return new String(code);
    }

    public String digest(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
