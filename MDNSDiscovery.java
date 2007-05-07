/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package plugins.MDNSDiscovery;

import java.io.IOException;
import java.util.LinkedList;

import plugins.MDNSDiscovery.javax.jmdns.JmDNS;
import plugins.MDNSDiscovery.javax.jmdns.ServiceEvent;
import plugins.MDNSDiscovery.javax.jmdns.ServiceInfo;
import plugins.MDNSDiscovery.javax.jmdns.ServiceListener;
import freenet.config.Config;
import freenet.pluginmanager.*;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * This plugin implements Zeroconf (called Bonjour/RendezVous by apple) support on a Freenet node.
 * 
 * @author Florent Daigni&egrave;re &lt;nextgens@freenetproject.org&gt;
 *
 * @see http://www.dns-sd.org/ServiceTypes.html
 * @see http://www.multicastdns.org/
 * @see http://jmdns.sourceforge.net/
 * 
 * TODO: We shouldn't start a thread at all ... but they are issues on startup (the configuration framework isn't available yet)
 * TODO: We will need to manage the list on our own insteed of requesting it for each http request
 * TODO: Plug into config. callbacks to reflect changes @see #1217
 * TODO: Maybe we should make add forms onto that toadlet and let the user choose what to advertise or not 
 */
public class MDNSDiscovery implements FredPlugin, FredPluginHTTP {
	public static String freenetServiceType = "_freenet._udp.local.";
	private boolean goon = true;
	private JmDNS jmdns;
	private Config nodeConfig;
	private LinkedList ourAdvertisedServices, ourDisabledServices;
	private PluginRespirator pr;
	
	/**
	 * Called upon plugin unloading : we unregister advertised services
	 */
	public synchronized void terminate() {
		jmdns.close();
		goon = false;
		notify();
	}

	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
		// wait until the node is initialised.
		while(pr.getNode() == null || !pr.getNode().isHasStarted()){
			try{
				Thread.sleep(1000);	
			}catch (InterruptedException e) {}			
		}
			
		nodeConfig = pr.getNode().config;
		ourAdvertisedServices = new LinkedList();
		ourDisabledServices = new LinkedList();
		final ServiceInfo fproxyInfo, TMCIInfo, fcpInfo, nodeInfo;
		
		try{
			// Create the multicast listener
			jmdns = new JmDNS();
			final String address = "server -=" + pr.getNode().getMyName() + "=-";
			
			// Watch out for other nodes
			jmdns.addServiceListener(MDNSDiscovery.freenetServiceType, new NodeMDNSListener(this));
			
			// Advertise Fproxy
			fproxyInfo = new ServiceInfo("_http._tcp.local.", truncateAndSanitize("Freenet 0.7 Fproxy " + address),
					nodeConfig.get("fproxy").getInt("port"), 0, 0, "path=/");
			if(nodeConfig.get("fproxy").getBoolean("enabled") && !nodeConfig.get("fproxy").getOption("bindTo").isDefault()){
				jmdns.registerService(fproxyInfo);
				ourAdvertisedServices.add(fproxyInfo);
			}else
				ourDisabledServices.add(fproxyInfo);

			// Advertise FCP
			fcpInfo = new ServiceInfo("_fcp._tcp.local.", truncateAndSanitize("Freenet 0.7 FCP " + address),
					nodeConfig.get("fcp").getInt("port"), 0, 0, "");
			if(nodeConfig.get("fcp").getBoolean("enabled") && !nodeConfig.get("fcp").getOption("bindTo").isDefault()){
				jmdns.registerService(fcpInfo);
				ourAdvertisedServices.add(fcpInfo);
			}else
				ourDisabledServices.add(fcpInfo);
			
			// Advertise TMCI
			TMCIInfo = new ServiceInfo("_telnet._tcp.local.", truncateAndSanitize("Freenet 0.7 TMCI " + address),
					nodeConfig.get("console").getInt("port"), 0, 0, "");
			if(nodeConfig.get("console").getBoolean("enabled") && !nodeConfig.get("console").getOption("bindTo").isDefault()){
				jmdns.registerService(TMCIInfo);
				ourAdvertisedServices.add(TMCIInfo);
			}else
				ourDisabledServices.add(TMCIInfo);
				
			// Advertise the node
			nodeInfo = new ServiceInfo(MDNSDiscovery.freenetServiceType, truncateAndSanitize("Freenet 0.7 Node " + address),
					nodeConfig.get("node").getInt("listenPort"), 0, 0, "");
			jmdns.registerService(nodeInfo);
			ourAdvertisedServices.add(nodeInfo);

		} catch (IOException e) {
			e.printStackTrace();
		}

		while(goon){
			synchronized (this) {
				try{
					wait(Long.MAX_VALUE);
				}catch (InterruptedException e) {}	
			}
		}
	}

	private class NodeMDNSListener implements ServiceListener {
		final MDNSDiscovery plugin;
		
		public NodeMDNSListener(MDNSDiscovery plugin) {
			this.plugin = plugin;
		}
		
        public void serviceAdded(ServiceEvent event) {
            System.out.println("Service added   : " + event.getName()+"."+event.getType());
            // Force the gathering of informations
			jmdns.getServiceInfo(MDNSDiscovery.freenetServiceType, event.getName());
            synchronized (plugin) {
                plugin.notify();				
			}
        }
        
        public void serviceRemoved(ServiceEvent event) {
            System.out.println("Service removed : " + event.getName()+"."+event.getType());
            synchronized (plugin) {
                plugin.notify();				
			}
        }
        
        public void serviceResolved(ServiceEvent event) {
            System.out.println("Service resolved: " + event.getInfo());
            synchronized (plugin) {
                plugin.notify();				
			}
        }
    }
	
	private void PrintServices(HTMLNode contentNode, String description, ServiceInfo[] services)
	{
		HTMLNode peerTableInfobox = contentNode.addChild("div", "class", "infobox infobox-"+ (services.length > 0 ? "normal" : "warning"));
		HTMLNode peerTableInfoboxHeader = peerTableInfobox.addChild("div", "class", "infobox-header");
		HTMLNode peerTableInfoboxContent = peerTableInfobox.addChild("div", "class", "infobox-content");
		
		if(services != null && services.length > 0){
			peerTableInfoboxHeader.addChild("#", description);
			HTMLNode peerTable = peerTableInfoboxContent.addChild("table", "class", "darknet_connections");
			HTMLNode peerTableHeaderRow = peerTable.addChild("tr");
			peerTableHeaderRow.addChild("th").addChild("span", new String[] { "title", "style" }, new String[] { "The name or the  service.", "border-bottom: 1px dotted; cursor: help;" }, "Service Name");
			peerTableHeaderRow.addChild("th").addChild("span", new String[] { "title", "style" }, new String[] { "The name of the machine hosting the service.", "border-bottom: 1px dotted; cursor: help;" }, "Machine");
			peerTableHeaderRow.addChild("th").addChild("span", new String[] { "title", "style" }, new String[] { "The node's network address as IP:Port", "border-bottom: 1px dotted; cursor: help;" }, "Address");
			peerTableHeaderRow.addChild("th").addChild("span", new String[] { "title", "style" }, new String[] { "Service parameters", "border-bottom: 1px dotted; cursor: help;" }, "Parameters");
			
			HTMLNode peerRow;
			String mDNSService, mDNSServer, mDNSHost, mDNSPort, mDNSDescription;
			
			for(int i=0; i<services.length; i++){
			    peerRow = peerTable.addChild("tr");
			    ServiceInfo info = services[i];
			    mDNSService = info.getName();
				mDNSServer = info.getServer();
				mDNSHost = info.getHostAddress();
				mDNSPort = Integer.toString(info.getPort());
				mDNSDescription = info.getTextString();
				
				peerRow.addChild("td", "class", "peer-name").addChild("#", (mDNSService == null ? "null" : mDNSService));
				peerRow.addChild("td", "class", "peer-machine").addChild("#", (mDNSServer == null ? "null" : mDNSServer));
				peerRow.addChild("td", "class", "peer-address").addChild("#", (mDNSHost == null ? "null" : mDNSHost) + ':' + (mDNSPort == null ? "null": mDNSPort));
				peerRow.addChild("td", "class", "peer-private-darknet-comment-note").addChild("#", (mDNSDescription == null ? "" : mDNSDescription));
			}
		}else{
			peerTableInfoboxHeader.addChild("#", description);
			peerTableInfoboxContent.addChild("#", "No Freenet resources found on the local subnet, sorry!");
		}
	}
		
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		HTMLNode pageNode = pr.getPageMaker().getPageNode("MDNSDiscovery plugin configuration page", false, null);
		HTMLNode contentNode = pr.getPageMaker().getContentNode(pageNode);

		ServiceInfo[] foundNodes = jmdns.list(MDNSDiscovery.freenetServiceType);

		PrintServices(contentNode, "The following services are being broadcast from this node :", (ServiceInfo[])ourAdvertisedServices.toArray(new ServiceInfo[ourAdvertisedServices.size()]));

		PrintServices(contentNode, "The following nodes have been found on the local subnet :", foundNodes);
		
		if(ourAdvertisedServices.size() < 3){
			HTMLNode disabledServicesInfobox = contentNode.addChild("div", "class", "infobox infobox-normal");
			HTMLNode disabledServicesInfoboxHeader = disabledServicesInfobox.addChild("div", "class", "infobox-header");
			HTMLNode disabledServicesInfoboxContent = disabledServicesInfobox.addChild("div", "class", "infobox-content");

			disabledServicesInfoboxHeader.addChild("#", "Disabled services");
			
			disabledServicesInfoboxContent.addChild("#", "The following services won't be advertised on the local network" +
					" because they are either disabled ot bound to the local interface :");
			
			HTMLNode disabledServicesList = disabledServicesInfoboxContent.addChild("ul", "id", "disabled-service-list");
			
			for(int i=0; i<ourDisabledServices.size(); i++)
				disabledServicesList.addChild("li").addChild("#", ((ServiceInfo) ourDisabledServices.get(i)).getName());
		}
		
		return pageNode.generate();
	}
	
	public String handleHTTPPut(HTTPRequest request) throws PluginHTTPException {
		return null;
	}
	
	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		return null;
	}
	
	/**
	 * Function used to sanitize a service name (it ought to be less than 63 char. long and shouldn't contain '.')
	 * @param The string to sanitize
	 * @return a sanitized String
	 */
	private String truncateAndSanitize(String str)
	{
		int indexOfDot; 
		do{
			indexOfDot = str.indexOf('.');
			if(indexOfDot == -1) break;
			str = str.substring(0, indexOfDot) + ',' +str.substring(indexOfDot + 1);
		} while(true);
		
		if(str.length() > 62)
			str = str.substring(0, 62);
		return str;
	}
}
