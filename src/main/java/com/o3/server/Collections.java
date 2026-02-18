package com.o3.server;

import java.io.IOException;
import java.io.OutputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class Collections implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String suffix = path.substring("/collections/".length());

		switch (exchange.getRequestMethod().toUpperCase()) {
			case "POST":
                String action = suffix.toLowerCase();
                JSONObject responseJson = handlePOSTRequest(exchange, action);
                handlePOSTResponse(exchange, responseJson);
				break;
			case "GET":
                int id;
                try {
                    if ("".equals(suffix)) {
                        id = -1;
                    } else {
                        id = Integer.parseInt(suffix);
                    }
                } catch (NumberFormatException e) {
                    handleResponse(exchange, 400, "Invalid ID");
                    return;
                }
                
				String responseString = handleGETRequest(id);
                handleGETResponse(exchange, responseString);
				break;
			default:
				handleResponse(exchange, 405, "Not supported");
		}
    }

    private String handleGETRequest(int id) {
        if (id == -1) {
            return MessageDatabase.getInstance().getCollectionIds().toString();
        }

        JSONArray messages = MessageDatabase.getInstance().getMessagesByCollectionId(id);

        return messages.toString();
    }

    private JSONObject handlePOSTRequest(HttpExchange exchange, String action) {
        JSONArray messageIds;
        switch (action) {
            case "create":
                JSONObject responseJsonCreate = new JSONObject("{\"created_collection_id\": -1, \"code\": 200}");
                messageIds = getMessageIDs(exchange);
                if (messageIds.length() == 0) {
                    responseJsonCreate.put("code", 400);
                    return responseJsonCreate;
                }
                int createdCollectionId = MessageDatabase.getInstance().createCollection(messageIds);
                responseJsonCreate.put("created_collection_id", createdCollectionId);
                if (createdCollectionId == -1) {
                    responseJsonCreate.put("code", 500);
                }
                return responseJsonCreate;
            case "add":
                JSONObject responseJsonAdd = new JSONObject("{\"code\": 200}");
                int collectionId = getCollectionId(exchange);
                messageIds = getMessageIDs(exchange);
                if (collectionId == -1 || messageIds.length() == 0) {
                    responseJsonAdd.put("code", 400);
                    return responseJsonAdd;
                }
                boolean success = MessageDatabase.getInstance().addMessagesToCollection(collectionId, messageIds);
                if (!success) {
                    responseJsonAdd.put("code", 500);
                }
                return responseJsonAdd;
            default:
                return new JSONObject("{\"code\": 400}");
        }
    }

	private void handlePOSTResponse(HttpExchange httpExchange, JSONObject responseJson) throws IOException {
		int code = responseJson.getInt("code");
        responseJson.remove("code");
        if (responseJson.isEmpty()) {
            httpExchange.sendResponseHeaders(code, -1);
            return;
        }
        String responseString = responseJson.toString();
        handleResponse(httpExchange, code, responseString);
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

    private JSONArray getMessageIDs(HttpExchange exchange) {
        String requestBody;
        try {
            requestBody = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
        } catch (IOException e) {
            return new JSONArray();
        }
        try {
            return new JSONArray(requestBody);
        } catch (JSONException e1) {
            try {
                JSONObject jsonObject = new JSONObject(requestBody);
                return jsonObject.getJSONArray("message_ids");
            } catch (JSONException e2) {
                return new JSONArray();
            }
        }
    }

    private int getCollectionId(HttpExchange exchange) {
        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
            JSONObject jsonObject = new JSONObject(requestBody);
            return jsonObject.getInt("collection_id");
        } catch (JSONException | IOException e) {
            return -1;
        }
    }
}