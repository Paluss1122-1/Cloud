# chatgpt_send.py
import asyncio
import sys
from playwright.async_api import async_playwright

PROMPT = "Was ist die Hauptstadt von Frankreich?"  # hier anpassen

async def main():
    prompt = " ".join(sys.argv[1:]) if len(sys.argv) > 1 else PROMPT

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=False)
        page = await browser.new_page()

        await page.goto("https://chatgpt.com/", wait_until="domcontentloaded")
        print("Warte auf Login / Seite...")

        editor = page.locator("#prompt-textarea")
        await editor.wait_for(state="visible", timeout=60000)

        await editor.click()
        await editor.fill(prompt)

        send_btn = page.locator('[data-testid="send-button"]')
        await send_btn.wait_for(state="visible", timeout=10000)
        await send_btn.click()

        print("Abgeschickt. Warte auf Antwort...")

        # warten bis streaming fertig (Send-Button wieder aktiv)
        await page.wait_for_selector('[data-testid="send-button"]:not([disabled])', timeout=120000)

        # letzten Antwort-Block holen
        messages = await page.locator('[data-message-author-role="assistant"]').all()
        if messages:
            text = await messages[-1].inner_text()
            print("\n--- Antwort ---")
            print(text)
        else:
            print("Keine Antwort gefunden.")

        await browser.close()

asyncio.run(main())