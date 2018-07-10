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
 * Created by rachel on 2018-07-09.
 */

public class DatabaseManager {
    public static final String TAG = Visualization.class.getCanonicalName();
    private static Connection conn = null;

    private static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";

    //@TODO Previously using InternetLink.SCAN_MAX but variable is now private (6000)
    private static int threshold = (6000 * 5) / 1000;

    private static void setConnection() {
        Dotenv dotenv = Dotenv.configure()
                .directory("src/.env")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
        String DB_URL = dotenv.get("DB_URL");
        String USER = dotenv.get("DB_USER");
        String PASS = dotenv.get("DB_PASSWORD");
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
    }

    private static boolean deviceExists(String meshId)
    {
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

    private static void addDevice(String meshId,
                                 Integer role,
                                 boolean connected)
    {
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

    private static void updateDevice(String meshId,
                                 Integer role,
                                 boolean connected)
    {
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

    public static void addPeer(String id, int role, boolean connected)
    {
        setConnection();
        try {
            if (!deviceExists(id)) {
                //new node
                addDevice(id, role, connected);
            } else {
                //update node
                updateDevice(id, role, connected);
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

    private static boolean linkExists(String sourceMeshId, String targetMeshId)
    {
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

    private static void insertLink(String sourceMeshId,
                               String targetMeshId)
    {
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

    private static void updateLink(String sourceMeshId,
                                    String targetMeshId)
    {
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

    public static void addLink(String targetId, String nextHopId)
    {
        setConnection();
        try {
            if(!linkExists(targetId, nextHopId)) {
                insertLink(targetId, nextHopId);
            } else {
                updateLink(targetId, nextHopId);
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

    private static void deleteLink(String sourceMeshId,
                                  String targetMeshId)
    {
        setConnection();
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

    private static ResultSet cleanupStaleLinks()
    {
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
                deleteLink(sid, tid);
            }
            MeshUtility.Log(TAG, "Finding stale links");
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "Error finding stale link");
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
            MeshUtility.Log(TAG, "SQL: " + statement.toString());
        }

        return rs;
    }

    private static ResultSet cleanupStaleDevices()
    {
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
                updateDevice(pid, rs.getInt("role"), false);
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
                updateDevice(pid, rs.getInt("role"), false);
            }
            MeshUtility.Log(TAG, "Finding stale links");
        } catch (SQLException ex) {
            MeshUtility.Log(TAG, "Error finding stale link");
            MeshUtility.Log(TAG, "SQLException: " + ex.getMessage());
            MeshUtility.Log(TAG, "SQLState: " + ex.getSQLState());
            MeshUtility.Log(TAG, "VendorError: " + ex.getErrorCode());
            MeshUtility.Log(TAG, "SQL: " + statement.toString());
        }

        return rs;
    }

    public static void addMasterAndCleanup(String id)
    {
        setConnection();
        try {
            if (deviceExists(id)) {
                updateDevice(id, SUPERPEER.getNumber(), true);

                //clean up unheard from devices
                cleanupStaleDevices();

                //cleanup unheard from links
                cleanupStaleLinks();
            } else {
                addDevice(id, SUPERPEER.getNumber(), true);
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
