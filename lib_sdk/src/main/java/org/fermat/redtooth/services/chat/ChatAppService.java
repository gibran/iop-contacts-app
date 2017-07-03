package org.fermat.redtooth.services.chat;

import org.fermat.redtooth.profile_server.ProfileInformation;
import org.fermat.redtooth.profile_server.engine.app_services.AppService;
import org.fermat.redtooth.profile_server.engine.app_services.CallProfileAppService;
import org.fermat.redtooth.profile_server.model.Profile;
import org.fermat.redtooth.services.EnabledServices;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by mati on 16/05/17.
 */

public class ChatAppService extends AppService{

    private LinkedList<ChatMsgListener> listeners;

    public ChatAppService() {
        super(EnabledServices.CHAT.getName());
        listeners = new LinkedList<>();
    }

    public void addListener(ChatMsgListener msgListener){
        listeners.add(msgListener);
    }

    @Override
    public void onPreCall() {
        super.onPreCall();
    }

    @Override
    public void onWrapCall(final CallProfileAppService callProfileAppService) {
        callProfileAppService.setMsgListener(new CallProfileAppService.CallMessagesListener() {
            @Override
            public void onMessage(byte[] msg) {
                for (ChatMsgListener listener : listeners) {
                    listener.onMsgReceived(callProfileAppService.getRemotePubKey(),msg);
                }
            }
        });
    }

    @Override
    public void onCallConnected(Profile localProfile, ProfileInformation remoteProfile) {
        super.onCallConnected(localProfile, remoteProfile);
        for (ChatMsgListener listener : listeners) {
            listener.onChatConnected(remoteProfile.getHexPublicKey());
        }
    }
}