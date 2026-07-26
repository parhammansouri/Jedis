package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

class ProfileStore {
    private final File directory = new File("profiles");

    ProfileStore() {
        directory.mkdirs();
    }

    synchronized boolean exists(String username) {
        return file(username).isFile();
    }

    synchronized UserProfile load(String username) throws IOException, ClassNotFoundException {
        File target = file(username);
        if (!target.isFile()) {
            return null;
        }
        ObjectInputStream input = new ObjectInputStream(new FileInputStream(target));
        try {
            return (UserProfile) input.readObject();
        } finally {
            input.close();
        }
    }

    synchronized void save(UserProfile profile) throws IOException {
        FileOutputStream fileOutput = new FileOutputStream(file(profile.username));
        ObjectOutputStream output = new ObjectOutputStream(fileOutput);
        try {
            output.writeObject(profile);
        } finally {
            output.close();
        }
    }

    private File file(String username) {
        return new File(directory, safe(username) + ".dat");
    }

    private String safe(String username) {
        return username.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
