package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

class OfflineQueueManager {
    private final File directory = new File("offline_data");

    OfflineQueueManager() {
        directory.mkdirs();
    }

    synchronized void enqueue(String username, OfflineMessage message) throws IOException {
        List<OfflineMessage> messages = read(username);
        messages.add(message);
        write(username, messages);
    }

    synchronized List<OfflineMessage> drain(String username) throws IOException, ClassNotFoundException {
        List<OfflineMessage> messages = read(username);
        File file = file(username);
        if (file.isFile()) {
            file.delete();
        }
        return messages;
    }

    synchronized void flushAll() {
    }

    @SuppressWarnings("unchecked")
    private List<OfflineMessage> read(String username) throws IOException {
        File file = file(username);
        if (!file.isFile()) {
            return new ArrayList<OfflineMessage>();
        }
        try {
            ObjectInputStream input = new ObjectInputStream(new FileInputStream(file));
            try {
                return (List<OfflineMessage>) input.readObject();
            } finally {
                input.close();
            }
        } catch (ClassNotFoundException error) {
            throw new IOException(error);
        }
    }

    private void write(String username, List<OfflineMessage> messages) throws IOException {
        ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(file(username)));
        try {
            output.writeObject(messages);
        } finally {
            output.close();
        }
    }

    private File file(String username) {
        return new File(directory, username.replaceAll("[^A-Za-z0-9_.-]", "_") + ".dat");
    }
}
