package org.libertaria.world.locnet;

import org.libertaria.world.global.GpsLocation;
import org.libertaria.world.locnet.protocol.IopLocNet;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;



public class Converter
{
    static final float GPS_COORDINATE_PROTOBUF_INT_MULTIPLIER = 1000000.f;


    public static GpsLocation fromProtoBuf(IopLocNet.GpsLocation location)
    {
        return new GpsLocation(
            location.getLatitude() / GPS_COORDINATE_PROTOBUF_INT_MULTIPLIER,
            location.getLongitude() / GPS_COORDINATE_PROTOBUF_INT_MULTIPLIER );
    }


    public static NodeInfo.Contact fromProtoBuf(IopLocNet.NodeContact contact) throws UnknownHostException
    {
        byte[] byteAddress = contact.getIpAddress().toByteArray();
        InetAddress address = null;

        if (byteAddress.length > 0)
            address = InetAddress.getByAddress(byteAddress);

        return new NodeInfo.Contact(address,
            contact.getClientPort() );
    }


    public static NodeInfo.ServiceType fromProtoBuf(String type) throws UnknownHostException
    {
        switch (type)
        {
            case "Content":       return NodeInfo.ServiceType.Content;
            case "Latency":       return NodeInfo.ServiceType.Latency;
            case "Location":      return NodeInfo.ServiceType.Location;
            case "Minting":       return NodeInfo.ServiceType.Minting;
            case "Profile":       return NodeInfo.ServiceType.Profile;
            case "Proximity":     return NodeInfo.ServiceType.Proximity;
            case "Relay":         return NodeInfo.ServiceType.Relay;
            case "Reputation":    return NodeInfo.ServiceType.Reputation;
            case "Token":         return NodeInfo.ServiceType.Unstructured;
            case "Unstructured":  return NodeInfo.ServiceType.Unstructured;
            default: throw new IllegalArgumentException("Not implemented for unknown enum value: " + type);
        }
    }


    public static NodeInfo fromProtoBuf(IopLocNet.NodeInfo node) throws UnknownHostException
    {
        List<NodeInfo.ServiceInfo> services = new ArrayList();
        for ( IopLocNet.ServiceInfo info : node.getServicesList() )
        {
            services.add( new NodeInfo.ServiceInfo(
                fromProtoBuf( info.getType() ),
                info.getPort(),
                info.getServiceData().toByteArray() ) );
        }

        IopLocNet.NodeContact contact = node.getContact();

        if (contact.getIpAddress().size() == 0)
            return null;

        return new NodeInfo(
            node.getNodeId().toByteArray(),
            fromProtoBuf(  contact ),
            fromProtoBuf( node.getLocation() ),
            services );
    }


    public static List<NodeInfo> fromProtoBuf(List<IopLocNet.NodeInfo> nodes) throws UnknownHostException
    {
        ArrayList<NodeInfo> result = new ArrayList<>();
        for (IopLocNet.NodeInfo node : nodes) {
                NodeInfo nodeConvert = Converter.fromProtoBuf(node);

                if (nodeConvert != null)
                    result.add(  nodeConvert );
        }
        return result;
    }


    public static IopLocNet.GpsLocation toProtoBuf(GpsLocation location)
    {
        return IopLocNet.GpsLocation.newBuilder()
            .setLatitude(  (int)(location.getLatitude()  * GPS_COORDINATE_PROTOBUF_INT_MULTIPLIER) )
            .setLongitude( (int)(location.getLongitude() * GPS_COORDINATE_PROTOBUF_INT_MULTIPLIER) )
            .build();
    }
}
