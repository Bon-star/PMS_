package com.example.pms.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private static class OtpData {
        String otp;
        long expire;
    }

    private final Map<String, OtpData> store = new ConcurrentHashMap<>();

    public String generateOtp(String email) {
        String otp = String.valueOf(new Random().nextInt(900000) + 100000);
        OtpData data = new OtpData();
        data.otp = otp;
        data.expire = System.currentTimeMillis() + 5 * 60 * 1000;
        store.put(email, data);
        return otp;
    }

    public boolean verify(String email, String otp) {
        OtpData data = store.get(email);
        if (data == null) return false;
        if (System.currentTimeMillis() > data.expire) return false;

        boolean ok = data.otp.equals(otp);
        if (ok) store.remove(email);
        return ok;
    }
}
