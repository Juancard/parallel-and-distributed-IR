package Controller;

import java.io.*;
import java.util.HashMap;

import Common.Socket.SocketConnection;
import Model.Query;
import com.jcraft.jsch.*;

public class GpuServerHandler {
	private static final String INDEX = "IND";
	private static final String EVALUATE = "EVA";
    private final String username;
    private final String pass;
    private final int sshPort;
    private final String indexPath;
    private final String irIndexPath;
    private final String postingsFileName;
    private final String documentsNormFileName;
    private int port;
	private String host;

	public GpuServerHandler(
            String host,
            int port,
            String username,
            String pass,
            int sshPort,
            String gpuIndexPath,
            String irIndexPath,
            String documentsNormFileName,
            String postingsFileName
    ) {
		this.host = host;
		this.port = port;
        this.username = username;
        this.pass = pass;
        this.sshPort = sshPort;
        this.indexPath = gpuIndexPath;
        this.irIndexPath = irIndexPath;
        this.documentsNormFileName = documentsNormFileName;
        this.postingsFileName = postingsFileName;
	}

	public HashMap<Integer, Double> sendQuery(Query query) throws IOException {
        this.out("Connecting to Gpu server at " + this.host + ":" + this.port);
        SocketConnection connection = this.connect();
		DataOutputStream out = new DataOutputStream(connection.getSocketOutput());
        DataInputStream in = new DataInputStream(connection.getSocketInput());

        String qStr = query.toSocketString();
        this.out("Sending query: " + qStr);
        out.writeInt(EVALUATE.length());
		out.writeBytes(EVALUATE);
		out.writeInt(qStr.length());
		out.writeBytes(qStr);

        this.out("Receiving documents scores...");
        HashMap<Integer, Double> docsScore = new HashMap<Integer, Double>();
        int docs = in.readInt();
        int doc, weightLength;
        String weightStr;
        byte [] weightBytes = null;
        for (int i=0; i<docs; i++){
            doc = in.readInt();
            weightLength = in.readInt();

            weightBytes = new byte[weightLength];    // Se le da el tamaño
            in.read(weightBytes, 0, weightLength);   // Se leen los bytes
            weightStr = new String (weightBytes); // Se convierten a String

            docsScore.put(doc, new Double(weightStr));
        }

        this.out("Closing connection with Gpu Server");
        connection.close();

        return docsScore;
	}

	public boolean loadIndexInGpu() throws IOException{
        this.out("Connecting to Gpu server at " + this.host + ":" + this.port);
		SocketConnection connection = this.connect();
        DataOutputStream out = new DataOutputStream(connection.getSocketOutput());
        DataInputStream in = new DataInputStream(connection.getSocketInput());

        this.out("Sending index to Gpu");
        out.writeInt(INDEX.length());
		out.writeBytes(INDEX);
		int result = in.readInt();

        this.out("Closing connection with Gpu Server");
        connection.close();
        return result == 1;
	}

    public void sendIndex() throws JSchException, SftpException, FileNotFoundException {
        this.out("Setting up secure connection to Gpu Server");
        JSch jsch = new JSch();
        Session session = jsch.getSession(this.username, this.host,this.sshPort);
        session.setPassword(this.pass);
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        System.out.println("Connecting to Gpu Server");
        session.connect();
        this.out("Opening sftp channel");
        Channel channel = session.openChannel("sftp");
        channel.connect();
        ChannelSftp channelSftp = (ChannelSftp)channel;
        channelSftp.cd(this.indexPath);
        File postingsFile = new File(this.irIndexPath + this.postingsFileName);
        File docsNormFile = new File(this.irIndexPath + this.documentsNormFileName);
        this.out("Sending index: transfering postings");
        channelSftp.put(new FileInputStream(postingsFile), postingsFile.getName());
        this.out("Sending index: transfering documents norm");
        channelSftp.put(new FileInputStream(docsNormFile), docsNormFile.getName());
    }


    private SocketConnection connect() throws IOException {
        return new SocketConnection(host, port);
    }

    private void out(String m){
        System.out.println(m);
    }

    @Override
    public String toString() {
        return "GpuServerHandler{" +
                "username='" + username + '\'' +
                ", pass='" + pass + '\'' +
                ", sshPort=" + sshPort +
                ", indexPath='" + indexPath + '\'' +
                ", irIndexPath='" + irIndexPath + '\'' +
                ", postingsFileName='" + postingsFileName + '\'' +
                ", documentsNormFileName='" + documentsNormFileName + '\'' +
                ", port=" + port +
                ", host='" + host + '\'' +
                '}';
    }
}
