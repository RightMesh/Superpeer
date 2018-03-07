#!/bin/bash
if ! type parity > /dev/null; 
then
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
else 
    echo "Parity is already installed."
fi


if ! ps ax | grep parity | grep -v grep > /dev/null;
then
    echo "Launching parity ..."
    parity --geth --chain kovan --force-ui --reseal-min-period 0 --jsonrpc-cors "*" --jsonrpc-apis web3,eth,net,parity,traces,rpc,personal --jsonrpc-interface all &> parity.log &
fi
PID=`ps -ef | grep parity | grep -v grep | awk '{print $2}'`
echo "Parity is running with PID $PID"
