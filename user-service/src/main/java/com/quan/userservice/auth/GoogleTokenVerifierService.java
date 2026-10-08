package com.quan.userservice.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.quan.userservice.auth.exception.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class GoogleTokenVerifierService {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifierService.class);
    private final GoogleIdTokenVerifier verifier;
    private final String expectedClientId;

    public GoogleTokenVerifierService(@Value("${google.client-id}") String clientId) {
        this.expectedClientId = clientId;
        log.info("Initialized GoogleTokenVerifierService with expected clientId: {}", clientId);
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleIdToken.Payload verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.error("Google ID Token verify returned null! Expected clientId: {}", expectedClientId);
                throw new InvalidCredentialsException("Xác thực Google thất bại: Token không hợp lệ hoặc sai Client ID");
            }
            return idToken.getPayload();
        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.error("Exception during Google ID Token verification: {}", e.getMessage(), e);
            throw new InvalidCredentialsException("Lỗi xác thực Google: " + e.getMessage());
        }
    }
}
