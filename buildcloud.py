import subprocess
import os

project_path = r"C:\Users\pauls\AndroidStudioProjects\Cloud"
gradle_cmd = os.path.join(project_path, "gradlew.bat")

# Use Android Studio's bundled JDK
java_home = r"C:\Program Files\Android\Android Studio\jbr"

env = os.environ.copy()
env["JAVA_HOME"] = java_home
env["PATH"] = os.path.join(java_home, "bin") + ";" + env["PATH"]

result = subprocess.run(
    [gradle_cmd, "assembleDebug"],
    cwd=project_path,
    capture_output=True,
    text=True,
    shell=True,
    env=env
)

print("STDOUT:", result.stdout)
print("STDERR:", result.stderr)
print("Exit Code:", result.returncode)