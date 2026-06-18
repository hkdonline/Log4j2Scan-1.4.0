package burp.utils;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyPool {

    private List<ProxyConfig> proxies = new CopyOnWriteArrayList<>();
    private AtomicInteger currentIndex = new AtomicInteger(0);
    private ConcurrentHashMap<String, Integer> failureCount = new ConcurrentHashMap<>();
    private static final int MAX_FAILURES = 3;

    public static class ProxyConfig {
        public String type;
        public String host;
        public int port;
        public String username;
        public String password;

        public Proxy toProxy() {
            Proxy.Type proxyType = "SOCKS5".equalsIgnoreCase(type) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            return new Proxy(proxyType, new InetSocketAddress(host, port));
        }

        public String getId() {
            return type + "://" + host + ":" + port;
        }
    }

    public void loadFromString(String proxyPoolText) {
        proxies.clear();
        failureCount.clear();
        
        if (proxyPoolText == null || proxyPoolText.trim().isEmpty()) {
            return;
        }

        String[] lines = proxyPoolText.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("://");
            if (parts.length != 2) {
                continue;
            }

            String type = parts[0].toUpperCase();
            String rest = parts[1];
            
            String[] hostParts = rest.split(":");
            if (hostParts.length < 2) {
                continue;
            }

            String host = hostParts[0];
            int port = Integer.parseInt(hostParts[1]);
            String username = "";
            String password = "";

            if (hostParts.length >= 3) {
                username = hostParts[2];
            }
            if (hostParts.length >= 4) {
                password = hostParts[3];
            }

            ProxyConfig config = new ProxyConfig();
            config.type = type;
            config.host = host;
            config.port = port;
            config.username = username;
            config.password = password;
            proxies.add(config);
        }
    }

    public ProxyConfig getNextProxyConfig() {
        if (proxies.isEmpty()) {
            return null;
        }

        int startIndex = currentIndex.get();
        for (int i = 0; i < proxies.size(); i++) {
            int index = (startIndex + i) % proxies.size();
            ProxyConfig config = proxies.get(index);
            if (failureCount.getOrDefault(config.getId(), 0) < MAX_FAILURES) {
                currentIndex.set(index + 1);
                return config;
            }
        }
        return null;
    }

    public Proxy getNextProxy() {
        ProxyConfig config = getNextProxyConfig();
        return config != null ? config.toProxy() : Proxy.NO_PROXY;
    }

    public String getNextProxyUsername() {
        if (proxies.isEmpty()) {
            return "";
        }

        int index = (currentIndex.get() - 1 + proxies.size()) % proxies.size();
        return proxies.get(index).username;
    }

    public String getNextProxyPassword() {
        if (proxies.isEmpty()) {
            return "";
        }

        int index = (currentIndex.get() - 1 + proxies.size()) % proxies.size();
        return proxies.get(index).password;
    }

    public void markFailure(ProxyConfig config) {
        failureCount.merge(config.getId(), 1, Integer::sum);
    }

    public void resetFailure(String proxyId) {
        failureCount.remove(proxyId);
    }

    public void resetAllFailures() {
        failureCount.clear();
    }

    public int getProxyCount() {
        return proxies.size();
    }

    public List<ProxyConfig> getProxies() {
        return new ArrayList<>(proxies);
    }

    public boolean isEmpty() {
        return proxies.isEmpty();
    }
}
