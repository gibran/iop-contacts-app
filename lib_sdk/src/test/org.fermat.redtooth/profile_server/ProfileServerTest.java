package org.fermat.redtooth.profile_server;

import org.junit.Test;
import org.libertaria.world.connection.DeviceNetworkConnection;
import org.libertaria.world.core.IoPConnect;
import org.libertaria.world.core.IoPConnectContext;
import org.libertaria.world.crypto.CryptoWrapper;
import org.libertaria.world.global.DeviceLocation;
import org.libertaria.world.global.GpsLocation;
import org.libertaria.world.locnet.Explorer;
import org.libertaria.world.locnet.NodeInfo;
import org.libertaria.world.profile_server.SslContextFactory;
import org.libertaria.world.profile_server.engine.MessageQueueManager;
import org.libertaria.world.profiles_manager.LocalProfilesDao;
import org.libertaria.world.profiles_manager.PairingRequestsManager;
import org.libertaria.world.profiles_manager.ProfilesManager;

import java.util.List;
import java.util.concurrent.FutureTask;

public class ProfileServerTest {

    @Test
    public void shouldBeGetListProfileServers() throws Exception {


        //        Explorer explorer = new Explorer( NodeInfo.ServiceType.Profile, new GpsLocation(-19.9017f, -43.9642f), 10000, 10 );
//        FutureTask< List<NodeInfo> > task = new FutureTask<>(explorer);
//        task.run();
//        List<NodeInfo> resultNodes = task.get();
//
        IoPConnectContext contextWrapper = null;
        CryptoWrapper cryptoWrapper = null;
        SslContextFactory sslContextFactory = null;
        LocalProfilesDao localProfilesDao = null;
        ProfilesManager profilesManager = null;
        PairingRequestsManager pairingRequestsManager = null;
        DeviceLocation deviceLocation = null;
        DeviceNetworkConnection deviceNetworkConnection = null;
        MessageQueueManager messageQueueManager = null;

        IoPConnect ioPConnect = new IoPConnect(contextWrapper,
                cryptoWrapper,
                sslContextFactory,
                localProfilesDao,
                profilesManager,
                pairingRequestsManager,
                deviceLocation,
                deviceNetworkConnection,
                messageQueueManager);

        List<NodeInfo> resultNodes =  ioPConnect.getProfileServers(-19.9017f, -43.9642f,10000, 10 );

        System.out.println("Found " + resultNodes.size() + " matching nodes");

        for (NodeInfo node : resultNodes)
            System.out.println("  " + node);

        assert resultNodes.size() > 0;
    }
}
