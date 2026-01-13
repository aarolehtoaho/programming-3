package com.o3.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Server implements HttpHandler {
	StringBuilder textDump = new StringBuilder("");

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		switch (exchange.getRequestMethod().toUpperCase()) {
			case "POST":
				StringBuilder text = new StringBuilder("");
				handlePOSTrequest(exchange, text);
				handlePOSTResponse(exchange, text);
				break;
			case "GET":
				String responseString = handleGETrequest(exchange);
				handleGETResponse(exchange, responseString);
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
		if (textDump.length() > 0) {
			return textDump.toString();
		}
		return "No messages";
	}

	private void handlePOSTResponse(HttpExchange httpExchange, StringBuilder text) throws IOException {
		httpExchange.sendResponseHeaders(200, -1);
		textDump.append(text);
	}

	private void handleGETResponse(HttpExchange httpExchange, String responseString) throws IOException {
		try {
			byte[] bytes = responseString.getBytes("UTF-8");
			httpExchange.sendResponseHeaders(200, bytes.length);
			OutputStream outputStream = httpExchange.getResponseBody();
			outputStream.write(bytes);
			outputStream.flush();
			outputStream.close();
		} catch (UnsupportedEncodingException e) {}
	}

	public static void main(String[] args) throws Exception {
		HttpServer server = com.sun.net.httpserver.HttpServer.create(
			new java.net.InetSocketAddress(8001), 0);
		server.createContext("/datarecord", new Server());
		server.setExecutor(null);
		server.start();
	}
}
