import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Arrays;

// Java source launcher (JDK 17+). Passwords arrive only through the child environment.
class VerifySigningCredentials {
    public static void main(String[] args) {
        char[] storePassword = null;
        char[] keyPassword = null;
        boolean verified = false;
        try {
            String path = System.getenv("ANDROID_KEYSTORE_PATH");
            String alias = System.getenv("ANDROID_KEY_ALIAS");
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD").toCharArray();
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD").toCharArray();
            KeyStore store = KeyStore.getInstance(new File(path), storePassword);
            verified = store.getKey(alias, keyPassword) instanceof PrivateKey
                && store.getCertificate(alias) != null;
        } catch (Exception ignored) {
            // Do not include exception messages, credential values or keystore metadata.
        } finally {
            if (storePassword != null) Arrays.fill(storePassword, '\0');
            if (keyPassword != null) Arrays.fill(keyPassword, '\0');
        }
        if (!verified) {
            System.err.println("Unable to unlock the signing private key with the supplied credentials.");
            System.exit(1);
        }
        System.out.println("Signing keystore and private-key credentials verified.");
    }
}
