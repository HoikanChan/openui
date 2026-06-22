package com.huawei.bsp.roa.util.restclient;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class RestfulFactory {
  private static final RestClient CLIENT = new RestClient();

  private RestfulFactory() {}

  public static RestClient getRestInstance() {
    return CLIENT;
  }

  public static class RestClient {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public RestfulResponse post(String endpoint, RestfulParametes parametes) throws Exception {
      String baseUrl = System.getProperty("openui.bsp.restclient.baseUrl", "");
      URI uri = URI.create(baseUrl + endpoint);
      String rawData = parametes == null || parametes.getRawData() == null ? "" : parametes.getRawData();
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(uri)
              .POST(HttpRequest.BodyPublishers.ofString(rawData, StandardCharsets.UTF_8));
      if (parametes != null) {
        parametes.getHttpContextHeaders().forEach(builder::header);
      }

      HttpResponse<byte[]> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
      RestfulResponse restfulResponse = new RestfulResponse();
      restfulResponse.setStatus(response.statusCode());
      String body = new String(response.body(), StandardCharsets.UTF_8);
      restfulResponse.setResponseContent(body);
      restfulResponse.setDataStream(new ByteArrayInputStream(response.body()));
      return restfulResponse;
    }
  }
}
