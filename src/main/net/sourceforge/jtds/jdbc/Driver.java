// jTDS JDBC Driver for Microsoft SQL Server
// Copyright (C) 2004 The jTDS Project
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
package net.sourceforge.jtds.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * jTDS implementation of the java.sql.Driver interface.
 * <p>
 * Implementation note:
 * <ol>
 * <li>Property text names and descriptions are loaded from an external file resource.
 *     This allows the actual names and descriptions to be changed or localised without
 *     impacting this code.
 * <li>The way in which the URL is parsed and converted to properties is rather
 *     different from the original jTDS Driver class.
 *     See parseURL and Connection.unpackProperties methods for more detail.
 * </ol>
 * @see java.sql.Driver
 * @author Brian Heineman
 * @author Mike Hutchinson
 * @author Alin Sinpalean
 * @version $Id: Driver.java,v 1.30 2004-08-06 03:18:10 ddkilzer Exp $
 */
public class Driver implements java.sql.Driver {
    private static String driverPrefix = "jdbc:jtds:";
    static final int MAJOR_VERSION = 0;
    static final int MINOR_VERSION = 9;
    public static final boolean JDBC3 =
            "1.4".compareTo(System.getProperty("java.specification.version")) <= 0;
    /** TDS 4.2 protocol. */
    public static final int TDS42 = 1;
    /** TDS 5.0 protocol. */
    public static final int TDS50 = 2;
    /** TDS 7.0 protocol. */
    public static final int TDS70 = 3;
    /** TDS 8.0 protocol. */
    public static final int TDS80 = 4;
    /** Microsoft SQL Server. */
    public static final int SQLSERVER = 1;
    /** Sybase ASE. */
    public static final int SYBASE = 2;

    static {
        try {
            // Register this with the DriverManager
            DriverManager.registerDriver(new Driver());
        } catch (SQLException e) {
        }
    }

    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    public boolean jdbcCompliant() {
        return false;
    }

    public boolean acceptsURL(String url) throws SQLException {
        if (url == null) {
            return false;
        }
        
        return url.toLowerCase().startsWith(driverPrefix);
    }

    public Connection connect(String url, Properties info)
        throws SQLException  {
        if (url == null || !url.toLowerCase().startsWith(driverPrefix)) {
            return null;
        }

        Properties props = parseURL(url, info);

        if (props == null) {
            throw new SQLException(Messages.get("error.driver.badurl", url), "08001");
        }

        if (props.getProperty(Messages.get("prop.logintimeout")) == null) {
            props.setProperty(Messages.get("prop.logintimeout"), Integer.toString(DriverManager.getLoginTimeout()));
        }

        if (JDBC3) {
            return new ConnectionJDBC3(url, props);
        }

        return new ConnectionJDBC2(url, props);
    }

    public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties props)
            throws SQLException {

        final Properties info = parseURL(url, (props == null ? new Properties() : props));

        if (info == null) {
            throw new SQLException(
                        Messages.get("error.driver.badurl", url), "08001");
        }

        final Map propertyMap = new HashMap();
        final Map descriptionMap = new HashMap();
        Messages.loadDriverProperties(propertyMap, descriptionMap);

        final Map driverPropertyInfoMap = new HashMap();
        for (Iterator iterator = propertyMap.keySet().iterator(); iterator.hasNext(); ) {
            final String key = (String) iterator.next();
            final String name = (String) propertyMap.get(key);
            final DriverPropertyInfo driverPropertyInfo = new DriverPropertyInfo(name, info.getProperty(name));
            driverPropertyInfo.description = (String) descriptionMap.get(key);
            driverPropertyInfoMap.put(name, driverPropertyInfo);
        }

        assignDriverPropertyInfoRequired(driverPropertyInfoMap, "prop.servername", true);
        assignDriverPropertyInfoRequired(driverPropertyInfoMap, "prop.servertype", true);

        final String[] serverTypeChoices = new String[]{
                            String.valueOf(SQLSERVER),
                            String.valueOf(SYBASE),
                        };
        assignDriverPropertyInfoChoice(driverPropertyInfoMap, "prop.servertype", serverTypeChoices);

        final String[] tdsChoices = new String[] {
                            DefaultProperties.TDS_VERSION_42,
                            DefaultProperties.TDS_VERSION_50,
                            DefaultProperties.TDS_VERSION_70,
                            DefaultProperties.TDS_VERSION_80,
                        };
        assignDriverPropertyInfoChoice(driverPropertyInfoMap, "prop.tds", tdsChoices);

        final String[] booleanChoices = new String[] {"true", "false"};
        assignDriverPropertyInfoChoice(driverPropertyInfoMap, "prop.lastupdatecount", booleanChoices);
        assignDriverPropertyInfoChoice(driverPropertyInfoMap, "prop.namedpipe", booleanChoices);
        assignDriverPropertyInfoChoice(driverPropertyInfoMap, "prop.preparesql", booleanChoices);
        assignDriverPropertyInfoChoice(driverPropertyInfoMap, "prop.useunicode", booleanChoices);

        return (DriverPropertyInfo[]) driverPropertyInfoMap.values().toArray(
                new DriverPropertyInfo[driverPropertyInfoMap.size()]);
    }

    /**
     * Assigns the <code>choices</code> value to a {@link DriverPropertyInfo}
     * object stored in a <code>Map</code>.
     * 
     * @param infoMap The map of {@link DriverPropertyInfo} objects.
     * @param messageKey The message key used to retrieve the object.
     * @param choices The value to set on the <code>choices</code> field of the object.
     */ 
    private void assignDriverPropertyInfoChoice(
            final Map infoMap, final String messageKey, final String[] choices) {

        ((DriverPropertyInfo) infoMap.get(Messages.get(messageKey))).choices = choices;
    }

    /**
     * Assigns the <code>required</code> value to a {@link DriverPropertyInfo}
     * object stored in a <code>Map</code>.
     * 
     * @param infoMap The map of {@link DriverPropertyInfo} objects.
     * @param messageKey The message key used to retrieve the object.
     * @param required The value to set on the <code>required</code> field of the object.
     */ 
    private void assignDriverPropertyInfoRequired(
            final Map infoMap, final String messageKey, final boolean required) {

        ((DriverPropertyInfo) infoMap.get(Messages.get(messageKey))).required = required;
    }

    /**
     * Parse the driver URL and extract the properties.
     *
     * @param url The URL to parse.
     * @param info Any existing properties already loaded in a Properties object.
     * @return The URL properties as a <code>Properties</code> object.
     */
    private static Properties parseURL(String url, Properties info) {
        Properties props = new Properties();

        // Take local copy of existing properties
        for (Enumeration e = info.keys(); e.hasMoreElements();) {
            String key = (String) e.nextElement();
            String value = info.getProperty(key);
            
            if (value != null) {
                props.setProperty(key.toUpperCase(), value);
            }
        }

        StringBuffer token = new StringBuffer(16);
        int pos = 0;

        pos = nextToken(url, pos, token); // Skip jdbc

        if (!token.toString().equalsIgnoreCase("jdbc")) {
            return null; // jdbc: missing
        }

        pos = nextToken(url, pos, token); // Skip jtds
        
        if (!token.toString().equalsIgnoreCase("jtds")) {
            return null; // jtds: missing
        }

        pos = nextToken(url, pos, token); // Get server type
        String type = token.toString().toLowerCase();

        if (type.equals("sqlserver")) {
            props.setProperty(Messages.get("prop.servertype"),
                              String.valueOf(SQLSERVER));
        } else if (type.equals("sybase")) {
            props.setProperty(Messages.get("prop.servertype"),
                              String.valueOf(SYBASE));
        } else {
            return null; // Bad server type
        }

        pos = nextToken(url, pos, token); // Null token between : and //

        if (token.length() > 0) {
            return null; // There should not be one!
        }

        pos = nextToken(url, pos, token); // Get server name
        String host = token.toString();

        if (host.length() == 0 &&
            props.getProperty(Messages.get("prop.servername")) == null) {
            return null; // Server name missing
        }

        props.setProperty(Messages.get("prop.servername"), host);

        if (url.charAt(pos - 1) == ':' && pos < url.length()) {
            pos = nextToken(url, pos, token); // Get port number

            try {
                int port = Integer.parseInt(token.toString());
                props.setProperty(Messages.get("prop.portnumber"), Integer.toString(port));
            } catch(NumberFormatException e) {
                return null; // Bad port number
            }
        }

        if (url.charAt(pos - 1) == '/' && pos < url.length()) {
            pos = nextToken(url, pos, token); // Get database name
            props.setProperty(Messages.get("prop.databasename"), token.toString());
        }

        //
        // Process any additional properties in URL
        //
        while (url.charAt(pos - 1) == ';' && pos < url.length()) {
            pos = nextToken(url, pos, token);
            String tmp = token.toString();
            int index = tmp.indexOf('=');

            if (index > 0 && index < tmp.length() - 1) {
                props.setProperty(tmp.substring(0, index).toUpperCase(), tmp.substring(index + 1));
            } else {
                props.setProperty(tmp.toUpperCase(), "");
            }
        }

        //
        // Set default properties
        //
        props = DefaultProperties.addDefaultProperties(props);

        return props;
    }

    /**
     * Extract the next lexical token from the URL.
     *
     * @param url The URL being parsed
     * @param pos The current position in the URL string.
     * @param token The buffer containing the extracted token.
     * @return The updated position as an <code>int</code>.
     */
    private static int nextToken(String url, int pos, StringBuffer token) {
        token.setLength(0);

        while (pos < url.length()) {
            char ch = url.charAt(pos++);

            if (ch == ':' || ch == ';') {
                break;
            }

            if (ch == '/') {
                if (pos < url.length() && url.charAt(pos) == '/') {
                    pos++;
                }

                break;
            }

            token.append(ch);
        }

        return pos;
    }
}
