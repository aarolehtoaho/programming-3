package com.o3.server;

import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.commons.codec.digest.Crypt;
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
                "time_received INTEGER NOT NULL," +
                "time_edited INTEGER," +
                "update_reason TEXT" +
                ");";
        String createTableCollectionsSQL = "CREATE TABLE collections (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "collection TEXT NOT NULL" +
                ");";

        Statement statement = connection.createStatement();
        statement.executeUpdate(createTableUsersSQL);
        statement.executeUpdate(createTableMessagesSQL);
        statement.executeUpdate(createTableCollectionsSQL);
        statement.close();
    }

    public synchronized void insertUser(User user) throws SQLException {
        if (connection == null) {
            throw new SQLException("Database not initialized.");
        }

        String hash = generateHash(user.getPassword());

        String insertUserSQL = "INSERT INTO users (username, password, email, nickname) VALUES (?, ?, ?, ?);";
        try (PreparedStatement statement = connection.prepareStatement(insertUserSQL)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, hash);
            statement.setString(3, user.getEmail());
            statement.setString(4, user.getNickname());
            statement.executeUpdate();
        }
    }

    public synchronized void insertMessage(ObservationRecord record) throws SQLException {
        checkDatabase();

        String insertMessageSQL = "INSERT INTO messages (target_body_name, center_body_name, epoch, orbital_elements, " +
                "state_vector, owner_nickname, observatory, payload, time_received, time_edited, update_reason) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement statement = connection.prepareStatement(insertMessageSQL)) {
            statement.setString(1, record.getTargetBodyName());
            statement.setString(2, record.getCenterBodyName());
            statement.setString(3, record.getEpoch());

            String orbitalElements = record.getOrbitalElements();
            if ("NULL".equalsIgnoreCase(orbitalElements)) {
                statement.setNull(4, java.sql.Types.VARCHAR);
            } else {
                statement.setString(4, orbitalElements);
            }

            String stateVector = record.getStateVector();
            if ("NULL".equalsIgnoreCase(stateVector)) {
                statement.setNull(5, java.sql.Types.VARCHAR);
            } else {
                statement.setString(5, stateVector);
            }

            statement.setString(6, record.getRecordOwner());

            String observatory = record.getObservatory();
            if ("NULL".equalsIgnoreCase(observatory)) {
                statement.setNull(7, java.sql.Types.VARCHAR);
            } else {
                statement.setString(7, observatory);
            }

            statement.setString(8, record.getPayload());
            statement.setLong(9, record.getTimeReceivedAsInt());
            
            long timeEdited = record.getTimeEditedAsInt();
            
            String updateReason = record.getUpdateReason();
            if (updateReason != null) {
                statement.setString(11, updateReason);
            } else {
                statement.setNull(11, java.sql.Types.VARCHAR);
            }
            
            statement.executeUpdate();
        }
    }

    public synchronized void updateMessage(ObservationRecord record) throws SQLException {
        checkDatabase();

        String updateMessageSQL = "UPDATE messages SET target_body_name = ?, center_body_name = ?, epoch = ?, orbital_elements = ?, " +
                "state_vector = ?, owner_nickname = ?, observatory = ?, payload = ?, time_received = ?, time_edited = ?, update_reason = ? " +
                "WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(updateMessageSQL)) {
            statement.setString(1, record.getTargetBodyName());
            statement.setString(2, record.getCenterBodyName());
            statement.setString(3, record.getEpoch());

            String orbitalElements = record.getOrbitalElements();
            if ("NULL".equalsIgnoreCase(orbitalElements)) {
                statement.setNull(4, java.sql.Types.VARCHAR);
            } else {
                statement.setString(4, orbitalElements);
            }

            String stateVector = record.getStateVector();
            if ("NULL".equalsIgnoreCase(stateVector)) {
                statement.setNull(5, java.sql.Types.VARCHAR);
            } else {
                statement.setString(5, stateVector);
            }

            statement.setString(6, record.getRecordOwner());

            String observatory = record.getObservatory();
            if ("NULL".equalsIgnoreCase(observatory)) {
                statement.setNull(7, java.sql.Types.VARCHAR);
            } else {
                statement.setString(7, observatory);
            }

            statement.setString(8, record.getPayload());
            statement.setLong(9, record.getTimeReceivedAsInt());
            
            long timeEdited = record.getTimeEditedAsInt();
            statement.setLong(10, timeEdited);
            
            String updateReason = record.getUpdateReason();
            if (updateReason != null) {
                statement.setString(11, updateReason);
            } else {
                statement.setNull(11, java.sql.Types.VARCHAR);
            }
            
            statement.setInt(12, record.getId());
            statement.executeUpdate();
        }
    }

    public synchronized List<ObservationRecord> getAllMessages() throws SQLException {
        checkDatabase();

        String queryMessagesSQL = "SELECT * FROM messages;";
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(queryMessagesSQL);

        List<ObservationRecord> records = new java.util.ArrayList<>();
        while (resultSet.next()) {
            int id = resultSet.getInt("id");
            String targetBodyName = resultSet.getString("target_body_name");
            String centerBodyName = resultSet.getString("center_body_name");
            String epoch = resultSet.getString("epoch");
            String orbitalElementsStr = resultSet.getString("orbital_elements");
            String stateVectorStr = resultSet.getString("state_vector");
            String ownerNickname = resultSet.getString("owner_nickname");
            String payload = resultSet.getString("payload");
            String observatoryStr = resultSet.getString("observatory");
            long timeReceived = resultSet.getLong("time_received");
            long timeEdited = -1;
            try {
                timeEdited = resultSet.getLong("time_edited");
            } catch (SQLException e) {}
            String updateReason = resultSet.getString("update_reason");
            ObservationRecord record = new ObservationRecord(ownerNickname);
            record.setId(id);
            record.setTargetBodyName(targetBodyName);
            record.setCenterBodyName(centerBodyName);
            record.setEpoch(epoch);
            record.setPayload(payload);
            record.setTimeReceived(timeReceived);
            if (timeEdited > 0) {
                record.setTimeEdited(timeEdited);
            }
            if (updateReason != null) {
                record.setUpdateReason(updateReason);
            }

            JSONObject orbitalElements;
            JSONObject stateVector;
            JSONArray observatory;
            try {
                orbitalElements = new JSONObject(orbitalElementsStr);
            } catch (JSONException | NullPointerException e) {
                orbitalElements = null;
            }
            try {
                stateVector = new JSONObject(stateVectorStr);
            } catch (JSONException | NullPointerException e) {
                stateVector = null;
            }
            try {
                observatory = new JSONArray(observatoryStr);
            } catch (JSONException | NullPointerException e) {
                observatory = null;
            }
            record.setOrbitalElements(orbitalElements);
            record.setStateVector(stateVector);
            record.setObservatory(observatory);

            records.add(record);
        }
        statement.close();
        return records;
    }

    public synchronized ObservationRecord getMessage(int id) throws SQLException {
        checkDatabase();

        String queryMessageSQL = "SELECT * FROM messages WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(queryMessageSQL)) {
            statement.setInt(1, id);
            ResultSet resultSet = statement.executeQuery();
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
                long timeEdited = -1;
                try {
                    timeEdited = resultSet.getLong("time_edited");
                } catch (SQLException e) {}
                String updateReason = resultSet.getString("update_reason");
                ObservationRecord record = new ObservationRecord(ownerNickname);
                record.setId(id);
                record.setTargetBodyName(targetBodyName);
                record.setCenterBodyName(centerBodyName);
                record.setEpoch(epoch);
                record.setPayload(payload);
                record.setTimeReceived(timeReceived);
                if (timeEdited > 0) {
                    record.setTimeEdited(timeEdited);
                }
                if (updateReason != null) {
                    record.setUpdateReason(updateReason);
                }

                JSONObject orbitalElements;
                JSONObject stateVector;
                JSONArray observatory;
                try {
                    orbitalElements = new JSONObject(orbitalElementsStr);
                } catch (JSONException | NullPointerException e) {
                    orbitalElements = null;
                }
                try {
                    stateVector = new JSONObject(stateVectorStr);
                } catch (JSONException | NullPointerException e) {
                    stateVector = null;
                }
                try {
                    observatory = new JSONArray(observatoryStr);
                } catch (JSONException | NullPointerException e) {
                    observatory = null;
                }
                record.setOrbitalElements(orbitalElements);
                record.setStateVector(stateVector);
                record.setObservatory(observatory);

                statement.close();
                return record;
            } else {
                statement.close();
                return null;
            }
        }
    }

    public synchronized User getUser(String username) throws SQLException {
        checkDatabase();

        String queryUserSQL = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement statement = connection.prepareStatement(queryUserSQL)) {
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
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

        return verifyHash(password, user.getPassword());

    }

    public synchronized JSONArray getCollectionIds() {
        checkDatabase();

        List<Integer> collectionIds = new java.util.ArrayList<>();

        try {
            String queryCollectionIdSQL = "SELECT id FROM collections;";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(queryCollectionIdSQL);

            while (resultSet.next()) {
                collectionIds.add(resultSet.getInt("id"));
            }
            statement.close();            
        } catch (SQLException e) {
            e.printStackTrace();
        }

        JSONArray jsonArray = new JSONArray(collectionIds);
        return jsonArray;
    }

    public synchronized JSONArray getMessagesByCollectionId(int collectionId) {
        JSONArray collection = getCollection(collectionId);
        JSONArray messages = new JSONArray();
        
        for (int i = 0; i < collection.length(); i++) {
            int messageId = collection.getInt(i);
            try {
                messages.put(getMessage(messageId).toJSON());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return messages;
    }

    public synchronized int createCollection(JSONArray messageIds) {
        checkDatabase();

        String insertCollectionSQL = "INSERT INTO collections (collection) VALUES (?);";
        try (PreparedStatement preparedStatement = connection.prepareStatement(insertCollectionSQL)) {
            preparedStatement.setString(1, messageIds.toString());
            preparedStatement.executeUpdate();
            String queryNewCollectionIdSQL = "SELECT last_insert_rowid();";
            try (PreparedStatement queryStatement = connection.prepareStatement(queryNewCollectionIdSQL)) {
                ResultSet resultSet = queryStatement.executeQuery();
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public synchronized boolean addMessagesToCollection(int collectionId, JSONArray messageIds) {
        checkDatabase();
 
        JSONArray currentCollection = getCollection(collectionId);
        for (int i = 0; i < messageIds.length(); i++) {
            if (!currentCollection.toList().contains(messageIds.get(i))) {
                currentCollection.put(messageIds.get(i));
            }
        }
        String updateCollectionSQL = "UPDATE collections SET collection = ? WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(updateCollectionSQL)) {
            preparedStatement.setString(1, currentCollection.toString());
            preparedStatement.setInt(2, collectionId);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private synchronized JSONArray getCollection(int id) {
        checkDatabase();

        JSONArray collection = new JSONArray();

        String queryCollectionSQL = "SELECT collection FROM collections WHERE id = ?;";
        try (PreparedStatement preparedStatement = connection.prepareStatement(queryCollectionSQL)) {
            preparedStatement.setInt(1, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                collection = new JSONArray(resultSet.getString("collection"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return collection;
    }

	private String generateHash(String password) {
        return Crypt.crypt(password);
	}

    private boolean verifyHash(String password, String storedHash) {
        return Crypt.crypt(password, storedHash).equals(storedHash);
    }

    private synchronized void checkDatabase() {
        if (connection == null) {
            throw new IllegalStateException("Database not initialized.");
        }
    }
}