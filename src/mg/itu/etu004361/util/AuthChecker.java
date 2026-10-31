package mg.itu.etu004361.util;

import mg.itu.etu004361.Mapping;
import mg.itu.etu004361.annotation.Auth;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;

public class AuthChecker {
    
    public enum AuthResult {
        AUTHORIZED,
        NON_AUTHENTIFIE,
        INTERDIT
    }
    
    public static void validate(Map<String, Mapping> mappings) {
        if (mappings == null) {
            return;
        }
        
        for (Map.Entry<String, Mapping> entry : mappings.entrySet()) {
            Mapping mapping = entry.getValue();
            try {
                Class<?> clazz = Class.forName(mapping.getClassName());
                Method method = findMethod(clazz, mapping.getMethodName());
                
                Auth classAuth = clazz.getAnnotation(Auth.class);
                Auth methodAuth = method.getAnnotation(Auth.class);
                
                if (classAuth != null || methodAuth != null) {
                    // Validation successful - auth annotation present
                }
            } catch (Exception e) {
                // Skip validation for this mapping if class/method not found
                // This can happen during development
            }
        }
    }
    
    public static AuthResult check(HttpServletRequest request, Class<?> clazz, Method method) {
        Auth classAuth = clazz.getAnnotation(Auth.class);
        Auth methodAuth = method.getAnnotation(Auth.class);
        
        // Method-level annotation takes precedence over class-level
        Auth authToUse = methodAuth != null ? methodAuth : classAuth;
        
        if (authToUse == null) {
            return AuthResult.AUTHORIZED;
        }
        
        // Check authentication
        String authSessionKey = FrameworkConfig.getAuthSessionKey();
        javax.servlet.http.HttpSession session = request.getSession(false);
        
        if (session == null || session.getAttribute(authSessionKey) == null) {
            return AuthResult.NON_AUTHENTIFIE;
        }
        
        // Check roles if specified
        String[] requiredRoles = authToUse.roles();
        if (requiredRoles != null && requiredRoles.length > 0) {
            String userRole = (String) session.getAttribute("auth.role");
            if (userRole == null) {
                return AuthResult.INTERDIT;
            }
            
            boolean hasRole = false;
            for (String role : requiredRoles) {
                if (role.equals(userRole)) {
                    hasRole = true;
                    break;
                }
            }
            
            if (!hasRole) {
                return AuthResult.INTERDIT;
            }
        }
        
        return AuthResult.AUTHORIZED;
    }
    
    private static Method findMethod(Class<?> clazz, String methodName) throws NoSuchMethodException {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new NoSuchMethodException(methodName);
    }
}
