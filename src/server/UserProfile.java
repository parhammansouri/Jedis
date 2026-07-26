package server;

import java.io.Serializable;

class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    String username;
    String password;
    long sent;
    long received;

    UserProfile(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
