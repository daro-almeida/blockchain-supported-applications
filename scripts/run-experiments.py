import subprocess
import time

BASE_FOLDER = "metrics/results/"

threads = 16
ops_sec = [5, 10, 20, 40, 80, 160, 320, 480, 640, 700, 860, 1000]
faulty_runs = []
faulty_runs.append("-ir '{\"1\": \"10\"}'")
faulty_runs.append("-ir '{\"1\": \"10\", \"2\": \"50\"}'")
faulty_runs.append("-ib 10")
runs = 5

duration = 180

if __name__ == '__main__':
    for j in range(0, len(ops_sec)):
        for k in range(1, runs+1):
            folder = f"{BASE_FOLDER}16c_{ops_sec[j]}ops_{k}/"

            subprocess.run(["python3", "scripts/run-test.py", "-c", f"{threads - 1}", "-o", f"{ops_sec[j]}",
                            "-b", f"{int((threads * ops_sec[j] * 0.75) / 2)}", "-f", folder, "-d", f"{duration}"])
            time.sleep(5)

    for i in range(1, 4):
        for j in range(1, runs+1):
            folder = f"{BASE_FOLDER}16c_40ops_fr{i}_{j}/"
            subprocess.run(f"python3 scripts/run-test.py -c {threads - 1} -o 5 -b 60 -d {duration} "
                           f"{faulty_runs[i-1]} -f folder", shell=True)
            time.sleep(5)