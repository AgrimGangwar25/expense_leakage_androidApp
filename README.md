# Expense Leakage Tracker

Expense Leakage Tracker is an Android application that detects transaction SMS messages, lets the user classify each transaction, and highlights non-essential spending ("leakage"). The app provides a local dashboard and history view, and synchronizes categorized transactions to a containerized Flask API hosted on **AWS EC2** with PostgreSQL provided by **AWS RDS** and automated **GitHub Actions CI/CD**.

> **Project status:** A live cloud-deployed prototype featuring an Android client in Kotlin, a Dockerized Flask backend on AWS EC2 (`t3.micro`), and an AWS RDS PostgreSQL database.

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
    |             AWS RDS PostgreSQL
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
- Flask service and RDS environment variables in [`docker-compose.yml`](C:/Users/agrim/Downloads/expense_tracker_android/flask%20server/docker-compose.yml)

### AWS deployment

The production backend is hosted on an AWS EC2 `t3.micro` instance running Ubuntu 26.04.

```text
GitHub push to main
    |
    v
GitHub Actions deploy workflow
    |
    | SSH using the EC2 secrets
    v
EC2 Elastic IP: 32.236.221.252
    |
    +--> git pull origin main
    +--> docker compose up --build -d
    +--> docker image prune -f
            |
            v
    Flask container
            |
            v
    AWS RDS PostgreSQL
```

The instance is assigned an AWS Elastic IP, so the public address remains stable across instance restarts and redeployments. The Flask API is exposed on port `5000`. PostgreSQL is no longer run in Docker Compose; it is managed by AWS RDS:

| Service | Public/internal address | Purpose |
| --- | --- | --- |
| Flask API | `http://32.236.221.252:5000` | Receives Android expense requests |
| Expense POST endpoint | `http://32.236.221.252:5000/sms` | Stores categorized expenses |
| AWS RDS PostgreSQL | Private RDS endpoint on TCP `5432` | Persists backend expenses |

The Android client is configured to use the Elastic IP in [`CategoryActivity.kt`](C:/Users/agrim/Downloads/expense_tracker_android/app/src/main/java/com/example/expensetracker/CategoryActivity.kt).

## GitHub Actions CI/CD

The deployment workflow is [`deploy.yml`](C:/Users/agrim/Downloads/expense_tracker_android/.github/workflows/deploy.yml). It runs when a commit is pushed to `main` and changes either:

- anything under [`flask server/`](C:/Users/agrim/Downloads/expense_tracker_android/flask%20server)
- [`.github/workflows/deploy.yml`](C:/Users/agrim/Downloads/expense_tracker_android/.github/workflows/deploy.yml)

The workflow uses Appleboy's SSH action to connect to EC2, then executes the following operations on the server:

```bash
cd ~/expense_leakage_androidApp
git pull origin main
cd "flask server"
sudo docker compose up --build -d
sudo docker image prune -f
```

Configure these repository secrets in **GitHub > Settings > Secrets and variables > Actions**:

| Secret | Value |
| --- | --- |
| `EC2_HOST` | Elastic IP address of the EC2 instance |
| `EC2_USER` | SSH user for the Ubuntu instance, typically `ubuntu` |
| `EC2_SSH_KEY` | Private SSH key used to access the EC2 instance |

Never commit `EC2_SSH_KEY`, database credentials, or other secrets to the repository.

### EC2 setup

Before the first deployment, the EC2 instance needs Git, Docker, and Docker Compose:

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker ubuntu
```

Log out and back in if Docker group permissions are added, then clone the repository into the path expected by the workflow:

```bash
cd ~
git clone https://github.com/AgrimGangwar25/expense_leakage_androidApp.git
cd expense_leakage_androidApp
git checkout main
```

In the EC2 security group, allow:

- SSH (TCP `22`) from the administrator's trusted IP, or another appropriately restricted source
- Flask HTTP (TCP `5000`) from the networks that need API access

The Elastic IP can then be associated with the instance for a stable public endpoint.

Configure the RDS security group to allow PostgreSQL traffic (TCP `5432`) only from the EC2 instance security group. Do not expose RDS port `5432` to the public internet.

### RDS setup

Create or identify an AWS RDS PostgreSQL instance and database for the application. Record the RDS endpoint, database name, master/user credentials, and port. Configure the RDS security group so the EC2 application instance can connect on port `5432`.

Apply the repository schema to RDS once with a PostgreSQL client:

```bash
psql "host=<RDS_ENDPOINT> port=5432 dbname=<DB_NAME> user=<DB_USER> sslmode=require" \
  -f "flask server/init.sql"
```

The `psql` command will prompt for the database password. RDS initialization is independent of Docker Compose; redeploying or restarting the Flask container does not recreate or modify the RDS database.

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
| Flask API | `http://localhost:5000` | Receives categorized expenses and connects to RDS PostgreSQL |

Docker Compose reads the RDS connection settings from its `environment` section:

```text
DB_HOST=RDS endpoint
DB_NAME=RDS database name
DB_USER=RDS database username
DB_PASS=RDS database password
```

For local development, configure `docker-compose.yml` or use environment-variable substitution. Do not commit real RDS credentials. A recommended production approach is to store database credentials in AWS Systems Manager Parameter Store or AWS Secrets Manager and inject them when deploying the container.

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

Stopping Docker Compose does not stop or delete the RDS database. The RDS instance and all of its data remain managed independently.

### 2. Configure the Android API address

The Android client currently sends requests to the deployed AWS endpoint:

```text
http://32.236.221.252:5000/sms
```

For a local development backend, update the URL in [`CategoryActivity.kt`](C:/Users/agrim/Downloads/expense_tracker_android/app/src/main/java/com/example/expensetracker/CategoryActivity.kt) to the appropriate LAN or emulator address.

Common development values:

- AWS deployment: `http://32.236.221.252:5000/sms`.
- Physical device on a local network: use the computer's LAN IP address, for example `http://192.168.1.10:5000/sms`.
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

The AWS RDS PostgreSQL database schema is defined in [`init.sql`](C:/Users/agrim/Downloads/expense_tracker_android/flask%20server/init.sql). Apply it once to RDS after creating the database; Docker Compose does not contain a PostgreSQL service or initialize an RDS database.

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
- AWS RDS PostgreSQL

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

- Confirm the EC2 instance is running and its Elastic IP is associated with the instance.
- Confirm port `5000` is allowed by the EC2 security group and the route is not blocked by a network firewall.
- Check a deployed health request with `curl http://32.236.221.252:5000/`.
- For GitHub Actions failures, open the workflow run and inspect the SSH deployment logs.
- On EC2, confirm the containers are running with `sudo docker compose ps` and inspect logs with `sudo docker compose logs -f web`.
- Confirm the Android URL is `http://32.236.221.252:5000/sms` unless the app is intentionally using a local backend.
- Confirm the Docker services are running with `docker compose ps`.
- Confirm the Android device can reach the computer's LAN IP.
- Confirm Windows Firewall allows inbound connections to port `5000`.
- Verify that the URL in `CategoryActivity.kt` matches the selected device/emulator setup.
- Check backend output with `docker compose logs -f web`.

### PostgreSQL data is unavailable or the API returns a database error

- Confirm the RDS endpoint, database name, username, and password in the Compose environment settings.
- Confirm the RDS security group allows inbound PostgreSQL traffic (TCP `5432`) from the EC2 or local-development network.
- Confirm the RDS instance is available and the EC2 instance can resolve and reach the RDS endpoint.
- Ensure [`init.sql`](C:/Users/agrim/Downloads/expense_tracker_android/flask%20server/init.sql) has been applied to the RDS database.
- Restart the Flask container after changing database environment variables:

```bash
sudo docker compose up --build -d
sudo docker compose logs -f web
```

## Security and production considerations

This repository is configured for local development and is not production-ready without additional hardening:

- Remove hard-coded RDS credentials from `docker-compose.yml` and do not commit real database passwords.
- Configure RDS credentials through environment variables or a secret manager.
- Restrict RDS access to trusted application hosts and do not expose port `5432` publicly.
- Replace cleartext HTTP with HTTPS.
- Move the API URL out of source code.
- Add authentication and authorization to the API.
- Validate request bodies and enforce server-side amount/category constraints.
- Avoid logging full transaction messages in production because they may contain sensitive financial information.
- Review SMS and overlay permissions against Google Play policy and the intended distribution channel.
- Add retry/queue handling for transactions that cannot be synchronized immediately.

## License

No license file is currently included. Add a license before distributing or publishing this project.
