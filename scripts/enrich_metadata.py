import os
import sys
import json
import glob
import re
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

METADATA_DIR = "app/src/main/assets/metadata"
PROGRESS_FILE = "scripts/progress.json"

def clean_explanation(text):
    if not text:
        return text
    # Split by "Ví dụ:" or "ví dụ:" case-insensitive
    parts = re.split(r'\n*\s*Ví dụ\s*:', text, flags=re.IGNORECASE)
    cleaned = parts[0].strip()
    return cleaned

def fetch_top_vocab(kanji):
    url = "https://mazii.net/api/search"
    payload = json.dumps({
        "query": kanji,
        "dict": "javi",
        "type": "word"
    }).encode("utf-8")
    
    headers = {
        "Content-Type": "application/json",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
    
    req = urllib.request.Request(url, data=payload, headers=headers)
    
    max_retries = 3
    for attempt in range(max_retries):
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                items = data.get("data", [])
                
                vocab_list = []
                for item in items:
                    word = item.get("word") or ""
                    # Ensure the word contains the kanji
                    if kanji not in word:
                        continue
                    
                    phonetic = item.get("phonetic") or ""
                    # Pick first reading if multiple space-separated
                    phonetic_first = phonetic.split()[0] if phonetic else ""
                    
                    mean = item.get("short_mean") or ""
                    # Clean up meaning, pick first phrase or clean semicolon
                    mean_first = mean.split(";")[0].strip() if mean else ""
                    
                    if not mean_first and mean:
                        mean_first = mean.strip()
                        
                    vocab_list.append({
                        "word": word,
                        "reading": phonetic_first,
                        "meaning": mean_first
                    })
                    
                    if len(vocab_list) >= 10:
                        break
                        
                return vocab_list
        except Exception as e:
            if attempt == max_retries - 1:
                print(f"[Error] Failed to fetch vocab for {kanji}: {e}")
                return []
            time.sleep(1 + attempt)
    return []

def process_file(filepath):
    filename = os.path.basename(filepath)
    kanji = filename.replace(".json", "")
    
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            data = json.load(f)
            
        # 1. Clean explanation
        exp = data.get("explanation")
        if isinstance(exp, dict) and "text" in exp and exp["text"]:
            cleaned_text = clean_explanation(exp["text"])
            exp["text"] = cleaned_text
            data["explanation"] = exp
            
        # 2. Check if vocabulary already exists
        if "vocabulary" in data and len(data["vocabulary"]) > 0:
            # Already processed
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            return kanji, True, len(data["vocabulary"])
            
        # 3. Fetch vocabulary
        vocab = fetch_top_vocab(kanji)
        data["vocabulary"] = vocab
        
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            
        return kanji, True, len(vocab)
    except Exception as e:
        print(f"[Exception] File {filepath}: {e}")
        return kanji, False, 0

def main():
    files = glob.glob(os.path.join(METADATA_DIR, "*.json"))
    total = len(files)
    print(f"Total files to process: {total}")
    
    # Process concurrently with ThreadPoolExecutor
    completed_count = 0
    start_time = time.time()
    
    # 5 workers to be polite to the API and avoid getting rate-limited
    with ThreadPoolExecutor(max_workers=5) as executor:
        futures = {executor.submit(process_file, f): f for f in files}
        
        for future in as_completed(futures):
            completed_count += 1
            kanji, success, vocab_count = future.result()
            
            if completed_count % 50 == 0 or completed_count == total:
                elapsed = time.time() - start_time
                speed = completed_count / elapsed if elapsed > 0 else 0
                eta = (total - completed_count) / speed if speed > 0 else 0
                print(f"[{completed_count}/{total}] ({completed_count*100//total}%) "
                      f"Last: {kanji} (vocab: {vocab_count}) - "
                      f"Speed: {speed:.1f} files/s - ETA: {int(eta)}s")
                      
    print(f"All {total} files processed successfully!")

if __name__ == "__main__":
    main()
