import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class HttpUtils {


    // GET connect to url with cookie(if provided), return set-cookie in the header and the body
    /*
     * @
     */
    public static HttpURLConnection get(String url, String cookie) {
        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) new URL(url).openConnection();

            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.140 Safari/537.36 Edge/17.17134");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.8,zh-Hans-CN;q=0.5,zh-Hans;q=0.3");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (cookie != null) conn.setRequestProperty("Cookie", cookie);

            conn.connect();

//            map.put("Set-Cookie", conn.getHeaderField("Set-Cookie"));
//
//            try (Scanner in = new Scanner(conn.getInputStream())) {
//                map.put("Body", in.useDelimiter("\\A").next());
//            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return conn;
    }

    /*
     * @parameters: Map of Url, Referer, Cookie, PostMessage
     * @return: String of responseBody
     */
    public static String post(Map<String, String> parameters) {
        String response = null;

        try {
            if (!parameters.containsKey("Url"))
                System.err.println("POST ERROR: please provide an url!");

            HttpURLConnection conn = (HttpURLConnection) new URL(parameters.get("Url")).openConnection();

            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.140 Safari/537.36 Edge/17.17134");
            if (parameters.containsKey("Referer"))
                conn.setRequestProperty("Referer", parameters.get("Referer"));
            if (parameters.containsKey("Cookie"))
                conn.setRequestProperty("Cookie", parameters.get("Cookie"));

            conn.connect();

            try (PrintWriter out = new PrintWriter(conn.getOutputStream())) {
                if (parameters.containsKey("PostMessage") )
                    out.write(parameters.get("PostMessage"));
            }

//            getResponseHeader(conn);

            try (Scanner in = new Scanner(conn.getInputStream())) {
                response = in.next();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }

    public static String getResponseHeader(HttpURLConnection conn) {
        StringBuilder builder = new StringBuilder();
        try {
            // build response line
            builder.append(conn.getResponseCode())
                    .append(" ")
                    .append(conn.getResponseMessage())
                    .append("\n");

            Map<String, List<String>> headerFields = conn.getHeaderFields();

            // build response header
            for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
                if (entry.getKey() == null)
                    continue;

                builder.append(entry.getKey())
                        .append(": ");

                Iterator<String> it = entry.getValue().iterator();
                if (it.hasNext()) {
                    builder.append(it.next());
                    while (it.hasNext()) {
                        builder.append(", ")
                                .append(it.next());
                    }
                }

                builder.append("\n");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

}
