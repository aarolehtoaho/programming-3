package com.o3.server;

import org.json.JSONException;
import org.json.JSONObject;

public class ObservationRecord {
    private String targetBodyName;
    private String centerBodyName;
    private String epoch;
    private JSONObject orbitalElements;
    private JSONObject stateVector;

    public String getTargetBodyName() { return targetBodyName; }
    public String getCenterBodyName() { return centerBodyName; }
    public String getEpoch() { return epoch; }
    public JSONObject getOrbitalElements() { return orbitalElements; }
    public JSONObject getStateVector() { return stateVector; }

    public void setTargetBodyName(String targetBodyName) { this.targetBodyName = targetBodyName; }
    public void setCenterBodyName(String centerBodyName) { this.centerBodyName = centerBodyName; }
    public void setEpoch(String epoch) { this.epoch = epoch; }
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

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("target_body_name", targetBodyName);
        json.put("center_body_name", centerBodyName);
        json.put("epoch", epoch);
        json.put("orbital_elements", orbitalElements);
        json.put("state_vector", stateVector);
        return json;
    }
}
