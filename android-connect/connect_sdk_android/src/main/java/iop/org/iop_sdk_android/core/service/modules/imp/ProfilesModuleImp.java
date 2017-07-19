package iop.org.iop_sdk_android.core.service.modules.imp;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;

import org.fermat.redtooth.core.IoPConnect;
import org.fermat.redtooth.global.Version;
import org.fermat.redtooth.profile_server.engine.app_services.PairingListener;
import org.fermat.redtooth.profile_server.engine.futures.BaseMsgFuture;
import org.fermat.redtooth.profile_server.engine.futures.ConnectionFuture;
import org.fermat.redtooth.profile_server.model.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import iop.org.iop_sdk_android.core.service.IoPConnectService;
import iop.org.iop_sdk_android.core.service.modules.AbstractModule;
import iop.org.iop_sdk_android.core.service.modules.ModuleId;
import iop.org.iop_sdk_android.core.service.modules.interfaces.ProfilesModule;
import iop.org.iop_sdk_android.core.utils.ImageUtils;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.ACTION_ON_CHECK_IN_FAIL;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.ACTION_ON_PAIR_RECEIVED;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.ACTION_ON_PROFILE_CONNECTED;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.ACTION_ON_RESPONSE_PAIR_RECEIVED;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.INTENT_EXTRA_PROF_KEY;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.INTENT_EXTRA_PROF_NAME;
import static iop.org.iop_sdk_android.core.IntentBroadcastConstants.INTENT_RESPONSE_DETAIL;

/**
 * Created by furszy on 7/19/17.
 */

public class ProfilesModuleImp extends AbstractModule implements ProfilesModule{

    private static final Logger logger = LoggerFactory.getLogger(ProfilesModuleImp.class);

    // todo: change this for the non local broadcast..
    private LocalBroadcastManager localBroadcastManager;
    private IoPConnect ioPConnect;
    // This instance is just for now to start dividing things, to get and set the profile
    private IoPConnectService connectService;
    private Handler handler = new Handler();

    public ProfilesModuleImp(Context context, IoPConnect ioPConnect, IoPConnectService connectService) {
        super(
                Version.newProtocolAcceptedVersion(), // version 1 default for now..
                ModuleId.PROFILES.getId() // module identifier
        );
        this.ioPConnect = ioPConnect;
        this.connectService = connectService;
        this.localBroadcastManager = LocalBroadcastManager.getInstance(context);
    }

    private PairingListener pairingListener = new PairingListener() {
        @Override
        public void onPairReceived(String requesteePubKey, final String name) {
            Intent intent = new Intent(ACTION_ON_PAIR_RECEIVED);
            intent.putExtra(INTENT_EXTRA_PROF_KEY,requesteePubKey);
            intent.putExtra(INTENT_EXTRA_PROF_NAME,name);
            localBroadcastManager.sendBroadcast(intent);
        }

        @Override
        public void onPairResponseReceived(String requesteePubKey, String responseDetail) {
            Intent intent = new Intent(ACTION_ON_RESPONSE_PAIR_RECEIVED);
            intent.putExtra(INTENT_EXTRA_PROF_KEY,requesteePubKey);
            intent.putExtra(INTENT_RESPONSE_DETAIL,responseDetail);
            localBroadcastManager.sendBroadcast(intent);
        }
    };

    @Override
    public String registerProfile(String name, String type, byte[] img, int latitude, int longitude, String extraData) throws Exception {
        if (img!=null){
            img = ImageUtils.compressJpeg(img, 20480);
        }
        Profile profile = ioPConnect.createProfile(null,name,type,img,extraData,null);
        // just for now..
        connectService.setProfile(profile);
        return profile.getHexPublicKey();
    }

    @Override
    public String registerProfile(String name, byte[] img) throws Exception {
        return registerProfile(name,"IoP-contacts",img,0,0,null);
    }

    @Override
    public void connect(final String pubKey) throws Exception {
        final ConnectionFuture msgListenerFuture = new ConnectionFuture();
        msgListenerFuture.setListener(new BaseMsgFuture.Listener<Boolean>() {
            @Override
            public void onAction(int messageId, Boolean object) {
                Profile profile = connectService.getProfile();
                profile.setHomeHost(msgListenerFuture.getProfServerData().getHost());
                profile.setHomeHostId(msgListenerFuture.getProfServerData().getNetworkId());
                onCheckInCompleted(profile.getHexPublicKey());
            }

            @Override
            public void onFail(int messageId, int status, String statusDetail) {
                Profile profile = connectService.getProfile();
                onCheckInFail(profile,status,statusDetail);
                if (status==400){
                    logger.info("Checking fail, detail "+statusDetail+", trying to reconnect after 5 seconds");
                    handler.postDelayed(reconnectRunnable, TimeUnit.SECONDS.toMillis(15));
                }

            }
            Runnable reconnectRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        connect(pubKey);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // connection fail
                    }
                }
            };

        });
        ioPConnect.connectProfile(pubKey,pairingListener,null,msgListenerFuture);
    }

    public void onCheckInCompleted(String localProfilePubKey) {
        Intent intent = new Intent(ACTION_ON_PROFILE_CONNECTED);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void onCheckInFail(Profile profile, int status, String statusDetail) {
        logger.warn("on check in fail: "+statusDetail);
        Intent intent = new Intent(ACTION_ON_CHECK_IN_FAIL);
        intent.putExtra(INTENT_RESPONSE_DETAIL,statusDetail);
        localBroadcastManager.sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        connectService = null;
        ioPConnect = null;
    }
}
