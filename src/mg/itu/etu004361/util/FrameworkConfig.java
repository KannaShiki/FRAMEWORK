package mg.itu.etu004361.util;

import java.util.Map;

public class FrameworkConfig {
    private static String authSessionKey;
    private static String loginUrl;

    public static void init(Map<String, String> contextParams) {
        if (contextParams == null) {
            return;
        }
        
        authSessionKey = contextParams.get("auth-session-key");
        loginUrl = contextParams.get("login-url");
    }

    public static String getAuthSessionKey() {
        return authSessionKey != null ? authSessionKey : "auth.user";
    }

    public static String getLoginUrl() {
        return loginUrl != null ? loginUrl : "/login";
    }
}
