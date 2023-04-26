#!/bin/bash

crypto_path="deploy/crypto/"

keystore_password="password"
key_password="password"
truststore_password="password"
key_alg="RSA"
validity_days=365

# Define a function that takes an argument
create_keystore_and_certificate() {
  alias="client$1"
  dname="CN=$alias"
  # Remove existing keystores and certificates
  rm -f "$crypto_path$alias".ks > /dev/null
  rm -f "$crypto_path$alias".cert > /dev/null
  # Generate key
  keytool -genkey \
    -alias $alias \
    -keyalg $key_alg \
    -validity $validity_days \
    -dname "$dname" \
    -storetype pkcs12 \
    -keystore "$crypto_path$alias".ks \
    -storepass $keystore_password \
    -keypass $key_password \
    -noprompt > /dev/null
  # Export certificate
  keytool -exportcert \
    -alias $alias \
    -file "$crypto_path$alias".cert \
    -keystore "$crypto_path$alias".ks \
    -storepass $keystore_password \
    -noprompt > /dev/null
}

store_certificate () {
  alias="client$1"
  # Remove existing certificate from truststore
  keytool -delete \
    -alias $alias \
    -keystore "$crypto_path"truststore.ks \
    -storepass $truststore_password \
    -noprompt > /dev/null
  # Import certificate into truststore
  keytool -importcert \
    -alias $alias \
    -file "$crypto_path$alias".cert \
    -keystore "$crypto_path"truststore.ks \
    -storepass $truststore_password \
    -noprompt > /dev/null
}

echo "Enter number of clients:"
read N

echo "client/node? (client)"
read TYPE
# if not node or client, default to client
if [ "$TYPE" != "node" ]; then
  TYPE="client"
fi

# Loop through a range of incrementing indices using a for loop
for (( i=1; i<=$N; i++ ))
do
  create_keystore_and_certificate $i &
done
wait

# Loop through a range of incrementing indices using a for loop
for (( i=1; i<=$N; i++ ))
do
  store_certificate $i
done
wait