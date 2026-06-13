from flask import Flask, request, jsonify, Response, send_from_directory
import csv
import os
import json
import time
import queue
import threading

app = Flask(__name__)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CSV_FILE = os.path.join(BASE_DIR, 'guests.csv')
STATIC_DIR = os.path.join(BASE_DIR, 'static')

# SSE broadcast queue - mỗi TV client có queue riêng
sse_clients = []
sse_lock = threading.Lock()

FIELDNAMES = ['nfc_id', 'ho_ten', 'loi_chao', 'ghi_chu']

# In-memory check-in history (resets on server restart)
checkin_history = []
checkin_history_lock = threading.Lock()

# ─── CSV helpers ────────────────────────────────────────────

def load_guests():
    if not os.path.exists(CSV_FILE):
        return []
    with open(CSV_FILE, newline='', encoding='utf-8') as f:
        return list(csv.DictReader(f))

def save_guests(guests):
    with open(CSV_FILE, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=FIELDNAMES)
        writer.writeheader()
        writer.writerows(guests)

def find_guest(nfc_id):
    for g in load_guests():
        if g['nfc_id'].strip().upper() == nfc_id.strip().upper():
            return g
    return None

# ─── SSE helpers ────────────────────────────────────────────

def broadcast(data: dict):
    msg = f"data: {json.dumps(data, ensure_ascii=False)}\n\n"
    with sse_lock:
        for q in sse_clients:
            q.put(msg)

# ─── Routes: TV ─────────────────────────────────────────────

@app.route('/')
def index():
    return send_from_directory(STATIC_DIR, 'index.html')

@app.route('/stream')
def stream():
    q = queue.Queue()
    with sse_lock:
        sse_clients.append(q)

    def event_stream():
        try:
            while True:
                try:
                    msg = q.get(timeout=15)
                    yield msg
                except queue.Empty:
                    yield ": heartbeat\n\n"
        finally:
            with sse_lock:
                if q in sse_clients:
                    sse_clients.remove(q)

    resp = Response(event_stream(), mimetype='text/event-stream')
    resp.headers['Cache-Control'] = 'no-cache'
    resp.headers['X-Accel-Buffering'] = 'no'
    return resp

# ─── Routes: NFC check-in ────────────────────────────────────

@app.route('/checkin', methods=['POST'])
def checkin():
    data = request.get_json()
    if not data or 'nfc_id' not in data:
        return jsonify({'success': False, 'error': 'Thiếu nfc_id'}), 400

    nfc_id = data['nfc_id'].strip().upper()
    guest = find_guest(nfc_id)

    if not guest:
        broadcast({
            'type': 'unknown',
            'nfc_id': nfc_id,
            'message': 'Thẻ chưa đăng ký'
        })
        return jsonify({'success': False, 'error': 'Không tìm thấy khách', 'nfc_id': nfc_id}), 404

    entry = {
        'type': 'checkin',
        'nfc_id': nfc_id,
        'ho_ten': guest['ho_ten'],
        'loi_chao': guest['loi_chao'],
        'ghi_chu': guest.get('ghi_chu', ''),
        'timestamp': time.strftime('%H:%M:%S')
    }
    broadcast(entry)

    with checkin_history_lock:
        checkin_history.append(entry)

    return jsonify({'success': True, 'guest': guest})

# ─── Routes: Guest CRUD ──────────────────────────────────────

@app.route('/guests', methods=['GET'])
def get_guests():
    return jsonify(load_guests())

@app.route('/guests', methods=['POST'])
def add_guest():
    data = request.get_json()
    for field in ['nfc_id', 'ho_ten', 'loi_chao']:
        if not data.get(field):
            return jsonify({'success': False, 'error': f'Thiếu {field}'}), 400

    guests = load_guests()
    nfc_id = data['nfc_id'].strip().upper()

    # Kiểm tra trùng ID
    if any(g['nfc_id'].upper() == nfc_id for g in guests):
        return jsonify({'success': False, 'error': 'NFC ID đã tồn tại'}), 409

    guests.append({
        'nfc_id': nfc_id,
        'ho_ten': data['ho_ten'].strip(),
        'loi_chao': data['loi_chao'].strip(),
        'ghi_chu': data.get('ghi_chu', '').strip()
    })
    save_guests(guests)
    return jsonify({'success': True})

@app.route('/guests/<nfc_id>', methods=['PUT'])
def update_guest(nfc_id):
    data = request.get_json()
    guests = load_guests()
    nfc_id = nfc_id.strip().upper()

    for i, g in enumerate(guests):
        if g['nfc_id'].upper() == nfc_id:
            guests[i]['ho_ten'] = data.get('ho_ten', g['ho_ten']).strip()
            guests[i]['loi_chao'] = data.get('loi_chao', g['loi_chao']).strip()
            guests[i]['ghi_chu'] = data.get('ghi_chu', g.get('ghi_chu', '')).strip()
            save_guests(guests)
            return jsonify({'success': True})

    return jsonify({'success': False, 'error': 'Không tìm thấy khách'}), 404

@app.route('/guests/<nfc_id>', methods=['DELETE'])
def delete_guest(nfc_id):
    guests = load_guests()
    nfc_id = nfc_id.strip().upper()
    new_guests = [g for g in guests if g['nfc_id'].upper() != nfc_id]

    if len(new_guests) == len(guests):
        return jsonify({'success': False, 'error': 'Không tìm thấy khách'}), 404

    save_guests(new_guests)
    return jsonify({'success': True})

# ─── Routes: Check-in history ────────────────────────────────

@app.route('/history', methods=['GET'])
def get_history():
    with checkin_history_lock:
        return jsonify(list(reversed(checkin_history)))

# ─── Main ────────────────────────────────────────────────────

if __name__ == '__main__':
    os.makedirs(STATIC_DIR, exist_ok=True)
    # Tạo CSV mẫu nếu chưa có
    if not os.path.exists(CSV_FILE):
        save_guests([{
            'nfc_id': 'AABBCCDD',
            'ho_ten': 'Nguyễn Văn A',
            'loi_chao': 'Chào mừng anh A đến với chuyến đi!',
            'ghi_chu': 'Ghế số 1'
        }])
        print(f"Đã tạo file mẫu: {CSV_FILE}")

    print("Server chạy tại http://0.0.0.0:5000")
    app.run(host='0.0.0.0', port=5000, threaded=True)
