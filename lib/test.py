url = "https://gist.githubusercontent.com/athrvvvv/c50b190a58f41948d0a6e968494823df/raw/1748962ea07fbc0384b9d51322d26e17e211a4f1/test.txt"
GIST_ID = "c50b190a58f41948d0a6e968494823df"
TOKEN = "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"

import requests

# response = requests.get(url)
# if response.status_code == 200:
#     content = response.text
#     print("File content:", content)
# else:
#     print("Failed to get file:", response.status_code)

url = f"https://api.github.com/gists/{GIST_ID}"

headers = {
    "Authorization": f"token {TOKEN}"
}

data = {
    "files": {
        "test-txt": {
            "content": "This is the new content, bro! Change it anytime."
        }
    }
}

response = requests.patch(url, json=data, headers=headers)

if response.status_code == 200:
    print("Gist updated successfully!")
else:
    print("Failed to update gist:", response.status_code, response.text)