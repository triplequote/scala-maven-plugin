package scala_maven;

import com.license4j.License;
import com.triplequote.license.HydraLicenseStore;

import java.io.IOException;

/**
 * @goal hydraDeactivateLicense
 * @requiresDirectInvocation true
 */
public class HydraDeactivateLicenseMojo extends HydraLicenseMojo {

    @Override
    protected License executeLicenseOperation(HydraLicenseStore store) throws IOException {
        getLog().info("Deactivating license using " + activationServerUrl + ". This may take a while.");
        return store.deactivate();
    }
}

