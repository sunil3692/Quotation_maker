#!/usr/bin/env bash
# Downloads the PDF libraries and fonts into web/lib so the app works offline.
# Runs automatically in GitHub Actions; can also be run locally.
set -euo pipefail
cd "$(dirname "$0")/.."
LIB=web/lib
mkdir -p "$LIB/fonts"

curl -fsSL https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js -o "$LIB/html2canvas.min.js"
curl -fsSL https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js -o "$LIB/jspdf.umd.min.js"

# Google Fonts CSS (woff2), then download every font file and point the CSS at the local copies
UA='Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36'
FAMILIES='family=Mukta:wght@400;500;600;700&family=IBM+Plex+Sans:wght@400;500;600;700&family=IBM+Plex+Sans+Devanagari:wght@400;600&family=Lora:wght@400;600;700&family=Roboto+Condensed:wght@400;600;700&family=Poppins:wght@300;400;600;700&display=swap'
curl -fsSL -A "$UA" "https://fonts.googleapis.com/css2?${FAMILIES}" -o "$LIB/fonts.src.css"

python3 - "$LIB" <<'EOF'
import re, sys, os, urllib.request, hashlib
lib = sys.argv[1]
css = open(os.path.join(lib, 'fonts.src.css'), encoding='utf-8').read()
def grab(m):
    url = m.group(1)
    name = hashlib.sha1(url.encode()).hexdigest()[:16] + '.woff2'
    path = os.path.join(lib, 'fonts', name)
    if not os.path.exists(path):
        urllib.request.urlretrieve(url, path)
    return 'url(fonts/' + name + ')'
css = re.sub(r'url\((https://fonts\.gstatic\.com/[^)]+)\)', grab, css)
open(os.path.join(lib, 'fonts.css'), 'w', encoding='utf-8').write(css)
os.remove(os.path.join(lib, 'fonts.src.css'))
print('fonts:', len(os.listdir(os.path.join(lib, 'fonts'))))
EOF

echo "Assets ready in $LIB"
