import asyncio
import base64
import httpx
from playwright.async_api import async_playwright
import os
import json

USERNAME = "paul.schoettl23@gmail.com"
PASSWORD = "7C_Paluss1122_EM"
OLLAMA_URL = "http://localhost:11434/api/generate"
COOKIES_PATH = "schulmanager_cookies.json"

async def get_stundenplan_screenshot() -> str:
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        context = await browser.new_context()

        if os.path.exists(COOKIES_PATH):
            cookies = json.loads(open(COOKIES_PATH).read())
            await context.add_cookies(cookies)

        page = await context.new_page()
        await page.goto("https://login.schulmanager-online.de/#/modules/schedules/view//")
        await page.wait_for_timeout(2000)

        if "login" in page.url or "emailOrUsername" in await page.content():
            await page.fill('input[name="emailOrUsername"]', USERNAME)
            await page.fill('input[name="password"]', PASSWORD)
            await page.click('button.btn-primary')
            await page.wait_for_timeout(3000)
            cookies = await context.cookies()
            open(COOKIES_PATH, "w").write(json.dumps(cookies))

        await page.wait_for_timeout(3000)
        screenshot = await page.screenshot(full_page=False)
        await browser.close()
        return base64.b64encode(screenshot).decode("utf-8")

def analyze_with_ollama(image_b64: str) -> str:
    payload = {
        "model": "llama3.2-vision:11b",
        "prompt": (
            "Das ist ein Screenshot meines Stundenplans für heute. "
            "Liste alle Stunden mit Uhrzeit, Fach, Lehrer und Raum auf. "
            "Falls eine Stunde ausfällt oder eine Vertretung eingetragen ist, markiere das deutlich. "
            "Antworte auf Deutsch, kurz und strukturiert."
        ),
        "images": [image_b64],
        "stream": False,
    }
    with httpx.Client(timeout=None) as client:
        r = client.post(OLLAMA_URL, json=payload)
        r.raise_for_status()
        return r.json()["response"]

async def main():
    print("Screenshot wird gemacht...")
    img = await get_stundenplan_screenshot()
    print("Ollama analysiert...")
    result = analyze_with_ollama(img)
    print("\n--- Stundenplan heute ---")
    print(result)

asyncio.run(main())