package Controller.IndexerHandler;

import Common.Socket.SocketConnection;

import java.io.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: juan
 * Date: 01/07/17
 * Time: 14:40
 */
public class PythonIndexer {
    // classname for the logger
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private final static String REQUEST_INDEX = "IND";
    private final static String RESPONSE_INDEX_SUCCESS = "OK";
    private final static String RESPONSE_INDEX_FAIL = "NOK";

    private String corpusPath;
    private String indexerScript;

    String host;
    int port;
    private boolean isScript;
    private boolean isSocket;

    public PythonIndexer(
            String host,
            int port,
            String corpusPath
    ) {
        this.host = host;
        this.port = port;
        this.corpusPath = corpusPath;
        this.isScript = false;
        this.isSocket = true;
    }

    public PythonIndexer(
            String corpus,
            String indexerScript
    ) {
        this.corpusPath = corpus;
        this.indexerScript = indexerScript;
        this.isSocket = false;
        this.isScript = true;
    }

    public synchronized boolean callScriptIndex() throws IOException {
        List<String> command = new ArrayList<String>();
        command.add("python");
        command.add(this.indexerScript);
        command.add(this.corpusPath);

        SystemCommandExecutor commandExecutor = new SystemCommandExecutor(command);
        try {
            int result = commandExecutor.executeCommand();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // get the output from the command
        StringBuilder stdout = commandExecutor.getStandardOutputFromCommand();
        StringBuilder stderr = commandExecutor.getStandardErrorFromCommand();

        // print the output from the command
        if (stdout.length() > 0)
            System.out.println(stdout);
        if (stderr.length() > 0) {
            String[] errors = stderr.toString().split("\n");
            String exceptions = "";
            for (String err : errors) {
                if (err.startsWith("WARNING"))
                    LOGGER.warning(err);
                else {
                    exceptions += err;
                }
            }
            if (!exceptions.isEmpty())
                throw new IOException(exceptions);
        }
        return true;
    }

    public synchronized boolean indexViaSocket() throws IOException {
        SocketConnection sc = null;
        try {
            sc = new SocketConnection(host, port);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            throw new IOException("Could not connect to indexer host. Cause: " + e.getMessage());
        }
        sc.writeInt("IND".length());
        sc.writeBytes("IND");
        sc.writeInt(this.corpusPath.length());
        sc.writeBytes(this.corpusPath);

        int msgLength = sc.readInt();
        String status = sc.readString(msgLength);
        if (status == RESPONSE_INDEX_FAIL){
            msgLength = sc.readInt();
            String errorMsg = sc.readString(msgLength);
            throw new IOException("At Indexer host: '" + errorMsg + "'");
        }
        return status.equals(this.RESPONSE_INDEX_SUCCESS);
    }
}
