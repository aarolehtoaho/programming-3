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
				int postResult = handlePOSTrequest(exchange);
				handlePOSTResponse(exchange, postResult);
				break;
			case "GET":
				String responseString = handleGETrequest();
				handleGETResponse(exchange, responseString);
				break;
			case "PUT":
				int putResult = handlePUTrequest(exchange);
				handlePUTResponse(exchange, putResult);
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

			if (!validMessageJson(json)) {
				return 405;
			}

			observationRecord.setTargetBodyName(json.getString("target_body_name"));
			observationRecord.setCenterBodyName(json.getString("center_body_name"));
			observationRecord.setEpoch(json.getString("epoch"));
			observationRecord.setPayload(json.getJSONObject("metadata").getString("record_payload"));

			JSONArray observatory = json.getJSONObject("metadata").optJSONArray("observatory");
			JSONObject orbitalElements = json.optJSONObject("orbital_elements");
			JSONObject stateVector = json.optJSONObject("state_vector");

			observationRecord.setObservatory(observatory);
			observationRecord.setOrbitalElements(orbitalElements);
			observationRecord.setStateVector(stateVector);
			
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
		boolean stateVectorMissing = json.isNull("state_vector");
		boolean orbitalElementsMissing = json.isNull("orbital_elements");
		boolean invalidMessage = stateVectorMissing && orbitalElementsMissing;
		if (invalidMessage) {
			return false;
		}

		boolean missingData = json.isNull("target_body_name") || json.isNull("center_body_name") || json.isNull("epoch");
		if (missingData) {
			return false;
		}

		String iso8601DateFormat = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";
		boolean validEpoch = json.optString("epoch", "").matches(iso8601DateFormat);
		if (!validEpoch) {
			return false;
		}

		boolean invalidTypes = json.optString("target_body_name", "").isEmpty() ||
			json.optString("center_body_name", "").isEmpty() ||
			json.optString("epoch", "").isEmpty();
		if (invalidTypes) {
			return false;
		}

		boolean invalidOrbitalElements = !json.isNull("orbital_elements") && (
			Double.isNaN(json.getJSONObject("orbital_elements").optDouble("semi_major_axis_au", Double.NaN)) ||
			Double.isNaN(json.getJSONObject("orbital_elements").optDouble("eccentricity", Double.NaN)) ||
			Double.isNaN(json.getJSONObject("orbital_elements").optDouble("inclination_deg", Double.NaN)) ||
			Double.isNaN(json.getJSONObject("orbital_elements").optDouble("longitude_ascending_node_deg", Double.NaN)) ||
			Double.isNaN(json.getJSONObject("orbital_elements").optDouble("argument_of_periapsis_deg", Double.NaN)) ||
			Double.isNaN(json.getJSONObject("orbital_elements").optDouble("mean_anomaly_deg", Double.NaN)));
		if (invalidOrbitalElements) {
			return false;
		}

		boolean invalidStateVector = !json.isNull("state_vector") && (
			json.getJSONObject("state_vector").optJSONArray("position_au") == null ||
			json.getJSONObject("state_vector").optJSONArray("velocity_au_per_day") == null);
		if (invalidStateVector) {
			return false;
		}

		JSONArray observatory = json.isNull("metadata") || json.getJSONObject("metadata").isNull("observatory")
			? null
			: json.getJSONObject("metadata").getJSONArray("observatory");
		if (observatory == null) {
			return true;
		}

		int observatoryIndex = 0;
		JSONObject observatoryData = observatory.optJSONObject(observatoryIndex++);
		if (observatoryData == null) {
			return false;
		}
		
		while (observatoryData != null) {
			boolean latitudeMissing = Double.isNaN(observatoryData.optDouble("latitude", Double.NaN));
			boolean longitudeMissing = Double.isNaN(observatoryData.optDouble("longitude", Double.NaN));
			if (latitudeMissing || longitudeMissing) {
				return false;
			}
			observatoryData = observatory.optJSONObject(observatoryIndex++);
		}

		return true;
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

	private int handlePUTrequest(HttpExchange httpExchange) throws IOException {
        String path = httpExchange.getRequestURI().getPath();
        String idString = path.substring("/datarecord?id=".length());
		int id = Integer.parseInt(idString);

		ObservationRecord oldMessage;
		try {
			oldMessage = MessageDatabase.getInstance().getMessage(id);
		} catch (SQLException e) {
			e.printStackTrace();
			return 500;
		}
		if (oldMessage == null) {
			return 404;
		}

		String originalOwner = oldMessage.getRecordOwner();
		String username = httpExchange.getPrincipal().getUsername();
		String requestNickname = ((UserAuthenticator) httpExchange.getHttpContext().getAuthenticator()).getNickname(username);

		if (!originalOwner.equals(requestNickname)) {
			return 403;
		}

		InputStreamReader inputStream = new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8);
		BufferedReader bufferedReader = new BufferedReader(inputStream);
		String updateMessageText = bufferedReader.lines().collect(Collectors.joining("\n")).trim();
		bufferedReader.close();
		inputStream.close();

		ObservationRecord updatedMessage = oldMessage;
		try {
			JSONObject updateJson = new JSONObject(updateMessageText);
			// Set missing field to old message values
			if (updateJson.optString("target_body_name", "").isEmpty()) {
				updateJson.put("target_body_name", oldMessage.getTargetBodyName());
			}
			if (updateJson.optString("center_body_name", "").isEmpty()) {
				updateJson.put("center_body_name", oldMessage.getCenterBodyName());
			}
			if (updateJson.optString("epoch", "").isEmpty()) {
				updateJson.put("epoch", oldMessage.getEpoch());
			}
			if (updateJson.isNull("orbital_elements")) {
				updateJson.put("orbital_elements", oldMessage.getOrbitalElements());
			}
			if (updateJson.isNull("state_vector")) {
				updateJson.put("state_vector", oldMessage.getStateVector());
			}
			if (updateJson.getJSONObject("metadata").isNull("observatory")) {
				updateJson.getJSONObject("metadata").put("observatory", oldMessage.getObservatory());
			}
			if (updateJson.getJSONObject("metadata").isNull("record_payload")) {
				updateJson.getJSONObject("metadata").put("record_payload", oldMessage.getPayload());
			}

			if (updateJson.getJSONObject("metadata").isNull("update_reason")) {
				updateJson.getJSONObject("metadata").put("update_reason", "N/A");
			}

			if (!validMessageJson(updateJson)) {
				return 405;
			}

			// Construct the updated ObservationRecord
			updatedMessage.setTargetBodyName(updateJson.getString("target_body_name"));
			updatedMessage.setCenterBodyName(updateJson.getString("center_body_name"));
			updatedMessage.setEpoch(updateJson.getString("epoch"));
			updatedMessage.setPayload(updateJson.getJSONObject("metadata").getString("record_payload"));
			updatedMessage.setUpdateReason(updateJson.getJSONObject("metadata").getString("update_reason"));
			updatedMessage.setTimeEdited(System.currentTimeMillis());

			JSONArray observatory = updateJson.getJSONObject("metadata").optJSONArray("observatory");
			JSONObject orbitalElements = updateJson.optJSONObject("orbital_elements");
			JSONObject stateVector = updateJson.optJSONObject("state_vector");

			updatedMessage.setObservatory(observatory);
			updatedMessage.setOrbitalElements(orbitalElements);
			updatedMessage.setStateVector(stateVector);
		} catch (JSONException e) {
			return 400;
		}

		try {
			MessageDatabase.getInstance().updateMessage(updatedMessage);
		} catch (SQLException e) {
			e.printStackTrace();
			return 500;
		}

		return 200;
	}

	private void handlePOSTResponse(HttpExchange httpExchange, int code) throws IOException {
		httpExchange.getResponseHeaders().set("Content-Type", "application/json");
		httpExchange.sendResponseHeaders(code, -1);
	}

	private void handleGETResponse(HttpExchange httpExchange, String responseString) throws IOException {
		handleResponse(httpExchange, 200, responseString);
	}

	private void handlePUTResponse(HttpExchange httpExchange, int code) throws IOException {
		httpExchange.getResponseHeaders().set("Content-Type", "application/json");
		httpExchange.sendResponseHeaders(code, -1);
	}

	private void handleResponse(HttpExchange httpExchange, int code, String responseString) throws IOException {
		byte[] bytes = responseString.getBytes("UTF-8");
		httpExchange.getResponseHeaders().set("Content-Type", "application/json");
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
			server.createContext("/collections", new Collections());

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
