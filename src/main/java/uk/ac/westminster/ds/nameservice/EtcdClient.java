package uk.ac.westminster.ds.nameservice;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EtcdClient {
    private final String etcdAddress;

    public EtcdClient(String etcdAddress) {
        this.etcdAddress = etcdAddress;
    }

    public void put(String key, String value) throws IOException {
        System.out.println("Putting Key=" + key + ", Value=" + value);
        String putUrl = etcdAddress + "/v3/kv/put";
        String serverResponse = callEtcd(putUrl, buildPutRequestPayload(key, value));
        System.out.println(serverResponse);
    }

    public String get(String key) throws IOException {
        System.out.println("Getting value for Key=" + key);
        String getUrl = etcdAddress + "/v3/kv/range";
        return callEtcd(getUrl, buildGetRequestPayload(key));
    }

    private String callEtcd(String url, String payload) throws IOException {
        URL etcdUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) etcdUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.connect();

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        try (InputStream inputStream = connection.getInputStream()) {
            return readResponse(inputStream);
        } finally {
            connection.disconnect();
        }
    }

    private String readResponse(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        int c = inputStream.read();
        while (c != -1) {
            builder.append((char) c);
            c = inputStream.read();
        }
        return builder.toString();
    }

    private String buildPutRequestPayload(String key, String value) {
        String keyEncoded = Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
        String valueEncoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

        JSONObject putRequest = new JSONObject();
        putRequest.put("key", keyEncoded);
        putRequest.put("value", valueEncoded);
        return putRequest.toString();
    }

    private String buildGetRequestPayload(String key) {
        String keyEncoded = Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
        JSONObject getRequest = new JSONObject();
        getRequest.put("key", keyEncoded);
        return getRequest.toString();
    }
}
