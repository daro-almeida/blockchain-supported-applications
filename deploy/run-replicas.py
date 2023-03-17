import sys
import subprocess

base_port = 5000

if '__main__' == __name__:
    if len(sys.argv) < 2:
        num_replicas = 4
    else:
        num_replicas = int(sys.argv[1])

    for i in range(1, num_replicas+1):
        cmd = ["java",
               "-Dlog4j.configurationFile=log4j2.xml", f"-DlogFilename=node{i}",
               "-jar", "csd2223-proj1.jar",
               f"base_port={base_port + i}",
               f"crypto_name=node{i}"]
        if i == 0:
            cmd.append("bootstrap_leader=true")
        subprocess.Popen(cmd)

    print("Press Enter to terminate all replicas")
    input()
    subprocess.run("kill $(ps aux | grep 'csd2223-proj1.jar' | awk '{print $2}')", shell=True)


