package com.akincoskun.outreach.service.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class HmacTokenService {

    private final byte[] secretKey;

    public HmacTokenService(@Value("${app.security.hmac-secret}") String secret) {
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken(String payload) {
        try {
            String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String data = payload + "." + nonce;
            String sig = hmacSha256(data);
            return HexFormat.of().formatHex((data + "." + sig).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Token generation failed", e);
        }
    }

    public boolean verifyToken(String token, String expectedPayload) {
        try {
            byte[] bytes = HexFormat.of().parseHex(token);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\.");
            if (parts.length != 3) return false;

            String data = parts[0] + "." + parts[1];
            String sig = parts[2];
            String expectedSig = hmacSha256(data);

            return expectedSig.equals(sig) && parts[0].equals(expectedPayload);
        } catch (Exception e) {
            return false;
        }
    }

    private String hmacSha256(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
        byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(result);
    }
}
