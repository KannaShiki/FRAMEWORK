package mg.itu.etu004361;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Session {
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
}
