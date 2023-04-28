#!/bin/bash

client_crypto_dir="client/deploy/crypto/"

for i in {1..5000}
do
  echo -ne 'client'$i'\n\n\n\n\n\nyes\n' | keytool -genkey -alias "$client_crypto_dir"client"$i" -keyalg RSA -validity 365 -keystore clients.ks -storetype pkcs12 -storepass password
done
