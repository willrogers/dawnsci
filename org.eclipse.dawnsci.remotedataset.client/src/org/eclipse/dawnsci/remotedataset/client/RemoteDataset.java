package org.eclipse.dawnsci.remotedataset.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.eclipse.dawnsci.analysis.api.dataset.DataEvent;
import org.eclipse.dawnsci.analysis.api.dataset.DataListenerDelegate;
import org.eclipse.dawnsci.analysis.api.dataset.IDataListener;
import org.eclipse.dawnsci.analysis.api.dataset.IRemoteDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.AbstractDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.LazyWriteableDataset;
import org.eclipse.jetty.websocket.WebSocket.Connection;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketClient;
import org.eclipse.jetty.websocket.WebSocketClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class manages a remote connection to data and
 * allows datasets to work seamlessly remotely as if
 * they were ILazyDatasets.
 * 
 * To do this they require to know the data server's name and port.
 * They also need a path on the server for the file which they are linked to.
 * 
 * The DataServer is a standard HTTP with web sockets for events. This makes
 * RemoteDataset able to pass over HTTP with port 80 open for external data 
 * viewing too.
 * 
 * 
<usage><code>
final IRemoteDatasetService service = ...
final IRemoteDataset data = service.createRemoteDataset("localhost", 8080);<br>
data.setPath(h5File.getAbsolutePath());
data.setDataset("image"); // We just get the first image in the PNG file.
data.connect();

try {
    // Use it the same way as ILazyDataset
} finally {
    data.disconnect();
}
</code></usage>
 * 
 * @author Matthew Gerring
 *
 */
class RemoteDataset extends LazyWriteableDataset implements IRemoteDataset {
	
	private static final Logger logger = LoggerFactory.getLogger(RemoteDataset.class);
	
	private final URLBuilder urlBuilder;
	
	// Web socket stuff
	private Connection connection;
    private DataListenerDelegate eventDelegate;

	private boolean dynamicShape = true;
	private int[] transShape;

	/**
	 * 
	 */
	private static final long serialVersionUID = -9031675045219778735L;

	/**
	 * 
		<usage><code>
		final IRemoteDatasetService service = ...
		final IRemoteDataset data = service.createRemoteDataset("localhost", 8080);<br>
		data.setPath(h5File.getAbsolutePath());<br>
		data.setDataset("image"); // We just get the first image in the PNG file.<br>
		data.connect();<br>
		<br>
		try {<br>
		    // Use it the same way as ILazyDataset<br>
		} finally {<br>
		    data.disconnect();<br>
		}<br>
		</code></usage>
	 * @param serverName
	 * @param port
	 */
	public RemoteDataset(String serverName, int port) {
		super("unknown", Dataset.INT, new int[]{1}, new int[]{-1}, null, null);
		this.urlBuilder = new URLBuilder(serverName, port);
		this.eventDelegate = new DataListenerDelegate();
	}
	
	/**
	 * Call to read the dataset, set current shape and create event connnection for
	 * IDynamicDataset part of the dataset
	 */
    public void connect() throws Exception {
    	
		this.loader = new RemoteLoader(urlBuilder);
		createInfo();
		createFileListener();
    }
    
    public void disconnect() throws Exception {
        if (connection.isOpen()) {
        	connection.sendMessage("Disconnected from "+urlBuilder.getPath());
       	    connection.close();
        }
       	// TODO Close loader as well?    
    }
	
	private void createFileListener() throws Exception {
		
        URI uri = URI.create(urlBuilder.getEventURL());

        WebSocketClientFactory factory = new WebSocketClientFactory();
        factory.start();
        WebSocketClient        client = new WebSocketClient(factory);

        final DataEventSocket clientSocket = new DataEventSocket();
        // Attempt Connect
        Future<Connection> fut = client.open(uri, clientSocket);

        // Wait for Connect
        connection = fut.get();

        // Send a message
        connection.sendMessage("Connected to "+urlBuilder.getPath());
	}
	
	private class DataEventSocket implements WebSocket, WebSocket.OnTextMessage {

		@Override
		public void onOpen(Connection connection) {
			logger.debug(getClass()+" opened");
		}

		@Override
		public void onClose(int closeCode, String message) {
			logger.debug(getClass()+" closed");
		}

		@Override
		public synchronized void onMessage(String data) {	
			try {
				DataEvent evt = DataEvent.decode(data);
				if (evt.getShape()!=null) {
					if (dynamicShape) {
						resize(evt.getShape());
						setMax(evt.getShape());
						
						eventDelegate.fire(evt);
					} else {
						RemoteDataset.this.transShape =  evt.getShape();
					}
				}
			} catch (Exception ne) {
				logger.error("Cannot set shape of dataset!", ne);
			}
		}
	}
	
	public void setShapeDynamic(boolean isDyn) {
		dynamicShape  = isDyn;
		if (dynamicShape && transShape!=null) {
		    this.shape = transShape;
		    setMax(shape);
		    transShape = null;
		}
	}

	private void setMax(int[] shape) {
		int[] max = new int[shape.length];
		for (int i = 0; i < max.length; i++) max[i] = -1;
		setMaxShape(max);
	}

	private void createInfo() throws Exception {
		
		List<String> info = getInfo();
		
		this.name  = info.get(0);
		this.shape = toIntArray(info.get(1));
		setMax(shape);
		this.oShape = shape;
		this.dtype = Integer.parseInt(info.get(2));
		this.isize = Integer.parseInt(info.get(3));
		try {
			size = AbstractDataset.calcLongSize(shape);
		} catch (IllegalArgumentException e) {
			size = Long.MAX_VALUE; // this indicates that the entire dataset cannot be read in! 
		}

		if (info.size() > 4) {
			setMaxShape(toIntArray(info.get(4)));
			setChunking(toIntArray(info.get(5)));
		}
	}

	private int[] toIntArray(String array) {
		if (array.equals("null"))
			return null;

		final String[] split = array.substring(1, array.length()-1).split(",");
		final int[]    ret   = new int[split.length];
		for (int i = 0; i < split.length; i++) {
			ret[i] = Integer.parseInt(split[i].trim());
 		}
		return ret;
	}

	private List<String> getInfo() throws Exception {
		
		final List<String> ret = new ArrayList<String>();
		final URL url = new URL(urlBuilder.getInfoURL());
		URLConnection  conn = url.openConnection();

		final BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
		try {
			String line = null;
			while((line = reader.readLine())!=null) ret.add(line);
		} finally {
			reader.close();
		}
		return ret;
	}

	@Override
	public void addDataListener(IDataListener l) {
		eventDelegate.addDataListener(l);
	}

	@Override
	public void removeDataListener(IDataListener l) {
		eventDelegate.removeDataListener(l);
	}

	public String getPath() {
		return urlBuilder.getPath();
	}

	public void setPath(String path) {
		urlBuilder.setPath(path);
	}

	public String getDataset() {
		return urlBuilder.getDataset();
	}

	public void setDataset(String dataset) {
		urlBuilder.setDataset(dataset);
	}
}
