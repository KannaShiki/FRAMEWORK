package mg.itu.etu004361;

public class Mapping {
    private final String method;
    private final String url;
    private final String className;
    private final String methodName;
    private final boolean webAPI;

    public Mapping(String method, String url, String className, String methodName) {
        this(method, url, className, methodName, false);
    }

    public Mapping(String method, String url, String className, String methodName, boolean webAPI) {
        this.method = method;
        this.url = url;
        this.className = className;
        this.methodName = methodName;
        this.webAPI = webAPI;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean isWebAPI() {
        return webAPI;
    }
}
