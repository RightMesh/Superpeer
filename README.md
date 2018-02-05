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

Don't forget when you clone the repo to update your credentials from 
[https://developer.rightmesh.io] in the [build.gradle](build.gradle) 
file.

# Setting up
If you want to run the parity client directly, run the `configure.sh`
script. This will install parity from .deb (along with all dependencies)
and start it up.

Otherwise you can uncomment the last few lines in the 
build.gradle script to run parity in vagrant. If you wish to run it
in vagrant, you'll need to install vagrant and virtualbox. This will
install the parity wallet within the vm so you don't accidentally
clobber your local wallet.

Todo: detect in gradle or use a set variable to specify whether we are
running on a local machine or AWS instance. Also add an option to set
a remote parity machine (if the superpeer wants to separate funciton).

# Steps to run on AWS
Install and get parity running
`./configure.sh`

Verify parity is running:
`ps ax | grep parity`

Compile against the latest library version
`./gradlew build --refresh-dependencies`

Create the binary to run the superpeer
`./gradlew installDist`

Run the superpeer
`./build/install/Superpeer/bin/Superpeer`

In your app, make sure you set the superpeer to the IP address of the AWS
instance:
`mm.setSuperPeer("IP");`
