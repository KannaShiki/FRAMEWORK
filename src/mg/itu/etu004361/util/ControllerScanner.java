package mg.itu.etu004361.util;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import mg.itu.etu004361.Mapping;
import mg.itu.etu004361.annotation.Controller;
import mg.itu.etu004361.annotation.UrlMapping;

public class ControllerScanner {
    public static Map<String, Mapping> scanPackage(String packageName, ServletContext context) {
        Map<String, Mapping> mappings = new LinkedHashMap<>();
        if (packageName == null || packageName.isEmpty()) {
            return mappings;
        }

        String packagePath = packageName.replace('.', '/');
        String resourcePath = "/WEB-INF/classes/" + packagePath + "/";
        Set<String> resourcePaths = context.getResourcePaths(resourcePath);

        if (resourcePaths == null) {
            context.log("ControllerScanner: package non trouvé : " + packageName);
            return mappings;
        }

        for (String path : resourcePaths) {
            scanResource(path, mappings, context);
        }

        return mappings;
    }

    private static void scanResource(String resourcePath, Map<String, Mapping> mappings, ServletContext context) {
        if (resourcePath.endsWith("/")) {
            Set<String> childPaths = context.getResourcePaths(resourcePath);
            if (childPaths != null) {
                for (String childPath : childPaths) {
                    scanResource(childPath, mappings, context);
                }
            }
            return;
        }

        if (!resourcePath.endsWith(".class")) {
            return;
        }

        String className = resourcePath
                .substring("/WEB-INF/classes/".length(), resourcePath.length() - ".class".length())
                .replace('/', '.');

        try {
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
            if (clazz.isAnnotationPresent(Controller.class)) {
                context.log("Classe trouvée : " + clazz.getName());
                for (Method method : clazz.getDeclaredMethods()) {
                    UrlMapping annotation = method.getAnnotation(UrlMapping.class);
                    if (annotation != null) {
                        String url = annotation.value();
                        mappings.put(url, new Mapping(clazz.getName(), method.getName()));
                        context.log("URL trouvée : " + url + " -> " + clazz.getName() + "." + method.getName());
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            context.log("ControllerScanner: impossible de charger la classe " + className + " -> " + e.getMessage());
        }
    }
}
