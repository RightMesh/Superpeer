#!/bin/bash
PARITY_DEB_URL=https://parity-downloads-mirror.parity.io/v1.8.6/x86_64-unknown-linux-gnu/parity_1.8.6_amd64.deb
PASSWORD=Password123

echo "home: $HOME"
echo "user: $(whoami)"

echo "Installing parity"

sudo apt-get update -qq
sudo apt-get install -y -qq curl

##################
# install parity #
##################
file=/tmp/parity.deb
curl -Lk $PARITY_DEB_URL > $file
sudo dpkg -i $file
rm $file


#####################
# create an account #
#####################
mkdir -p .parity/keys/kovan

cat > .parity/keys/kovan/key.json <<EOL
{"id":"0c9ad7b3-cc85-f81a-7991-05695a5495ad","version":3,"crypto":{"cipher":"aes-128-ctr","cipherparams":{"iv":"ce9b7b9741796b46c2ed494850781544"},"ciphertext":"86ff7819eeb9a7ccddbe781476b3d0268bdf6754d8253d3a523a208164197273","kdf":"pbkdf2","kdfparams":{"c":10240,"dklen":32,"prf":"hmac-sha256","salt":"9df3dfd806b5b4f95eaea6a4457ba806ae33c9cea5d55037029e83a6830ea278"},"mac":"3ff7df7e97bbbf845e7c5962e1522a1040319eb1fe4c510ff182b39e2cfa4309"},"address":"133e5245e3e5ab3f65e73120b34cc29f0f7ba504","name":"0c9ad7b3-cc85-f81a-7991-05695a5495ad","meta":"{}"}
EOL

echo $PASSWORD >> .parity-pass
sudo chown -R $(whoami):$(whoami) .parity
sudo chown $(whoami):$(whoami) .parity-pass

address="133e5245e3e5ab3f65e73120b34cc29f0f7ba504"
echo "address: $address"

# Run parity itself with the address and keys we just setup.
parity --geth --chain kovan --keys-path .parity/keys --force-ui --reseal-min-period 0 --jsonrpc-cors "*" --jsonrpc-apis web3,eth,net,parity,traces,rpc,personal --jsonrpc-interface all --author ${address} --unlock ${address} --password .parity-pass &> parity.log &
