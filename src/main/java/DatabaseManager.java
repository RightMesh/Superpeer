import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import io.github.cdimascio.dotenv.Dotenv;

import io.left.rightmesh.id.MeshId;
import io.left.rightmesh.util.Logger;

import static io.left.rightmesh.proto.MeshDnsProtos.MeshRequest.Role.SUPERPEER;

/**
 * Manages the connections to the database for the superpeer visualization.
 *
 * Created by rachel on 2018-07-09.
 */
public class DatabaseManager {
    public static final String TAG = Visualization.class.getCanonicalName();

    private static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";

    //@TODO Previously using InternetLink.SCAN_MAX but variable is now private (6000)
    private static int threshold = (6000 * 5) / 1000;

    /**
     * Instantiates the database connection
     */
    public static Connection getConnection(String dbUrl, String username, String password)
    {
        Connection conn = null;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(dbUrl, username, password);
        } catch (SQLException ex) {
            Logger.log(TAG, "SQLException: " + ex.getMessage());
            Logger.log(TAG, "SQLState: " + ex.getSQLState());
            Logger.log(TAG, "VendorError: " + ex.getErrorCode());
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        }

        return conn;
    }

    /**
     * Checks if the devices exists in the database.
     *
     * @param meshId
     * @return
     */
    private static boolean deviceExists(Connection conn, String meshId)
    {
        assert conn != null;

        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            statement = conn.prepareStatement("SELECT * FROM devices WHERE uuid = x?");
            statement.setString(1, meshId);
            rs = statement.executeQuery();
            if (rs.isBeforeFirst()) {
                return true;
            }
            Logger.log(TAG, "Finding device: " + meshId);
        } catch (SQLException ex) {
            Logger.log(TAG, "Error finding device");
            Logger.log(TAG, "SQLException: " + ex.getMessage());
            Logger.log(TAG, "SQLState: " + ex.getSQLState());
            Logger.log(TAG, "VendorError: " + ex.getErrorCode());
            Logger.log(TAG, "SQL: " + statement.toString());
        }

        return false;
    }

    /**
     * Adds the device to the database
     *
     * @param meshId
     * @param role
     * @param connected
     */
    private static void addDevice(Connection conn,
                                  String meshId,
                                  Integer role,
                                  boolean connected)
    {
        assert conn != null;

        PreparedStatement statement = null;
        try {
            statement = conn.prepareStatement("INSERT INTO devices(uuid, role, connected) " +
                    "VALUES(x?, ?, ?)");
            statement.setString(1, meshId);
            statement.setInt(2, role);
            statement.setBoolean(3, connected);
            statement.executeUpdate();
            Logger.log(TAG, "Adding device: " + meshId);
        } catch (SQLException ex) {
            Logger.log(TAG, "Error adding device");
            Logger.log(TAG, "SQLException: " + ex.getMessage());
            Logger.log(TAG, "SQLState: " + ex.getSQLState());
            Logger.log(TAG, "VendorError: " + ex.getErrorCode());
            Logger.log(TAG, "SQL: " + statement.toString());
        }
    }

    /**
     * Checks/Updates the highest number of nodes that have connected at one time
     *
     */
    private static void checkMaxNodes(Connection conn)
    {
        assert conn != null;

        PreparedStatement statement = null;
        ResultSet rs = null;

        try {
            statement = conn.prepareStatement("SELECT COUNT(*) " +
                    "FROM devices " +
                    "WHERE connected = 1");
            rs = statement.executeQuery();
            rs.next();
            int deviceCount = rs.getInt(1);

            statement = conn.prepareStatement("SELECT max_device_count " +
                    "FROM max_devices");
            rs = statement.executeQuery();
            rs.next();
            int maxCount = rs.getInt(1);

            // if there's a new highest network count, update the entry with the new count and when it happened
            if (deviceCount >= maxCount) {
                statement = conn.prepareStatement("UPDATE max_devices " +
                        "SET max_device_count = ?, happened_on = CURRENT_TIMESTAMP");
                statement.setInt(1, deviceCount);
                statement.executeUpdate();
                Logger.log(TAG, "Updating Max Devices From " + maxCount + " to " + deviceCount);
            }

        } catch (SQLException ex) {
            Logger.log(TAG, "Error adding device");
            Logger.log(TAG, "SQLException: " + ex.getMessage());
            Logger.log(TAG, "SQLState: " + ex.getSQLState());
            Logger.log(TAG, "VendorError: " + ex.getErrorCode());
            Logger.log(TAG, "SQL: " + statement.toString());
        }
    }

    /**
     * Update the record of the device
     *
     * @param meshId
     * @param role
     * @param connected
     */
    private static void updateDevice(Connection conn,
                                     String meshId,
                                     Integer role,
                                     boolean connected)
    {
        assert conn != null;

        PreparedStatement statement = null;
        try {
            statement = conn.prepareStatement("UPDATE devices "+
                    "SET connected = ?, role = ?, last_heard = CURRENT_TIMESTAMP " +
                    "WHERE uuid=x?");
            statement.setBoolean(1, connected);
            statement.setInt(2, role);
            statement.setString(3, meshId);
            statement.executeUpdate();
            Logger.log(TAG, "Updating device: " + meshId);
        } catch (SQLException ex) {
            Logger.log(TAG, "Error updating device");
            Logger.log(TAG, "SQLException: " + ex.getMessage());
            Logger.log(TAG, "SQLState: " + ex.getSQLState());
            Logger.log(TAG, "VendorError: " + ex.getErrorCode());
            Logger.log(TAG, "SQL: " + statement.toString());
        }
    }

    /**
     * Adds the peer to the database
     *
     * @param id
     * @param role
     * @param connected
     */
    public static void addPeer(Connection conn, String id, int role, boolean connected)
    {
        if (conn != null) {
            if (!deviceExists(conn, id)) {
                //new node
                addDevice(conn, id, role, connected);
            } else {
                //update node
                updateDevice(conn, id, role, connected);
            }
            checkMaxNodes(conn);
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Checks if the link exists
     *
     * @param sourceMeshId
     * @param targetMeshId
     * @return
     */
    private static boolean linkExists(Connection conn,
                                      String sourceMeshId,
                                      String targetMeshId)
    {
        assert conn != null;

        PreparedStatement statement = null;
        ResultSet rs = null;

        try {
            statement = conn.prepareStatement("SELECT * FROM links WHERE source = x? AND target = x?");
            statement.setString(1, sourceMeshId);
            statement.setString(2, targetMeshId);
            rs = statement.executeQuery();
            if (rs.isBeforeFirst()) {
                return true;
            }
            Logger.log(TAG, "Finding link: source->" + sourceMeshId +
                                    " target->" + targetMeshId);
        } catch (SQLException ex) {
            Logger.log(TAG, "Error finding link");
            Logger.log(TAG, "SQLException: " + ex.getMessage());
            Logger.log(TAG, "SQLState: " + ex.getSQLState());
            Logger.log(TAG, "VendorError: " + ex.getErrorCode());
            Logger.log(TAG, "SQL: " + statement.toString());
        }

        return false;
    }

    /**
     * Inserts the link to the database
     *
     * @param sourceMeshId
     * @param targetMeshId
     */
    private static void insertLink(Connection conn,
                                   String sourceMeshId,
                                   String targetMeshId)
    {
        assert conn != null;

        PreparedStatement statement = null;

        try {
            statement = conn.prepareStatement("INSERT INTO links(source, target) " +
                    "VALUES(x?, x?)");
            statement.setString(1, sourceMeshId);
            statement.setString(2, targetMeshId);
            statement.executeUpdate();
            Logger.log(TAG, "Adding link: " + sourceMeshId + ", " + targetMeshId);
        } catch (SQLException ex) {
            Logger.log(TAG, "Error adding link");
            Logger.log(TAG, "SQLException: " + ex.getMessage());
            Logger.log(TAG, "SQLState: " + ex.getSQLState());
            Logger.log(TAG, "VendorError: " + ex.getErrorCode());
            Logger.log(TAG, "SQL: " + statement.toString());
        }
    }

    /**
     * Updates the link in the database
     *
     * @param sourceMeshId
     * @param targetMeshId
     */
    private static void updateLink(Connection conn,
                                   String sourceMeshId,
                                   String targetMeshId)
    {
        assert conn != null;

        PreparedStatement statement = null;

        try {
            statement = conn.prepareStatement("UPDATE links "+
                    "SET last_heard = CURRENT_TIMESTAMP " +
                    "WHERE source = x? AND target = x? ");
            statement.setString(1, sourceMeshId);
            statement.setString(2, targetMeshId);
            statement.executeUpdate();
            Logger.log(TAG, "Updating link: " + sourceMeshId + ", " + targetMeshId);
        } catch (SQLException ex) {
            Logger.log(TAG, "Error updating link");
            Logger.log(TAG, "SQLException: " + ex.getMessage());
            Logger.log(TAG, "SQLState: " + ex.getSQLState());
            Logger.log(TAG, "VendorError: " + ex.getErrorCode());
            Logger.log(TAG, "SQL: " + statement.toString());
        }
    }

    /**
     * Adds or updates the link in the database.
     *
     * @param targetId
     * @param nextHopId
     */
    public static void addLink(Connection conn, String targetId, String nextHopId)
    {
        if (conn != null) {
            if (!linkExists(conn, targetId, nextHopId)) {
                insertLink(conn, targetId, nextHopId);
            } else {
                updateLink(conn, targetId, nextHopId);
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Removes the link in the database
     *
     * @param sourceMeshId
     * @param targetMeshId
     */
    private static void deleteLink(Connection conn,
                                   String sourceMeshId,
                                   String targetMeshId)
    {
        assert conn != null;

        PreparedStatement statement = null;
        try {
            statement = conn.prepareStatement("DELETE FROM links "+
                    "WHERE source = x? AND target = x? ");
            statement.setString(1, sourceMeshId);
            statement.setString(2, targetMeshId);
            statement.executeUpdate();
            Logger.log(TAG, "Deleting link: " + sourceMeshId + ", " + targetMeshId);
        } catch (SQLException ex) {
            Logger.log(TAG, "Error deleting link");
            Logger.log(TAG, "SQLException: " + ex.getMessage());
            Logger.log(TAG, "SQLState: " + ex.getSQLState());
            Logger.log(TAG, "VendorError: " + ex.getErrorCode());
            Logger.log(TAG, "SQL: " + statement.toString());
        }
    }

    /**
     * Cleans up any stale links
     *
     * @return
     */
    private static void cleanupStaleLinks(Connection conn)
    {
        assert conn != null;

        PreparedStatement statement = null;
        ResultSet rs = null;

        try {
            statement = conn.prepareStatement("SELECT * FROM links "+
                            "WHERE last_heard < DATE_SUB(NOW(), INTERVAL ? SECOND)");
            statement.setInt(1, threshold);
            rs = statement.executeQuery();

            if (rs.last()) {
                // not rs.first() because the rs.next() below will move on,
                // missing the first element
                rs.beforeFirst();
            }
            while (rs.next()) {
                MeshId sourceId = new MeshId();
                sourceId.setRawMeshId(rs.getBytes("source"));
                String sid = sourceId.toString().substring(2);
                MeshId targetId = new MeshId();
                targetId.setRawMeshId(rs.getBytes("target"));
                String tid = targetId.toString().substring(2);
                deleteLink(conn, sid, tid);
            }
            Logger.log(TAG, "Cleaning stale links");
        } catch (SQLException ex) {
            Logger.log(TAG, "Error finding stale link");
            Logger.log(TAG, "SQLException: " + ex.getMessage());
            Logger.log(TAG, "SQLState: " + ex.getSQLState());
            Logger.log(TAG, "VendorError: " + ex.getErrorCode());
            Logger.log(TAG, "SQL: " + statement.toString());
        }
    }

    /**
     * Cleans up any stale devices
     *
     * @return
     */
    private static void cleanupStaleDevices(Connection conn)
    {
        assert conn != null;

        PreparedStatement statement = null;
        ResultSet rs = null;

        try {
            statement = conn.prepareStatement("SELECT * FROM devices " +
                    "WHERE last_heard < DATE_SUB(NOW(), INTERVAL ? SECOND) AND connected = ?");
            statement.setInt(1, threshold);
            statement.setBoolean(2, true);
            rs = statement.executeQuery();

            if (rs.last()) {
                // not rs.first() because the rs.next() below will move on,
                // missing the first element
                rs.beforeFirst();
            }
            while (rs.next()) {
                MeshId peerid = new MeshId();
                peerid.setRawMeshId(rs.getBytes("uuid"));
                String pid = peerid.toString().substring(2);
                updateDevice(conn, pid, rs.getInt("role"), false);
            }
            if (rs.last()) {
                // not rs.first() because the rs.next() below will move on,
                // missing the first element
                rs.beforeFirst();
            }
            while (rs.next()) {
                MeshId peerId = new MeshId();
                peerId.setRawMeshId(rs.getBytes("uuid"));
                String pid = peerId.toString().substring(2);
                updateDevice(conn, pid, rs.getInt("role"), false);
            }
            Logger.log(TAG, "Cleaning stale devices");
        } catch (SQLException ex) {
            Logger.log(TAG, "Error finding stale device");
            Logger.log(TAG, "SQLException: " + ex.getMessage());
            Logger.log(TAG, "SQLState: " + ex.getSQLState());
            Logger.log(TAG, "VendorError: " + ex.getErrorCode());
            Logger.log(TAG, "SQL: " + statement.toString());
        }
    }

    /**
     * Adds the superpeer and cleans up any stale devices or links.
     *
     * @param id
     */
    public static void addSuperpeerAndCleanup(Connection conn, String id)
    {
        if (conn != null) {
            if (deviceExists(conn, id)) {
                updateDevice(conn, id, SUPERPEER.getNumber(), true);

                //clean up unheard from devices
                cleanupStaleDevices(conn);

                //cleanup unheard from links
                cleanupStaleLinks(conn);
            } else {
                addDevice(conn, id, SUPERPEER.getNumber(), true);
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

}
