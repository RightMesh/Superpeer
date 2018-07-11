import java.util.Iterator;
import java.util.Map;

import io.left.rightmesh.id.MeshLink;
import io.left.rightmesh.id.MeshPeer;
import io.left.rightmesh.mesh.PeerListener;
import io.left.rightmesh.id.MeshId;
import io.left.rightmesh.mesh.JavaMeshManager;
import io.left.rightmesh.proto.MeshDnsProtos;
import io.left.rightmesh.routing.Route;
import io.left.rightmesh.routing.RoutingTable;

import static io.left.rightmesh.proto.MeshDnsProtos.MeshRequest.Role.MASTER;
import static io.left.rightmesh.proto.MeshDnsProtos.MeshRequest.Role.SUPERPEER;
import static io.left.rightmesh.proto.MeshDnsProtos.MeshRequest.Role.UNSET;

/**
 * This class writes to the database for any peer changed event and tracks
 * the devices and links in the network that's connected to the Superpeer.
 *
 * Created by rachel on 2018-07-09.
 */
public class Visualization implements PeerListener {
	private JavaMeshManager jmm;

	public Visualization() {
		jmm = new JavaMeshManager(true);
		jmm.registerAllPeerListener(this);

		Runnable visualizationTask = () -> {
			while(true)
			{
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					continue;
				}
				updateMaster();
			}
		};

		// start the thread
		new Thread(visualizationTask).start();
	}

	/**
	 * Get role from peer
	 * @param peerMeshId
	 * @return Role
	 */
	private MeshDnsProtos.MeshRequest.Role getRoleFromPeer(MeshId peerMeshId)
	{
		MeshDnsProtos.MeshRequest.Role role = UNSET;
		if (peerMeshId.equals(jmm.getUuid())) {
			role = SUPERPEER;
		} else {
			RoutingTable table = jmm.getTable();
			if (table == null)
				return role;

			MeshPeer peer = table.getPeer(peerMeshId);
			if (peer != null) {
				Route route = table.getRoute(peer.getMeshId(), false);
				if (route != null) {
					MeshLink link = peer.getLink(route.getInterfaceName(), route.getInterfaceType());
					if (link != null)
						role = link.getRole();
				}
			}
			//only way we should get unset role is if it a master thats behind a router
			if (role == UNSET)
				role = MASTER;
		}

		return role;
	}

	/**
	 * Peer changed handler
	 * @param peerMeshId
	 * @param state
	 */
	public void peerChanged(MeshId peerMeshId, int state)
	{
		if (state == REMOVED) {
			return;
		}

		String id = peerMeshId.toString().substring(2);

		//determine the role of the peer we just found
		MeshDnsProtos.MeshRequest.Role role = getRoleFromPeer(peerMeshId);

		boolean connected = true;
		if (state == REMOVED)
			connected = false;

		DatabaseManager.addPeer(id, role.getNumber(), connected);

		RoutingTable table = jmm.getTable();
		if (table != null) {
			Iterator<Map.Entry<MeshId, Route>> routes = table.getRoutes().entries().iterator();
			while (routes.hasNext()) {
				Map.Entry<MeshId, Route> route = routes.next();
				MeshId target = route.getKey();
				MeshId nextHop = route.getValue().getNextHopMeshId();
				if (target.equals(nextHop))
					target = jmm.getUuid();

				//skip the link to myself
				if (target.equals(nextHop))
					continue;

				String targetId = target.toString().substring(2);
				String nextHopId = nextHop.toString().substring(2);
				DatabaseManager.addLink(targetId, nextHopId);
			}
		}
	}

	/**
	 * Update database with superpeer information
	 */
	private void updateMaster()
	{
		String id = jmm.getUuid().toString().substring(2);
		DatabaseManager.addMasterAndCleanup(id);
	}
}