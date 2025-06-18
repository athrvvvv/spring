import requests

GIST_ID = "7aeea27e2f06bbcfa5b0937f4929383a"
FILE_NAME = "counter.txt"
TOKEN = "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"

headers = {
    "Authorization": f"token {TOKEN}",
    "Accept": "application/vnd.github.v3+json"
}

# Get current number from API
response = requests.get(f"https://api.github.com/gists/{GIST_ID}", headers=headers)
if response.status_code != 200:
    print("Failed to get gist content")
    exit()

gist_data = response.json()
current_content = gist_data['files'][FILE_NAME]['content'].strip()

try:
    current_number = int(current_content)
except ValueError:
    print("Content is not a valid integer")
    exit()

print("Current number:", current_number)

# Increment
new_number = current_number + 1

# Update gist
data = {
    "files": {
        FILE_NAME: {
            "content": str(new_number)
        }
    }
}

update_response = requests.patch(f"https://api.github.com/gists/{GIST_ID}", json=data, headers=headers)
if update_response.status_code == 200:
    print("Gist updated successfully to", new_number)
else:
    print("Failed to update gist:", update_response.status_code, update_response.text)
