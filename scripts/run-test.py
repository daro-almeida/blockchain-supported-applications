import os
import subprocess
import argparse
import time
import threading

server_server_base_port = 5000
client_server_base_port = 6000

cwd = os.getcwd()
pause = 10


def run_replicas(num: int, ops_block: int):
    for i in range(1, num + 1):
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
            "-v", f"{cwd}/metrics/results:/usr/local/metrics/results/",
            "-w", "/usr/local/",
            "openjdk:17",

            "java", "-Xmx8G", "-ea",
            "-Dlog4j.configurationFile=log4j2.xml", f"-DlogFilename=node{i}",
            "-jar", "server.jar",
            f"id={i}",
            f"server_server_port={server_server_base_port + i}",
            f"client_server_port={client_server_base_port + i}",
            f"crypto_name=node{i}",
            "bootstrap_primary_id=1",
            f"metrics_name=node{i}",
            f"max_ops_per_block={ops_block}",
        ]

        subprocess.Popen(cmd)


def run_clients(num: int, ops_sec: int):
    if num == 0:
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
        "-v", f"{cwd}/metrics/results:/usr/local/metrics/results/",
        "-w", "/usr/local/",
        "openjdk:17",

        "java", "-ea",
        "-Dlog4j.configurationFile=log4j2.xml", f"-DlogFilename=clients",
        "-jar", "clients.jar",
        f"clients={num}",
        "metrics_name=clients",
        f"ops_sec={ops_sec}"
    ]
    subprocess.Popen(cmd)


def wait_for_enter():
    input("Press Enter to terminate.")


def wait_for_time(duration):
    print(f"Running for {duration}s.")
    time.sleep(duration)


if '__main__' == __name__:
    parser = argparse.ArgumentParser()
    parser.add_argument("-r", "--replicas", type=int, default=4)
    parser.add_argument("-c", "--clients", type=int, default=5)
    parser.add_argument("-d", "--duration", type=int, default=0)
    parser.add_argument("-o", "--ops_sec", type=int, default=10)
    parser.add_argument("-b", "--ops_per_block", type=int, default=0)

    args = parser.parse_args()

    print(f"Starting {args.replicas} replicas")
    run_replicas(args.replicas, args.ops_per_block)
    print(f"Waiting {pause}s to start clients")
    time.sleep(pause)
    print(f"Starting {args.clients} clients + 1 exchange")
    run_clients(args.clients, args.ops_sec)

    if args.duration <= 0:
        wait_for_enter()
    else:
        wait_for_time(args.duration)

    subprocess.run("docker kill $(docker ps -q)", shell=True)
