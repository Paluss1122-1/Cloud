import subprocess
import sys
import requests
import os

try:
    import msvcrt
    WINDOWS = True
except ImportError:
    import tty, termios
    WINDOWS = False

OLLAMA_MODEL = "qwen2.5-coder:3b"
OLLAMA_URL = "http://localhost:11434/api/generate"

def get_repo_root():
    result = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True)
    if result.returncode != 0:
        print("Kein Git-Repo gefunden.")
        sys.exit(1)
    return result.stdout.strip()

def get_changed_files(repo_root):
    staged = subprocess.run(["git", "diff", "--cached", "--name-only"], capture_output=True, text=True, cwd=repo_root).stdout.strip()
    unstaged = subprocess.run(["git", "diff", "--name-only"], capture_output=True, text=True, cwd=repo_root).stdout.strip()
    files = []
    for f in staged.splitlines() + unstaged.splitlines():
        if f and f not in files:
            files.append(f)
    return files

def get_diff(filepath, repo_root):
    diff = subprocess.run(["git", "diff", "--cached", filepath], capture_output=True, text=True, cwd=repo_root).stdout.strip()
    if not diff:
        diff = subprocess.run(["git", "diff", filepath], capture_output=True, text=True, cwd=repo_root).stdout.strip()
    return diff

def generate_message(diff):
    prompt = f"Generate a short imperative git commit message in past tense (max 72 chars, no quotes, no explanation, verbs must be past tense e.g. 'Updated', 'Fixed', 'Added') for this diff:\n\n{diff[:3000]}"
    response = requests.post(OLLAMA_URL, json={"model": OLLAMA_MODEL, "prompt": prompt, "stream": False})
    return response.json()["response"].strip().strip('"\'')

def read_key():
    if WINDOWS:
        key = msvcrt.getch()
        if key == b'\xe0':
            key2 = msvcrt.getch()
            if key2 == b'H': return 'UP'
            if key2 == b'P': return 'DOWN'
        if key == b'\r': return 'ENTER'
        if key == b'\x1b': return 'ESC'
    else:
        fd = sys.stdin.fileno()
        old = termios.tcgetattr(fd)
        try:
            tty.setraw(fd)
            key = sys.stdin.read(1)
            if key == '\x1b':
                key += sys.stdin.read(2)
                if key == '\x1b[A': return 'UP'
                if key == '\x1b[B': return 'DOWN'
            if key == '\r': return 'ENTER'
            if key == '\x1b': return 'ESC'
        finally:
            termios.tcsetattr(fd, termios.TCSADRAIN, old)
    return None

def draw_menu(files, selected):
    os.system('cls' if WINDOWS else 'clear')
    print("🔍 Uncommitete Dateien — ↑↓ navigieren, Enter wählen, ESC beenden\n")
    for i, f in enumerate(files):
        if i == selected:
            print(f"  ▶ {f}")
        else:
            print(f"    {f}")

def main():
    repo_root = get_repo_root()
    files = get_changed_files(repo_root)

    if not files:
        print("Keine uncommiteten Änderungen gefunden.")
        sys.exit(0)

    selected = 0
    while True:
        draw_menu(files, selected)
        key = read_key()
        if key == 'UP':
            selected = (selected - 1) % len(files)
        elif key == 'DOWN':
            selected = (selected + 1) % len(files)
        elif key == 'ENTER':
            filepath = files[selected]
            os.system('cls' if WINDOWS else 'clear')
            print(f"📄 {filepath}\nGeneriere Commit Message...")
            diff = get_diff(filepath, repo_root)
            if not diff:
                print("Keine Änderungen gefunden.")
                input("\nEnter zum Fortfahren...")
                continue
            message = generate_message(diff)
            print(f"\n✨ {message}\n")
            answer = input("Committen? [y/N] ").lower()
            if answer == 'y':
                subprocess.run(["git", "add", filepath], cwd=repo_root, capture_output=True, text=True)
                result = subprocess.run(["git", "commit", "-m", message], cwd=repo_root, capture_output=True, text=True)
                files = get_changed_files(repo_root)
                if not files:
                    print("Alle Änderungen committet!")
                    break
                selected = min(selected, len(files) - 1)
        elif key == 'ESC':
            break

import traceback

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\nFEHLER: {e}")
        traceback.print_exc()
        input("\nEnter zum Schließen...")