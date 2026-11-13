# Expense Leakage Tracker

Expense Leakage Tracker is an Android application that detects transaction SMS messages, lets the user classify each transaction, and highlights non-essential spending ("leakage"). The app provides a local dashboard and history view, and can synchronise categorized transactions to a Flask API backed by PostgreSQL.

> **Project status:** This is a working development project. The Android client currently uses a hard-coded backend URL and the backend configuration contains development credentials. Review the security notes before deploying outside a trusted development network.

## Features

- Detects incoming transaction SMS messages using common payment keywords and currency markers.
- Extracts Indian Rupee, INR, and dollar amounts from supported SMS formats.
- Opens a classification prompt for each detected transaction.
- Classifies expenses as:
  - **Essential**
  - **Wasted** (non-essential spending)
- Assigns a category such as Food & Dining, Transportation, Shopping, Bills & Utilities, and more.
- Stores expenses locally on the device for offline dashboard and history access.
- Displays:
  - Total expenses
  - Money wasted
  - Remaining monthly budget
  - Essential, wasted, and unspent amounts in a pie chart
  - The five most recent expenses
- Sends categorized transactions to the Flask API for PostgreSQL storage.
- Shows a high-priority notification when spending exceeds the configured budget.
- Includes a complete expense history screen.

## Architecture

```text
Incoming SMS
    |
    v
SmsReceiver
    |
    +--> Transaction notification and classification screen
    |        |
    |        +--> SharedPreferences (local dashboard/history data)
    |        |
    |        +--> POST /sms
    |                  |
    |                  v
    |             Flask API
    |                  |
    |                  v
    |             PostgreSQL
    |
    v
MainActivity dashboard
```

### Android client

The Android module is written in Kotlin and uses XML layouts. Its main components are:

| Component | Responsibility |
| --- | --- |
| `MainActivity` | Dashboard, budget management, chart, recent expenses, permissions, and over-budget notifications |
| `SmsReceiver` | Receives SMS broadcasts, identifies likely transactions, extracts amounts, and opens the classification flow |
| `CategoryActivity` | Collects essential/wasted and category selections, saves locally, and synchronizes with the API |
| `HistoryActivity` | Displays all locally stored expenses in reverse chronological order |

Local expenses are stored as a JSON array in the `ExpensePrefs` `SharedPreferences` file under the `all_expenses` key. The default monthly budget is `₹10,000`.

### Backend

The backend is in [`flask server/`](C:/Users/agrim/Downloads/expense_tracker_android/flask%20server). It contains:

- Flask application and API route in [`app.py`](C:/Users/agrim/Downloads/expense_tracker_android/flask%20server/app.py)
- PostgreSQL schema and default category in [`init.sql`](C:/Users/agrim/Downloads/expense_tracker_android/flask%20server/init.sql)
- Docker image definition in [`Dockerfile`](C:/Users/agrim/Downloads/expense_tracker_android/flask%20server/Dockerfile)
- PostgreSQL and Flask services in [`docker-compose.yml`](C:/Users/agrim/Downloads/expense_tracker_android/flask%20server/docker-compose.yml)

## Requirements

### Android development

- Android Studio with Android SDK Platform 34
- JDK 8 or a compatible Android Studio-bundled JDK
- An Android device or emulator running Android 7.0 (API 24) or newer
- Gradle wrapper support; the project uses Gradle 8.4 and Android Gradle Plugin 8.2.0
- A device capable of receiving SMS for end-to-end SMS detection testing

### Backend development

- Docker Desktop with Docker Compose
- A device and development computer connected to the same network when testing with a physical Android device

## Getting started

### 1. Start the backend

From the repository root:

```bash
cd "flask server"
docker compose up --build -d
```

This starts:

| Service | Address | Purpose |
| --- | --- | --- |
| Flask API | `http://localhost:5000` | Receives and stores categorized expenses |
| PostgreSQL | `localhost:5433` | Persists backend expenses |

Check that the API is running:

```bash
curl http://localhost:5000/
```

Expected response:

```text
Expense Tracker Server is Running!
```

View service logs:

```bash
docker compose logs -f web
```

Stop the backend:

```bash
docker compose down
```

To also remove the PostgreSQL data volume:

```bash
docker compose down -v
```

### 2. Configure the Android API address

The Android client currently sends requests to:

```text
http://YOUR-IP-ADDRESS/sms
```

Update the URL in [`CategoryActivity.kt`](C:/Users/agrim/Downloads/expense_tracker_android/app/src/main/java/com/example/expensetracker/CategoryActivity.kt) before building if the backend computer has a different LAN address.

Common development values:

- Physical device: use the computer's LAN IP address, for example `http://YOUR-IP-ADDRESS/sms`.
- Android emulator: use `http://10.0.2.2:5000/sms` to reach the host computer.
- Do not use `localhost` from a physical device; it refers to the device itself.

The manifest enables cleartext HTTP for local development. Use HTTPS and a secure configuration mechanism before exposing the API beyond a trusted development network.

### 3. Build and install the Android app

Open the repository in Android Studio, allow Gradle sync to complete, then run the `app` configuration on a connected device or emulator.

Alternatively, from the repository root:

```bash
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it with Android Debug Bridge if a device is connected:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Android permissions

On first launch, grant the permissions required by the current implementation:

- **Receive SMS** and **Read SMS**: transaction detection.
- **Post notifications** on Android 13 and newer: transaction and budget notifications.
- **Display over other apps**: show the classification prompt immediately after a transaction SMS.

If a permission is denied, SMS detection, notifications, or the classification prompt may not work. Permissions can be changed later from the device's app settings.

## Using the app

1. Start the backend if server synchronization is required.
2. Launch the Android app and grant the requested permissions.
3. Set a monthly budget with **Set Budget**. The default is `₹10,000`.
4. When a supported transaction SMS arrives, review the detected amount.
5. Select **Essential** or **Wasted**, choose a category, and tap **Save Transaction**.
6. Review totals and the spending breakdown on the dashboard.
7. Tap the recent expenses card to view the complete history.
8. Use **Reset Data** to clear locally stored expenses and rebuild the dashboard.

Local data is saved before the server request is made. Therefore, a failed synchronization does not remove the expense from the device, but it also does not currently queue the request for automatic retry.

## API reference

### `GET /`

Returns a simple health/status page.

### `POST /sms`

Stores a categorized transaction in PostgreSQL.

Request body:

```json
{
  "sender": "BANK",
  "message": "INR 250 debited from your account",
  "amount": "250",
  "isEssential": false,
  "category": "Food & Dining",
  "timestamp": 1714387200000
}
```

Successful response:

```json
{
  "status": "success",
  "message": "Data received and saved to PostgreSQL",
  "expense_id": 1
}
```

The server creates a category when the submitted category does not already exist. Expenses are stored with the sender as `source`, the SMS body as `description`, the current server date as `expense_date`, and user ID `1`.

## Database schema

The PostgreSQL database is initialized by [`init.sql`](C:/Users/agrim/Downloads/expense_tracker_android/flask%20server/init.sql).

### `Categories`

- `category_id`: primary key
- `category_name`: unique category name
- `is_essential`: default essential classification

### `expenses`

- `expense_id`: primary key
- `amount`: numeric amount
- `description`: original transaction message
- `expense_date`: date stored by the API
- `source`: SMS sender
- `user_id`: currently defaults to `1`
- `category_id`: category reference
- `status`: currently defaults to `categorized`

## Project structure

```text
.
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/expensetracker/
│       │   ├── MainActivity.kt
│       │   ├── CategoryActivity.kt
│       │   ├── HistoryActivity.kt
│       │   └── SmsReceiver.kt
│       └── res/
├── flask server/
│   ├── app.py
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── init.sql
│   └── requirements.txt
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── gradlew / gradlew.bat
```

## Dependencies

The Android app uses:

- AndroidX Core KTX
- AndroidX AppCompat
- Material Components
- OkHttp
- Kotlin Coroutines
- MPAndroidChart

The backend uses:

- Flask
- psycopg2-binary
- Gunicorn
- PostgreSQL 15

## Troubleshooting

### The app does not receive transaction SMS messages

- Confirm SMS permissions are granted.
- Confirm the test message contains a supported transaction keyword and currency marker.
- Confirm the device/emulator can receive SMS broadcasts; many emulators cannot receive real banking SMS messages.
- Check logcat for the `SmsReceiver` tag.

### The classification prompt does not appear

- Grant the **Display over other apps** permission.
- Check that notifications are enabled.
- Verify that the app is not restricted by device battery/background execution settings.

### The API request fails

- Confirm the Docker services are running with `docker compose ps`.
- Confirm the Android device can reach the computer's LAN IP.
- Confirm Windows Firewall allows inbound connections to port `5000`.
- Verify that the URL in `CategoryActivity.kt` matches the selected device/emulator setup.
- Check backend output with `docker compose logs -f web`.

### PostgreSQL data is not available after restart

The Compose configuration uses the named `postgres_data` volume. Do not run `docker compose down -v` unless you intentionally want to delete the database volume.

## Security and production considerations

This repository is configured for local development and is not production-ready without additional hardening:

- Replace the development PostgreSQL password and do not commit real credentials.
- Configure secrets through environment variables or a secret manager.
- Replace cleartext HTTP with HTTPS.
- Move the API URL out of source code.
- Add authentication and authorization to the API.
- Validate request bodies and enforce server-side amount/category constraints.
- Avoid logging full transaction messages in production because they may contain sensitive financial information.
- Review SMS and overlay permissions against Google Play policy and the intended distribution channel.
- Add retry/queue handling for transactions that cannot be synchronized immediately.

## License

No license file is currently included. Add a license before distributing or publishing this project.
