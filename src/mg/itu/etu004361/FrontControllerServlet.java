package mg.itu.etu004361;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import mg.itu.etu004361.util.ControllerScanner;

public class FrontControllerServlet extends HttpServlet {
    private final Map<String, Mapping> mappings = new LinkedHashMap<>();
    private String packageToScan;
    private String initializationError;

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
                Object instance = clazz.getDeclaredConstructor().newInstance();
                Method method = clazz.getDeclaredMethod(mapping.getMethodName());

                Object result = method.invoke(instance);

                if (mapping.isWebAPI()) {
                    response.setContentType("application/json; charset=UTF-8");
                    try (PrintWriter writer = response.getWriter()) {
                        writer.write(toJson(result));
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
