/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package plugins.MDNSDiscovery;

import java.io.IOException;

import plugins.MDNSDiscovery.javax.jmdns.JmDNS;
import plugins.MDNSDiscovery.javax.jmdns.ServiceEvent;
import plugins.MDNSDiscovery.javax.jmdns.ServiceInfo;
import plugins.MDNSDiscovery.javax.jmdns.ServiceListener;
import freenet.clients.http.PageMaker;
import freenet.config.Config;
import freenet.pluginmanager.*;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * This plugin implements Zeroconf (called Bonjour/RendezVous by apple) support on a freenet node.
 * 
 * @author Florent Daigni&egrave;re &lt;nextgens@freenetproject.org&gt;
 *
 * @see http://www.dns-sd.org/ServiceTypes.html
 * @see http://www.multicastdns.org/
 * @see http://jmdns.sourceforge.net/
 * 
 * TODO: We shouldn't start a thread at all ... but they are issues on startup (the configuration framework isn't available yet)
 * TODO: We shouldn't advertise services if they aren't reachable (ie: bound to localhost)
 * TODO: We will need to manage the list on our own insteed of requesting it for each http request
 * TODO: Plug into config. callbacks to reflect changes
 */
public class MDNSDiscovery implements FredPlugin, FredPluginHTTP{
	public static String freenetServiceType = "_freenet._udp.local.";
	private boolean goon = true;
	private JmDNS jmdns;
	private ServiceInfo fproxyInfo, tcmiInfo, fcpInfo, nodeInfo;
	private Config nodeConfig;
	private PageMaker pageMaker;
	private ServiceListener serviceListener;
	
	/**
	 * Called upon plugin unloading : we unregister advertised services
	 */
	public void terminate() {
		jmdns.unregisterAllServices();
		jmdns.removeServiceListener(MDNSDiscovery.freenetServiceType, serviceListener);
		goon = false;
		synchronized (this) {
			notify();
		}
	}

	public void runPlugin(PluginRespirator pr) {
		// wait until the node is initialised.
		do{
			try{
				Thread.sleep(1000);	
			}catch (InterruptedException e) {}			
		}while(pr.getNode() == null || !pr.getNode().isHasStarted());
			
		nodeConfig = pr.getNode().config;
		pageMaker = new PageMaker("clean");
		
		try{
			// Create the multicast listener
			jmdns = new JmDNS();
			final String address = "server on " + jmdns.getLocalHost().getName();
			
			// Advertise Fproxy
			if(nodeConfig.get("fproxy").getBoolean("enabled")){
				fproxyInfo = new ServiceInfo("_http._tcp.local.", "Freenet 0.7 Fproxy " + address,
						nodeConfig.get("fproxy").getInt("port"), 0, 0, "path=/");
				jmdns.registerService(fproxyInfo);
			}

			// Advertise FCP
			if(nodeConfig.get("fcp").getBoolean("enabled")){
				fcpInfo = new ServiceInfo("_fcp._tcp.local.", "Freenet 0.7 FCP " + address,
						nodeConfig.get("fcp").getInt("port"), 0, 0, "");
				jmdns.registerService(fcpInfo);
			}
			
			// Advertise TCMI
			if(nodeConfig.get("console").getBoolean("enabled")){
				tcmiInfo = new ServiceInfo("_telnet._tcp.local.", "Freenet 0.7 TCMI " + address,
						nodeConfig.get("console").getInt("port"), 0, 0, "");
				jmdns.registerService(tcmiInfo);
			}
				
			// Advertise the node
			nodeInfo = new ServiceInfo(MDNSDiscovery.freenetServiceType, "Freenet 0.7 Node " + address,
					nodeConfig.get("node").getInt("listenPort"), 0, 0, "");
			jmdns.registerService(nodeInfo);
			
			// Watch out for other nodes
			
			serviceListener = new NodeMDNSListener(this);
			jmdns.addServiceListener(MDNSDiscovery.freenetServiceType, serviceListener);

		} catch (IOException e) {
			e.printStackTrace();
		}

		while(goon){
			synchronized (this) {
				try{
					wait(300000);
				}catch (InterruptedException e) {}	
			}
		}
	}
	
	static class NodeMDNSListener implements ServiceListener {
		final MDNSDiscovery plugin;
		
		public NodeMDNSListener(MDNSDiscovery plugin) {
			this.plugin = plugin;
		}
		
        public synchronized void serviceAdded(ServiceEvent event) {
            System.out.println("Service added   : " + event.getName()+"."+event.getType());
            synchronized (plugin) {
                plugin.notify();				
			}
        }
        
        public synchronized void serviceRemoved(ServiceEvent event) {
            System.out.println("Service removed : " + event.getName()+"."+event.getType());
            synchronized (plugin) {
                plugin.notify();				
			}
        }
        
        public synchronized void serviceResolved(ServiceEvent event) {
            System.out.println("Service resolved: " + event.getInfo());
            synchronized (plugin) {
                plugin.notify();				
			}
        }
    }
	
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		HTMLNode pageNode = pageMaker.getPageNode("MDNSDiscovery plugin configuration page", false);
		HTMLNode contentNode = pageMaker.getContentNode(pageNode);
		
		ServiceInfo[] foundNodes = jmdns.list(MDNSDiscovery.freenetServiceType);
		if(foundNodes != null && foundNodes.length > 0){
			HTMLNode peerTableInfobox = contentNode.addChild("div", "class", "infobox infobox-normal");
			HTMLNode peerTableInfoboxHeader = peerTableInfobox.addChild("div", "class", "infobox-header");
			peerTableInfoboxHeader.addChild("#", "The following nodes have been found on the local subnet :");
			HTMLNode peerTableInfoboxContent = peerTableInfobox.addChild("div", "class", "infobox-content");
			
			HTMLNode peerTable = peerTableInfoboxContent.addChild("table", "class", "darknet_connections");
			HTMLNode peerTableHeaderRow = peerTable.addChild("tr");
			peerTableHeaderRow.addChild("th").addChild("span", new String[] { "title", "style" }, new String[] { "The node's name.", "border-bottom: 1px dotted; cursor: help;" }, "Name");
			peerTableHeaderRow.addChild("th").addChild("span", new String[] { "title", "style" }, new String[] { "The node's network address as IP:Port", "border-bottom: 1px dotted; cursor: help;" }, "Address");
			peerTableHeaderRow.addChild("th").addChild("span", new String[] { "title", "style" }, new String[] { "A description of the service.", "border-bottom: 1px dotted; cursor: help;" }, "Description");
			
			HTMLNode peerRow = peerTable.addChild("tr");
			
			for(int i=0; i<foundNodes.length; i++){
				peerRow.addChild("td", "class", "peer-name").addChild("#", foundNodes[i].getServer());
				peerRow.addChild("td", "class", "peer-address").addChild("#", foundNodes[i].getHostAddress()+':'+foundNodes[i].getPort());
				peerRow.addChild("td", "class", "peer-private-darknet-comment-note").addChild("#", foundNodes[i].getTextString());
			}
		}else{
			HTMLNode peerTableInfobox = contentNode.addChild("div", "class", "infobox infobox-warning");
			HTMLNode peerTableInfoboxHeader = peerTableInfobox.addChild("div", "class", "infobox-header");
			peerTableInfoboxHeader.addChild("#", "Nothing found!");
			HTMLNode peerTableInfoboxContent = peerTableInfobox.addChild("div", "class", "infobox-content");
			peerTableInfoboxContent.addChild("#", "No freenet node found on the local subnet, sorry!");
		}
		
		return pageNode.generate();
	}
	
	public String handleHTTPPut(HTTPRequest request) throws PluginHTTPException {
		throw new PluginHTTPException();
	}
	
	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		throw new PluginHTTPException();
	}
}
