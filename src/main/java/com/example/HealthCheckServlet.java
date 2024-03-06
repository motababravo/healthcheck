package com.example;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;

@WebServlet("/")
public class HealthCheckServlet extends HttpServlet {

    static {
        // Ignore SSL certificate errors
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }}, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String configFilePath = "/opt/props/healthcheck.conf";
        BufferedReader reader = new BufferedReader(new FileReader(configFilePath));
        String[] targetUrls = {};
        int timeout = 0;
        String line;
        boolean allTargetsOK = true; // Flag to track the overall status
        StringBuilder responseBody = new StringBuilder(); // Collect the response body
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("=");
            if (parts.length == 2) {
                if (parts[0].trim().equals("targetUrls")) {
                    targetUrls = parts[1].trim().split(",");
                } else if (parts[0].trim().equals("timeout")) {
                    timeout = Integer.parseInt(parts[1].trim());
                }
            }
        }
        reader.close();

        for (String targetUrl : targetUrls) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(targetUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(timeout * 1000);
                connection.setReadTimeout(timeout * 1000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    responseBody.append(targetUrl).append(": OK!\n"); // Append OK status
                } else {
                    allTargetsOK = false; // Set the flag to false if any target fails
                    responseBody.append(targetUrl).append(": Not OK!\n"); // Append Not OK status
                }
            } catch (IOException e) {
                allTargetsOK = false; // Set the flag to false if any target fails
                responseBody.append(targetUrl).append(": Not OK! Error: ").append(e.getMessage()).append("\n"); // Append Not OK status with error message
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        if (allTargetsOK) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        response.getWriter().println(responseBody.toString()); // Print the response body
    }
}
