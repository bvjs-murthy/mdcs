package com.mdcs.shared.network;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

import com.mdcs.shared.fileio.FileIO;
import com.mdcs.shared.models.network.Http.Request;

/**
 * Protocol methods to send requests to the server. Provides different methods like, POST, GET,...
 */

public class ProtoMet {
    private HttpClient client;
    private String url_base;
    
    /**
     * Expects payload along with the headers and metadata. Returns complete server response :
     * HttpResponse<String>
     */
    public <T> HttpResponse<String> post(Request<T> message)
    throws IOException, InterruptedException{
        
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(this.url_base + message.endpoint))
            .headers(message.headers)
            .POST(HttpRequest.BodyPublishers.ofString(FileIO.toJson(message.body)))
            .build();

        HttpResponse<String> res = this.client.send(req, HttpResponse.BodyHandlers.ofString());
        
        return res;
    }

    public String get(String api, String headers[])
    throws IOException, InterruptedException{
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(this.url_base + api))
            .headers(headers)
            .GET()
            .build();

        HttpResponse<String> res = this.client.send(req, HttpResponse.BodyHandlers.ofString());
        
        return res.toString();
    }

    public ProtoMet(Properties APP){
        String host = APP.getProperty("server.host");
        String port = APP.getProperty("server.port");

        this.client = HttpClient.newHttpClient();
        this.url_base = "http://" + host + ":" + port + "/mdcs";
    }
}
