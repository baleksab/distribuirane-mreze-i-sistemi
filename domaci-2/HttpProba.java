import java.net.URI;
import java.net.http.*;
public class HttpProba {
    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "https://example.com";
        var client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(3)).build();
        var req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        long start = System.currentTimeMillis();
        try {
            var res = client.send(req,
                    HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + res.statusCode());
            System.out.println(res.headers().map());
        } catch (Exception e) {
            System.out.println(e.getClass().getName());
            System.out.println(e.getMessage());
        }
        long ms = System.currentTimeMillis() - start;
        System.out.println("Trajanje: " + ms + " ms");
    }
}