package com.o3.server;

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
    public void setOrbitalElements(JSONObject orbitalElements) { this.orbitalElements = orbitalElements; }
    public void setStateVector(JSONObject stateVector) { this.stateVector = stateVector; }
}
