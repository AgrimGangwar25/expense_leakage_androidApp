import os
from datetime import datetime
from flask import Flask, request, jsonify
import psycopg2
from psycopg2.extras import RealDictCursor

app = Flask(__name__)

# Database connection details (Works with Docker & local PostgreSQL)
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

# 1. Keeps your browser status check working
@app.route('/', methods=['GET'])
def index():
    return "<h1>Expense Tracker Server is Running!</h1><p>Connected to PostgreSQL and waiting for SMS data at /sms...</p>"

# 2. Receives data from CategoryActivity.kt and saves it to PostgreSQL
@app.route('/sms', methods=['POST'])
def receive_sms():
    try:
        data = request.json
        sender = data.get('sender', 'Unknown')
        message = data.get('message', '')
        amount = float(str(data.get('amount', 0.0)).replace(',', ''))
        is_essential = bool(data.get('isEssential', True))
        category_name = data.get('category', 'Uncategorized')
        expense_date = datetime.now().date()

        print(f"\n--- New Transaction Received ---")
        print(f"From: {sender} | Amount: {amount} | Category: {category_name} | Essential: {is_essential}")
        print(f"Message: {message}")
        print(f"--------------------------------\n")

        conn = get_db_connection()
        cur = conn.cursor(cursor_factory=RealDictCursor)

        # Find existing category_id or create it automatically if new
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

        # Save the expense into PostgreSQL
        query = """
            INSERT INTO expenses 
            (amount, description, expense_date, source, user_id, category_id, status)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            RETURNING expense_id;
        """
        cur.execute(query, (amount, message, expense_date, sender, 1, category_id, 'categorized'))
        new_expense_id = cur.fetchone()['expense_id']

        conn.commit()
        cur.close()
        conn.close()

        return jsonify({
            "status": "success",
            "message": "Data received and saved to PostgreSQL",
            "expense_id": new_expense_id
        }), 200

    except Exception as e:
        print(f"Database Error: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
