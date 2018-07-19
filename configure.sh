#!/bin/bash

# Install parity if this is a linux system
#
installLinux() {
    PARITY_DEB_URL=https://parity-downloads-mirror.parity.io/v1.8.6/x86_64-unknown-linux-gnu/parity_1.8.6_amd64.deb
    if ! type curl > /dev/null;
    then
        echo "Installing curl."
        sudo apt-get update -qq
        sudo apt-get install -y -qq curl
    else
        echo "Curl is already installed."
    fi
    echo "Installing parity"
    file=/tmp/parity.deb
    curl -Lk $PARITY_DEB_URL > $file
    sudo dpkg -i $file
    rm $file
}

# Install parity if this is a Darwin system (macOS)
#
installMac() {

    if  !type brew > /dev/null;
    then

        echo "This requires Brew."
        echo "Installing Homebew"
        /usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"

    fi

    echo "Installing parity"

    brew tap paritytech/paritytech
    brew install parity
}


# OS check and run correct installation
if ! type parity > /dev/null;
then
    unamestr=`uname`
    if [[ "$unamestr" == 'Darwin' ]]; then
       installMac;
    else
        installLinux
    fi

else
    echo "Parity is already installed."
fi


# Launch parity if it is not running already
if ! ps ax | grep parity | grep -v grep > /dev/null;
then
    echo "Launching parity ..."
    parity --geth --chain kovan --force-ui --reseal-min-period 0 --jsonrpc-cors "*" --jsonrpc-apis web3,eth,net,parity,traces,rpc,personal --jsonrpc-interface all &> parity.log &
fi
PID=`ps -ef | grep parity | grep -v grep | awk '{print $2}'`
echo "Parity is running with PID $PID"
