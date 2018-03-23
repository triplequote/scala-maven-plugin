package scala_maven;

import com.license4j.License;
import com.triplequote.license.HydraLicenseStore;

import java.io.IOException;

/**
 * @goal hydraActivateLicense
 * @requiresDirectInvocation true
 */
public class HydraActivateLicenseMojo extends HydraLicenseMojo {

    /**
     * @parameter property="hydra.license.key"
     */
    private String licenseKey;

    @Override
    protected License executeLicenseOperation(HydraLicenseStore store) throws IOException {
        String key = licenseKey == null ? store.loadProperties().getProperty(HydraLicenseStore.LICENSE_KEY) : licenseKey;
        getLog().info("Activating license " + key + " using " + activationServerUrl + ". This may take a while.");

        return store.activate(key);
    }
}
