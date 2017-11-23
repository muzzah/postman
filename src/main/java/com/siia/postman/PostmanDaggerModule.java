package com.siia.postman;

import android.content.Context;
import android.net.nsd.NsdManager;

import com.osiyent.sia.commons.core.android.AndroidDaggerModule;
import com.siia.postman.server.IPPostmanServer;
import com.siia.postman.server.PostmanServer;

import dagger.Module;
import dagger.Provides;

@Module(includes = AndroidDaggerModule.class)
public class PostmanDaggerModule {

    @Provides
    public PostmanServer providesPostmanServer(Context context, NsdManager nsdManager) {
        return new IPPostmanServer(context, nsdManager);
    }
}
