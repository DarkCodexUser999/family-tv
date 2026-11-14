package app.firetvremote.remote;

/** Strict IPv4 validation, including empty trailing octets. */
final class RemoteInput {
    private RemoteInput() {}
    static boolean freshCommand(int version, int currentVersion, long queuedAt, long now) {
        return version == currentVersion && now >= queuedAt && now - queuedAt <= 750;
    }

    static boolean validAddress(String address) {
        if (address == null) return false;
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            int octet = 0;
            for (int i = 0; i < part.length(); i++) {
                char digit = part.charAt(i);
                if (digit < '0' || digit > '9') return false;
                octet = octet * 10 + digit - '0';
            }
            if (octet > 255) return false;
        }
        return true;
    }
}
