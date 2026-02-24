package com.o3.server;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ObservationRecord {
    private String targetBodyName;
    private String centerBodyName;
    private String epoch;
    private JSONObject orbitalElements;
    private JSONObject stateVector;
    private JSONObject metadata;

    public ObservationRecord(String recordOwner) {
        this.targetBodyName = null;
        this.centerBodyName = null;
        this.epoch = null;
        this.orbitalElements = null;
        this.stateVector = null;

        String metadataStr = "{\"record_time_received\": null,\"record_owner\": null, \"id\": -1, \"observatory\": [], \"record_payload\": null}";
        this.metadata = new JSONObject(metadataStr);

        long epochMillis = System.currentTimeMillis();
        String receivedStr = epochMillisToString(epochMillis);

        this.metadata.put("record_time_received", receivedStr);
        this.metadata.put("record_owner", recordOwner);
    }

    public String getTargetBodyName() { return targetBodyName; }
    public String getCenterBodyName() { return centerBodyName; }
    public String getEpoch() { return epoch; }
    public String getOrbitalElements() {
        if (orbitalElements == null) {
            return "NULL";
        }
        return orbitalElements.toString();
    }
    public String getStateVector() {
        if (stateVector == null) {
            return "NULL";
        }
        return stateVector.toString();
    }
    public String getTimeReceived() { return metadata.getString("record_time_received"); }
    public long getTimeReceivedAsInt() {
        String timeStr = getTimeReceived();
        ZonedDateTime received = ZonedDateTime.parse(timeStr);
        return received.toInstant().toEpochMilli();
    }
    public String getRecordOwner() { return metadata.getString("record_owner"); }
    public String getPayload() { return metadata.getString("record_payload"); }
    public int getId() { return metadata.getInt("id"); }
    public String getObservatory() {
        String observatory;
        try {
            observatory = metadata.getJSONArray("observatory").toString();
        } catch (JSONException e) {
            observatory = "NULL";
        }
        return observatory;
    }

    public void setId(int id) { this.metadata.put("id", id); }
    public void setTargetBodyName(String targetBodyName) { this.targetBodyName = targetBodyName; }
    public void setCenterBodyName(String centerBodyName) { this.centerBodyName = centerBodyName; }
    public void setEpoch(String epoch) { this.epoch = epoch; }
    public void setPayload(String payload) { this.metadata.put("record_payload", payload); }
    public void setTimeReceived(long timeReceived) {
        String receivedStr = epochMillisToString(timeReceived);
        this.metadata.put("record_time_received", receivedStr);
    }
    public void setOrbitalElements(JSONObject orbitalElements) throws JSONException {
        if (orbitalElements != null) {
            // Throws JSONException if any of the required fields are missing or values are incorrect
            orbitalElements.getDouble("semi_major_axis_au");
            orbitalElements.getDouble("eccentricity");
            orbitalElements.getDouble("inclination_deg");
            orbitalElements.getDouble("longitude_ascending_node_deg");
            orbitalElements.getDouble("argument_of_periapsis_deg");
            orbitalElements.getDouble("mean_anomaly_deg");
        }
        this.orbitalElements = orbitalElements;
    }
    public void setStateVector(JSONObject stateVector) {
        if (stateVector != null) {
            // Throws JSONException if any of the required fields are missing or values are incorrect
            stateVector.getJSONArray("position_au");
            stateVector.getJSONArray("velocity_au_per_day");
        }
        this.stateVector = stateVector;
    }

    public void setObservatory(JSONArray observatory) throws JSONException {
        for (int i = 0; i < observatory.length(); i++) {
            JSONObject obs = observatory.getJSONObject(i);
            if (obs.has("weather")) {
                double latitude = obs.getDouble("latitude");
                double longitude = obs.getDouble("longitude");
                JSONObject weather = Weather.getWeatherData(latitude, longitude);
                obs.put("weather", weather);
            }
        }
        this.metadata.put("observatory", observatory);
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("target_body_name", targetBodyName);
        json.put("center_body_name", centerBodyName);
        json.put("epoch", epoch);
        json.put("orbital_elements", orbitalElements);
        json.put("state_vector", stateVector);
        json.put("metadata", metadata);
        return json;
    }

    private String epochMillisToString(long epochMillis) {
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("UTC"));
        return dateTime.format(DateTimeFormatter.ISO_INSTANT);
    }
}
