package mg.itu.etu004361;

import java.lang.reflect.Method;

public class Mapping {
    private final String httpMethod;
    private final String url;
    private final Class<?> controllerClass;
    private final Method controllerMethod;

    public Mapping(String httpMethod, String url, Class<?> controllerClass, Method controllerMethod) {
        this.httpMethod = httpMethod;
        this.url = url;
        this.controllerClass = controllerClass;
        this.controllerMethod = controllerMethod;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getUrl() {
        return url;
    }

    public Class<?> getControllerClass() {
        return controllerClass;
    }

    public Method getControllerMethod() {
        return controllerMethod;
    }

    public String getClassName() {
        return controllerClass.getName();
    }

    public String getMethodName() {
        return controllerMethod.getName();
    }
}
