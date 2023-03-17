import os
import subprocess
import sys

base_port = 5000

if '__main__' == __name__:
    if len(sys.argv) < 2:
        num_replicas = 4
    else:
        num_replicas = int(sys.argv[1])

    for i in range(1, num_replicas+1):
        cwd = os.getcwd()
        cmd = [
            "docker", "run",
            f"--name=ogmrs_{i}",
            "--rm",
            #"-itd",
            "--network=host",
            "-v", f"{cwd}/deploy/csd2223-proj1.jar:/usr/local/csd2223-proj1.jar",
            "-v", f"{cwd}/deploy/config.properties:/usr/local/config.properties",
            "-v", f"{cwd}/deploy/log4j2.xml:/usr/local/log4j2.xml",
            "-v", f"{cwd}/deploy/crypto:/usr/local/crypto/",
            "-v", f"{cwd}/logs:/usr/local/logs/",
            "-w", "/usr/local/",
            "openjdk:17",

            "java",
            "-Dlog4j.configurationFile=log4j2.xml", f"-DlogFilename=node{i}",
            "-jar", "csd2223-proj1.jar",
            f"base_port={base_port + i}",
            f"crypto_name=node{i}"]

        if i == 0:
            cmd.append("bootstrap_primary=true")
        subprocess.Popen(cmd)
        #subprocess.Popen(cmd, start_new_session=True, close_fds=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    print("Press Enter to terminate all replicas")
    input()
    subprocess.run("docker kill $(docker ps -q)", shell=True)