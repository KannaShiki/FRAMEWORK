package mg.itu.etu004361;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import mg.itu.etu004361.annotation.Controller;
import mg.itu.etu004361.util.ControllerScanner;

public class FrontControllerServlet extends HttpServlet {
    private final List<String> controllers = new ArrayList<>();
    private final List<String> scanDetails = new ArrayList<>();
    private String packageToScan;

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

        getServletContext().log("Package scanné : " + packageToScan);
        List<Class<?>> classes = ControllerScanner.scanPackage(packageToScan, getServletContext());

        for (Class<?> clazz : classes) {
            getServletContext().log("Classe trouvée : " + clazz.getName());
            if (clazz.isAnnotationPresent(Controller.class)) {
                getServletContext().log("@Controller détecté");
                controllers.add(clazz.getName());
                scanDetails.add("Classe trouvée : " + clazz.getName() + "\n@Controller : OUI");
            } else {
                getServletContext().log("Pas de @Controller");
                scanDetails.add("Classe trouvée : " + clazz.getName() + "\n@Controller : NON");
            }
        }

        getServletContext().log("====================");
        getServletContext().log("Controllers enregistrés :");
        for (String controller : controllers) {
            getServletContext().log(controller);
        }
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain; charset=UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.println("Package scanné : " + packageToScan);
            writer.println();
            for (String detail : scanDetails) {
                writer.println(detail);
                writer.println();
            }
            writer.println("====================");
            writer.println();
            writer.println("Controllers trouvés :");
            for (String controller : controllers) {
                writer.println(controller);
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
