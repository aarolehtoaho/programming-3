package com.o3.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Server implements HttpHandler {
	StringBuilder textDump = new StringBuilder("Dumped text: ");

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		switch (exchange.getRequestMethod().toUpperCase()) {
			case "POST":
				StringBuilder text = new StringBuilder("");
				handlePOSTrequest(exchange, text);
				handlePOSTResponse(exchange, text);
				break;
			case "GET":
				// Handle GET requests here (users use this to get messages)
				break;
			default:
				// Inform user here that only POST and GET functions are supported and send an error code
				// 400 with a message “Not supported” (witouth the “).
		}
	}

	private void handlePOSTrequest(HttpExchange httpExchange, StringBuilder stringBuilder) throws IOException {
		InputStreamReader inputStream = new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8);
		BufferedReader bufferedReader = new BufferedReader(inputStream);
		String text = bufferedReader.lines().collect(Collectors.joining("\n"));
		stringBuilder.append(text);
		bufferedReader.close();
		inputStream.close();
	}

	private String handleGETrequest(HttpExchange httpExchange) {
		return null;
	}

	private void handlePOSTResponse(HttpExchange httpExchange, StringBuilder text) throws IOException {
		httpExchange.sendResponseHeaders(200, -1);
		textDump.append(text);
	}

	public static void main(String[] args) throws Exception {
		HttpServer server = com.sun.net.httpserver.HttpServer.create(
			new java.net.InetSocketAddress(8001), 0);
		server.createContext("/datarecord", new Server());
		server.setExecutor(null);
		server.start();
	}
}
