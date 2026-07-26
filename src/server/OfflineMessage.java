package server;

import java.io.Serializable;

class OfflineMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    int id;
    String sender;
    String text;

    OfflineMessage(int id, String sender, String text) {
        this.id = id;
        this.sender = sender;
        this.text = text;
    }
}
