package com.cloudbees.lxd.client;

import okhttp3.logging.HttpLoggingInterceptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Config {

    private final String baseURL;
    private final String unixSocketPath;
    private final Map<String, String> remotesURL = new HashMap<>();

    private HttpLoggingInterceptor.Level logLevel = HttpLoggingInterceptor.Level.BODY;

    /** PEM encoded bytes of the client's certificate.
     * If {@link Config#baseURL} indicates a Unix socket, the certificate and key bytes will not be used. */
    private String clientPEMCert;

    /** PEM encoded private bytes of the client's key associated with its certificate */
    private String clientPEMKey;

    /** Password to decrypt the PEM encoded private bytes */
    private String clientPEMKeyPassword;

    private Config(String baseURL, String unixSocketPath, String clientPEMCert, String clientPEMKey, String clientPEMKeyPassword) {
        this.baseURL = baseURL;
        this.unixSocketPath = unixSocketPath;
        this.clientPEMCert = clientPEMCert;
        this.clientPEMKey = clientPEMKey;
        this.clientPEMKeyPassword = clientPEMKeyPassword;

        // from https://github.com/lxc/lxd/blob/34f62a7ea5cfea0f640ceb16ffc49a8f7c206c6c/config.go#L54
        remotesURL.put("images", "https://images.linuxcontainers.org");
        remotesURL.put("ubuntu", "https://cloud-images.ubuntu.com/releases");
        remotesURL.put("ubuntu-daily", "https://cloud-images.ubuntu.com/daily");
    }

    public String getBaseURL() {
        return baseURL;
    }

    public String getUnixSocketPath() {
        return unixSocketPath;
    }

    public boolean useUnixTransport() {
        return unixSocketPath != null;
    }

    public String getClientPEMCert() {
        return clientPEMCert;
    }

    public String getClientPEMKey() {
        return clientPEMKey;
    }

    public String getClientPEMKeyPassphrase() {
        return clientPEMKeyPassword;
    }

    public HttpLoggingInterceptor.Level getLogLevel() {
        return logLevel;
    }

    public Map<String, String> getRemotesURL() {
        return remotesURL;
    }

    public static Config localAccessConfig() {
        return new Config("http://localhost:80", "/var/lib/lxd/unix.socket", null, null, null);
    }
    public static Config localAccessConfig(String unixSocketPath) {
        return new Config("http://localhost:80", unixSocketPath, null, null, null);
    }

    public static Config remoteAccessConfig(String baseURL, String clientPEMCert, String clientPEMKey, String clientPEMCa) {
        return new Config(baseURL, null, clientPEMCert, clientPEMKey, clientPEMCa);
    }

    public static Config remoteAccessConfig(String baseURL) {
        return new Config(baseURL, null, null, null, null);
    }
}
