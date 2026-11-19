package com.eventflow.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class HmacService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public String computeSignature(String secret, long timestamp, String payload) {
        try {
            String toSign = timestamp + "." + payload;
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to calculate HMAC signature", e);
        }
    }

    public String formatSignatureHeader(String secret, long timestamp, String payload) {
        String signature = computeSignature(secret, timestamp, payload);
        return "t=" + timestamp + ",v1=" + signature;
    }
}
