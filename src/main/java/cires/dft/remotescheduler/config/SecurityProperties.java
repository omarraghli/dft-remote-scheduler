package cires.dft.remotescheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Security settings, under {@code remote.security.*}. */
@ConfigurationProperties(prefix = "remote.security")
public class SecurityProperties {

    /** The account created on an empty database, so there is someone to log in as. */
    private Bootstrap bootstrap = new Bootstrap();

    /**
     * Shared secret for machine callers, sent as {@code X-Service-Token}. A request carrying it
     * is treated as an admin. Empty disables it entirely, which is the default — nothing is
     * accepted unless you deliberately configure a token.
     */
    private String serviceToken = "";

    public static class Bootstrap {

        private String email = "admin@cirestechnologies.ma";

        /**
         * Left empty on purpose. With no password configured a random one is generated and
         * printed to the log once, which is safer than a default everyone knows. Either way it
         * must be changed at first sign-in.
         */
        private String password = "";

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }
}
