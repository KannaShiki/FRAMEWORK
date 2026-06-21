package mg.itu.etu004361.util;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ControllerScanner {
    public static List<Class<?>> scanPackage(String packageName, ServletContext context) {
        List<Class<?>> classes = new ArrayList<>();
        if (packageName == null || packageName.isEmpty()) {
            return classes;
        }

        String packagePath = packageName.replace('.', '/');
        String resourcePath = "/WEB-INF/classes/" + packagePath + "/";
        Set<String> resourcePaths = context.getResourcePaths(resourcePath);

        if (resourcePaths == null) {
            context.log("ControllerScanner: package non trouvé : " + packageName);
            return classes;
        }

        for (String path : resourcePaths) {
            scanResource(path, classes, context);
        }

        return classes;
    }

    private static void scanResource(String resourcePath, List<Class<?>> classes, ServletContext context) {
        if (resourcePath.endsWith("/")) {
            Set<String> childPaths = context.getResourcePaths(resourcePath);
            if (childPaths != null) {
                for (String childPath : childPaths) {
                    scanResource(childPath, classes, context);
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
            classes.add(clazz);
        } catch (ClassNotFoundException e) {
            context.log("ControllerScanner: impossible de charger la classe " + className + " -> " + e.getMessage());
        }
    }
}
