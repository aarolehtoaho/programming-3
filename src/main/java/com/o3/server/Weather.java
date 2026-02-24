package com.o3.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class Weather {
    static JSONObject getWeatherData(double latitude, double longitude) {
        String urlString = "http://localhost:4001/wfs?latlon=" + latitude + "," + longitude;

        try {
            URL url = new URI(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
                );
                StringBuilder response = new StringBuilder();
                String line = reader.readLine();
                while (line != null) {
                    response.append(line);
                    line = reader.readLine();
                }
                reader.close();
                
                String responseBody = response.toString().trim();
                
                return parseWeatherXML(responseBody);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    static JSONObject parseWeatherXML(String xmlResponse) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));

            JSONObject result = new JSONObject();
            NodeList elements = document.getElementsByTagName("BsWfs:BsWfsElement");
            
            for (int i = 0; i < elements.getLength(); i++) {
                Node element = elements.item(i);
                if (element.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) element;
                    String parameterName = eElement.getElementsByTagName("BsWfs:ParameterName").item(0).getTextContent();
                    String parameterValue = eElement.getElementsByTagName("BsWfs:ParameterValue").item(0).getTextContent();
                    
                    switch (parameterName) {
                        case "Temperature":
                            result.put("temperature_in_kelvins", Double.parseDouble(parameterValue.replace(",", ".")) + 273.15);
                            break;
                        case "TotalCloudCover":
                            result.put("cloudiness_percentage", Double.parseDouble(parameterValue.replace(",", ".")));
                            break;
                        case "RadiationGlobalAccumulation":
                            result.put("background_light_volume", Double.parseDouble(parameterValue.replace(",", ".")));
                            break;
                    }
                }
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
