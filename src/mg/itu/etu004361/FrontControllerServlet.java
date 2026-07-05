package mg.itu.etu004361;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
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
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (initializationError != null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, initializationError);
            return;
        }

        response.setContentType("text/plain; charset=UTF-8");
        String path = request.getPathInfo();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            path = "/";
        }

        String httpMethod = request.getMethod().toUpperCase();
        String routeKey = httpMethod + " " + path;

        try (PrintWriter writer = response.getWriter()) {
            if (mappings.containsKey(routeKey)) {
                Mapping mapping = mappings.get(routeKey);
                writer.println("Route supported");
                writer.println();
                writer.println("HTTP method : " + mapping.getMethod());
                writer.println("URL : " + mapping.getUrl());
                writer.println("Classe : " + mapping.getClassName());
                writer.println("Méthode : " + mapping.getMethodName());
            } else {
                writer.println("Route not supported");
                writer.println();
                writer.println("Available routes :");
                for (String route : mappings.keySet()) {
                    writer.println(route);
                }
            }
        }
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
