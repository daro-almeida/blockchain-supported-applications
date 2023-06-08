import argparse
import json
import os
import subprocess
import time

server_server_base_port = 5000
client_server_base_port = 6000

cwd = os.getcwd()
pause = 10


def run_replicas(num: int, ops_block: int, ignore_request_block: dict, invalid_block: int, folder: str):
    for i in range(1, num + 1):
        if str(i) in ignore_request_block:
            ignore_block = int(ignore_request_block[str(i)])
        else:
            ignore_block = -1

        cmd = [
            "docker", "run",
            f"--name=toolbox_server{i}",
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
            "-cp", "server.jar", "app.toolbox.Toolbox",
            f"id={i}",
            f"server_server_port={server_server_base_port + i}",
            f"client_server_port={client_server_base_port + i}",
            f"crypto_name=node{i}",
            "bootstrap_primary_id=1",
            f"metrics_name=node{i}",
            f"max_ops_per_block={ops_block}",
            f"metrics_folder={folder}"
        ]
        if ignore_block >= 0:
            cmd.append(f"ignore_request_block={ignore_block}")
        if invalid_block >= 0 and i == 1:
            cmd.append(f"invalid_block={invalid_block}")

        subprocess.Popen(cmd)


def run_clients(num: int, ops_sec: int, folder: str, epsilon: float):
    if num == 0:
        return

    cmd = [
        "docker", "run",
        f"--name=toolbox_clients",
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
        "-cp", "clients.jar", "app.toolbox.ToolboxClient",
        f"clients={num}",
        "metrics_name=clients",
        "server_proto=600",
        f"ops_sec={ops_sec}",
        f"metrics_folder={folder}",

        f"epsilon={epsilon}"
    ]
    subprocess.Popen(cmd)


def wait_for_enter():
    input("Press Enter to terminate.")


def wait_for_time(duration):
    print(f"Running for {duration}s.")
    time.sleep(duration)


if '__main__' == __name__:
    parser = argparse.ArgumentParser()
    parser.add_argument("-c", "--clients", type=int, default=16, help="Number of clients")
    parser.add_argument("-d", "--duration", type=int, default=0, help="Duration of the test in seconds, if 0 wait for Enter press")
    parser.add_argument("-o", "--ops_sec", type=int, default=10, help="Number of operations per second per client")
    parser.add_argument("-b", "--ops_per_block", type=int, default=50, help="Number of operations per block")
    parser.add_argument("-f", "--folder", type=str, default="metrics/results/", help="Folder to store metrics")
    parser.add_argument("-ir", "--ignore_request_block", type=json.loads, default={}, help="JSON map format. Key is replica id, value is block number to ignore requests (as leader) from other replicas. Example: '{\"1\": 10, \"2\": 50}'")
    parser.add_argument("-ib", "--invalid_block", type=int, default=-1, help="Block number to send invalid block to other replicas (as leader) (only for replica 1)")
    parser.add_argument("-e", "--epsilon", type=float, default=1.0, help="Epsilon value for DP")

    args = parser.parse_args()

    print(f"Starting 4 replicas")
    run_replicas(4, args.ops_per_block, args.ignore_request_block, args.invalid_block, args.folder)
    print(f"Waiting {pause}s to start clients")
    time.sleep(pause)
    print(f"Starting {args.clients} clients")
    run_clients(args.clients, args.ops_sec, args.folder, args.epsilon)

    if args.duration <= 0:
        wait_for_enter()
    else:
        wait_for_time(args.duration)

    subprocess.run("docker kill $(docker ps -q)", shell=True)
