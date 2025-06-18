import requests
import time

# Base raw Gist URL
BASE_RAW_URL = "https://gist.githubusercontent.com/athrvvvv/6196c0fc4d9426af69df7aee8f7481aa/raw/secrets.txt"

def get_latest_secret():
    try:
        # Add timestamp as a cache buster
        cache_buster_url = f"{BASE_RAW_URL}?t={int(time.time())}"
        response = requests.get(cache_buster_url)
        response.raise_for_status()
        return response.text.strip()
    except requests.RequestException as e:
        print(f"❌ Error fetching gist: {e}")
        return None

# Example usage
secret = get_latest_secret()
if secret:
    print("🔑 Latest secret:", secret)
else:
    print("⚠️ Failed to fetch updated secret.")
