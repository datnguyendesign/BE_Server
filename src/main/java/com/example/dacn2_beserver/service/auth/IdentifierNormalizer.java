package com.example.dacn2_beserver.service.auth;

public class IdentifierNormalizer {
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * Chuẩn hoá phone rất basic.
     * - Nếu đã có + thì giữ
     * - Nếu bắt đầu bằng 0 và dài 10 -> +84xxxx
     * Có thể thay bằng lib chuẩn sau (libphonenumber) (beta).
     */
    public static String normalizePhone(String phoneRaw) {
        if (phoneRaw == null) return null;
        String p = phoneRaw.trim().replace(" ", "").replace("-", "");
        if (p.startsWith("+")) return p;
        if (p.startsWith("0") && p.length() == 10) return "+84" + p.substring(1);
        return p;
    }
}