# RightMesh Superpeer

This minimalist RightMesh "Superpeer" implementation provides two functionalities: enabling the forwarding of data packets from one geographically separate mesh to another, and facilitating transactions involving RightMesh Tokens. The second functionality includes recording transactions to the Ethereum blockchain.

## Running the Superpeer

Superpeer is a Gradle application - it can be built with `gradlew build`, and a binary can be generated with `gradlew installDist`.

If run without arguments Superpeer polls STDIN for input - typing `exit` will shut down RightMesh and stop the application. Unless you are developing/debugging, you will likely want to run Superpeer with the `-h | --headless` flag, which doesn't poll for input and responds to SIGINT signals (e.g. can be killed cleanly with `Ctrl+C` or task managers).

## Connecting to your Superpeer

By default RightMesh devices with internet connections will connect to a Superpeer operated by RightMesh at `research.rightmesh.io`. In the future the goal is to have a network of Superpeers, with Superpeers implemented and operated by both RightMesh and community members.

If you would like to have devices use your Superpeer instance for testing, you can hard-code its address into a RightMesh application to make it use your instance instead of the mainnet. You can do this by passing the address as a parameter to a special MeshManager constructor that is available in the developer version of the library. Note that this will never be an option on the mainnet - all RightMesh applications will use the same network, so that the owner of the device can specify their preferences for where/when/how to connect to Superpeers.

If you would like to send data to a Java application that makes use of RightMesh in a production application, you will need to find out its MeshID and send data to it through the RightMesh library.

## Running the Superpeer as a Service

This was all tested with the following:
* Ubuntu 16.04.4 LTS
* Java 8
* Parity 1.8.6
* RightMesh library 0.7.0

This repo contains everything needed to set up a Superpeer as a service on Ubuntu. These steps will help you get started:

1. Set up an Amazon EC2 Instance running Ubuntu
    - A Micro instance is fine, or any other Ubuntu server
    - Make sure port 22/TCP is open for SSH, 40,000/UDP for the Superpeer, and both 30303/TCP and 30303/UDP for Parity to communicate with the Ethereum network.
2. SSH Into the server.
    - `ssh ubuntu@your.ip.address.here`
3. Install Java
    - `sudo add-apt-repository ppa:webupd8team/java`
    - `sudo apt-get update`
    - `sudo apt-get install oracle-java8-installer`
4. Clone the Superpeer repository and navigate to the directory.
    - `git clone https://github.com/rightmesh/superpeer`
    - `cd superpeer`
5. Update build.gradle with your credentials from the [RightMesh developer portal](https://developer.rightmesh.io/keys/).
6. Install and configure Parity
    - `./configure.sh`
    - To verify parity is running:
    - `ps ax | grep parity`
7. Compile against the latest library version
    - `./gradlew build --refresh-dependencies`
8. Build the Superpeer binary.
    - `./gradlew installDist`
9. Copy the systemctl unit to the proper directory.
    - _Note: if you cloned the Superpeer repo anywhere other than `/home/ubuntu/superpper` you will need to update the file first with the correct path._
    - `sudo cp superpeer.service /etc/systemd/system/`
10. Enable the superpeer systemctl unit
    - `sudo systemctl enable superpeer`

## Interacting with a Superpeer

Once the Superpeer is registered as a systemctl unit, it can be started, stopped and otherwise manipulated through systemctl. The commands are straightforward:

- To start the Superpeer:
    - `sudo systemctl start superpeer`
- To stop the Superpeer:
    - `sudo systemctl stop superpeer`
- And to restart the Superpeer:
    - `sudo systemctl restart superpeer`

STDIN is automatically captured and stored by Systemctl. The logs can be viewed and filtered using the journalctl command.

- To view the logs from the beginning:
    - `sudo journalctl -u superpeer.service`
- To tail the logs:
    - `sudo journalctl -u superpeer.service -fe`


## Running Parity

If you want to run the Parity client directly, run the `configure.sh`
script. This will install Parity from .deb (along with all dependencies)
and start it up.

Otherwise you can uncomment the last few lines in the 
build.gradle script to run Parity in Vagrant. If you wish to run it
in Vagrant, you'll need to install Vagrant and VirtualBox. This will
install the Parity wallet within the virtual machine so you don't accidentally
clobber your local wallet.
