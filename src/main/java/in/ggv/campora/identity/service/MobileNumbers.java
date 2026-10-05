package in.ggv.campora.identity.service;

import java.util.regex.Pattern;

import in.ggv.campora.common.exception.BusinessException;

/** Normalises Indian mobile numbers to their 10-digit form. */
public final class MobileNumbers {

    private static final Pattern VALID = Pattern.compile("^[6-9]\\d{9}$");

    private MobileNumbers() {
    }

    /** Accepts "98765 43210", "+91-9876543210", "09876543210" → "9876543210". */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new BusinessException("Mobile number is required.");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!VALID.matcher(digits).matches()) {
            throw new BusinessException("Enter a valid 10-digit Indian mobile number.");
        }
        return digits;
    }

    /** "9876543210" → "98•••••210" for display on screens that should not reveal it. */
    public static String mask(String mobile) {
        if (mobile == null || mobile.length() < 6) {
            return mobile;
        }
        return mobile.substring(0, 2) + "•••••" + mobile.substring(mobile.length() - 3);
    }
}
