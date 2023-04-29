import os
import subprocess
import sys
import argparse
import time

server_server_base_port = 5000
client_server_base_port = 6000

cwd = os.getcwd()


def run_replicas(n: int):
    for i in range(1, n + 1):
        cmd = [
            "docker", "run",
            f"--name=open-goods_server{i}",
            "--rm",
            # "-itd",
            "--network=host",
            "-v", f"{cwd}/server/deploy/server.jar:/usr/local/server.jar",
            "-v", f"{cwd}/server/deploy/config.properties:/usr/local/config.properties",
            "-v", f"{cwd}/server/deploy/log4j2.xml:/usr/local/log4j2.xml",
            "-v", f"{cwd}/server/deploy/crypto/node{i}.ks:/usr/local/crypto/node{i}.ks",
            "-v", f"{cwd}/server/deploy/crypto/truststore.ks:/usr/local/crypto/truststore.ks",
            "-v", f"{cwd}/logs:/usr/local/logs/",
            "-w", "/usr/local/",
            "openjdk:17",

            "java", "-Xmx8G", "-ea",
            "-Dlog4j.configurationFile=log4j2.xml", f"-DlogFilename=node{i}",
            "-jar", "server.jar",
            f"id={i}",
            f"server_server_port={server_server_base_port + i}",
            f"client_server_port={client_server_base_port + i}",
            f"crypto_name=node{i}",
            "bootstrap_primary_id=1"
        ]

        subprocess.Popen(cmd)


def run_clients(n: int):
    if n == 0:
        return

    cmd = [
        "docker", "run",
        f"--name=open-goods_clients",
        "--rm",
        # "-itd",
        "--network=host",
        "-v", f"{cwd}/client/deploy/clients.jar:/usr/local/clients.jar",
        "-v", f"{cwd}/client/deploy/config.properties:/usr/local/config.properties",
        "-v", f"{cwd}/client/deploy/log4j2.xml:/usr/local/log4j2.xml",
        "-v", f"{cwd}/client/deploy/crypto/:/usr/local/crypto/",
        "-v", f"{cwd}/logs:/usr/local/logs/",
        "-w", "/usr/local/",
        "openjdk:17",

        "java", "-ea",
        "-Dlog4j.configurationFile=log4j2.xml", f"-DlogFilename=clients",
        "-jar", "clients.jar",
        f"clients={n}"
    ]
    subprocess.Popen(cmd)


if '__main__' == __name__:
    parser = argparse.ArgumentParser()
    parser.add_argument("-r", "--replicas", type=int, default=4)
    parser.add_argument("-c", "--clients", type=int, default=5)

    args = parser.parse_args()
    num_replicas = args.replicas
    num_clients = args.clients

    print(f"Starting {num_replicas} replicas")
    run_replicas(num_replicas)
    print("Waiting 5s to start clients")
    time.sleep(5)
    print(f"Starting {num_clients} clients")
    run_clients(num_clients)

    print("Press Enter to terminate")
    input()
    subprocess.run("docker kill $(docker ps -q)", shell=True)
