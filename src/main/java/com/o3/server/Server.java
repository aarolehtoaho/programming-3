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
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

public class Server implements HttpHandler {
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		switch (exchange.getRequestMethod().toUpperCase()) {
			case "POST":
				int code = handlePOSTrequest(exchange);
				handlePOSTResponse(exchange, code);
				break;
			case "GET":
				String responseString = handleGETrequest();
				handleGETResponse(exchange, responseString);
				break;
			default:
				handleResponse(exchange, 400, "Not supported");
		}
	}

	private int handlePOSTrequest(HttpExchange httpExchange) throws IOException {
		InputStreamReader inputStream = new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8);
		BufferedReader bufferedReader = new BufferedReader(inputStream);
		String text = bufferedReader.lines().collect(Collectors.joining("\n")).trim();
		bufferedReader.close();
		inputStream.close();

		String username = httpExchange.getPrincipal().getUsername();
		String recordOwner = ((UserAuthenticator) httpExchange.getHttpContext().getAuthenticator()).getNickname(username);

		ObservationRecord observationRecord = new ObservationRecord(recordOwner);

		try {
			JSONObject json = new JSONObject(text);

			boolean stateVectorMissing = json.isNull("state_vector");
			boolean orbitalElementsMissing = json.isNull("orbital_elements");
			boolean observatoryMissing = json.getJSONObject("metadata").isNull("observatory");
			boolean invalidMessage = stateVectorMissing && orbitalElementsMissing;
			if (invalidMessage || !validMessageJson(json)) {
				return 405;
			}

			observationRecord.setTargetBodyName(json.getString("target_body_name"));
			observationRecord.setCenterBodyName(json.getString("center_body_name"));
			observationRecord.setEpoch(json.getString("epoch"));
			observationRecord.setPayload(json.getJSONObject("metadata").getString("record_payload"));
			if (!observatoryMissing) {
				observationRecord.setObservatory(json.getJSONObject("metadata").getJSONArray("observatory"));
			} else {
				observationRecord.setObservatory(null);
			}
			if (!orbitalElementsMissing) {
				observationRecord.setOrbitalElements(json.getJSONObject("orbital_elements"));
			} else {
				observationRecord.setOrbitalElements(null);
			}
			if (!stateVectorMissing) {
				observationRecord.setStateVector(json.getJSONObject("state_vector"));
			} else {
				observationRecord.setStateVector(null);
			}
		} catch (JSONException e) {
			return 405;
		}

		try {
			MessageDatabase.getInstance().insertMessage(observationRecord);
		} catch (SQLException e) {
			e.printStackTrace();
			return 500;
		}
		
		return 200;
	}

	private boolean validMessageJson(JSONObject json) {
		boolean missingData = json.isNull("target_body_name") || json.isNull("center_body_name") || json.isNull("epoch");
		if (missingData) {
			return false;
		}

		boolean invalidTypes = json.optString("target_body_name", "").isEmpty() ||
			json.optString("center_body_name", "").isEmpty() ||
			json.optString("epoch", "").isEmpty();
		if (invalidTypes) {
			return false;
		}

		boolean invalidOrbitalElements = !json.isNull("orbital_elements") && (
			json.getJSONObject("orbital_elements").optDouble("semi_major_axis_au", Double.NaN) == Double.NaN ||
			json.getJSONObject("orbital_elements").optDouble("eccentricity", Double.NaN) == Double.NaN ||
			json.getJSONObject("orbital_elements").optDouble("inclination_deg", Double.NaN) == Double.NaN ||
			json.getJSONObject("orbital_elements").optDouble("longitude_ascending_node_deg", Double.NaN) == Double.NaN ||
			json.getJSONObject("orbital_elements").optDouble("argument_of_periapsis_deg", Double.NaN) == Double.NaN ||
			json.getJSONObject("orbital_elements").optDouble("mean_anomaly_deg", Double.NaN) == Double.NaN);
		if (invalidOrbitalElements) {
			return false;
		}

		boolean invalidStateVector = !json.isNull("state_vector") && (
			json.getJSONObject("state_vector").optJSONArray("position_au") == null ||
			json.getJSONObject("state_vector").optJSONArray("velocity_au_per_day") == null);

		return !invalidStateVector;
	}

	private String handleGETrequest() {
		JSONArray responseMessages = new JSONArray();

		List<ObservationRecord> records;
		try {
			records = MessageDatabase.getInstance().getAllMessages();
		} catch (SQLException e) {
			return "[]";
		}

		for (ObservationRecord record : records) {
			responseMessages.put(record.toJSON());
		}
		return responseMessages.toString();
	}

	private void handlePOSTResponse(HttpExchange httpExchange, int code) throws IOException {
		httpExchange.sendResponseHeaders(code, -1);
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

			SSLContext sslContext = myServerSSLContext(args[0],args[1]);
			HttpsConfigurator httpsConfigurator = new HttpsConfigurator(sslContext) {
				public void configure (HttpsParameters params) {
					InetSocketAddress remote = params.getClientAddress();
					SSLContext c = getSSLContext();
					SSLParameters sslparams = c.getDefaultSSLParameters();
					params.setSSLParameters(sslparams);				
				}
			};
			server.setHttpsConfigurator(httpsConfigurator);

			UserAuthenticator userAuthenticator = new UserAuthenticator();
			HttpContext httpContext = server.createContext("/datarecord", new Server());
			httpContext.setAuthenticator(userAuthenticator);

			server.createContext("/registration", new RegistrationHandler(userAuthenticator));

			MessageDatabase database = MessageDatabase.getInstance();
			String dbPath = System.getenv("DATABASE_PATH");
			if (dbPath == null || dbPath.isEmpty()) {
				throw new Exception("DATABASE_PATH environment variable not set");
			}
			database.open(dbPath);

			server.setExecutor(Executors.newCachedThreadPool());
			server.start();
		} catch (FileNotFoundException e) {
			System.out.println("Certificate file not found");
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
