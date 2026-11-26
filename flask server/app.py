import os
import re
from datetime import datetime
from flask import Flask, request, jsonify
import psycopg2
from psycopg2.extras import RealDictCursor

app = Flask(__name__)

# Database connection details
DB_HOST = os.getenv("DB_HOST", "db")
DB_NAME = os.getenv("DB_NAME", "expense_db")
DB_USER = os.getenv("DB_USER", "postgres")
DB_PASS = os.getenv("DB_PASS", "agrim2510")

def get_db_connection():
    return psycopg2.connect(
        host=DB_HOST,
        database=DB_NAME,
        user=DB_USER,
        password=DB_PASS
    )

def extract_clean_description(message):
    if not message:
        return "UPI Transaction"

    # Pattern 1: HDFC multi-line format ("To MEENU BHANDARI\nOn 19/11/26...")
    to_match = re.search(r'(?im)^To\s+([A-Za-z0-9 &._-]+?)(?:\r?\n|$)', message)
    if to_match:
        return to_match.group(1).strip()

    # Pattern 2: Single-line formats ("...at jaggi via UPI", "...paid to Zomato", "...to Swiggy on...")
    inline_match = re.search(
        r'(?i)\b(?:at|to)\s+([A-Za-z0-9 &._-]+?)(?:\s+(?:via|on|using|from|ref|upi)\b|[.,\r\n]|$)',
        message
    )
    if inline_match:
        merchant = inline_match.group(1).strip()
        if merchant.lower() not in {"your", "account", "a/c", "bank"}:
            return merchant

    # Fallback: Strip boilerplate ("Not You? Call...") and keep the first clean line
    first_line = message.split('\n')[0].strip()
    return first_line[:80] if first_line else "Transaction"

@app.route('/', methods=['GET'])
def index():
    return "<h1>Expense Tracker Server is Running!</h1><p>Connected to PostgreSQL and waiting for SMS data at /sms...</p>"

@app.route('/sms', methods=['POST'])
def receive_sms():
    try:
        data = request.json
        sender = data.get('sender', 'Unknown')
        raw_message = data.get('message', '')
        clean_description = extract_clean_description(raw_message)
        
        amount = float(str(data.get('amount', 0.0)).replace(',', ''))
        is_essential = bool(data.get('isEssential', True))
        category_name = data.get('category', 'Uncategorized')
        expense_date = datetime.now().date()

        print(f"\n--- New Transaction Received ---")
        print(f"From: {sender} | Amount: {amount} | Category: {category_name} | Essential: {is_essential}")
        print(f"Clean Description: {clean_description}")
        print(f"--------------------------------\n")

        conn = get_db_connection()
        cur = conn.cursor(cursor_factory=RealDictCursor)

        cur.execute(
            "SELECT category_id FROM Categories WHERE LOWER(category_name) = LOWER(%s) LIMIT 1;",
            (category_name,)
        )
        cat_row = cur.fetchone()

        if cat_row:
            category_id = cat_row['category_id']
        else:
            cur.execute(
                "INSERT INTO Categories (category_name, is_essential) VALUES (%s, %s) RETURNING category_id;",
                (category_name, is_essential)
            )
            category_id = cur.fetchone()['category_id']

        query = """
            INSERT INTO expenses 
            (amount, description, expense_date, source, user_id, category_id, status)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            RETURNING expense_id;
        """
        cur.execute(query, (amount, clean_description, expense_date, sender, 1, category_id, 'categorized'))
        new_expense_id = cur.fetchone()['expense_id']

        conn.commit()
        cur.close()
        conn.close()

        return jsonify({
            "status": "success",
            "message": "Data received and saved to PostgreSQL",
            "expense_id": new_expense_id,
            "description": clean_description
        }), 200

    except Exception as e:
        print(f"Database Error: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
