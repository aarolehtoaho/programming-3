package com.o3.server;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public class MessageDatabase {
    private static MessageDatabase instance = null;

    private static Connection connection = null;

    public static MessageDatabase getInstance() {
        if (instance == null) {
            instance = new MessageDatabase();
        }
        return instance;
    }

    public void open(String dbPath) throws SQLException {
        String connectionAddress = "jdbc:sqlite:" + dbPath;
        File dbFile = new File(dbPath);
        boolean dbExists = dbFile.exists() && !dbFile.isDirectory();

        if (!dbExists) {
            initializeDatabase(connectionAddress);
        } else {
            connection = DriverManager.getConnection(connectionAddress);
        }
        
    }

    private void initializeDatabase(String connectionAddress) throws SQLException {
        connection = DriverManager.getConnection(connectionAddress);
        if (connection == null) {
            throw new SQLException("Failed to create database connection.");
        }

        String createTableUsersSQL = "CREATE TABLE users (" +
                "username TEXT PRIMARY KEY UNIQUE," +
                "password TEXT NOT NULL," +
                "email TEXT NOT NULL UNIQUE," +
                "nickname TEXT NOT NULL" +
                ");";
        String createTableMessagesSQL = "CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY UNIQUE," +
                "target_body_name TEXT NOT NULL," +
                "center_body_name TEXT NOT NULL," +
                "epoch TEXT NOT NULL," +
                "orbital_elements TEXT," +
                "state_vector TEXT," +
                "owner_nickname TEXT NOT NULL," +
                "payload TEXT," +
                "time_received INTEGER NOT NULL" +
                ");";

        Statement statement = connection.createStatement();
        statement.executeUpdate(createTableUsersSQL);
        statement.executeUpdate(createTableMessagesSQL);
        statement.close();
    }

    public void insertUser(User user) throws SQLException {
        if (connection == null) {
            throw new SQLException("Database not initialized.");
        }

        String insertUserSQL = "INSERT INTO users (username, password, email, nickname) VALUES (" +
                "'" + user.getUsername() + "', " +
                "'" + user.getPassword() + "', " +
                "'" + user.getEmail() + "', " +
                "'" + user.getNickname() + "');";
        Statement statement = connection.createStatement();
        statement.executeUpdate(insertUserSQL);
        statement.close();
    }

    public void insertMessage(ObservationRecord record) throws SQLException {
        if (connection == null) {
            throw new SQLException("Database not initialized.");
        }

        String insertMessageSQL = "INSERT INTO messages (id, target_body_name, center_body_name, epoch, "+ 
                "orbital_elements, state_vector, owner_nickname, payload, time_received) VALUES (" +
                "'" + record.getId() + "', " +
                "'" + record.getTargetBodyName() + "', " +
                "'" + record.getCenterBodyName() + "', " +
                "'" + record.getEpoch() + "', " +
                "'" + record.getOrbitalElements().toString() + "', " +
                "'" + record.getStateVector().toString() + "', " +
                "'" + record.getRecordOwner() + "', " +
                "'" + record.getPayload() + "', " +
                record.getTimeReceivedAsInt() + ");";
        Statement statement = connection.createStatement();
        statement.executeUpdate(insertMessageSQL);
        statement.close();
    }

    public ObservationRecord getMessage(int id) throws SQLException {
        if (connection == null) {
            throw new SQLException("Database not initialized.");
        }

        String queryMessageSQL = "SELECT * FROM messages WHERE id = " + id + ";";
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(queryMessageSQL);
        if (resultSet.next()) {
            String targetBodyName = resultSet.getString("target_body_name");
            String centerBodyName = resultSet.getString("center_body_name");
            String epoch = resultSet.getString("epoch");
            String orbitalElementsStr = resultSet.getString("orbital_elements");
            String stateVectorStr = resultSet.getString("state_vector");
            String ownerNickname = resultSet.getString("owner_nickname");
            String payload = resultSet.getString("payload");

            ObservationRecord record = new ObservationRecord(ownerNickname);
            record.setTargetBodyName(targetBodyName);
            record.setCenterBodyName(centerBodyName);
            record.setEpoch(epoch);
            record.setPayload(payload);
            try {
                record.setOrbitalElements(new JSONObject(orbitalElementsStr));
            } catch (JSONException e) {
                record.setOrbitalElements(null);
            }
            try {
                record.setStateVector(new JSONObject(stateVectorStr));
            } catch (JSONException e) {
                record.setStateVector(null);
            }
            statement.close();
            return record;
        } else {
            statement.close();
            return null;
        }
    }

    public List<ObservationRecord> getAllMessages() throws SQLException {
        if (connection == null) {
            throw new SQLException("Database not initialized.");
        }

        String queryMessageIdsSQL = "SELECT id FROM messages;";
        Statement statement = connection.createStatement();
        ResultSet idResultSet = statement.executeQuery(queryMessageIdsSQL);

        List<ObservationRecord> records = new java.util.ArrayList<>();
        while (idResultSet.next()) {
            int id = idResultSet.getInt("id");
            ObservationRecord record = getMessage(id);
            assert record != null;
            records.add(record);
        }
        statement.close();
        return records;
    }

    public User getUser(String username) throws SQLException {
        if (connection == null) {
            throw new SQLException("Database not initialized.");
        }

        String queryUserSQL = "SELECT * FROM users WHERE username = '" + username + "';";
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(queryUserSQL);
        if (resultSet.next()) {
            String password = resultSet.getString("password");
            String email = resultSet.getString("email");
            String nickname = resultSet.getString("nickname");
            statement.close();
            return new User(username, password, email, nickname);
        } else {
            statement.close();
            return null;
        }
    }

    public boolean userRegistered(String username) {
        try {
            return getUser(username) != null;
        } catch (SQLException e) {
            return false;
        }
    }
}
