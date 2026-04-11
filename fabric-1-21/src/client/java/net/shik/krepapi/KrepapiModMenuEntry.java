package net.shik.krepapi;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.shik.krepapi.client.KrepapiModMenuIntegration;

/** Lives outside {@code .client} for LabyMod entrypoint loading; delegates to {@link KrepapiModMenuIntegration}. */
public final class KrepapiModMenuEntry implements ModMenuApi {

    private final ModMenuApi delegate = new KrepapiModMenuIntegration();

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return delegate.getModConfigScreenFactory();
    }
}
