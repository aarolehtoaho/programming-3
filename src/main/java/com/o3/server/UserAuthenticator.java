package com.o3.server;

import java.util.Hashtable;
import java.util.Map;

import com.sun.net.httpserver.BasicAuthenticator;

public class UserAuthenticator extends BasicAuthenticator {
    private Map<String, User> users = null;
    
    public UserAuthenticator() {
        super("datarecord");
        this.users = new Hashtable<>();
    }

    public boolean addUser(String userName, String password, String email, String nickname) {
        if (users.containsKey(userName)) {
            return false;
        }
        users.put(userName, new User(userName, password, email, nickname));
        return true;
    }

    public String getNickname(String username) {
        User user = users.get(username);
        if (user != null) {
            return user.getNickname();
        }
        return null;
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        User user = users.get(username);
        return user != null && user.getPassword().equals(password);
    }
}
