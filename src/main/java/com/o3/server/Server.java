package com.o3.server;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.stream.Collectors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

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
				String responseString = handleGETrequest();
				handleGETResponse(exchange, responseString);
				break;
			default:
				handleResponse(exchange, 400, "Not supported");
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

	private String handleGETrequest() {
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
		handleResponse(httpExchange, 200, responseString);
	}

	private void handleResponse(HttpExchange httpExchange, int code, String responseString) throws IOException {
		byte[] bytes = responseString.getBytes("UTF-8");
		httpExchange.sendResponseHeaders(code, bytes.length);
		OutputStream outputStream = httpExchange.getResponseBody();
		outputStream.write(bytes);
		outputStream.flush();
		outputStream.close();
	}

	private static SSLContext myServerSSLContext(String keystoreFile, String passphrase) throws Exception {
		char[] passphraseChars = passphrase.toCharArray();
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(new FileInputStream(keystoreFile), passphraseChars);

		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(ks, passphraseChars);

		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		tmf.init(ks);

		SSLContext ssl = SSLContext.getInstance("TLS");
		ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

		return ssl;
	}

	public static void main(String[] args) throws Exception {
		try {
			HttpsServer server = com.sun.net.httpserver.HttpsServer.create(
				new java.net.InetSocketAddress(8001), 0);

			SSLContext sslContext = myServerSSLContext("keystore.jks", "ExamplePassword");
			//SSLContext sslContext = myServerSSLContext(args[0],args[1]);
			HttpsConfigurator httpsConfigurator = new HttpsConfigurator(sslContext) {
				public void configure (HttpsParameters params) {
					InetSocketAddress remote = params.getClientAddress();
					SSLContext c = getSSLContext();
					SSLParameters sslparams = c.getDefaultSSLParameters();
					params.setSSLParameters(sslparams);				
				}
			};
			server.setHttpsConfigurator(httpsConfigurator);

			server.createContext("/datarecord", new Server());
			server.setExecutor(null);
			server.start();			
		} catch (FileNotFoundException e) {
			System.out.println("Certificate file not found");
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
