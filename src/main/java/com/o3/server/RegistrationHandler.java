package com.o3.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

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
		InputStreamReader inputStream = new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8);
		BufferedReader bufferedReader = new BufferedReader(inputStream);

        String text = bufferedReader.lines().collect(Collectors.joining("\n"));
        String[] nameAndPassword = text.split(":");
        if (nameAndPassword.length != 2) {
            return 405;
        }

		bufferedReader.close();
		inputStream.close();

        boolean userAdded = this.userAuthenticator.addUser(nameAndPassword[0], nameAndPassword[1]);
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
                responseString = "Expected username:password";
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
