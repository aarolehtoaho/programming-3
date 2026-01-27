package com.o3.server;

import java.util.Hashtable;
import java.util.Map;

import com.sun.net.httpserver.BasicAuthenticator;

public class UserAuthenticator extends BasicAuthenticator {
    private Map<String, User> users = null;
    
    public UserAuthenticator() {
        super("datarecord");
        this.users = new Hashtable<>();
        users.put("dummy", new User("dummy", "passwd", "dummy@example.com"));
    }

    public boolean addUser(String userName, String password, String email) {
        if (users.containsKey(userName)) {
            return false;
        }
        users.put(userName, new User(userName, password, email));
        return true;
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        User user = users.get(username);
        return user != null && user.getPassword().equals(password);
    }
}
