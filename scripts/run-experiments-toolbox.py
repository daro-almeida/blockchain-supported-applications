import subprocess
import time

BASE_FOLDER = "metrics/results/"

# 0.25 - 4.0 with 0.25 step
epsilons = [0.25, 0.5, 0.75, 1.0, 1.25, 1.5,
            1.75, 2.0, 2.25, 2.5, 2.75, 3.0,
            3.25, 3.5, 3.75, 4.0]

clients = 5000

duration = 120

if __name__ == '__main__':
    for e in epsilons:
        folder = f"{BASE_FOLDER}{clients}c_{e}e/"

        subprocess.run(["python3", "scripts/run-toolbox.py", "-c", f"{clients}", "-f", folder,
                        "-d", f"{duration}", "-e", f"{e}"])
        time.sleep(5)
