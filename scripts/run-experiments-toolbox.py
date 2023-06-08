import subprocess
import time

BASE_FOLDER = "metrics/results/"

epsilons = [0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 4.0, 6.0, 8.0, 10.0]
clients = 5000

duration = 180

if __name__ == '__main__':
    for e in epsilons:
        folder = f"{BASE_FOLDER}{clients}c_{e}e/"

        subprocess.run(["python3", "scripts/run-toolbox.py", "-c", f"{clients}", "-f", folder,
                        "-d", f"{duration}", "-e", f"{e}"])
        time.sleep(5)
