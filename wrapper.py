# run_review.py
import subprocess
import sys
import time

while True:
    result = subprocess.run([sys.executable, r"C:\Users\Paul\AndroidStudioProjects\Tabslify\postcommit.py"])
    if result.returncode == 0:
        break
    print(f"[wrapper] crashed (code {result.returncode}), restarting in 5s...")
    time.sleep(5)