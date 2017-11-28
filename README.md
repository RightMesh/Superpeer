# Superpeer
This minimalist RM superpeer provides one very simple function right 
now, which is to enable the forwarding of data packets from one
geographically separate mesh to another. The only call required from the
developer is during construction of the JavaMeshManager. Rather than
starting with no parameters, the developer should pass the value `true`
which sets the device into `superpeer` mode.

Currently, RM is set by default to use superpeers which we operate at
`research.rightmesh.io`, however a developer may wish to operate their
own superpeer as well if they wish to connect a mesh app on phones to
their own app superpeer. This would be useful for example, if
developers wanted to visualize data coming from the mesh using a
traditional website, or create some interaction between the web and the
mesh.

In this case, they can use the developer
library and set the superpeer themselves with the mm.setSuperPeer
function (and pass the IP address or hostname of the superpeer).

In the longer term, the production library will not support this
function, however, developers will be able to determine the MeshID of
their superpeer(s) and use those to let the Mesh determine the best way
to reach the superpeer(s).

