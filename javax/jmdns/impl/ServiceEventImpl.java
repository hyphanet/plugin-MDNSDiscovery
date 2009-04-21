///Copyright 2003-2005 Arthur van Hoff, Rick Blair
//Licensed under Apache License version 2.0
//Original license LGPL

package plugins.MDNSDiscovery.javax.jmdns.impl;

import java.util.logging.Logger;

import plugins.MDNSDiscovery.javax.jmdns.JmDNS;
import plugins.MDNSDiscovery.javax.jmdns.ServiceEvent;
import plugins.MDNSDiscovery.javax.jmdns.ServiceInfo;

/**
 * ServiceEvent.
 *
 * @author Werner Randelshofer, Rick Blair
 * @version %I%, %G%
 */
public class ServiceEventImpl extends ServiceEvent
{
    private static Logger logger = Logger.getLogger(ServiceEvent.class.getName());
    /**
     * The type name of the service.
     */
    private String type;
    /**
     * The instance name of the service. Or null, if the event was
     * fired to a service type listener.
     */
    private String name;
    /**
     * The service info record, or null if the service could be be resolved.
     * This is also null, if the event was fired to a service type listener.
     */
    private ServiceInfoImpl info;

    /**
     * Creates a new instance.
     *
     * @param source the JmDNS instance which originated the event.
     * @param type   the type name of the service.
     * @param name   the instance name of the service.
     * @param info   the service info record, or null if the service could be be resolved.
     */
    public ServiceEventImpl(JmDNSImpl source, String type, String name, ServiceInfoImpl info)
    {
        super(source);
        this.type = type;
        this.name = name;
        this.info = info;
    }

    /**
     * @see plugins.MDNSDiscovery.javax.jmdns.ServiceEvent#getDNS()
     */
    public JmDNS getDNS()
    {
        return (JmDNS) getSource();
    }

    /**
     * @see plugins.MDNSDiscovery.javax.jmdns.ServiceEvent#getType()
     */
    public String getType()
    {
        return type;
    }

    /**
     * @see plugins.MDNSDiscovery.javax.jmdns.ServiceEvent#getName()
     */
    public String getName()
    {
        return name;
    }

    /**
     * @see plugins.MDNSDiscovery.javax.jmdns.ServiceEvent#toString()
     */
    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append("<" + getClass().getName() + "> ");
        buf.append(super.toString());
        buf.append(" name ");
        buf.append(getName());
        buf.append(" type ");
        buf.append(getType());
        buf.append(" info ");
        buf.append(getInfo());
        return buf.toString();
    }

    public ServiceInfo getInfo()
    {
        return info;
    }

}
