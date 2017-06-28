package org.fermat.redtooth.profile_server.client;


import com.google.protobuf.InvalidProtocolBufferException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;
import org.fermat.redtooth.profile_server.CantSendMessageException;
import org.fermat.redtooth.profile_server.IoSession;
import org.fermat.redtooth.profile_server.protocol.IopProfileServer;

/**
 * Created by mati on 08/11/16.
 */

public class ProfileServerSocket implements IoSession<IopProfileServer.Message> {

    private static final Logger logger = LoggerFactory.getLogger(ProfileServerSocket.class);
    /** socket id */
    private String tokenId;
    private int port;
    private String host;
    /** Server role type */
    private IopProfileServer.ServerRoleType portType;
    /** Socket factory */
    private SocketFactory socketFactory;
    /** Blocking Socket */
    private Socket socket;
    /** Handler */
    private PsSocketHandler<IopProfileServer.Message> handler;
    /** Reader thread */
    private Thread readThread;

    public ProfileServerSocket(SocketFactory socketFactory, String host, int port,IopProfileServer.ServerRoleType portType) throws Exception {
        this.socketFactory = socketFactory;
        if (port<=0) throw new IllegalArgumentException(portType+" port is 0");
        this.port = port;
        this.host = host;
        this.portType = portType;
    }

    public ProfileServerSocket(SocketFactory socketFactory, String host, int port,IopProfileServer.ServerRoleType portType,String tokenId) throws Exception {
        this(socketFactory,host,port,portType);
        this.tokenId = tokenId;
    }

    public void connect() throws IOException {
        if ((socket!=null && readThread!=null) && (socket.isConnected() || readThread.isAlive())) throw new IllegalStateException("ProfileServerSocket is running");
        logger.info("connect: "+host+", port "+port);
        this.socket = socketFactory.createSocket(host,port);
        readThread = new Thread(new Reader(),"Thread-reader-host-"+host+"-port-"+port);
        readThread.start();
        handler.portStarted(portType);
    }

    public void setHandler(PsSocketHandler<IopProfileServer.Message> handler) {
        this.handler = handler;
    }

    @Override
    public String getSessionTokenId() {
        return tokenId;
    }

    public void write(IopProfileServer.Message message) throws CantSendMessageException {
        try {
            int messageSize = message.toByteArray().length;
            IopProfileServer.MessageWithHeader messageWithHeaderBuilder = IopProfileServer.MessageWithHeader.newBuilder()
                    .setHeader(messageSize+computeProtocolOverhead(messageSize))
                    .setBody(message)
                    .build();
            byte[] messageToSend = messageWithHeaderBuilder.toByteArray();
            logger.info("Message lenght to send: "+messageToSend.length+", Message lenght in the header: "+messageWithHeaderBuilder.getHeader());
            socket.getOutputStream().write(messageToSend);
            socket.getOutputStream().flush();
            handler.messageSent(this,message);
        }catch (Exception e){
            throw new CantSendMessageException(e);
        }
    }

    private int computeProtocolOverhead(int lenght){
        if (lenght<0) throw new IllegalArgumentException("lenght < 0");
        int overhead = 0;
        if (lenght<=127){
            // 1 byte overhead + 1 byte type
            overhead = 2;
        }else if (lenght<=16383){
            // 2 byte  overhead + 1 byte type
            overhead = 3;
        } else{
            // 3 byte overhead + 1 byte type
            overhead = 4;
        }
        return overhead;
    }

    private synchronized void read(){
        int count;
        byte[] buffer = new byte[8192];
        try {
            // read reply
            if(!socket.isInputShutdown()) {
                count = socket.getInputStream().read(buffer);
                logger.info("Reciving data..");
                IopProfileServer.MessageWithHeader message1 = null;
                if (count > 0) {
                    ByteBuffer byteBufferToRead = ByteBuffer.allocate(count);
                    byteBufferToRead.put(buffer, 0, count);
                    message1 = IopProfileServer.MessageWithHeader.parseFrom(byteBufferToRead.array());
                    handler.messageReceived(this, message1.getBody());
                } else {
                    // read < 0 -> connection closed
                    // todo: falta notificar a las capas superiores que se cerró el socket.
                    logger.info("Connection closed, read<0 with portType: " + portType + " , removing socket");
                    socket.close();
                    readThread.interrupt();
                }
            }else {
                // input stream closed
                // todo: falta notificar a las capas superiores que se cerró el socket.
                logger.info("Connection closed, input stream shutdown with portType: " + portType + " , removing socket");
                socket.close();
                readThread.interrupt();
            }
        } catch (InvalidProtocolBufferException e) {
//                throw new InvalidProtocolViolation("Invalid message",e);
            e.printStackTrace();
        } catch (javax.net.ssl.SSLException e){
            e.printStackTrace();
            // something bad happen..
            // todo: falta notificar a las capas superiores que se cerró el socket.
            logger.info("Connection closed, sslException with portType: " + portType + " , "+tokenId+" removing socket");
            try {
                socket.close();
            } catch (IOException e1) {
                // nothing
            }
            try {
                readThread.interrupt();
            }catch (Exception e2){
                // nothing
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public IopProfileServer.ServerRoleType getPortType() {
        return portType;
    }

    @Override
    public void closeNow() throws IOException {
        logger.info("Closing socket port: "+portType);
        readThread.interrupt();
        socket.close();

    }

    @Override
    public boolean isActive() {
        return !socket.isOutputShutdown();
    }

    @Override
    public boolean isConnected() {
        return socket.isConnected();
    }

    @Override
    public Socket getChannel() {
        return socket;
    }

    @Override
    public boolean isReadSuspended() {
        return false;
    }

    @Override
    public boolean isWriteSuspended() {
        return false;
    }


    private class Reader implements Runnable{

        @Override
        public void run() {
            try {
                logger.info("Reader started for: "+port);

                for (;;) {
                    if (!socket.isClosed()) {
                        read();

                        if (!Thread.interrupted()) {
                            TimeUnit.SECONDS.sleep(1);
                        }
                    }else {
                        if (!Thread.currentThread().isInterrupted())
                            Thread.currentThread().interrupt();
                    }
                }
            } catch (InterruptedException e){
                // this happen when the thread is sleep and someone interrupt it.
                logger.info("InterruptedException on port: "+port);
            } catch (Exception e){
                logger.info("Exception on port: "+port);
                e.printStackTrace();
            }
        }
    }

    @Override
    public String toString() {
        return "ProfileServerSocket{" +
                "tokenId='" + tokenId + '\'' +
                ", port=" + port +
                ", host='" + host + '\'' +
                ", portType=" + portType +
                '}';
    }
}
