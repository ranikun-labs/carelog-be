package carelog.carelog.auth.domain;

import java.util.regex.Pattern;

/** Product Client Trust Anchor 식별자 규칙을 한 곳에서 관리한다. */
public final class ProductClientIdPolicy {

    private static final Pattern CLIENT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,99}$");

    private ProductClientIdPolicy() {
    }

    public static boolean isValid(String clientId) {
        return clientId != null && CLIENT_ID_PATTERN.matcher(clientId).matches();
    }

    public static String requireValid(String clientId) {
        if (!isValid(clientId)) {
            throw new IllegalArgumentException("clientId must match the product client identifier policy");
        }
        return clientId;
    }
}
