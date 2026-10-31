package mg.itu.etu004361.util;

import mg.itu.etu004361.annotation.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ControllerFactory {
    private static final Map<Class<?>, Object> singletonInstances = new ConcurrentHashMap<>();

    public static Object get(Class<?> clazz) throws ReflectiveOperationException {
        Singleton singletonAnnotation = clazz.getAnnotation(Singleton.class);
        
        if (singletonAnnotation != null) {
            // Singleton controller - reuse instance
            Object existing = singletonInstances.get(clazz);
            if (existing != null) {
                return existing;
            }
            
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Object previous = singletonInstances.putIfAbsent(clazz, instance);
            return previous != null ? previous : instance;
        } else {
            // Non-singleton controller - create new instance each time
            return clazz.getDeclaredConstructor().newInstance();
        }
    }

    public static void clear() {
        singletonInstances.clear();
    }
}
