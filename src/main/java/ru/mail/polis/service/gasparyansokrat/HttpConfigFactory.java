package ru.mail.polis.service.gasparyansokrat;

import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;

final class HttpConfigFactory {

    private HttpConfigFactory() {

    }

    public static HttpServerConfig buildHttpConfig(final int port, final int poolSize, final String address) {
        AcceptorConfig accConf = new AcceptorConfig();
        accConf.port = port;
        accConf.address = address;
        accConf.reusePort = true;
        accConf.threads = poolSize;
        HttpServerConfig config = new HttpServerConfig();
        config.acceptors = new AcceptorConfig[]{accConf};
        return config;
    }
}
