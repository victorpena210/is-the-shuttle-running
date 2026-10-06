package technology.renatus.routepulse.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.IWebApplication;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.IWebRequest;
import org.thymeleaf.web.IWebSession;

/** Enables Thymeleaf's real web-variable rules without a database or servlet container. */
public final class WebTemplateContext {
    private WebTemplateContext() { }

    public static WebContext create(Map<String, Object> model) {
        var request = proxy(IWebRequest.class, (object, method, args) -> switch (method.getName()) {
            case "getMethod" -> "GET";
            case "getScheme" -> "http";
            case "getServerName" -> "localhost";
            case "getServerPort" -> 8080;
            case "getApplicationPath", "getPathWithinApplication" -> "";
            case "getQueryString" -> null;
            case "getParameterMap", "getHeaderMap", "getCookieMap" -> Map.of();
            case "getAllParameterNames", "getAllHeaderNames", "getAllCookieNames" -> Set.of();
            case "getParameterCount", "getHeaderCount", "getCookieCount" -> 0;
            case "containsParameter", "containsHeader", "containsCookie" -> false;
            case "getParameterValues", "getHeaderValues", "getCookieValues" -> null;
            default -> InvocationHandler.invokeDefault(object, method, args);
        });
        var session = proxy(IWebSession.class, attributeHandler(Map.of("exists", false)));
        var application = proxy(IWebApplication.class, attributeHandler(Map.of("resourceExists", false)));
        var attributes = attributeHandler(Map.of());
        var exchange = proxy(IWebExchange.class, (object, method, args) -> switch (method.getName()) {
            case "getRequest" -> request;
            case "getSession" -> session;
            case "getApplication" -> application;
            case "hasSession" -> false;
            case "getPrincipal" -> null;
            case "getLocale" -> Locale.US;
            case "getContentType" -> "text/html";
            case "getCharacterEncoding" -> "UTF-8";
            case "transformURL" -> args[0];
            default -> attributes.invoke(object, method, args);
        });
        return new WebContext(exchange, Locale.US, model);
    }

    private static InvocationHandler attributeHandler(Map<String, Object> constants) {
        Map<String, Object> attributes = new HashMap<>();
        return (object, method, args) -> {
            if (constants.containsKey(method.getName())) return constants.get(method.getName());
            return switch (method.getName()) {
                case "containsAttribute" -> attributes.containsKey(args[0]);
                case "getAttributeCount" -> attributes.size();
                case "getAllAttributeNames" -> attributes.keySet();
                case "getAttributeMap" -> Map.copyOf(attributes);
                case "getAttributeValue" -> attributes.get(args[0]);
                case "setAttributeValue" -> { attributes.put((String) args[0], args[1]); yield null; }
                case "removeAttribute" -> { attributes.remove(args[0]); yield null; }
                case "getResourceAsStream" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
