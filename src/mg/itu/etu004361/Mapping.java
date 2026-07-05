package mg.itu.etu004361;

public class Mapping {
    private final String method;
    private final String url;
    private final String className;
    private final String methodName;

    public Mapping(String method, String url, String className, String methodName) {
        this.method = method;
        this.url = url;
        this.className = className;
        this.methodName = methodName;
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
}
