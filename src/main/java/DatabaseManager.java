import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import io.github.cdimascio.dotenv.Dotenv;

import io.left.rightmesh.id.MeshId;
import io.left.rightmesh.util.MeshUtility;

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
    private static Connection getConnection()
    {
        Dotenv dotenv = Dotenv.configure()
                .directory("src/.env")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
        String DB_URL = dotenv.get("DB_URL");
        String USER = dotenv.get("DB_USER");
        String PASS = dotenv.get("DB_PASSWORD");
        Connection conn = null;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
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
            MeshUtility.Log(TAG, "Finding device: " + meshId);
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "Error finding device");
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
            MeshUtility.Log(TAG, "SQL: " + statement.toString());
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
            MeshUtility.Log(TAG, "Adding device: " + meshId);
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "Error adding device");
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
            MeshUtility.Log(TAG, "SQL: " + statement.toString());
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
            MeshUtility.Log(TAG, "Updating device: " + meshId);
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "Error updating device");
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
            MeshUtility.Log(TAG, "SQL: " + statement.toString());
        }
    }

    /**
     * Adds the peer to the database
     *
     * @param id
     * @param role
     * @param connected
     */
    public static void addPeer(String id, int role, boolean connected)
    {
        Connection conn = getConnection();
        if (conn != null) {
            try {
                if (!deviceExists(conn, id)) {
                    //new node
                    addDevice(conn, id, role, connected);
                } else {
                    //update node
                    updateDevice(conn, id, role, connected);
                }
            } finally {
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
            MeshUtility.Log(TAG, "Finding link: source->" + sourceMeshId +
                                    " target->" + targetMeshId);
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "Error finding link");
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
            MeshUtility.Log(TAG, "SQL: " + statement.toString());
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
            MeshUtility.Log(TAG, "Adding link: " + sourceMeshId + ", " + targetMeshId);
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "Error adding link");
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
            MeshUtility.Log(TAG, "SQL: " + statement.toString());
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
            MeshUtility.Log(TAG, "Updating link: " + sourceMeshId + ", " + targetMeshId);
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "Error updating link");
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
            MeshUtility.Log(TAG, "SQL: " + statement.toString());
        }
    }

    /**
     * Adds or updates the link in the database.
     *
     * @param targetId
     * @param nextHopId
     */
    public static void addLink(String targetId, String nextHopId)
    {
        Connection conn = getConnection();
        if (conn != null) {
            try {
                if (!linkExists(conn, targetId, nextHopId)) {
                    insertLink(conn, targetId, nextHopId);
                } else {
                    updateLink(conn, targetId, nextHopId);
                }
            } finally {
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
            MeshUtility.Log(TAG, "Deleting link: " + sourceMeshId + ", " + targetMeshId);
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "Error deleting link");
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
            MeshUtility.Log(TAG, "SQL: " + statement.toString());
        } finally {
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
            MeshUtility.Log(TAG, "Cleaning stale links");
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "Error finding stale link");
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
            MeshUtility.Log(TAG, "SQL: " + statement.toString());
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
            MeshUtility.Log(TAG, "Cleaning stale devices");
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "Error finding stale device");
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
            MeshUtility.Log(TAG, "SQL: " + statement.toString());
        }
    }

    /**
     * Adds the superpeer and cleans up any stale devices or links.
     *
     * @param id
     */
    public static void addSuperpeerAndCleanup(String id)
    {
        Connection conn = getConnection();
        if (conn != null) {
            try {
                if (deviceExists(conn, id)) {
                    updateDevice(conn, id, SUPERPEER.getNumber(), true);

                    //clean up unheard from devices
                    cleanupStaleDevices(conn);

                    //cleanup unheard from links
                    cleanupStaleLinks(conn);
                } else {
                    addDevice(conn, id, SUPERPEER.getNumber(), true);
                }
            } finally {
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

}
