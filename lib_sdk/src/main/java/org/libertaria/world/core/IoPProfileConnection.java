package org.libertaria.world.core;


import org.libertaria.world.crypto.CryptoBytes;
import org.libertaria.world.crypto.CryptoWrapper;
import org.libertaria.world.global.DeviceLocation;
import org.libertaria.world.global.Version;
import org.libertaria.world.profile_server.CantSendMessageException;
import org.libertaria.world.profile_server.engine.ProfSerEngine;
import org.libertaria.world.profile_server.engine.app_services.AppServiceMsg;
import org.libertaria.world.profile_server.engine.app_services.CallProfileAppService;
import org.libertaria.world.profile_server.engine.crypto.BoxAlgo;
import org.libertaria.world.profile_server.engine.futures.SearchMessageFuture;
import org.libertaria.world.profile_server.engine.listeners.ConnectionListener;
import org.libertaria.world.profile_server.imp.ProfileInformationImp;
import org.libertaria.world.profile_server.protocol.IopProfileServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

/**
 * Created by mati on 08/05/17.
 *
 * Core class to manage a single profile connection to the IoP network.
 *
 */

public class IoPProfileConnection implements org.libertaria.world.profile_server.engine.app_services.CallsListener, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.CallStateListener {

    private static final Logger logger = LoggerFactory.getLogger(IoPProfileConnection.class);

    /** Context wrapper */
    private IoPConnectContext contextWrapper;
    /** Profile cached */
    private org.libertaria.world.profile_server.model.Profile profileCache;
    /** PS */
    private org.libertaria.world.profile_server.model.ProfServerData psConnData;
    /** profile server engine */
    private ProfSerEngine profSerEngine;
    /** Crypto implmentation dependent on the platform */
    private CryptoWrapper cryptoWrapper;
    /** Ssl context factory */
    private org.libertaria.world.profile_server.SslContextFactory sslContextFactory;
    /** Location helper dependent on the platform */
    private DeviceLocation deviceLocation;
    /** Open profile app service calls -> call token -> call in progress */
    private ConcurrentMap<String, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService> openCall = new ConcurrentHashMap<>();

    public IoPProfileConnection(IoPConnectContext contextWrapper, org.libertaria.world.profile_server.model.Profile profile, org.libertaria.world.profile_server.model.ProfServerData psConnData, CryptoWrapper cryptoWrapper, org.libertaria.world.profile_server.SslContextFactory sslContextFactory, DeviceLocation deviceLocation){
        this.contextWrapper = contextWrapper;
        this.cryptoWrapper = cryptoWrapper;
        this.sslContextFactory = sslContextFactory;
        this.psConnData = psConnData;
        this.profileCache = profile;
        this.deviceLocation = deviceLocation;
    }

    /**
     * Initialization method.
     *
     * @throws Exception
     */
    public void init(final org.libertaria.world.profile_server.engine.futures.MsgListenerFuture<Boolean> initFuture, ConnectionListener connectionListener) throws ExecutionException, InterruptedException {
        initProfileServer(psConnData,connectionListener);
        org.libertaria.world.profile_server.engine.futures.MsgListenerFuture<Boolean> initWrapper = new org.libertaria.world.profile_server.engine.futures.MsgListenerFuture<>();
        initWrapper.setListener(new org.libertaria.world.profile_server.engine.futures.BaseMsgFuture.Listener<Boolean>() {
            @Override
            public void onAction(int messageId, Boolean object) {
                // not that this is initialized, init the app services
                registerApplicationServices();
                initFuture.onMessageReceive(messageId,object);
            }

            @Override
            public void onFail(int messageId, int status, String statusDetail) {
                initFuture.onMsgFail(messageId,status,statusDetail);
            }
        });
        profSerEngine.start(initWrapper);
    }

    public void init(ConnectionListener connectionListener) throws Exception {
        init(null,connectionListener);
    }

    /**
     * Initialize the profile server
     * @throws Exception
     */
    private void initProfileServer(org.libertaria.world.profile_server.model.ProfServerData profServerData, ConnectionListener connectionListener) {
        if (profServerData.getHost()!=null) {
            profSerEngine = new ProfSerEngine(
                    contextWrapper,
                    profServerData,
                    profileCache,
                    cryptoWrapper,
                    sslContextFactory
            );
            profSerEngine.setCallListener(this);
            profSerEngine.addConnectionListener(connectionListener);
        }else {
            throw new IllegalStateException("Profile server not found, please set one first using LOC");
        }
    }

    public void stop() {
        for (Map.Entry<String, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService> stringCallProfileAppServiceEntry : openCall.entrySet()) {
            try{
                stringCallProfileAppServiceEntry.getValue().dispose();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        openCall.clear();
        profSerEngine.stop();
    }

    /**
     * Register the default app services
     */
    private void registerApplicationServices() {
        // registerConnect application services
        final org.libertaria.world.profile_server.model.Profile profile = profSerEngine.getProfNodeConnection().getProfile();
        for (final org.libertaria.world.profile_server.engine.app_services.AppService service : profile.getApplicationServices().values()) {
            addApplicationService(service);
        }
    }


    public void searchProfileByName(String name, org.libertaria.world.profile_server.engine.listeners.ProfSerMsgListener<List<IopProfileServer.ProfileQueryInformation>> listener) {
        profSerEngine.searchProfileByName(name,listener);
    }

    public SearchMessageFuture<List<IopProfileServer.ProfileQueryInformation>> searchProfiles(org.libertaria.world.profile_server.engine.SearchProfilesQuery searchProfilesQuery) {
        SearchMessageFuture<List<IopProfileServer.ProfileQueryInformation>> future = new SearchMessageFuture<>(searchProfilesQuery);
        profSerEngine.searchProfiles(searchProfilesQuery,future);
        return future;
    }

    public org.libertaria.world.profile_server.engine.futures.SubsequentSearchMsgListenerFuture<List<IopProfileServer.ProfileQueryInformation>> searchSubsequentProfiles(org.libertaria.world.profile_server.engine.SearchProfilesQuery searchProfilesQuery) {
        org.libertaria.world.profile_server.engine.futures.SubsequentSearchMsgListenerFuture future = new org.libertaria.world.profile_server.engine.futures.SubsequentSearchMsgListenerFuture(searchProfilesQuery);
        profSerEngine.searchSubsequentProfiles(searchProfilesQuery,future);
        return future;
    }

    public int updateProfile(Version version, String name, byte[] img, int latitude, int longitude, String extraData, org.libertaria.world.profile_server.engine.listeners.ProfSerMsgListener<Boolean> msgListener) {
        if (name!=null)
            profileCache.setName(name);
        if (version!=null)
            profileCache.setVersion(version);
        if (img!=null)
            profileCache.setImg(img);
        return profSerEngine.updateProfile(
                version,
                name,
                img,
                profileCache.getImgHash(),
                latitude,
                longitude,
                extraData,
                msgListener
        );
    }

    public int updateProfile(String name, byte[] img, String extraData, org.libertaria.world.profile_server.engine.listeners.ProfSerMsgListener msgListener){
        return updateProfile(profileCache.getVersion(),name,img,0,0,extraData,msgListener);
    }

    public org.libertaria.world.profile_server.model.Profile getProfile() {
        return profileCache;
    }

    /**
     * Method to check if the library is ready to use
     * @return
     */
    public boolean isReady() {
        return profSerEngine.isClConnectionReady();
    }

    /**
     * Method to check if the library is trying to stablish a connection with the node
     * @return
     */
    public boolean isConnecting() {
        return profSerEngine.isClConnectionConnecting();
    }

    /**
     * Method to check if the library fail on the connection
     * @return
     */
    public boolean hasFail() {
        return profSerEngine.hasClConnectionFail();
    }

    /**
     * Add more application services to an active profile
     * @param appService
     * @param appService
     */
    public void addApplicationService(org.libertaria.world.profile_server.engine.app_services.AppService appService) {
        profileCache.addApplicationService(appService);
        profSerEngine.addApplicationService(appService);
    }


    /**
     *
     *
     *
     * @param publicKey
     * @param msgProfFuture
     */
    public void getProfileInformation(String publicKey, org.libertaria.world.profile_server.engine.futures.MsgListenerFuture msgProfFuture) throws org.libertaria.world.profile_server.CantConnectException, CantSendMessageException {
        getProfileInformation(publicKey,false,false,false,msgProfFuture);
    }

    public void getProfileInformation(String publicKey, boolean includeApplicationServices, org.libertaria.world.profile_server.engine.listeners.ProfSerMsgListener msgProfFuture) throws org.libertaria.world.profile_server.CantConnectException, CantSendMessageException {
        getProfileInformation(publicKey,false,false,includeApplicationServices,msgProfFuture);
    }

    public void getProfileInformation(String publicKey, boolean includeProfileImage , boolean includeThumbnailImage, boolean includeApplicationServices, org.libertaria.world.profile_server.engine.listeners.ProfSerMsgListener msgProfFuture) throws org.libertaria.world.profile_server.CantConnectException, CantSendMessageException {
        profSerEngine.getProfileInformation(publicKey,includeProfileImage,includeThumbnailImage,includeApplicationServices,msgProfFuture);
    }

    /**
     * If this method is called is supposed that the service already have the ProfileInformation with the included application services
     *
     * @param remoteProfilePublicKey
     * @param appService
     * @param tryWithoutGetInfo -> if the redtooth knows the profile data there is no necesity to get the data again.
     */
    public void callProfileAppService(final String remoteProfilePublicKey, final String appService, boolean tryWithoutGetInfo, final boolean encryptMsg, final org.libertaria.world.profile_server.engine.listeners.ProfSerMsgListener<CallProfileAppService> profSerMsgListener) {
        logger.info("callProfileAppService from "+remoteProfilePublicKey+" using "+appService);
        org.libertaria.world.profile_server.ProfileInformation remoteProfInfo = new ProfileInformationImp(CryptoBytes.fromHexToBytes(remoteProfilePublicKey));
        final org.libertaria.world.profile_server.engine.app_services.CallProfileAppService callProfileAppService = new org.libertaria.world.profile_server.engine.app_services.CallProfileAppService(
                appService,
                profileCache,
                remoteProfInfo,
                profSerEngine,
                (encryptMsg)?new BoxAlgo():null
        );
        // wrap call in a Pairing call.
        profileCache.getAppService(appService).wrapCall(callProfileAppService);
        try {
            if (!tryWithoutGetInfo) {
                callProfileAppService.setStatus(org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.PENDING_AS_INFO);
                // first if the app doesn't have the profileInformation including the app services i have to request it.
                final org.libertaria.world.profile_server.engine.futures.MsgListenerFuture<IopProfileServer.GetProfileInformationResponse> getProfileInformationFuture = new org.libertaria.world.profile_server.engine.futures.MsgListenerFuture<>();
                getProfileInformationFuture.setListener(new org.libertaria.world.profile_server.engine.futures.BaseMsgFuture.Listener<IopProfileServer.GetProfileInformationResponse>() {
                    @Override
                    public void onAction(int messageId, IopProfileServer.GetProfileInformationResponse getProfileInformationResponse) {
                        logger.info("callProfileAppService getProfileInformation ok");
                        callProfileAppService.setStatus(org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.AS_INFO);
                        try {
                            // todo: check signature..
                            //getProfileInformationResponse.getSignedProfile().getSignature()
                            // todo: save this profile and it's services for future calls.
                            if (!getProfileInformationResponse.getIsOnline()) {
                                // remote profile not online.
                                // todo: launch notification and end the flow here
                                logger.info("call fail, remote is not online");
                                notifyCallError(
                                        callProfileAppService,
                                        profSerMsgListener,
                                        messageId,
                                        org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,
                                        "Remote profile not online");
                                return;
                            }
                            boolean isServiceSupported = false;
                            for (String supportedAppService : getProfileInformationResponse.getApplicationServicesList()) {
                                // add available services to the profile
                                callProfileAppService.getRemoteProfile().addAppService(supportedAppService);
                                if (supportedAppService.equals(appService)) {
                                    logger.info("callProfileAppService getProfileInformation -> profile support app service");
                                    isServiceSupported = true;
                                    break;
                                }
                            }
                            if (!isServiceSupported) {
                                // service not supported
                                // todo: launch notification and end the flow here
                                logger.info("call fail, remote not support appService");
                                notifyCallError(
                                        callProfileAppService,
                                        profSerMsgListener,
                                        messageId,
                                        org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,
                                        "Remote profile not accept service " + appService);
                                return;
                            }
                            // load profile with data if there is any
                            IopProfileServer.ProfileInformation protocProfile = getProfileInformationResponse.getSignedProfile().getProfile();
                            org.libertaria.world.profile_server.ProfileInformation remoteProfile = callProfileAppService.getRemoteProfile();
                            remoteProfile.setImg(getProfileInformationResponse.getProfileImage().toByteArray());
                            remoteProfile.setThumbnailImg(getProfileInformationResponse.getThumbnailImage().toByteArray());
                            remoteProfile.setLatitude(protocProfile.getLatitude());
                            remoteProfile.setLongitude(protocProfile.getLongitude());
                            remoteProfile.setExtraData(protocProfile.getExtraData());
                            remoteProfile.setVersion(Version.fromByteArray(protocProfile.getVersion().toByteArray()));
                            remoteProfile.setType(protocProfile.getType());
                            remoteProfile.setName(protocProfile.getName());
                            // call profile
                            callProfileAppService(callProfileAppService,profSerMsgListener);
                        } catch (CantSendMessageException e) {
                            e.printStackTrace();
                            notifyCallError(callProfileAppService,profSerMsgListener,messageId, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,e.getMessage());
                        } catch (org.libertaria.world.profile_server.CantConnectException e) {
                            e.printStackTrace();
                            notifyCallError(callProfileAppService,profSerMsgListener,messageId, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,e.getMessage());
                        } catch (CallProfileAppServiceException e) {
                            e.printStackTrace();
                            notifyCallError(callProfileAppService,profSerMsgListener,messageId, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,e.getMessage());
                        }
                    }

                    @Override
                    public void onFail(int messageId, int status, String statusDetail) {
                        // todo: launch notification..
                        logger.info("callProfileAppService getProfileInformation fail");
                        notifyCallError(callProfileAppService,profSerMsgListener,messageId, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,statusDetail);

                    }
                });
                getProfileInformation(remoteProfilePublicKey, true, getProfileInformationFuture);
            }else {
                callProfileAppService(callProfileAppService,profSerMsgListener);
            }
        } catch (CantSendMessageException e) {
            e.printStackTrace();
            notifyCallError(callProfileAppService,profSerMsgListener,0, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,e.getMessage());
        } catch (org.libertaria.world.profile_server.CantConnectException e) {
            e.printStackTrace();
            notifyCallError(callProfileAppService,profSerMsgListener,0, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,e.getMessage());
        } catch (CallProfileAppServiceException e) {
            e.printStackTrace();
            notifyCallError(callProfileAppService,profSerMsgListener,0, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,e.getMessage());
        }
    }

    /**
     *
     * @param callProfileAppService
     * @throws org.libertaria.world.profile_server.CantConnectException
     * @throws CantSendMessageException
     */
    private synchronized void callProfileAppService(final org.libertaria.world.profile_server.engine.app_services.CallProfileAppService callProfileAppService, final org.libertaria.world.profile_server.engine.listeners.ProfSerMsgListener<CallProfileAppService> profSerMsgListener) throws org.libertaria.world.profile_server.CantConnectException, CantSendMessageException, CallProfileAppServiceException {
        // call profile
        logger.info("callProfileAppService call profile");
        // check if the call already exist
        for (org.libertaria.world.profile_server.engine.app_services.CallProfileAppService call : openCall.values()) {
            if (call.getAppService().equals(callProfileAppService.getAppService())
                    &&
                    call.getRemoteProfile().getHexPublicKey().equals(callProfileAppService.getRemotePubKey())){
                // if the call don't answer this call intention.
                logger.info("callProfileAppService, call already exist. blocking this request");
                profSerMsgListener.onMsgFail(0,400,"Call already exists");
                throw new CallProfileAppServiceException("Call already exists");
            }
        }

        callProfileAppService.setStatus(org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.PENDING_CALL_AS);
        final org.libertaria.world.profile_server.engine.futures.MsgListenerFuture<IopProfileServer.CallIdentityApplicationServiceResponse> callProfileFuture = new org.libertaria.world.profile_server.engine.futures.MsgListenerFuture<>();
        callProfileFuture.setListener(new org.libertaria.world.profile_server.engine.futures.BaseMsgFuture.Listener<IopProfileServer.CallIdentityApplicationServiceResponse>() {
            @Override
            public void onAction(int messageId, IopProfileServer.CallIdentityApplicationServiceResponse appServiceResponse) {
                logger.info("callProfileAppService accepted");
                try {
                    callProfileAppService.setCallToken(appServiceResponse.getCallerToken().toByteArray());
                    String callToken = CryptoBytes.toHexString(appServiceResponse.getCallerToken().toByteArray());
                    logger.info("Adding call, token: "+callToken);
                    callProfileAppService.addCallStateListener(IoPProfileConnection.this);
                    openCall.put(callToken,callProfileAppService);
                    // setup call app service
                    setupCallAppServiceInitMessage(callProfileAppService,true,profSerMsgListener);
                } catch (CantSendMessageException e) {
                    e.printStackTrace();
                    notifyCallError(callProfileAppService,profSerMsgListener,messageId, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,e.getMessage());
                } catch (org.libertaria.world.profile_server.CantConnectException e) {
                    e.printStackTrace();
                    notifyCallError(callProfileAppService,profSerMsgListener,messageId, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,e.getMessage());
                }
            }
            @Override
            public void onFail(int messageId, int status, String statusDetail) {
                logger.info("callProfileAppService rejected, "+statusDetail);
                notifyCallError(callProfileAppService,profSerMsgListener,messageId, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_FAIL,statusDetail);
            }
        });
        profSerEngine.callProfileAppService(callProfileAppService.getRemotePubKey(), callProfileAppService.getAppService(), callProfileFuture);
    }

    private void notifyCallError(org.libertaria.world.profile_server.engine.app_services.CallProfileAppService callProfileAppService, org.libertaria.world.profile_server.engine.listeners.ProfSerMsgListener<CallProfileAppService> listener, int msgId, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status status, String errorStatus){
        callProfileAppService.setStatus(status);
        callProfileAppService.setErrorStatus(errorStatus);
        listener.onMessageReceive(msgId,callProfileAppService);
    }


    @Override
    public synchronized void incomingCallNotification(int messageId, IopProfileServer.IncomingCallNotificationRequest message) {
        logger.info("incomingCallNotification");
        try {
            // todo: launch notification to accept the incoming call here.
            //String remotePubKey = CryptoBytes.toHexString(message.getCallerPublicKey().toByteArray());
            String callToken = CryptoBytes.toHexString(message.getCalleeToken().toByteArray());
            org.libertaria.world.profile_server.ProfileInformation remoteProfInfo = new ProfileInformationImp(message.getCallerPublicKey().toByteArray());

            // check if the call exist (if the call exist don't accept it and close the channel)
            for (org.libertaria.world.profile_server.engine.app_services.CallProfileAppService callProfileAppService : openCall.values()) {
                if (callProfileAppService.getAppService().equals(message.getServiceName())
                        &&
                        callProfileAppService.getRemoteProfile().getHexPublicKey().equals(remoteProfInfo.getHexPublicKey())){
                    // if the call don't answer this call intention.
                    logger.info("incomingCallNotification, call already exist. don't answering call intention");
                    return;
                }
            }

            final org.libertaria.world.profile_server.engine.app_services.CallProfileAppService callProfileAppService = new org.libertaria.world.profile_server.engine.app_services.CallProfileAppService(message.getServiceName(), profileCache, remoteProfInfo,profSerEngine);
            callProfileAppService.setCallToken(message.getCalleeToken().toByteArray());

            // accept every single call
            logger.info("Adding call, token: "+callToken);
            callProfileAppService.addCallStateListener(this);
            openCall.put(callToken, callProfileAppService);
            profSerEngine.acceptCall(messageId);

            // init setup call message
            setupCallAppServiceInitMessage(callProfileAppService,false,new org.libertaria.world.profile_server.engine.listeners.ProfSerMsgListener<CallProfileAppService>() {
                @Override
                public void onMessageReceive(int messageId, org.libertaria.world.profile_server.engine.app_services.CallProfileAppService message) {
                    // once everything is correct, launch notification
                    org.libertaria.world.profile_server.engine.app_services.AppService appService = profileCache.getAppService(message.getAppService());
                    appService.wrapCall(callProfileAppService);
                    appService.onCallConnected(profileCache,message.getRemoteProfile(),message.isCallCreator());
                }

                @Override
                public void onMsgFail(int messageId, int statusValue, String details) {
                    logger.info("setupCallAppServiceInitMessage init message fail, "+details);
                }

                @Override
                public String getMessageName() {
                    return "setupCallAppServiceInitMessage";
                }
            });
        } catch (CantSendMessageException e) {
            e.printStackTrace();
        } catch (org.libertaria.world.profile_server.CantConnectException e) {
            e.printStackTrace();
        }
    }

    private void setupCallAppServiceInitMessage(final org.libertaria.world.profile_server.engine.app_services.CallProfileAppService callProfileAppService, final boolean isRequester , final org.libertaria.world.profile_server.engine.listeners.ProfSerMsgListener<CallProfileAppService> profSerMsgListener) throws org.libertaria.world.profile_server.CantConnectException, CantSendMessageException {
        callProfileAppService.setStatus(org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.PENDING_INIT_MESSAGE);
        // send init message to setup the call
        final org.libertaria.world.profile_server.engine.futures.MsgListenerFuture<IopProfileServer.ApplicationServiceSendMessageResponse> initMsgFuture = new org.libertaria.world.profile_server.engine.futures.MsgListenerFuture<>();
        initMsgFuture.setListener(new org.libertaria.world.profile_server.engine.futures.BaseMsgFuture.Listener<IopProfileServer.ApplicationServiceSendMessageResponse>() {
            @Override
            public void onAction(int messageId, IopProfileServer.ApplicationServiceSendMessageResponse object) {
                try {
                    logger.info("callProfileAppService setup message accepted");
                    callProfileAppService.setStatus(org.libertaria.world.profile_server.engine.app_services.CallProfileAppService.Status.CALL_AS_ESTABLISH);
                    if (callProfileAppService.isEncrypted() && isRequester) {
                        encryptCall(callProfileAppService, profSerMsgListener);
                    } else {
                        profSerMsgListener.onMessageReceive(messageId, callProfileAppService);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("callProfileAppService crypto message fail");
                    profSerMsgListener.onMsgFail(messageId,400,e.getMessage());
                }
            }
            @Override
            public void onFail(int messageId, int status, String statusDetail) {
                logger.info("callProfileAppService init message fail, "+statusDetail);
                profSerMsgListener.onMsgFail(messageId,status,statusDetail);
            }
        });
        profSerEngine.sendAppServiceMsg(callProfileAppService.getCallToken(), null, initMsgFuture);
    }

    /**
     * Encrypt call with the default algorithm for now
     */
    private void encryptCall(final org.libertaria.world.profile_server.engine.app_services.CallProfileAppService callProfileAppService, final org.libertaria.world.profile_server.engine.listeners.ProfSerMsgListener<CallProfileAppService> profSerMsgListener) throws Exception {
        org.libertaria.world.profile_server.engine.app_services.CryptoMsg cryptoMsg = new org.libertaria.world.profile_server.engine.app_services.CryptoMsg("box");
        org.libertaria.world.profile_server.engine.futures.MsgListenerFuture<Boolean> cryptoFuture = new org.libertaria.world.profile_server.engine.futures.MsgListenerFuture<Boolean>();
        cryptoFuture.setListener(new org.libertaria.world.profile_server.engine.futures.BaseMsgFuture.Listener<Boolean>() {
            @Override
            public void onAction(int messageId, Boolean object) {
                logger.info("Encrypt call sent..");
                profSerMsgListener.onMessageReceive(messageId, callProfileAppService);
            }

            @Override
            public void onFail(int messageId, int status, String statusDetail) {
                logger.info("callProfileAppService crypto message fail, "+statusDetail);
                profSerMsgListener.onMsgFail(messageId,status,statusDetail);
            }
        });
        callProfileAppService.sendMsg(cryptoMsg,cryptoFuture);
    }

    @Override
    public void incomingAppServiceMessage(int messageId, AppServiceMsg message) {
        logger.info("incomingAppServiceMessage");
        //logger.info("msg arrived! -> "+message.getMsg());
        // todo: Como puedo saber a donde deberia ir este mensaje..
        // todo: una vez sabido a qué openCall vá, la open call debe tener un listener de los mensajes entrantes (quizás una queue) y debe ir respondiendo con el ReceiveMessageNotificationResponse
        // todo: para notificar al otro lado que todo llegó bien.

        // todo: por alguna razon llega un mensaje para una llamada la cual no tiene listener asociado.. esto no deberia pasar.
        logger.info("Open calls keys: "+Arrays.toString(openCall.keySet().toArray()));
        logger.info("Open calls "+Arrays.toString(openCall.values().toArray()));
        if (openCall.containsKey(message.getCallTokenId())){
            // launch notification
            openCall.get(message.getCallTokenId()).onMessageReceived(message.getMsg());
            // now report the message received to the counter party
            try {
                profSerEngine.respondAppServiceReceiveMsg(message.getCallTokenId(),messageId);
            } catch (CantSendMessageException e) {
                e.printStackTrace();
                logger.warn("cant send responseAppServiceMsgReceived for msg id: "+message);
            } catch (org.libertaria.world.profile_server.CantConnectException e) {
                logger.warn("cant connect and send responseAppServiceMsgReceived for msg id: "+message);
                e.printStackTrace();
            }
        }else {
            logger.warn("incomingAppServiceMessage -> openCall not found",message);
        }
    }

    public org.libertaria.world.profile_server.engine.app_services.CallProfileAppService getActiveAppCallService(String remoteProfileKey){
        for (org.libertaria.world.profile_server.engine.app_services.CallProfileAppService callProfileAppService : openCall.values()) {
            if (callProfileAppService.getRemotePubKey().equals(remoteProfileKey)){
                return callProfileAppService;
            }
        }
        return null;
    }


    @Override
    public void onCallFinished(org.libertaria.world.profile_server.engine.app_services.CallProfileAppService callProfileAppService) {
        try {
            openCall.remove(CryptoBytes.toHexString(callProfileAppService.getCallToken()));
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}