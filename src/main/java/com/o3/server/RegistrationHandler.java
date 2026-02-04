package com.o3.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class RegistrationHandler implements HttpHandler {
    private UserAuthenticator userAuthenticator = null;
    
    public RegistrationHandler(UserAuthenticator userAuthenticator) {
        this.userAuthenticator = userAuthenticator;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
		switch (exchange.getRequestMethod().toUpperCase()) {
			case "POST":
				int code = handlePOSTrequest(exchange);
                handlePOSTResponse(exchange, code);
				break;
			case "GET":
				handleResponse(exchange, 400, "Not supported");
				break;
			default:
				handleResponse(exchange, 400, "Not supported");
		}
    }

	private int handlePOSTrequest(HttpExchange httpExchange) throws IOException {
        String userName;
        String password;
        String email;
        String nickname;

		InputStreamReader inputStream = new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8);
		BufferedReader bufferedReader = new BufferedReader(inputStream);

        String text = bufferedReader.lines().collect(Collectors.joining("\n")).trim();
        inputStream.close();
        bufferedReader.close();

        JSONObject json;
        try {
            json = new JSONObject(text);
            userName = json.getString("username");
            password = json.getString("password");
            email = json.getString("email");
            nickname = json.getString("nickname");
        } catch (JSONException e) {
            return 405;
        }

        boolean userAdded = this.userAuthenticator.addUser(userName, password, email, nickname);
        return userAdded ? 200 : 403;
	}

    private void handlePOSTResponse(HttpExchange httpExchange, int code) throws IOException {
        String responseString;
        switch (code) {
            case 200:
                responseString = "Success";
                break;
            case 403:
                responseString = "User already registered";
                break;
            case 405:
                responseString = "Expected {\"username\": string, \"password\": string, \"email\": string, \"nickname\": string}";
                break;
            default:
                responseString = "Not supported";
        }
        handleResponse(httpExchange, code, responseString);
    }

	private void handleResponse(HttpExchange httpExchange, int code, String responseString) throws IOException {
		byte[] bytes = responseString.getBytes("UTF-8");
		httpExchange.sendResponseHeaders(code, bytes.length);
		OutputStream outputStream = httpExchange.getResponseBody();
		outputStream.write(bytes);
		outputStream.flush();
		outputStream.close();
	}
}
