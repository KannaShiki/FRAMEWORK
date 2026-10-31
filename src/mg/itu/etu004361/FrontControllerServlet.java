package mg.itu.etu004361;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import mg.itu.etu004361.annotation.Auth;
import mg.itu.etu004361.util.AuthChecker;
import mg.itu.etu004361.util.ControllerScanner;
import mg.itu.etu004361.util.FrameworkConfig;

@MultipartConfig(
        maxFileSize = 10 * 1024 * 1024,
        maxRequestSize = 20 * 1024 * 1024,
        fileSizeThreshold = 1024
)
public class FrontControllerServlet extends HttpServlet {
    private final Map<String, Mapping> mappings = new LinkedHashMap<>();
    private final Map<Class<?>, Object> controllerInstances = new ConcurrentHashMap<>();
    private String packageToScan;
    private String initializationError;

    private static class BindingContext {
        private final Object[] arguments;
        private final Session sessionArgument;
        private final Set<String> originalSessionNames;

        private BindingContext(Object[] arguments, Session sessionArgument, Set<String> originalSessionNames) {
            this.arguments = arguments;
            this.sessionArgument = sessionArgument;
            this.originalSessionNames = originalSessionNames;
        }
    }

    // prefixe et suffixe lus depuis web.xml
    private String prefixe;
    private String suffixe;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        packageToScan = config.getInitParameter("scanPackage");
        if (packageToScan == null || packageToScan.trim().isEmpty()) {
            packageToScan = getServletContext().getInitParameter("scanPackage");
        }

        if (packageToScan == null || packageToScan.trim().isEmpty()) {
            getServletContext().log("FrontControllerServlet.init : aucun package à scanner défini");
            return;
        }

        getServletContext().log("=== SCAN FRAMEWORK ===");
        getServletContext().log("Package scanné : " + packageToScan);
        mappings.clear();
        try {
            mappings.putAll(ControllerScanner.scanPackage(packageToScan, getServletContext()));
        } catch (IllegalStateException e) {
            getServletContext().log(e.getMessage());
            initializationError = e.getMessage();
            return;
        }

        getServletContext().log("====================");
        getServletContext().log("Controllers enregistrés :");
        for (Map.Entry<String, Mapping> entry : mappings.entrySet()) {
            getServletContext().log(entry.getKey() + " -> " + entry.getValue().getClassName() + "." + entry.getValue().getMethodName());
        }

        // lecture du prefixe et suffixe depuis web.xml
        this.prefixe = getServletConfig().getInitParameter("prefixe");
        this.suffixe = getServletConfig().getInitParameter("suffixe");
        getServletContext().log("[Framework] prefixe = " + prefixe);
        getServletContext().log("[Framework] suffixe = " + suffixe);

        // Initialize FrameworkConfig with context params
        java.util.Map<String, String> contextParams = new java.util.LinkedHashMap<>();
        Enumeration<String> paramNames = getServletContext().getInitParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            contextParams.put(name, getServletContext().getInitParameter(name));
        }
        FrameworkConfig.init(contextParams);

        // Validate auth configuration
        try {
            AuthChecker.validate(mappings);
        } catch (Exception e) {
            getServletContext().log("Auth validation error: " + e.getMessage());
            initializationError = "Auth validation error: " + e.getMessage();
            return;
        }
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (initializationError != null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, initializationError);
            return;
        }

        response.setContentType("text/plain; charset=UTF-8");

        String path = request.getPathInfo();
        String contextPath = request.getContextPath();

        if (path == null || path.isEmpty() || "/".equals(path)) {
            String servletPath = request.getServletPath();
            if (servletPath != null && !servletPath.isEmpty() && !"/".equals(servletPath)) {
                path = servletPath;
            } else {
                String requestUri = request.getRequestURI();
                if (requestUri != null && !requestUri.isEmpty()) {
                    if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
                        path = requestUri.substring(contextPath.length());
                    } else {
                        path = requestUri;
                    }
                }
            }
        }

        if (contextPath != null && !contextPath.isEmpty() && path != null && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        String httpMethod = request.getMethod().toUpperCase();
        String routeKey = httpMethod + " " + path;

        if (mappings.containsKey(routeKey)) {
            Mapping mapping = mappings.get(routeKey);

            try {
                Class<?> clazz = Class.forName(mapping.getClassName());
                Method method = resolveControllerMethod(clazz, mapping.getMethodName());

                // Check authentication
                AuthChecker.AuthResult authResult = AuthChecker.check(request, clazz, method);
                if (authResult == AuthChecker.AuthResult.NON_AUTHENTIFIE) {
                    if (mapping.isWebAPI()) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    } else {
                        response.sendRedirect(request.getContextPath() + FrameworkConfig.getLoginUrl());
                    }
                    return;
                }
                if (authResult == AuthChecker.AuthResult.INTERDIT) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

                Object instance = getOrCreateControllerInstance(clazz);
                BindingContext bindingContext = bindMethodArguments(method, request);

                Object result = method.invoke(instance, bindingContext.arguments);
                synchronizeSessionAfterInvocation(request, bindingContext);

                if (mapping.isWebAPI()) {
                    response.setContentType("application/json; charset=UTF-8");
                    try (PrintWriter writer = response.getWriter()) {
                        writer.write(toJson(result));
                    }
                    return;
                }

                if (result instanceof String) {
                    String text = (String) result;
                    String trimmed = text.trim();
                    if (trimmed.startsWith("<!DOCTYPE html") || trimmed.startsWith("<html") || trimmed.contains("<form")) {
                        response.setContentType("text/html; charset=UTF-8");
                    } else {
                        response.setContentType("text/plain; charset=UTF-8");
                    }
                    try (PrintWriter writer = response.getWriter()) {
                        writer.write(text);
                    }
                    return;
                }

                if (result instanceof mg.itu.etu004361.ModelAndView) {
                    mg.itu.etu004361.ModelAndView mv = (mg.itu.etu004361.ModelAndView) result;

                    String prefixeJsp = (prefixe != null ? prefixe : "WEB-INF/views/");
                    if (!prefixeJsp.startsWith("/")) {
                        prefixeJsp = "/" + prefixeJsp;
                    }
                    String cheminJsp = prefixeJsp + mv.getUrl() + (suffixe != null ? suffixe : ".jsp");

                    for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                        request.setAttribute(entry.getKey(), entry.getValue());
                    }

                    RequestDispatcher dispatcher = request.getRequestDispatcher(cheminJsp);
                    dispatcher.forward(request, response);
                    return;
                } else {
                    try (PrintWriter writer = response.getWriter()) {
                        writer.println("Route supported");
                        writer.println();
                        writer.println("HTTP method : " + mapping.getMethod());
                        writer.println("URL : " + mapping.getUrl());
                        writer.println("Classe : " + mapping.getClassName());
                        writer.println("Méthode : " + mapping.getMethodName());
                    }
                }

            } catch (Exception e) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                e.printStackTrace();
            }

        } else {
            try (PrintWriter writer = response.getWriter()) {
                writer.println("Route not supported");
                writer.println();
                writer.println("Available routes :");
                for (String route : mappings.keySet()) {
                    writer.println(route);
                }
            }
        }
    }

    private Method resolveControllerMethod(Class<?> clazz, String methodName) throws NoSuchMethodException {
        for (Method candidate : clazz.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                return candidate;
            }
        }
        return clazz.getDeclaredMethod(methodName);
    }

    private Object getOrCreateControllerInstance(Class<?> clazz) throws ReflectiveOperationException {
        Object existing = controllerInstances.get(clazz);
        if (existing != null) {
            return existing;
        }

        Object instance = clazz.getDeclaredConstructor().newInstance();
        Object previous = controllerInstances.putIfAbsent(clazz, instance);
        return previous != null ? previous : instance;
    }


    private BindingContext bindMethodArguments(Method method, HttpServletRequest request) {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];
        Session sessionArgument = null;
        Set<String> originalSessionNames = new HashSet<>();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.getType() == Session.class) {
                sessionArgument = buildSessionFromHttpSession(request, originalSessionNames);
                arguments[i] = sessionArgument;
            } else {
                arguments[i] = bindParameterValue(parameter, request);
            }
        }

        return new BindingContext(arguments, sessionArgument, originalSessionNames);
    }

    private Session buildSessionFromHttpSession(HttpServletRequest request, Set<String> originalSessionNames) {
        HttpSession httpSession = request.getSession(true);
        Session session = new Session();

        Enumeration<String> attributeNames = httpSession.getAttributeNames();
        while (attributeNames.hasMoreElements()) {
            String attributeName = attributeNames.nextElement();
            originalSessionNames.add(attributeName);
            session.setAttribute(attributeName, httpSession.getAttribute(attributeName));
        }

        return session;
    }

    private void synchronizeSessionAfterInvocation(HttpServletRequest request, BindingContext bindingContext) {
        if (bindingContext == null || bindingContext.sessionArgument == null) {
            return;
        }

        HttpSession httpSession = request.getSession(false);
        if (httpSession == null) {
            return;
        }

        Map<String, Object> sessionData = bindingContext.sessionArgument.getData();
        for (Map.Entry<String, Object> entry : sessionData.entrySet()) {
            httpSession.setAttribute(entry.getKey(), entry.getValue());
        }

        for (String originalName : bindingContext.originalSessionNames) {
            if (!sessionData.containsKey(originalName)) {
                httpSession.removeAttribute(originalName);
            }
        }
    }

    private Object bindParameterValue(Parameter parameter, HttpServletRequest request) {
        String parameterName = parameter.getName();
        Class<?> parameterType = parameter.getType();

        if (parameterType == Session.class) {
            return buildSessionFromHttpSession(request, Collections.emptySet());
        }

        if (parameterType == MapFile.class) {
            return buildMapFileFromRequest(parameterName, request);
        }

        if (isSimpleType(parameterType)) {
            String requestValue = request.getParameter(parameterName);
            return convertSimpleValue(requestValue, parameterType);
        }

        return buildObjectFromRequest(parameterName, parameterType, request);
    }

    private Map<String, String[]> getRequestTextParameters(HttpServletRequest request) {
        Map<String, String[]> params = new LinkedHashMap<>();
        if (request.getParameterMap() != null) {
            params.putAll(request.getParameterMap());
        }

        if (!isMultipartRequest(request)) {
            return params;
        }

        try {
            for (Part part : request.getParts()) {
                if (part == null || part.getName() == null || part.getSubmittedFileName() != null
                        && !part.getSubmittedFileName().trim().isEmpty()) {
                    continue;
                }

                String value = readPartAsString(part);
                params.put(part.getName(), new String[]{value});
            }
        } catch (Exception ignored) {
            // ignore malformed multipart data and keep non-file parameters
        }

        return params;
    }

    private boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("multipart/");
    }

    private String readPartAsString(Part part) throws IOException {
        if (part == null) {
            return null;
        }

        try (InputStream inputStream = part.getInputStream(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toString();
        }
    }

    private MapFile buildMapFileFromRequest(String parameterName, HttpServletRequest request) {
        if (parameterName == null || parameterName.isEmpty() || !isMultipartRequest(request)) {
            return null;
        }

        try {
            Part part = request.getPart(parameterName);
            return buildMapFileFromPart(part);
        } catch (Exception ignored) {
            return null;
        }
    }

    private MapFile buildMapFileFromPart(Part part) {
        if (part == null) {
            return null;
        }

        String submittedFileName = part.getSubmittedFileName();
        if (submittedFileName == null || submittedFileName.trim().isEmpty()) {
            return null;
        }

        String fileName = submittedFileName.replace('\\', '/');
        int lastSeparator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            fileName = fileName.substring(lastSeparator + 1);
        }

        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            extension = fileName.substring(lastDot + 1);
        }

        byte[] bytes = readPartBytes(part);
        return new MapFile(fileName, extension, bytes);
    }

    private byte[] readPartBytes(Part part) {
        if (part == null) {
            return new byte[0];
        }

        try (InputStream inputStream = part.getInputStream(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type == Integer.class
                || type == Long.class
                || type == Double.class
                || type == Float.class
                || type == Boolean.class
                || type == Short.class
                || type == Byte.class
                || type == Character.class;
    }

    private Object buildObjectFromRequest(String parameterName, Class<?> parameterType, HttpServletRequest request) {
        if (parameterName == null || parameterName.isEmpty()) {
            return null;
        }

        try {
            Object object = parameterType.getDeclaredConstructor().newInstance();
            populateObjectFields(object, parameterName, request);
            return object;
        } catch (Exception e) {
            return null;
        }
    }

    private void populateObjectFields(Object object, String parameterName, HttpServletRequest request) {
        Map<String, String[]> params = getRequestTextParameters(request);

        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(parameterName + ".")) {
                continue;
            }

            String propertyPath = key.substring(parameterName.length() + 1);
            if (propertyPath == null || propertyPath.isEmpty()) {
                continue;
            }

            String value = entry.getValue() != null && entry.getValue().length > 0 ? entry.getValue()[0] : null;
            setNestedProperty(object, propertyPath, value);
        }

        if (isMultipartRequest(request)) {
            try {
                for (Part part : request.getParts()) {
                    System.out.println("DEBUG_PART name=" + part.getName() + " submitted=" + part.getSubmittedFileName() + " contentType=" + part.getContentType());
                    if (part == null || part.getName() == null || part.getSubmittedFileName() == null || part.getSubmittedFileName().trim().isEmpty()) {
                        continue;
                    }

                    String key = part.getName();
                    if (!key.startsWith(parameterName + ".")) {
                        continue;
                    }

                    String propertyPath = key.substring(parameterName.length() + 1);
                    if (propertyPath == null || propertyPath.isEmpty()) {
                        continue;
                    }

                    setNestedProperty(object, propertyPath, buildMapFileFromPart(part));
                }
            } catch (Exception ignored) {
                System.out.println("DEBUG_PART_ERR " + ignored.getMessage());
                // ignore malformed or unsupported multipart data
            }
        }
    }

    private boolean isMultipartFileField(HttpServletRequest request, String fieldName) {
        if (!isMultipartRequest(request) || fieldName == null || fieldName.isEmpty()) {
            return false;
        }

        try {
            Part part = request.getPart(fieldName);
            return part != null && part.getSubmittedFileName() != null && !part.getSubmittedFileName().trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void setNestedProperty(Object target, String propertyPath, String value) {
        if (target == null || propertyPath == null || propertyPath.isEmpty()) {
            return;
        }

        String[] parts = propertyPath.split("\\.");
        Object current = target;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object nested = readOrCreateNestedObject(current, part);
            if (nested == null) {
                return;
            }
            current = nested;
        }

        String finalProperty = parts[parts.length - 1];
        setPropertyValue(current, finalProperty, value);
    }

    private void setNestedProperty(Object target, String propertyPath, MapFile value) {
        if (target == null || propertyPath == null || propertyPath.isEmpty()) {
            return;
        }

        String[] parts = propertyPath.split("\\.");
        Object current = target;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object nested = readOrCreateNestedObject(current, part);
            if (nested == null) {
                return;
            }
            current = nested;
        }

        String finalProperty = parts[parts.length - 1];
        setPropertyValue(current, finalProperty, value);
    }

    private Object readOrCreateNestedObject(Object target, String propertyName) {
        Object nested = readProperty(target, propertyName);
        if (nested != null) {
            return nested;
        }

        Field field = findField(target.getClass(), propertyName);
        if (field == null) {
            return null;
        }

        Class<?> fieldType = field.getType();
        if (isSimpleType(fieldType)) {
            return null;
        }

        try {
            Object newObject = fieldType.getDeclaredConstructor().newInstance();
            setPropertyValue(target, propertyName, newObject);
            return newObject;
        } catch (Exception e) {
            return null;
        }
    }

    private Object readProperty(Object target, String propertyName) {
        try {
            Field field = findField(target.getClass(), propertyName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(target);
            }

            String getterName = "get" + capitalize(propertyName);
            Method getter = target.getClass().getMethod(getterName);
            return getter.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private void setPropertyValue(Object target, String propertyName, String value) {
        if (target == null) {
            return;
        }

        Field field = findField(target.getClass(), propertyName);
        if (field != null) {
            try {
                field.setAccessible(true);
                field.set(target, convertSimpleValue(value, field.getType()));
            } catch (Exception e) {
                // ignore invalid fields
            }
            return;
        }

        String setterName = "set" + capitalize(propertyName);
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                try {
                    Object convertedValue = convertSimpleValue(value, method.getParameterTypes()[0]);
                    method.invoke(target, convertedValue);
                    return;
                } catch (Exception e) {
                    // ignore invalid setter conversion
                }
            }
        }
    }

    private void setPropertyValue(Object target, String propertyName, Object value) {
        if (target == null) {
            return;
        }

        Field field = findField(target.getClass(), propertyName);
        if (field != null) {
            try {
                field.setAccessible(true);
                field.set(target, value);
            } catch (Exception e) {
                // ignore invalid fields
            }
            return;
        }

        String setterName = "set" + capitalize(propertyName);
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                try {
                    method.invoke(target, value);
                    return;
                } catch (Exception e) {
                    // ignore invalid setter conversion
                }
            }
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(fieldName)) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private Object convertSimpleValue(String requestValue, Class<?> parameterType) {
        if (requestValue == null) {
            if (parameterType == String.class) {
                return null;
            }
            if (parameterType == Boolean.class || parameterType == boolean.class) {
                return parameterType == boolean.class ? false : null;
            }
            if (parameterType == Character.class || parameterType == char.class) {
                return parameterType == char.class ? '\0' : null;
            }
            if (parameterType == Byte.class || parameterType == byte.class) {
                return parameterType == byte.class ? (byte) 0 : null;
            }
            if (parameterType == Short.class || parameterType == short.class) {
                return parameterType == short.class ? (short) 0 : null;
            }
            if (parameterType == Integer.class || parameterType == int.class) {
                return parameterType == int.class ? 0 : null;
            }
            if (parameterType == Long.class || parameterType == long.class) {
                return parameterType == long.class ? 0L : null;
            }
            if (parameterType == Float.class || parameterType == float.class) {
                return parameterType == float.class ? 0.0f : null;
            }
            if (parameterType == Double.class || parameterType == double.class) {
                return parameterType == double.class ? 0.0d : null;
            }
            return null;
        }

        if (parameterType == String.class) {
            return requestValue;
        }
        if (parameterType == Integer.class || parameterType == int.class) {
            return Integer.valueOf(requestValue);
        }
        if (parameterType == Long.class || parameterType == long.class) {
            return Long.valueOf(requestValue);
        }
        if (parameterType == Double.class || parameterType == double.class) {
            return Double.valueOf(requestValue);
        }
        if (parameterType == Float.class || parameterType == float.class) {
            return Float.valueOf(requestValue);
        }
        if (parameterType == Boolean.class || parameterType == boolean.class) {
            return Boolean.valueOf(requestValue);
        }
        if (parameterType == Short.class || parameterType == short.class) {
            return Short.valueOf(requestValue);
        }
        if (parameterType == Byte.class || parameterType == byte.class) {
            return Byte.valueOf(requestValue);
        }
        if (parameterType == Character.class || parameterType == char.class) {
            return requestValue.isEmpty() ? '\0' : requestValue.charAt(0);
        }

        return requestValue;
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof String) {
            String text = (String) value;
            String trimmed = text.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[") || "true".equalsIgnoreCase(trimmed)
                    || "false".equalsIgnoreCase(trimmed) || "null".equalsIgnoreCase(trimmed)
                    || trimmed.matches("-?\\d+(\\.\\d+)?")) {
                return text;
            }
            return quoteJson(text);
        }

        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }

        if (value instanceof Character) {
            return quoteJson(String.valueOf(value));
        }

        if (value instanceof java.util.Map<?, ?>) {
            return mapToJson((java.util.Map<?, ?>) value);
        }

        if (value instanceof java.util.Collection<?>) {
            return collectionToJson((java.util.Collection<?>) value);
        }

        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            java.util.List<Object> list = new java.util.ArrayList<>();
            for (int i = 0; i < length; i++) {
                list.add(java.lang.reflect.Array.get(value, i));
            }
            return collectionToJson(list);
        }

        if (value instanceof Enum<?>) {
            return quoteJson(((Enum<?>) value).name());
        }

        return beanToJson(value);
    }

    private String mapToJson(java.util.Map<?, ?> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append(quoteJson(String.valueOf(entry.getKey()))).append(":").append(toJson(entry.getValue()));
        }
        json.append("}");
        return json.toString();
    }

    private String collectionToJson(java.util.Collection<?> collection) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        boolean first = true;
        for (Object item : collection) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append(toJson(item));
        }
        json.append("]");
        return json.toString();
    }

    private String beanToJson(Object bean) {
        java.util.Map<String, Object> properties = new java.util.LinkedHashMap<>();
        for (java.lang.reflect.Method method : bean.getClass().getMethods()) {
            String name = method.getName();
            if (method.getParameterCount() != 0 || name.equals("getClass")) {
                continue;
            }
            if (name.startsWith("get") && name.length() > 3) {
                String property = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                try {
                    properties.put(property, method.invoke(bean));
                } catch (ReflectiveOperationException ignored) {
                    // ignore unreadable properties
                }
            } else if (name.startsWith("is") && name.length() > 2) {
                String property = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                try {
                    properties.put(property, method.invoke(bean));
                } catch (ReflectiveOperationException ignored) {
                    // ignore unreadable properties
                }
            }
        }
        return mapToJson(properties);
    }

    private String quoteJson(String value) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        sb.append('"');
        return sb.toString();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
}
