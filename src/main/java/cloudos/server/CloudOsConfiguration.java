package cloudos.server;

import cloudos.appstore.client.AppStoreApiClient;
import cloudos.appstore.model.app.AppLayout;
import cloudos.appstore.model.app.AppManifest;
import cloudos.databag.DnsMode;
import cloudos.dns.DnsClient;
import cloudos.dns.service.dyn.DynDnsManager;
import cloudos.dns.service.mock.MockDnsManager;
import cloudos.model.Account;
import cloudos.service.TwoFactorAuthService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.mail.sender.SmtpMailConfig;
import org.cobbzilla.mail.service.TemplatedMailSenderConfiguration;
import org.cobbzilla.util.dns.DnsManager;
import org.cobbzilla.util.http.ApiConnectionInfo;
import org.cobbzilla.util.system.CommandShell;
import org.cobbzilla.wizard.cache.redis.HasRedisConfiguration;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.cobbzilla.wizard.server.config.LdapConfiguration;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rooty.RootyConfiguration;
import rooty.toots.chef.ChefHandler;
import rooty.toots.postfix.PostfixHandler;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Configuration @Slf4j
public class CloudOsConfiguration extends RestServerConfiguration
        implements HasDatabaseConfiguration, HasTwoFactorAuthConfiguration, TemplatedMailSenderConfiguration,
                   HasRedisConfiguration {

    public static final String DEFAULT_ADMIN = "admin";
    public static final String APP_REPOSITORY = "app-repository";

    @Setter private DatabaseConfiguration database;
    @Bean public DatabaseConfiguration getDatabase() { return database; }

    @Getter @Setter private CloudOsRedisConfiguration redis = new CloudOsRedisConfiguration(this);

    @Getter @Setter private File appRepository = new File(System.getProperty("user.home"), APP_REPOSITORY);
    public AppLayout getAppLayout(String name) { return new AppLayout(getAppRepository(), name); }
    public AppLayout getAppLayout(String name, String version) { return new AppLayout(getAppRepository(), name, version); }
    public AppLayout getAppLayout(AppManifest manifest) { return new AppLayout(getAppRepository(), manifest); }
    public AppLayout getAppLayoutForLatestVersion(String name) { return new AppLayout(getAppRepository(), name, AppLayout.LATEST_VERSION); }

    @Getter @Setter private String appRefreshKey;

    @Getter @Setter private CloudStorageConfiguration cloudConfig = new CloudStorageConfiguration();

    @Getter @Setter private SmtpMailConfig smtp;
    @Getter @Setter private String emailTemplateRoot;

    @Getter @Setter private ApiConnectionInfo appStore;
    @Setter private AppStoreApiClient appStoreClient;
    public AppStoreApiClient getAppStoreClient () {
        if (appStoreClient == null) appStoreClient = new AppStoreApiClient(appStore);
        return appStoreClient;
    }

    @Getter @Setter private ApiConnectionInfo authy;

    private TwoFactorAuthService twoFactorAuthService = null;
    @Override public TwoFactorAuthService getTwoFactorAuthService () {
        if (twoFactorAuthService == null) twoFactorAuthService = new TwoFactorAuthService(authy);
        return twoFactorAuthService;
    }

    @Getter @Setter private LdapConfiguration ldap = new LdapConfiguration();
    @Getter @Setter private String defaultAdmin = DEFAULT_ADMIN;

    @Getter @Setter private RootyConfiguration rooty;
    public ChefHandler getChefHandler () { return rooty.getHandler(ChefHandler.class); }
    public PostfixHandler getPostfixHandler () { return rooty.getHandler(PostfixHandler.class); }

    @Getter @Setter private String rootyGroup = "rooty";

    @Getter @Setter private DnsMode dnsMode;

    @Getter @Setter private DnsConfiguration dns;

    @Getter(lazy=true) private final DnsManager dnsManager = initDnsManager();
    public DnsManager initDnsManager() {
        if (dns == null || !dns.isEnabled()) return new MockDnsManager();
        switch (dnsMode) {
            case dyn: return new DynDnsManager(dns);
            case internal: case cdns: return new DnsClient(dns);
            default: return die("initDnsManager: invalid mode: "+dnsMode);
        }
    }

    @Getter(lazy=true) private final String hostname = initHostname();
    private String initHostname() { return CommandShell.hostname(); }

    @Getter(lazy=true) private final String shortHostname = initShortHostname();
    private String initShortHostname () {
        final String h = getHostname();
        final int dotPos = h.indexOf(".");
        return dotPos == -1 ? h : h.substring(0, dotPos);
    }

    @Getter(lazy=true) private final String publicIp = initPublicIp();

    private String initPublicIp() {
        try {
            final String ip = InetAddress.getLocalHost().getHostAddress();
            log.info("initPublicIp: returning ip="+ip);
            return ip;
        } catch (UnknownHostException e) {
            return die("Error getting public ip: " + e, e);
        }
    }

    public String getResetPasswordUrl(String token) {
        return new StringBuilder().append(getPublicUriBase()).append("/reset_password.html?key=").append(token).toString();
    }

    public String getAssetUrlBase () {
        String base = getPublicUriBase();
        if (base.endsWith("/")) base = base.substring(0, base.length()-1);
        return base + getHttp().getBaseUri() + "/app_assets/";
    }

    public String getLocale(Account admin) {
        return admin.hasLocale() ? admin.getLocale() : getSystemLocale();
    }

    public String getSystemLocale () {
        return CommandShell.execScript("locale | grep LANG= | tr '=.' ' ' | awk '{print $2}'").trim().toLowerCase();
    }
}
