import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class ApiDocServer {
    public static void main(String[] args) throws Exception {
        int port = 8081;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/apidoc", new ApiDocHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("API Documentation server started on port " + port);
        System.out.println("Open http://localhost:" + port + "/apidoc/ in your browser");
    }

    static class ApiDocHandler implements HttpHandler {
        private final Path apiDocPath = Paths.get("target/docs-web-1.12-SNAPSHOT/apidoc");
        private final Map<String, String> mimeTypes = new HashMap<>();

        public ApiDocHandler() {
            mimeTypes.put(".html", "text/html");
            mimeTypes.put(".js", "application/javascript");
            mimeTypes.put(".css", "text/css");
            mimeTypes.put(".json", "application/json");
            mimeTypes.put(".png", "image/png");
            mimeTypes.put(".jpg", "image/jpeg");
            mimeTypes.put(".gif", "image/gif");
            mimeTypes.put(".svg", "image/svg+xml");
            mimeTypes.put(".ico", "image/x-icon");
            mimeTypes.put(".ttf", "font/ttf");
            mimeTypes.put(".woff", "font/woff");
            mimeTypes.put(".woff2", "font/woff2");
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath().substring("/apidoc".length());
            if (path.equals("") || path.equals("/")) {
                path = "/index.html";
            }

            Path filePath = apiDocPath.resolve(path.substring(1));
            
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                // Determine content type
                String contentType = "application/octet-stream";
                String fileName = filePath.toString();
                for (Map.Entry<String, String> entry : mimeTypes.entrySet()) {
                    if (fileName.endsWith(entry.getKey())) {
                        contentType = entry.getValue();
                        break;
                    }
                }
                
                byte[] content = Files.readAllBytes(filePath);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, content.length);
                exchange.getResponseBody().write(content);
            } else {
                String response = "File not found: " + path;
                exchange.sendResponseHeaders(404, response.length());
                exchange.getResponseBody().write(response.getBytes());
            }
            exchange.getResponseBody().close();
        }
    }
} 