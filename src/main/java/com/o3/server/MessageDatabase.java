package com.o3.server;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MessageDatabase {
    private static MessageDatabase instance = null;
    private static Connection connection = null;
    private static SecureRandom secureRandom = new SecureRandom();

    public synchronized static MessageDatabase getInstance() {
        if (instance == null) {
            instance = new MessageDatabase();
        }
        return instance;
    }

    public synchronized void open(String dbPath) throws SQLException {
        String connectionAddress = "jdbc:sqlite:" + dbPath;
        File dbFile = new File(dbPath);
        boolean dbExists = dbFile.exists() && !dbFile.isDirectory();

        if (!dbExists) {
            initializeDatabase(connectionAddress);
        } else {
            connection = DriverManager.getConnection(connectionAddress);
        }
        
    }

    private synchronized void initializeDatabase(String connectionAddress) throws SQLException {
        connection = DriverManager.getConnection(connectionAddress);
        if (connection == null) {
            throw new SQLException("Failed to create database connection.");
        }

        String createTableUsersSQL = "CREATE TABLE users (" +
                "username TEXT PRIMARY KEY UNIQUE," +
                "password TEXT NOT NULL," +
                "salt TEXT NOT NULL," +
                "email TEXT NOT NULL," +
                "nickname TEXT NOT NULL" +
                ");";
        String createTableMessagesSQL = "CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "target_body_name TEXT NOT NULL," +
                "center_body_name TEXT NOT NULL," +
                "epoch TEXT NOT NULL," +
                "orbital_elements TEXT," +
                "state_vector TEXT," +
                "observatory TEXT," +
                "owner_nickname TEXT NOT NULL," +
                "payload TEXT," +
                "time_received INTEGER NOT NULL" +
                ");";

        Statement statement = connection.createStatement();
        statement.executeUpdate(createTableUsersSQL);
        statement.executeUpdate(createTableMessagesSQL);
        statement.close();
    }

    public synchronized void insertUser(User user) throws SQLException {
        if (connection == null) {
            throw new SQLException("Database not initialized.");
        }
        String salt = generateSalt(16);
        String hash;
        try {
            hash = generateHash(user.getPassword(), salt, "SHA-256", "UTF-8");
        } catch (Exception e) {
            throw new SQLException("Failed to generate password hash.", e);
        }

        String insertUserSQL = "INSERT INTO users (username, password, salt, email, nickname) VALUES (" +
                "'" + user.getUsername() + "', " +
                "'" + hash + "', " +
                "'" + salt + "', " +
                "'" + user.getEmail() + "', " +
                "'" + user.getNickname() + "');";
        Statement statement = connection.createStatement();
        statement.executeUpdate(insertUserSQL);
        statement.close();
    }

    public synchronized void insertMessage(ObservationRecord record) throws SQLException {
        if (connection == null) {
            throw new SQLException("Database not initialized.");
        }

        String insertMessageSQL = "INSERT INTO messages (target_body_name, center_body_name, epoch, "+ 
                "orbital_elements, state_vector, owner_nickname, observatory, payload, time_received) VALUES (" +
                "'" + record.getTargetBodyName() + "', " +
                "'" + record.getCenterBodyName() + "', " +
                "'" + record.getEpoch() + "', " +
                "'" + record.getOrbitalElements() + "', " +
                "'" + record.getStateVector() + "', " +
                "'" + record.getRecordOwner() + "', " +
                "'" + record.getObservatory() + "', " +
                "'" + record.getPayload() + "', " +
                record.getTimeReceivedAsInt() + ");";
        Statement statement = connection.createStatement();
        statement.executeUpdate(insertMessageSQL);
        statement.close();
    }

    public synchronized ObservationRecord getMessage(int id) throws SQLException {
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
            String observatoryStr = resultSet.getString("observatory");
            long timeReceived = resultSet.getLong("time_received");
            ObservationRecord record = new ObservationRecord(ownerNickname);
            record.setTargetBodyName(targetBodyName);
            record.setCenterBodyName(centerBodyName);
            record.setEpoch(epoch);
            record.setPayload(payload);
            record.setTimeReceived(timeReceived);
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
            try {
                record.setObservatory(new JSONArray(observatoryStr));
            } catch (JSONException e) {
                record.setObservatory(null);
            }
            statement.close();
            return record;
        } else {
            statement.close();
            return null;
        }
    }

    public synchronized List<ObservationRecord> getAllMessages() throws SQLException {
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
            record.setId(id);
            records.add(record);
        }
        statement.close();
        return records;
    }

    public synchronized User getUser(String username) throws SQLException {
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
            return new User(username, password, email, nickname, true);
        } else {
            statement.close();
            return null;
        }
    }

    public synchronized boolean userRegistered(String username) {
        try {
            return getUser(username) != null;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean validPassword(User user, String password) {
        if (!user.isHashed()) {
            throw new IllegalArgumentException("Password is not hashed");
        }

        try {
            String salt = getUserSalt(user);
            if (salt == null) {
                return false;
            }
            String hashedPassword = generateHash(password, salt, "SHA-256", "UTF-8");
            return user.getPassword().equals(hashedPassword);
        } catch (SQLException e) {
            return false;
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            assert false : "Encoding or algorithm not supported";
            return false;
        }
    }

	private String generateSalt(int length) {
		byte[] salt = new byte[length];
		secureRandom.nextBytes(salt);
		return Base64.getEncoder().encodeToString(salt);
	}

	private String generateHash(String password, String salt, String algorithm, String encoding) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        //return Crypt.crypt(password, salt); //slow
        MessageDigest md = MessageDigest.getInstance(algorithm);
        String combined = password + salt;
        byte[] hashBytes = md.digest(combined.getBytes(encoding));
        return Base64.getEncoder().encodeToString(hashBytes);

	}

    private synchronized String getUserSalt(User user) throws SQLException {
        if (connection == null) {
            throw new SQLException("Database not initialized.");
        }

        String querySaltSQL = "SELECT salt FROM users WHERE username = '" + user.getUsername() + "';";
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(querySaltSQL);
        if (resultSet.next()) {
            String salt = resultSet.getString("salt");
            statement.close();
            return salt;
        } else {
            statement.close();
            return null;
        }
    }
}