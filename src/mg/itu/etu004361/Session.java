package mg.itu.etu004361;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Session {
    public static final String AUTH_USER_KEY = "auth.user";
    public static final String AUTH_ROLE_KEY = "auth.role";

    private final Map<String, Object> data = new LinkedHashMap<>();

    public void setAttribute(String name, Object value) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        if (value == null) {
            removeAttribute(name);
            return;
        }
        data.put(name, value);
    }

    public Object getAttribute(String name) {
        if (name == null) {
            return null;
        }
        return data.get(name);
    }

    public void removeAttribute(String name) {
        if (name == null) {
            return;
        }
        data.remove(name);
    }

    public boolean containsAttribute(String name) {
        if (name == null) {
            return false;
        }
        return data.containsKey(name);
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Set<String> getAttributeNames() {
        return Collections.unmodifiableSet(data.keySet());
    }

    public boolean isAuthenticated() {
        return containsAttribute(AUTH_USER_KEY) && getAttribute(AUTH_USER_KEY) != null
                && !String.valueOf(getAttribute(AUTH_USER_KEY)).trim().isEmpty();
    }

    public Object getUser() {
        return getAttribute(AUTH_USER_KEY);
    }

    public void setUser(Object user) {
        setAttribute(AUTH_USER_KEY, user);
    }

    public void logout() {
        removeAttribute(AUTH_USER_KEY);
        removeAttribute(AUTH_ROLE_KEY);
    }

    public String getRole() {
        Object role = getAttribute(AUTH_ROLE_KEY);
        return role != null ? String.valueOf(role) : null;
    }

    public void setRole(String role) {
        setAttribute(AUTH_ROLE_KEY, role);
    }
}
