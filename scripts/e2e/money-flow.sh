#!/usr/bin/env bash
# End-to-end money flow across all five services, against a running local stack:
#   register + verify email (Mailpit) -> customer profile -> KYC approval -> two accounts
#   -> ops deposit -> transfer -> overspend refused -> ledger reconciliation.
#
# Prerequisites (see README "Run locally"):
#   docker compose up -d --wait
#   each service running with the "local" profile (ports 8081-8085)
#
# Staff roles are granted through the admin API by the bootstrap administrator: auth's "local"
# profile (and compose) make $ADMIN_EMAIL the first admin once it is verified. The script registers
# it on the first run and reuses it afterwards. Never point this script at a shared environment.
#
# Exit code 0 = every check passed. Requires bash, curl and Python 3.
set -euo pipefail

AUTH=${AUTH_URL:-http://localhost:8081}
CUSTOMER=${CUSTOMER_URL:-http://localhost:8082}
ACCOUNT=${ACCOUNT_URL:-http://localhost:8083}
LEDGER=${LEDGER_URL:-http://localhost:8084}
PAYMENT=${PAYMENT_URL:-http://localhost:8085}
MAILPIT=${MAILPIT_URL:-http://localhost:8025}
# Must match auth's security.bootstrap-admin.email (local profile and compose default).
ADMIN_EMAIL=${BOOTSTRAP_ADMIN_EMAIL:-admin@novabank.local}
PASSWORD='correct horse battery staple'
RUN=$(date +%s)$RANDOM

# The first interpreter that really runs (on Windows "python3" can be a Microsoft Store stub).
PY=""
for candidate in python3 python; do
  if "$candidate" -c 'import sys; assert sys.version_info >= (3,)' 2>/dev/null; then
    PY=$candidate
    break
  fi
done
[ -n "$PY" ] || { echo "Python 3 is required" >&2; exit 1; }
cd "$(dirname "$0")/../.."

# ---------- helpers ----------
json() {
  "$PY" -c "
import sys, json
body = sys.stdin.read()
if not body.strip():
    sys.exit('FAIL: empty response (is the service running, and did the request succeed?)')
d = json.loads(body)
print($1)"
}
fail() { echo "FAIL: $*" >&2; exit 1; }
expect() { # name actual expected
  if [ "$2" = "$3" ]; then echo "  ok   $1 = $2"; else fail "$1: expected '$3', got '$2'"; fi
}
uuid() { "$PY" -c "import uuid; print(uuid.uuid4())"; }

register_verified_user() { # email
  curl -sf -o /dev/null -X POST "$AUTH/api/v1/users/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}" || fail "register $1"
  local id="" token
  for _ in $(seq 1 20); do
    id=$(curl -s "$MAILPIT/api/v1/search?query=to:%22$1%22" | json "d['messages'][0]['ID'] if d['messages'] else ''")
    [ -n "$id" ] && break
    sleep 1
  done
  [ -n "$id" ] || fail "no verification email for $1"
  token=$(curl -s "$MAILPIT/api/v1/message/$id" | json "d['Text']" | sed -n 's/.*#token=\([A-Za-z0-9_-]*\).*/\1/p')
  curl -sf -o /dev/null -X POST "$AUTH/api/v1/users/verify-email" -H 'Content-Type: application/json' \
    -d "{\"token\":\"$token\"}" || fail "verify $1"
}

# Signs in, registering and verifying the address first if it doesn't exist yet.
login_or_register() { # email
  local token
  token=$(curl -s -X POST "$AUTH/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}" | json "d.get('accessToken', '')" 2>/dev/null || true)
  if [ -z "$token" ]; then
    register_verified_user "$1"
    token=$(login "$1")
  fi
  echo "$token"
}

grant_role() { # admin-token email role
  local id
  id=$(curl -sf -X POST "$AUTH/api/v1/admin/users/lookup" -H "Authorization: Bearer $1" \
    -H 'Content-Type: application/json' -d "{\"email\":\"$2\"}" | json "d['userId']") || fail "look up $2"
  curl -sf -o /dev/null -X PUT "$AUTH/api/v1/admin/users/$id/roles/$3" -H "Authorization: Bearer $1" \
    || fail "grant $3 to $2"
}

login() {
  curl -sf -X POST "$AUTH/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}" | json "d['accessToken']"
}

await_payment() { # url token -> "STATUS REASON"
  local status=""
  for _ in $(seq 1 60); do
    status=$(curl -s "$1" -H "Authorization: Bearer $2" | json "d['status']")
    case "$status" in COMPLETED | FAILED) break ;; esac
    sleep 1
  done
  curl -s "$1" -H "Authorization: Bearer $2" | json "d['status'] + ' ' + (d['failureReason'] or '')" | sed 's/ $//'
}

await_available() { # accountId token expected
  local available=""
  for _ in $(seq 1 30); do
    available=$(curl -s "$ACCOUNT/api/v1/accounts/$1/balance" -H "Authorization: Bearer $2" | json "d['available']")
    [ "$available" = "$3" ] && break
    sleep 1
  done
  echo "$available"
}

open_account() { # token
  curl -sf -X POST "$ACCOUNT/api/v1/accounts" -H "Authorization: Bearer $1" -H "Idempotency-Key: e2e-$RUN-$(uuid)" \
    -H 'Content-Type: application/json' -d '{"type":"CURRENT","currency":"INR"}'
}

# ---------- users ----------
echo "1. Users"
CUSTOMER_EMAIL="e2e-customer-$RUN@example.com"
STAFF_EMAIL="e2e-staff-$RUN@example.com"
register_verified_user "$CUSTOMER_EMAIL"
register_verified_user "$STAFF_EMAIL"
AT=$(login_or_register "$ADMIN_EMAIL")
grant_role "$AT" "$STAFF_EMAIL" COMPLIANCE_OFFICER
grant_role "$AT" "$STAFF_EMAIL" OPERATIONS
CT=$(login "$CUSTOMER_EMAIL")
ST=$(login "$STAFF_EMAIL")
echo "  ok   customer and staff signed in"

# ---------- customer + KYC ----------
echo "2. Customer profile and KYC"
CUSTOMER_ID=$(curl -sf -X POST "$CUSTOMER/api/v1/customers" -H "Authorization: Bearer $CT" -H 'Content-Type: application/json' \
  -d '{"firstName":"Asha","lastName":"Rao","dateOfBirth":"1994-04-12","phone":"+91 98111 22233"}' | json "d['customerId']")
expect "open account before KYC" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$ACCOUNT/api/v1/accounts" -H "Authorization: Bearer $CT" \
    -H "Idempotency-Key: e2e-$RUN-early" -H 'Content-Type: application/json' -d '{"type":"CURRENT","currency":"INR"}')" "403"
expect "KYC approval" "$(curl -s -X POST "$CUSTOMER/api/v1/compliance/customers/$CUSTOMER_ID/kyc-approval" \
  -H "Authorization: Bearer $ST" | json "d['status']")" "ACTIVE"

# ---------- accounts ----------
echo "3. Accounts"
A=""
for _ in $(seq 1 30); do # CustomerVerified travels through Kafka first
  A=$(open_account "$CT" 2>/dev/null | json "d['accountId']" 2>/dev/null || true)
  [ -n "$A" ] && break
  sleep 1
done
[ -n "$A" ] || fail "account A not opened"
B_JSON=$(open_account "$CT")
B=$(echo "$B_JSON" | json "d['accountId']")
B_NUMBER=$(echo "$B_JSON" | json "d['accountNumber']")
echo "  ok   A=$A B=$B ($B_NUMBER)"

# New accounts are PENDING until the ledger confirms its side (LedgerAccountOpened, roadmap 6.4);
# only then can money move. Integration tests cover PENDING itself; here it may already be over.
statuses=""
for _ in $(seq 1 30); do
  statuses=$(curl -s "$ACCOUNT/api/v1/accounts" -H "Authorization: Bearer $CT" \
    | json "' '.join(sorted(a['status'] for a in d if a['accountId'] in ('$A', '$B')))")
  [ "$statuses" = "ACTIVE ACTIVE" ] && break
  sleep 1
done
expect "both accounts confirmed by the ledger" "$statuses" "ACTIVE ACTIVE"

# ---------- money ----------
echo "4. Deposit 500.00 into A (operations)"
DEPOSIT=$(curl -sf -X POST "$PAYMENT/api/v1/admin/deposits" -H "Authorization: Bearer $ST" -H "Idempotency-Key: e2e-$RUN-deposit" \
  -H 'Content-Type: application/json' -d "{\"toAccountId\":\"$A\",\"amount\":\"500.00\",\"currency\":\"INR\"}" | json "d['paymentId']")
expect "deposit" "$(await_payment "$PAYMENT/api/v1/admin/payments/$DEPOSIT" "$ST")" "COMPLETED"
expect "A available" "$(await_available "$A" "$CT" "500.00")" "500.00"

echo "5. Transfer 120.00 from A to B by account number"
BODY="{\"fromAccountId\":\"$A\",\"toAccountNumber\":\"$B_NUMBER\",\"amount\":\"120.00\",\"currency\":\"INR\"}"
TRANSFER=$(curl -sf -X POST "$PAYMENT/api/v1/payments" -H "Authorization: Bearer $CT" -H "Idempotency-Key: e2e-$RUN-transfer" \
  -H 'Content-Type: application/json' -d "$BODY" | json "d['paymentId']")
expect "transfer" "$(await_payment "$PAYMENT/api/v1/payments/$TRANSFER" "$CT")" "COMPLETED"
RETRY=$(curl -sf -X POST "$PAYMENT/api/v1/payments" -H "Authorization: Bearer $CT" -H "Idempotency-Key: e2e-$RUN-transfer" \
  -H 'Content-Type: application/json' -d "$BODY" | json "d['paymentId']")
expect "retry with the same Idempotency-Key returns the same payment" "$RETRY" "$TRANSFER"
expect "A available" "$(await_available "$A" "$CT" "380.00")" "380.00"
expect "B available" "$(await_available "$B" "$CT" "120.00")" "120.00"

echo "6. Overspend 1000.00 is refused"
BIG=$(curl -sf -X POST "$PAYMENT/api/v1/payments" -H "Authorization: Bearer $CT" -H "Idempotency-Key: e2e-$RUN-big" \
  -H 'Content-Type: application/json' \
  -d "{\"fromAccountId\":\"$A\",\"toAccountNumber\":\"$B_NUMBER\",\"amount\":\"1000.00\",\"currency\":\"INR\"}" | json "d['paymentId']")
expect "overspend" "$(await_payment "$PAYMENT/api/v1/payments/$BIG" "$CT")" "FAILED INSUFFICIENT_FUNDS"
expect "A still available" "$(await_available "$A" "$CT" "380.00")" "380.00"

echo "7. Ledger"
expect "A ledger balance" "$(curl -s "$LEDGER/api/v1/ledger/accounts/$A" -H "Authorization: Bearer $ST" | json "d['balance']")" "380.00"
expect "reconciliation" "$(curl -s "$LEDGER/api/v1/ledger/reconciliation" -H "Authorization: Bearer $ST" | json "d['balanced']")" "True"
expect "customers can't read the ledger" \
  "$(curl -s -o /dev/null -w '%{http_code}' "$LEDGER/api/v1/ledger/reconciliation" -H "Authorization: Bearer $CT")" "403"

echo "PASS: money flow end to end"
