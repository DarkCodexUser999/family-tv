package app.firetvremote.launcher;

import android.content.SharedPreferences;
import android.os.SystemClock;
import app.firetvremote.Link;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

final class RemoteServer implements AutoCloseable {
    interface Listener {
        void onCommand(String action);
        void onPaired();
    }

    private final SharedPreferences phones;
    private final Listener listener;
    private final ServerSocket server;
    private String pairingCode;
    private long pairingDeadline;

    RemoteServer(SharedPreferences phones, Listener listener) throws IOException {
        this.phones = phones;
        this.listener = listener;
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(Link.PORT));
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        server = socket;
        new Thread(this::serve, "family-tv-remote").start();
    }

    synchronized String startPairing() {
        pairingCode = Link.randomText(16);
        pairingDeadline = SystemClock.elapsedRealtime() + 120_000;
        return pairingCode;
    }

    synchronized void stopPairing() {
        pairingCode = null;
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket connection = server.accept()) {
                connection.setSoTimeout(1500);
                handle(connection);
            } catch (IOException ignored) {
                // A client can disconnect at any point, including before sending a request.
            }
        }
    }

    private void handle(Socket connection) throws IOException {
        DataInputStream input = new DataInputStream(connection.getInputStream());
        DataOutputStream output = new DataOutputStream(connection.getOutputStream());
        String nonce = Link.randomText(24);
        output.writeUTF(nonce);
        output.flush();
        String id = input.readUTF();
        String action = input.readUTF();
        String proof = input.readUTF();
        if (!id.matches("[A-Z2-9]{24}") ||
                !action.matches("PAIR|UP|DOWN|LEFT|RIGHT|OK|BACK|OPTIONS|HOME|AMAZON")) return;
        String request = nonce + ":" + id + ":" + action;

        String key;
        synchronized (this) {
            if (server.isClosed()) return;
            boolean pairing = action.equals("PAIR");
            if (pairing && (pairingCode == null || SystemClock.elapsedRealtime() > pairingDeadline)) return;
            key = pairing ? Link.phoneKey(pairingCode, id) : phones.getString(id, null);
            if (key == null || !Link.matches(Link.proof(key, "request:" + request), proof)) return;
            if (pairing) {
                if (!phones.edit().putString(id, key).commit()) return;
                pairingCode = null;
            }
        }
        output.writeUTF(Link.proof(key, "accepted:" + request));
        output.flush();
        if (action.equals("PAIR")) listener.onPaired();
        else listener.onCommand(action);
    }

    @Override
    public synchronized void close() {
        pairingCode = null;
        try {
            server.close();
        } catch (IOException ignored) {
            // Closing the activity also ends the socket's accept loop.
        }
    }
}
