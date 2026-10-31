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

LEVEL_ORDER = {
    "N5": 1,
    "N4": 2,
    "N3": 3,
    "N2": 4,
    "N1": 5
}

def clean_explanation(text):
    if not text:
        return text
    parts = re.split(r'\n*\s*Ví dụ\s*:', text, flags=re.IGNORECASE)
    cleaned = parts[0].strip()
    return cleaned

def clean_meaning(text):
    if not text:
        return ""
    # Take first definition split by semicolon or newline
    first_part = text.split(";")[0].strip()
    first_part = first_part.split("\n")[0].strip()
    return first_part.rstrip(" .")

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
                
                # Filter only items containing the kanji
                valid_items = [it for it in items if kanji in (it.get("word") or "")]
                
                # Sort key:
                # 1. JLPT rank (N5 > N4 > N3 > N2 > N1 > None)
                # 2. Length penalty (prefer 1-3 chars, 4 chars, >4 chars)
                # 3. Frequency weight (more negative = more frequent, e.g. -47900 < -100)
                def sort_key(it):
                    levels = it.get("level") or []
                    best_rank = min([LEVEL_ORDER.get(x, 10) for x in levels]) if levels else 10
                    word_str = it.get("word") or ""
                    word_len = len(word_str)
                    len_penalty = 0 if word_len <= 3 else (1 if word_len <= 5 else 2)
                    w = it.get("weight") if it.get("weight") is not None else 0
                    return (best_rank, len_penalty, w)

                valid_items.sort(key=sort_key)
                
                vocab_list = []
                seen_words = set()
                for item in valid_items:
                    word = (item.get("word") or "").strip()
                    if not word or word in seen_words:
                        continue
                    
                    phonetic = item.get("phonetic") or ""
                    phonetic_first = phonetic.split()[0] if phonetic else ""
                    
                    mean = clean_meaning(item.get("short_mean") or "")
                    if not mean:
                        # Fallback to means array if short_mean empty
                        means_arr = item.get("means") or []
                        if means_arr and isinstance(means_arr, list):
                            mean = clean_meaning(means_arr[0].get("mean") or "")
                            
                    if not mean:
                        continue
                        
                    seen_words.add(word)
                    vocab_list.append({
                        "word": word,
                        "reading": phonetic_first,
                        "meaning": mean
                    })
                    
                    if len(vocab_list) >= 10:
                        break
                        
                return vocab_list
        except Exception as e:
            if attempt == max_retries - 1:
                return []
            time.sleep(1 + attempt)
    return []

def load_progress():
    if os.path.exists(PROGRESS_FILE):
        try:
            with open(PROGRESS_FILE, "r", encoding="utf-8") as f:
                return set(json.load(f))
        except Exception:
            return set()
    return set()

def save_progress(processed_set):
    try:
        with open(PROGRESS_FILE, "w", encoding="utf-8") as f:
            json.dump(list(processed_set), f)
    except Exception as e:
        print(f"Error saving progress: {e}")

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
            
        # 2. Fetch vocabulary using new ranking
        vocab = fetch_top_vocab(kanji)
        if vocab:
            data["vocabulary"] = vocab
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            return kanji, True, len(vocab)
        else:
            return kanji, False, 0
    except Exception as e:
        return kanji, False, 0

def main():
    force_all = "--force" in sys.argv
    processed = set() if force_all else load_progress()
    
    files = glob.glob(os.path.join(METADATA_DIR, "*.json"))
    pending_files = [f for f in files if os.path.basename(f).replace(".json", "") not in processed]
    total = len(pending_files)
    print(f"Total pending files to process: {total} (Already completed: {len(processed)})")
    
    if total == 0:
        print("All files already enriched! Use --force to re-process all.")
        return
        
    completed_count = 0
    start_time = time.time()
    batch_counter = 0
    
    # 8 workers for balanced throughput without hitting rate limits
    with ThreadPoolExecutor(max_workers=8) as executor:
        futures = {executor.submit(process_file, f): f for f in pending_files}
        
        for future in as_completed(futures):
            completed_count += 1
            batch_counter += 1
            kanji, success, vocab_count = future.result()
            
            if success:
                processed.add(kanji)
                
            if batch_counter >= 50 or completed_count == total:
                save_progress(processed)
                batch_counter = 0
                elapsed = time.time() - start_time
                speed = completed_count / elapsed if elapsed > 0 else 0
                eta = (total - completed_count) / speed if speed > 0 else 0
                print(f"[{completed_count}/{total}] ({completed_count*100//total}%) "
                      f"Last: {kanji} (vocab: {vocab_count}) - "
                      f"Speed: {speed:.1f} files/s - ETA: {int(eta)}s")
                      
    save_progress(processed)
    print(f"All {total} files processed successfully!")

if __name__ == "__main__":
    main()
